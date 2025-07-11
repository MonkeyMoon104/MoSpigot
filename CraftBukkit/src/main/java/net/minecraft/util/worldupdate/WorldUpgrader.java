package net.minecraft.util.worldupdate;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Reference2FloatMap;
import it.unimi.dsi.fastutil.objects.Reference2FloatMaps;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemUtils;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.World;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.IChunkLoader;
import net.minecraft.world.level.chunk.storage.RecreatingChunkStorage;
import net.minecraft.world.level.chunk.storage.RecreatingSimpleRegionStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.level.saveddata.PersistentBase;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.SaveData;
import net.minecraft.world.level.storage.WorldPersistentData;
import org.slf4j.Logger;

public class WorldUpgrader implements AutoCloseable {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadFactory THREAD_FACTORY = (new ThreadFactoryBuilder()).setDaemon(true).build();
    private static final String NEW_DIRECTORY_PREFIX = "new_";
    static final IChatBaseComponent STATUS_UPGRADING_POI = IChatBaseComponent.translatable("optimizeWorld.stage.upgrading.poi");
    static final IChatBaseComponent STATUS_FINISHED_POI = IChatBaseComponent.translatable("optimizeWorld.stage.finished.poi");
    static final IChatBaseComponent STATUS_UPGRADING_ENTITIES = IChatBaseComponent.translatable("optimizeWorld.stage.upgrading.entities");
    static final IChatBaseComponent STATUS_FINISHED_ENTITIES = IChatBaseComponent.translatable("optimizeWorld.stage.finished.entities");
    static final IChatBaseComponent STATUS_UPGRADING_CHUNKS = IChatBaseComponent.translatable("optimizeWorld.stage.upgrading.chunks");
    static final IChatBaseComponent STATUS_FINISHED_CHUNKS = IChatBaseComponent.translatable("optimizeWorld.stage.finished.chunks");
    final IRegistry<WorldDimension> dimensions;
    final Set<ResourceKey<World>> levels;
    final boolean eraseCache;
    final boolean recreateRegionFiles;
    final Convertable.ConversionSession levelStorage;
    private final Thread thread;
    final DataFixer dataFixer;
    volatile boolean running = true;
    private volatile boolean finished;
    volatile float progress;
    volatile int totalChunks;
    volatile int totalFiles;
    volatile int converted;
    volatile int skipped;
    final Reference2FloatMap<ResourceKey<World>> progressMap = Reference2FloatMaps.synchronize(new Reference2FloatOpenHashMap());
    volatile IChatBaseComponent status = IChatBaseComponent.translatable("optimizeWorld.stage.counting");
    static final Pattern REGEX = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
    final WorldPersistentData overworldDataStorage;

    public WorldUpgrader(Convertable.ConversionSession convertable_conversionsession, DataFixer datafixer, SaveData savedata, IRegistryCustom iregistrycustom, boolean flag, boolean flag1) {
        this.dimensions = iregistrycustom.lookupOrThrow(Registries.LEVEL_STEM);
        this.levels = (Set) java.util.stream.Stream.of(convertable_conversionsession.dimensionType).map(Registries::levelStemToLevel).collect(Collectors.toUnmodifiableSet()); // CraftBukkit
        this.eraseCache = flag;
        this.dataFixer = datafixer;
        this.levelStorage = convertable_conversionsession;
        PersistentBase.a persistentbase_a = new PersistentBase.a((WorldServer) null, savedata.worldGenOptions().seed());

        this.overworldDataStorage = new WorldPersistentData(persistentbase_a, this.levelStorage.getDimensionPath(World.OVERWORLD).resolve("data"), datafixer, iregistrycustom);
        this.recreateRegionFiles = flag1;
        this.thread = WorldUpgrader.THREAD_FACTORY.newThread(this::work);
        this.thread.setUncaughtExceptionHandler((thread, throwable) -> {
            WorldUpgrader.LOGGER.error("Error upgrading world", throwable);
            this.status = IChatBaseComponent.translatable("optimizeWorld.stage.failed");
            this.finished = true;
        });
        this.thread.start();
    }

    public void cancel() {
        this.running = false;

        try {
            this.thread.join();
        } catch (InterruptedException interruptedexception) {
            ;
        }

    }

    private void work() {
        long i = SystemUtils.getMillis();

        WorldUpgrader.LOGGER.info("Upgrading entities");
        (new WorldUpgrader.d()).upgrade();
        WorldUpgrader.LOGGER.info("Upgrading POIs");
        (new WorldUpgrader.f()).upgrade();
        WorldUpgrader.LOGGER.info("Upgrading blocks");
        (new WorldUpgrader.b()).upgrade();
        this.overworldDataStorage.saveAndJoin();
        i = SystemUtils.getMillis() - i;
        WorldUpgrader.LOGGER.info("World optimizaton finished after {} seconds", i / 1000L);
        this.finished = true;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public Set<ResourceKey<World>> levels() {
        return this.levels;
    }

    public float dimensionProgress(ResourceKey<World> resourcekey) {
        return this.progressMap.getFloat(resourcekey);
    }

    public float getProgress() {
        return this.progress;
    }

    public int getTotalChunks() {
        return this.totalChunks;
    }

    public int getConverted() {
        return this.converted;
    }

    public int getSkipped() {
        return this.skipped;
    }

    public IChatBaseComponent getStatus() {
        return this.status;
    }

    public void close() {
        this.overworldDataStorage.close();
    }

    static Path resolveRecreateDirectory(Path path) {
        return path.resolveSibling("new_" + path.getFileName().toString());
    }

    static record c<T>(ResourceKey<World> dimensionKey, T storage, ListIterator<WorldUpgrader.e> files) {

    }

    static record e(RegionFile file, List<ChunkCoordIntPair> chunksToUpgrade) {

    }

    private abstract class a<T extends AutoCloseable> {

        private final IChatBaseComponent upgradingStatus;
        private final IChatBaseComponent finishedStatus;
        private final String type;
        private final String folderName;
        @Nullable
        protected CompletableFuture<Void> previousWriteFuture;
        protected final DataFixTypes dataFixType;

        a(final DataFixTypes datafixtypes, final String s, final String s1, final IChatBaseComponent ichatbasecomponent, final IChatBaseComponent ichatbasecomponent1) {
            this.dataFixType = datafixtypes;
            this.type = s;
            this.folderName = s1;
            this.upgradingStatus = ichatbasecomponent;
            this.finishedStatus = ichatbasecomponent1;
        }

        public void upgrade() {
            WorldUpgrader.this.totalFiles = 0;
            WorldUpgrader.this.totalChunks = 0;
            WorldUpgrader.this.converted = 0;
            WorldUpgrader.this.skipped = 0;
            List<WorldUpgrader.c<T>> list = this.getDimensionsToUpgrade();

            if (WorldUpgrader.this.totalChunks != 0) {
                float f = (float) WorldUpgrader.this.totalFiles;

                WorldUpgrader.this.status = this.upgradingStatus;

                while (WorldUpgrader.this.running) {
                    boolean flag = false;
                    float f1 = 0.0F;

                    for (WorldUpgrader.c<T> worldupgrader_c : list) {
                        ResourceKey<World> resourcekey = worldupgrader_c.dimensionKey;
                        ListIterator<WorldUpgrader.e> listiterator = worldupgrader_c.files;
                        T t0 = worldupgrader_c.storage;

                        if (listiterator.hasNext()) {
                            WorldUpgrader.e worldupgrader_e = (WorldUpgrader.e) listiterator.next();
                            boolean flag1 = true;

                            for (ChunkCoordIntPair chunkcoordintpair : worldupgrader_e.chunksToUpgrade) {
                                flag1 = flag1 && this.processOnePosition(resourcekey, t0, chunkcoordintpair);
                                flag = true;
                            }

                            if (WorldUpgrader.this.recreateRegionFiles) {
                                if (flag1) {
                                    this.onFileFinished(worldupgrader_e.file);
                                } else {
                                    WorldUpgrader.LOGGER.error("Failed to convert region file {}", worldupgrader_e.file.getPath());
                                }
                            }
                        }

                        float f2 = (float) listiterator.nextIndex() / f;

                        WorldUpgrader.this.progressMap.put(resourcekey, f2);
                        f1 += f2;
                    }

                    WorldUpgrader.this.progress = f1;
                    if (!flag) {
                        break;
                    }
                }

                WorldUpgrader.this.status = this.finishedStatus;

                for (WorldUpgrader.c<T> worldupgrader_c1 : list) {
                    try {
                        ((AutoCloseable) worldupgrader_c1.storage).close();
                    } catch (Exception exception) {
                        WorldUpgrader.LOGGER.error("Error upgrading chunk", exception);
                    }
                }

            }
        }

        private List<WorldUpgrader.c<T>> getDimensionsToUpgrade() {
            List<WorldUpgrader.c<T>> list = Lists.newArrayList();

            for (ResourceKey<World> resourcekey : WorldUpgrader.this.levels) {
                RegionStorageInfo regionstorageinfo = new RegionStorageInfo(WorldUpgrader.this.levelStorage.getLevelId(), resourcekey, this.type);
                Path path = WorldUpgrader.this.levelStorage.getDimensionPath(resourcekey).resolve(this.folderName);
                T t0 = this.createStorage(regionstorageinfo, path);
                ListIterator<WorldUpgrader.e> listiterator = this.getFilesToProcess(regionstorageinfo, path);

                list.add(new WorldUpgrader.c(resourcekey, t0, listiterator));
            }

            return list;
        }

        protected abstract T createStorage(RegionStorageInfo regionstorageinfo, Path path);

        private ListIterator<WorldUpgrader.e> getFilesToProcess(RegionStorageInfo regionstorageinfo, Path path) {
            List<WorldUpgrader.e> list = getAllChunkPositions(regionstorageinfo, path);

            WorldUpgrader.this.totalFiles += list.size();
            WorldUpgrader.this.totalChunks += list.stream().mapToInt((worldupgrader_e) -> {
                return worldupgrader_e.chunksToUpgrade.size();
            }).sum();
            return list.listIterator();
        }

        private static List<WorldUpgrader.e> getAllChunkPositions(RegionStorageInfo regionstorageinfo, Path path) {
            File[] afile = path.toFile().listFiles((file, s) -> {
                return s.endsWith(".mca");
            });

            if (afile == null) {
                return List.of();
            } else {
                List<WorldUpgrader.e> list = Lists.newArrayList();

                for (File file : afile) {
                    Matcher matcher = WorldUpgrader.REGEX.matcher(file.getName());

                    if (matcher.matches()) {
                        int i = Integer.parseInt(matcher.group(1)) << 5;
                        int j = Integer.parseInt(matcher.group(2)) << 5;
                        List<ChunkCoordIntPair> list1 = Lists.newArrayList();

                        try (RegionFile regionfile = new RegionFile(regionstorageinfo, file.toPath(), path, true)) {
                            for (int k = 0; k < 32; ++k) {
                                for (int l = 0; l < 32; ++l) {
                                    ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(k + i, l + j);

                                    if (regionfile.doesChunkExist(chunkcoordintpair)) {
                                        list1.add(chunkcoordintpair);
                                    }
                                }
                            }

                            if (!list1.isEmpty()) {
                                list.add(new WorldUpgrader.e(regionfile, list1));
                            }
                        } catch (Throwable throwable) {
                            WorldUpgrader.LOGGER.error("Failed to read chunks from region file {}", file.toPath(), throwable);
                        }
                    }
                }

                return list;
            }
        }

        private boolean processOnePosition(ResourceKey<World> resourcekey, T t0, ChunkCoordIntPair chunkcoordintpair) {
            boolean flag = false;

            try {
                flag = this.tryProcessOnePosition(t0, chunkcoordintpair, resourcekey);
            } catch (CompletionException | ReportedException reportedexception) {
                Throwable throwable = ((RuntimeException) reportedexception).getCause();

                if (!(throwable instanceof IOException)) {
                    throw reportedexception;
                }

                WorldUpgrader.LOGGER.error("Error upgrading chunk {}", chunkcoordintpair, throwable);
            }

            if (flag) {
                ++WorldUpgrader.this.converted;
            } else {
                ++WorldUpgrader.this.skipped;
            }

            return flag;
        }

        protected abstract boolean tryProcessOnePosition(T t0, ChunkCoordIntPair chunkcoordintpair, ResourceKey<World> resourcekey);

        private void onFileFinished(RegionFile regionfile) {
            if (WorldUpgrader.this.recreateRegionFiles) {
                if (this.previousWriteFuture != null) {
                    this.previousWriteFuture.join();
                }

                Path path = regionfile.getPath();
                Path path1 = path.getParent();
                Path path2 = WorldUpgrader.resolveRecreateDirectory(path1).resolve(path.getFileName().toString());

                try {
                    if (path2.toFile().exists()) {
                        Files.delete(path);
                        Files.move(path2, path);
                    } else {
                        WorldUpgrader.LOGGER.error("Failed to replace an old region file. New file {} does not exist.", path2);
                    }
                } catch (IOException ioexception) {
                    WorldUpgrader.LOGGER.error("Failed to replace an old region file", ioexception);
                }

            }
        }
    }

    private abstract class g extends WorldUpgrader.a<SimpleRegionStorage> {

        g(final DataFixTypes datafixtypes, final String s, final IChatBaseComponent ichatbasecomponent, final IChatBaseComponent ichatbasecomponent1) {
            super(datafixtypes, s, s, ichatbasecomponent, ichatbasecomponent1);
        }

        @Override
        protected SimpleRegionStorage createStorage(RegionStorageInfo regionstorageinfo, Path path) {
            return (SimpleRegionStorage) (WorldUpgrader.this.recreateRegionFiles ? new RecreatingSimpleRegionStorage(regionstorageinfo.withTypeSuffix("source"), path, regionstorageinfo.withTypeSuffix("target"), WorldUpgrader.resolveRecreateDirectory(path), WorldUpgrader.this.dataFixer, true, this.dataFixType) : new SimpleRegionStorage(regionstorageinfo, path, WorldUpgrader.this.dataFixer, true, this.dataFixType));
        }

        protected boolean tryProcessOnePosition(SimpleRegionStorage simpleregionstorage, ChunkCoordIntPair chunkcoordintpair, ResourceKey<World> resourcekey) {
            NBTTagCompound nbttagcompound = (NBTTagCompound) ((Optional) simpleregionstorage.read(chunkcoordintpair).join()).orElse((Object) null);

            if (nbttagcompound != null) {
                int i = IChunkLoader.getVersion(nbttagcompound);
                NBTTagCompound nbttagcompound1 = this.upgradeTag(simpleregionstorage, nbttagcompound);
                boolean flag = i < SharedConstants.getCurrentVersion().dataVersion().version();

                if (flag || WorldUpgrader.this.recreateRegionFiles) {
                    if (this.previousWriteFuture != null) {
                        this.previousWriteFuture.join();
                    }

                    this.previousWriteFuture = simpleregionstorage.write(chunkcoordintpair, nbttagcompound1);
                    return true;
                }
            }

            return false;
        }

        protected abstract NBTTagCompound upgradeTag(SimpleRegionStorage simpleregionstorage, NBTTagCompound nbttagcompound);
    }

    private class f extends WorldUpgrader.g {

        f() {
            super(DataFixTypes.POI_CHUNK, "poi", WorldUpgrader.STATUS_UPGRADING_POI, WorldUpgrader.STATUS_FINISHED_POI);
        }

        @Override
        protected NBTTagCompound upgradeTag(SimpleRegionStorage simpleregionstorage, NBTTagCompound nbttagcompound) {
            return simpleregionstorage.upgradeChunkTag(nbttagcompound, 1945);
        }
    }

    private class d extends WorldUpgrader.g {

        d() {
            super(DataFixTypes.ENTITY_CHUNK, "entities", WorldUpgrader.STATUS_UPGRADING_ENTITIES, WorldUpgrader.STATUS_FINISHED_ENTITIES);
        }

        @Override
        protected NBTTagCompound upgradeTag(SimpleRegionStorage simpleregionstorage, NBTTagCompound nbttagcompound) {
            return simpleregionstorage.upgradeChunkTag(nbttagcompound, -1);
        }
    }

    private class b extends WorldUpgrader.a<IChunkLoader> {

        b() {
            super(DataFixTypes.CHUNK, "chunk", "region", WorldUpgrader.STATUS_UPGRADING_CHUNKS, WorldUpgrader.STATUS_FINISHED_CHUNKS);
        }

        protected boolean tryProcessOnePosition(IChunkLoader ichunkloader, ChunkCoordIntPair chunkcoordintpair, ResourceKey<World> resourcekey) {
            NBTTagCompound nbttagcompound = (NBTTagCompound) ((Optional) ichunkloader.read(chunkcoordintpair).join()).orElse((Object) null);

            if (nbttagcompound != null) {
                int i = IChunkLoader.getVersion(nbttagcompound);
                ChunkGenerator chunkgenerator = ((WorldDimension) WorldUpgrader.this.dimensions.getValueOrThrow(Registries.levelToLevelStem(resourcekey))).generator();
                NBTTagCompound nbttagcompound1 = ichunkloader.upgradeChunkTag(Registries.levelToLevelStem(resourcekey), () -> { // CraftBukkit
                    return WorldUpgrader.this.overworldDataStorage;
                }, nbttagcompound, chunkgenerator.getTypeNameForDataFixer(), chunkcoordintpair, null); // CraftBukkit
                ChunkCoordIntPair chunkcoordintpair1 = new ChunkCoordIntPair(nbttagcompound1.getIntOr("xPos", 0), nbttagcompound1.getIntOr("zPos", 0));

                if (!chunkcoordintpair1.equals(chunkcoordintpair)) {
                    WorldUpgrader.LOGGER.warn("Chunk {} has invalid position {}", chunkcoordintpair, chunkcoordintpair1);
                }

                boolean flag = i < SharedConstants.getCurrentVersion().dataVersion().version();

                if (WorldUpgrader.this.eraseCache) {
                    flag = flag || nbttagcompound1.contains("Heightmaps");
                    nbttagcompound1.remove("Heightmaps");
                    flag = flag || nbttagcompound1.contains("isLightOn");
                    nbttagcompound1.remove("isLightOn");
                    NBTTagList nbttaglist = nbttagcompound1.getListOrEmpty("sections");

                    for (int j = 0; j < nbttaglist.size(); ++j) {
                        Optional<NBTTagCompound> optional = nbttaglist.getCompound(j);

                        if (!optional.isEmpty()) {
                            NBTTagCompound nbttagcompound2 = (NBTTagCompound) optional.get();

                            flag = flag || nbttagcompound2.contains("BlockLight");
                            nbttagcompound2.remove("BlockLight");
                            flag = flag || nbttagcompound2.contains("SkyLight");
                            nbttagcompound2.remove("SkyLight");
                        }
                    }
                }

                if (flag || WorldUpgrader.this.recreateRegionFiles) {
                    if (this.previousWriteFuture != null) {
                        this.previousWriteFuture.join();
                    }

                    this.previousWriteFuture = ichunkloader.write(chunkcoordintpair, () -> {
                        return nbttagcompound1;
                    });
                    return true;
                }
            }

            return false;
        }

        @Override
        protected IChunkLoader createStorage(RegionStorageInfo regionstorageinfo, Path path) {
            return (IChunkLoader) (WorldUpgrader.this.recreateRegionFiles ? new RecreatingChunkStorage(regionstorageinfo.withTypeSuffix("source"), path, regionstorageinfo.withTypeSuffix("target"), WorldUpgrader.resolveRecreateDirectory(path), WorldUpgrader.this.dataFixer, true) : new IChunkLoader(regionstorageinfo, path, WorldUpgrader.this.dataFixer, true));
        }
    }
}
