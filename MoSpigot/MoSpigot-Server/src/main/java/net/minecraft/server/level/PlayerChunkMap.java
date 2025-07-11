package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportedException;
import net.minecraft.SystemUtils;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.PacketPlayOutViewCentre;
import net.minecraft.server.level.progress.WorldLoadListener;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CSVWriter;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.TriState;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.util.thread.IAsyncTaskHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.entity.boss.EntityComplexPart;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.ChunkConverter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.ILightAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunkExtension;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.IChunkLoader;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.levelgen.ChunkGeneratorAbstract;
import net.minecraft.world.level.levelgen.GeneratorSettingBase;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.WorldPersistentData;
import net.minecraft.world.phys.Vec3D;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
// CraftBukkit end

public class PlayerChunkMap extends IChunkLoader implements PlayerChunk.b, GeneratingChunkMap {

    private static final ChunkResult<List<IChunkAccess>> UNLOADED_CHUNK_LIST_RESULT = ChunkResult.error("Unloaded chunks found in range");
    private static final CompletableFuture<ChunkResult<List<IChunkAccess>>> UNLOADED_CHUNK_LIST_FUTURE = CompletableFuture.completedFuture(PlayerChunkMap.UNLOADED_CHUNK_LIST_RESULT);
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    private static final int MAX_ACTIVE_CHUNK_WRITES = 128;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    public final Long2ObjectLinkedOpenHashMap<PlayerChunk> updatingChunkMap = new Long2ObjectLinkedOpenHashMap();
    public volatile Long2ObjectLinkedOpenHashMap<PlayerChunk> visibleChunkMap;
    private final Long2ObjectLinkedOpenHashMap<PlayerChunk> pendingUnloads;
    private final List<ChunkGenerationTask> pendingGenerationTasks;
    public final WorldServer level;
    private final LightEngineThreaded lightEngine;
    private final IAsyncTaskHandler<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final Supplier<WorldPersistentData> overworldDataStorage;
    private final TicketStorage ticketStorage;
    private final VillagePlace poiManager;
    public final LongSet toDrop;
    private boolean modified;
    private final ChunkTaskDispatcher worldgenTaskDispatcher;
    private final ChunkTaskDispatcher lightTaskDispatcher;
    public final WorldLoadListener progressListener;
    private final ChunkStatusUpdateListener chunkStatusListener;
    public final PlayerChunkMap.a distanceManager;
    private final AtomicInteger tickingGenerated;
    private final String storageName;
    private final PlayerMap playerMap;
    public final Int2ObjectMap<PlayerChunkMap.EntityTracker> entityMap;
    private final Long2ByteMap chunkTypeCache;
    private final Long2LongMap nextChunkSaveTime;
    private final LongSet chunksToEagerlySave;
    private final Queue<Runnable> unloadQueue;
    private final AtomicInteger activeChunkWrites;
    public int serverViewDistance;
    private final WorldGenContext worldGenContext;

    // CraftBukkit start - recursion-safe executor for Chunk loadCallback() and unloadCallback()
    public final CallbackExecutor callbackExecutor = new CallbackExecutor();
    public static final class CallbackExecutor implements java.util.concurrent.Executor, Runnable {

        private final java.util.Queue<Runnable> queue = new java.util.ArrayDeque<>();

        @Override
        public void execute(Runnable runnable) {
            queue.add(runnable);
        }

        @Override
        public void run() {
            Runnable task;
            while ((task = queue.poll()) != null) {
                task.run();
            }
        }
    };
    // CraftBukkit end

    public PlayerChunkMap(WorldServer worldserver, Convertable.ConversionSession convertable_conversionsession, DataFixer datafixer, StructureTemplateManager structuretemplatemanager, Executor executor, IAsyncTaskHandler<Runnable> iasynctaskhandler, ILightAccess ilightaccess, ChunkGenerator chunkgenerator, WorldLoadListener worldloadlistener, ChunkStatusUpdateListener chunkstatusupdatelistener, Supplier<WorldPersistentData> supplier, TicketStorage ticketstorage, int i, boolean flag) {
        super(new RegionStorageInfo(convertable_conversionsession.getLevelId(), worldserver.dimension(), "chunk"), convertable_conversionsession.getDimensionPath(worldserver.dimension()).resolve("region"), datafixer, flag);
        this.visibleChunkMap = this.updatingChunkMap.clone();
        this.pendingUnloads = new Long2ObjectLinkedOpenHashMap();
        this.pendingGenerationTasks = new ArrayList();
        this.toDrop = new LongOpenHashSet();
        this.tickingGenerated = new AtomicInteger();
        this.playerMap = new PlayerMap();
        this.entityMap = new Int2ObjectOpenHashMap();
        this.chunkTypeCache = new Long2ByteOpenHashMap();
        this.nextChunkSaveTime = new Long2LongOpenHashMap();
        this.chunksToEagerlySave = new LongLinkedOpenHashSet();
        this.unloadQueue = Queues.newConcurrentLinkedQueue();
        this.activeChunkWrites = new AtomicInteger();
        Path path = convertable_conversionsession.getDimensionPath(worldserver.dimension());

        this.storageName = path.getFileName().toString();
        this.level = worldserver;
        IRegistryCustom iregistrycustom = worldserver.registryAccess();
        long j = worldserver.getSeed();

        // CraftBukkit start - SPIGOT-7051: It's a rigged game! Use delegate for random state creation, otherwise it is not so random.
        ChunkGenerator randomGenerator = chunkgenerator;
        if (randomGenerator instanceof CustomChunkGenerator customChunkGenerator) {
            randomGenerator = customChunkGenerator.getDelegate();
        }
        if (randomGenerator instanceof ChunkGeneratorAbstract chunkgeneratorabstract) {
            // CraftBukkit end
            this.randomState = RandomState.create((GeneratorSettingBase) chunkgeneratorabstract.generatorSettings().value(), iregistrycustom.lookupOrThrow(Registries.NOISE), j);
        } else {
            this.randomState = RandomState.create(GeneratorSettingBase.dummy(), iregistrycustom.lookupOrThrow(Registries.NOISE), j);
        }

        this.chunkGeneratorState = chunkgenerator.createState(iregistrycustom.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, j, worldserver.spigotConfig); // Spigot
        this.mainThreadExecutor = iasynctaskhandler;
        ConsecutiveExecutor consecutiveexecutor = new ConsecutiveExecutor(executor, "worldgen");

        this.progressListener = worldloadlistener;
        this.chunkStatusListener = chunkstatusupdatelistener;
        ConsecutiveExecutor consecutiveexecutor1 = new ConsecutiveExecutor(executor, "light");

        this.worldgenTaskDispatcher = new ChunkTaskDispatcher(consecutiveexecutor, executor);
        this.lightTaskDispatcher = new ChunkTaskDispatcher(consecutiveexecutor1, executor);
        this.lightEngine = new LightEngineThreaded(ilightaccess, this, this.level.dimensionType().hasSkyLight(), consecutiveexecutor1, this.lightTaskDispatcher);
        this.distanceManager = new PlayerChunkMap.a(ticketstorage, executor, iasynctaskhandler);
        this.overworldDataStorage = supplier;
        this.ticketStorage = ticketstorage;
        this.poiManager = new VillagePlace(new RegionStorageInfo(convertable_conversionsession.getLevelId(), worldserver.dimension(), "poi"), path.resolve("poi"), datafixer, flag, iregistrycustom, worldserver.getServer(), worldserver);
        this.setServerViewDistance(i);
        this.worldGenContext = new WorldGenContext(worldserver, chunkgenerator, structuretemplatemanager, this.lightEngine, iasynctaskhandler, this::setChunkUnsaved);
    }

    private void setChunkUnsaved(ChunkCoordIntPair chunkcoordintpair) {
        this.chunksToEagerlySave.add(chunkcoordintpair.toLong());
    }

    protected ChunkGenerator generator() {
        return this.worldGenContext.generator();
    }

    protected ChunkGeneratorStructureState generatorState() {
        return this.chunkGeneratorState;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    boolean isChunkTracked(EntityPlayer entityplayer, int i, int j) {
        return entityplayer.getChunkTrackingView().contains(i, j) && !entityplayer.connection.chunkSender.isPending(ChunkCoordIntPair.asLong(i, j));
    }

    private boolean isChunkOnTrackedBorder(EntityPlayer entityplayer, int i, int j) {
        if (!this.isChunkTracked(entityplayer, i, j)) {
            return false;
        } else {
            for (int k = -1; k <= 1; ++k) {
                for (int l = -1; l <= 1; ++l) {
                    if ((k != 0 || l != 0) && !this.isChunkTracked(entityplayer, i + k, j + l)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    protected LightEngineThreaded getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    public PlayerChunk getUpdatingChunkIfPresent(long i) {
        return (PlayerChunk) this.updatingChunkMap.get(i);
    }

    @Nullable
    protected PlayerChunk getVisibleChunkIfPresent(long i) {
        return (PlayerChunk) this.visibleChunkMap.get(i);
    }

    protected IntSupplier getChunkQueueLevel(long i) {
        return () -> {
            PlayerChunk playerchunk = this.getVisibleChunkIfPresent(i);

            return playerchunk == null ? ChunkTaskQueue.PRIORITY_LEVEL_COUNT - 1 : Math.min(playerchunk.getQueueLevel(), ChunkTaskQueue.PRIORITY_LEVEL_COUNT - 1);
        };
    }

    public String getChunkDebugData(ChunkCoordIntPair chunkcoordintpair) {
        PlayerChunk playerchunk = this.getVisibleChunkIfPresent(chunkcoordintpair.toLong());

        if (playerchunk == null) {
            return "null";
        } else {
            String s = playerchunk.getTicketLevel() + "\n";
            ChunkStatus chunkstatus = playerchunk.getLatestStatus();
            IChunkAccess ichunkaccess = playerchunk.getLatestChunk();

            if (chunkstatus != null) {
                s = s + "St: \u00a7" + chunkstatus.getIndex() + String.valueOf(chunkstatus) + "\u00a7r\n";
            }

            if (ichunkaccess != null) {
                s = s + "Ch: \u00a7" + ichunkaccess.getPersistedStatus().getIndex() + String.valueOf(ichunkaccess.getPersistedStatus()) + "\u00a7r\n";
            }

            FullChunkStatus fullchunkstatus = playerchunk.getFullStatus();

            s = s + String.valueOf('\u00a7') + fullchunkstatus.ordinal() + String.valueOf(fullchunkstatus);
            return s + "\u00a7r";
        }
    }

    private CompletableFuture<ChunkResult<List<IChunkAccess>>> getChunkRangeFuture(PlayerChunk playerchunk, int i, IntFunction<ChunkStatus> intfunction) {
        if (i == 0) {
            ChunkStatus chunkstatus = (ChunkStatus) intfunction.apply(0);

            return playerchunk.scheduleChunkGenerationTask(chunkstatus, this).thenApply((chunkresult) -> {
                return chunkresult.map(List::of);
            });
        } else {
            int j = MathHelper.square(i * 2 + 1);
            List<CompletableFuture<ChunkResult<IChunkAccess>>> list = new ArrayList(j);
            ChunkCoordIntPair chunkcoordintpair = playerchunk.getPos();

            for (int k = -i; k <= i; ++k) {
                for (int l = -i; l <= i; ++l) {
                    int i1 = Math.max(Math.abs(l), Math.abs(k));
                    long j1 = ChunkCoordIntPair.asLong(chunkcoordintpair.x + l, chunkcoordintpair.z + k);
                    PlayerChunk playerchunk1 = this.getUpdatingChunkIfPresent(j1);

                    if (playerchunk1 == null) {
                        return PlayerChunkMap.UNLOADED_CHUNK_LIST_FUTURE;
                    }

                    ChunkStatus chunkstatus1 = (ChunkStatus) intfunction.apply(i1);

                    list.add(playerchunk1.scheduleChunkGenerationTask(chunkstatus1, this));
                }
            }

            return SystemUtils.sequence(list).thenApply((list1) -> {
                List<IChunkAccess> list2 = new ArrayList(list1.size());

                for (ChunkResult<IChunkAccess> chunkresult : list1) {
                    if (chunkresult == null) {
                        throw this.debugFuturesAndCreateReportedException(new IllegalStateException("At least one of the chunk futures were null"), "n/a");
                    }

                    IChunkAccess ichunkaccess = chunkresult.orElse(null); // CraftBukkit - decompile error

                    if (ichunkaccess == null) {
                        return PlayerChunkMap.UNLOADED_CHUNK_LIST_RESULT;
                    }

                    list2.add(ichunkaccess);
                }

                return ChunkResult.of(list2);
            });
        }
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException illegalstateexception, String s) {
        StringBuilder stringbuilder = new StringBuilder();
        Consumer<PlayerChunk> consumer = (playerchunk) -> {
            playerchunk.getAllFutures().forEach((pair) -> {
                ChunkStatus chunkstatus = (ChunkStatus) pair.getFirst();
                CompletableFuture<ChunkResult<IChunkAccess>> completablefuture = (CompletableFuture) pair.getSecond();

                if (completablefuture != null && completablefuture.isDone() && completablefuture.join() == null) {
                    stringbuilder.append(playerchunk.getPos()).append(" - status: ").append(chunkstatus).append(" future: ").append(completablefuture).append(System.lineSeparator());
                }

            });
        };

        stringbuilder.append("Updating:").append(System.lineSeparator());
        this.updatingChunkMap.values().forEach(consumer);
        stringbuilder.append("Visible:").append(System.lineSeparator());
        this.visibleChunkMap.values().forEach(consumer);
        CrashReport crashreport = CrashReport.forThrowable(illegalstateexception, "Chunk loading");
        CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Chunk loading");

        crashreportsystemdetails.setDetail("Details", s);
        crashreportsystemdetails.setDetail("Futures", stringbuilder);
        return new ReportedException(crashreport);
    }

    public CompletableFuture<ChunkResult<Chunk>> prepareEntityTickingChunk(PlayerChunk playerchunk) {
        return this.getChunkRangeFuture(playerchunk, 2, (i) -> {
            return ChunkStatus.FULL;
        }).thenApply((chunkresult) -> {
            return chunkresult.map((list) -> {
                return (Chunk) list.get(list.size() / 2);
            });
        });
    }

    @Nullable
    PlayerChunk updateChunkScheduling(long i, int j, @Nullable PlayerChunk playerchunk, int k) {
        if (!ChunkLevel.isLoaded(k) && !ChunkLevel.isLoaded(j)) {
            return playerchunk;
        } else {
            if (playerchunk != null) {
                playerchunk.setTicketLevel(j);
            }

            if (playerchunk != null) {
                if (!ChunkLevel.isLoaded(j)) {
                    this.toDrop.add(i);
                } else {
                    this.toDrop.remove(i);
                }
            }

            if (ChunkLevel.isLoaded(j) && playerchunk == null) {
                playerchunk = (PlayerChunk) this.pendingUnloads.remove(i);
                if (playerchunk != null) {
                    playerchunk.setTicketLevel(j);
                } else {
                    playerchunk = new PlayerChunk(new ChunkCoordIntPair(i), j, this.level, this.lightEngine, this::onLevelChange, this);
                }

                this.updatingChunkMap.put(i, playerchunk);
                this.modified = true;
            }

            return playerchunk;
        }
    }

    private void onLevelChange(ChunkCoordIntPair chunkcoordintpair, IntSupplier intsupplier, int i, IntConsumer intconsumer) {
        this.worldgenTaskDispatcher.onLevelChange(chunkcoordintpair, intsupplier, i, intconsumer);
        this.lightTaskDispatcher.onLevelChange(chunkcoordintpair, intsupplier, i, intconsumer);
    }

    @Override
    public void close() throws IOException {
        try {
            this.worldgenTaskDispatcher.close();
            this.lightTaskDispatcher.close();
            this.poiManager.close();
        } finally {
            super.close();
        }

    }

    protected void saveAllChunks(boolean flag) {
        if (flag) {
            List<PlayerChunk> list = this.visibleChunkMap.values().stream().filter(PlayerChunk::wasAccessibleSinceLastSave).peek(PlayerChunk::refreshAccessibility).toList();
            MutableBoolean mutableboolean = new MutableBoolean();

            do {
                mutableboolean.setFalse();
                list.stream().map((playerchunk) -> {
                    IAsyncTaskHandler iasynctaskhandler = this.mainThreadExecutor;

                    Objects.requireNonNull(playerchunk);
                    iasynctaskhandler.managedBlock(playerchunk::isReadyForSaving);
                    return playerchunk.getLatestChunk();
                }).filter((ichunkaccess) -> {
                    return ichunkaccess instanceof ProtoChunkExtension || ichunkaccess instanceof Chunk;
                }).filter(this::save).forEach((ichunkaccess) -> {
                    mutableboolean.setTrue();
                });
            } while (mutableboolean.isTrue());

            this.poiManager.flushAll();
            this.processUnloads(() -> {
                return true;
            });
            this.flushWorker();
        } else {
            this.nextChunkSaveTime.clear();
            long i = SystemUtils.getMillis();
            ObjectIterator objectiterator = this.visibleChunkMap.values().iterator();

            while (objectiterator.hasNext()) {
                PlayerChunk playerchunk = (PlayerChunk) objectiterator.next();

                this.saveChunkIfNeeded(playerchunk, i);
            }
        }

    }

    protected void tick(BooleanSupplier booleansupplier) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("poi");
        this.poiManager.tick(booleansupplier);
        gameprofilerfiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            this.processUnloads(booleansupplier);
        }

        gameprofilerfiller.pop();
    }

    public boolean hasWork() {
        return this.lightEngine.hasLightWork() || !this.pendingUnloads.isEmpty() || !this.updatingChunkMap.isEmpty() || this.poiManager.hasWork() || !this.toDrop.isEmpty() || !this.unloadQueue.isEmpty() || this.worldgenTaskDispatcher.hasWork() || this.lightTaskDispatcher.hasWork() || this.distanceManager.hasTickets();
    }

    private void processUnloads(BooleanSupplier booleansupplier) {
        for (LongIterator longiterator = this.toDrop.iterator(); longiterator.hasNext(); longiterator.remove()) {
            long i = longiterator.nextLong();
            PlayerChunk playerchunk = (PlayerChunk) this.updatingChunkMap.get(i);

            if (playerchunk != null) {
                this.updatingChunkMap.remove(i);
                this.pendingUnloads.put(i, playerchunk);
                this.modified = true;
                this.scheduleUnload(i, playerchunk);
            }
        }

        int j = Math.max(0, this.unloadQueue.size() - 2000);

        Runnable runnable;

        while ((j > 0 || booleansupplier.getAsBoolean()) && (runnable = (Runnable) this.unloadQueue.poll()) != null) {
            --j;
            runnable.run();
        }

        this.saveChunksEagerly(booleansupplier);
    }

    private void saveChunksEagerly(BooleanSupplier booleansupplier) {
        long i = SystemUtils.getMillis();
        int j = 0;
        LongIterator longiterator = this.chunksToEagerlySave.iterator();

        while (j < 20 && this.activeChunkWrites.get() < 128 && booleansupplier.getAsBoolean() && longiterator.hasNext()) {
            long k = longiterator.nextLong();
            PlayerChunk playerchunk = (PlayerChunk) this.visibleChunkMap.get(k);
            IChunkAccess ichunkaccess = playerchunk != null ? playerchunk.getLatestChunk() : null;

            if (ichunkaccess != null && ichunkaccess.isUnsaved()) {
                if (this.saveChunkIfNeeded(playerchunk, i)) {
                    ++j;
                    longiterator.remove();
                }
            } else {
                longiterator.remove();
            }
        }

    }

    private void scheduleUnload(long i, PlayerChunk playerchunk) {
        CompletableFuture<?> completablefuture = playerchunk.getSaveSyncFuture();
        Runnable runnable = () -> {
            CompletableFuture<?> completablefuture1 = playerchunk.getSaveSyncFuture();

            if (completablefuture1 != completablefuture) {
                this.scheduleUnload(i, playerchunk);
            } else {
                IChunkAccess ichunkaccess = playerchunk.getLatestChunk();

                if (this.pendingUnloads.remove(i, playerchunk) && ichunkaccess != null) {
                    if (ichunkaccess instanceof Chunk) {
                        Chunk chunk = (Chunk) ichunkaccess;

                        chunk.setLoaded(false);
                    }

                    this.save(ichunkaccess);
                    if (ichunkaccess instanceof Chunk) {
                        Chunk chunk1 = (Chunk) ichunkaccess;

                        this.level.unload(chunk1);
                    }

                    this.lightEngine.updateChunkStatus(ichunkaccess.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.progressListener.onStatusChange(ichunkaccess.getPos(), (ChunkStatus) null);
                    this.nextChunkSaveTime.remove(ichunkaccess.getPos().toLong());
                }

            }
        };
        Queue queue = this.unloadQueue;

        Objects.requireNonNull(this.unloadQueue);
        completablefuture.thenRunAsync(runnable, queue::add).whenComplete((ovoid, throwable) -> {
            if (throwable != null) {
                PlayerChunkMap.LOGGER.error("Failed to save chunk {}", playerchunk.getPos(), throwable);
            }

        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) {
            return false;
        } else {
            this.visibleChunkMap = this.updatingChunkMap.clone();
            this.modified = false;
            return true;
        }
    }

    private CompletableFuture<IChunkAccess> scheduleChunkLoad(ChunkCoordIntPair chunkcoordintpair) {
        CompletableFuture<Optional<SerializableChunkData>> completablefuture = this.readChunk(chunkcoordintpair).thenApplyAsync((optional) -> {
            return optional.map((nbttagcompound) -> {
                SerializableChunkData serializablechunkdata = SerializableChunkData.parse(this.level, this.level.registryAccess(), nbttagcompound);

                if (serializablechunkdata == null) {
                    PlayerChunkMap.LOGGER.error("Chunk file at {} is missing level data, skipping", chunkcoordintpair);
                }

                return serializablechunkdata;
            });
        }, SystemUtils.backgroundExecutor().forName("parseChunk"));
        CompletableFuture<?> completablefuture1 = this.poiManager.prefetch(chunkcoordintpair);

        return completablefuture.thenCombine(completablefuture1, (optional, object) -> {
            return optional;
        }).thenApplyAsync((optional) -> {
            Profiler.get().incrementCounter("chunkLoad");
            if (optional.isPresent()) {
                IChunkAccess ichunkaccess = ((SerializableChunkData) optional.get()).read(this.level, this.poiManager, this.storageInfo(), chunkcoordintpair);

                this.markPosition(chunkcoordintpair, ichunkaccess.getPersistedStatus().getChunkType());
                return ichunkaccess;
            } else {
                return this.createEmptyChunk(chunkcoordintpair);
            }
        }, this.mainThreadExecutor).exceptionallyAsync((throwable) -> {
            return this.handleChunkLoadFailure(throwable, chunkcoordintpair);
        }, this.mainThreadExecutor);
    }

    private IChunkAccess handleChunkLoadFailure(Throwable throwable, ChunkCoordIntPair chunkcoordintpair) {
        Throwable throwable1;

        if (throwable instanceof CompletionException completionexception) {
            throwable1 = completionexception.getCause();
        } else {
            throwable1 = throwable;
        }

        Throwable throwable2 = throwable1;

        if (throwable2 instanceof ReportedException reportedexception) {
            throwable1 = reportedexception.getCause();
        } else {
            throwable1 = throwable2;
        }

        Throwable throwable3 = throwable1;
        boolean flag = throwable3 instanceof Error;
        boolean flag1 = throwable3 instanceof IOException || throwable3 instanceof NbtException;

        if (!flag) {
            if (!flag1) {
                ;
            }

            this.level.getServer().reportChunkLoadFailure(throwable3, this.storageInfo(), chunkcoordintpair);
            return this.createEmptyChunk(chunkcoordintpair);
        } else {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception loading chunk");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Chunk being loaded");

            crashreportsystemdetails.setDetail("pos", chunkcoordintpair);
            this.markPositionReplaceable(chunkcoordintpair);
            throw new ReportedException(crashreport);
        }
    }

    private IChunkAccess createEmptyChunk(ChunkCoordIntPair chunkcoordintpair) {
        this.markPositionReplaceable(chunkcoordintpair);
        return new ProtoChunk(chunkcoordintpair, ChunkConverter.EMPTY, this.level, this.level.registryAccess().lookupOrThrow(Registries.BIOME), (BlendingData) null);
    }

    private void markPositionReplaceable(ChunkCoordIntPair chunkcoordintpair) {
        this.chunkTypeCache.put(chunkcoordintpair.toLong(), (byte) -1);
    }

    private byte markPosition(ChunkCoordIntPair chunkcoordintpair, ChunkType chunktype) {
        return this.chunkTypeCache.put(chunkcoordintpair.toLong(), (byte) (chunktype == ChunkType.PROTOCHUNK ? -1 : 1));
    }

    @Override
    public GenerationChunkHolder acquireGeneration(long i) {
        PlayerChunk playerchunk = (PlayerChunk) this.updatingChunkMap.get(i);

        playerchunk.increaseGenerationRefCount();
        return playerchunk;
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder generationchunkholder) {
        generationchunkholder.decreaseGenerationRefCount();
    }

    @Override
    public CompletableFuture<IChunkAccess> applyStep(GenerationChunkHolder generationchunkholder, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d) {
        ChunkCoordIntPair chunkcoordintpair = generationchunkholder.getPos();

        if (chunkstep.targetStatus() == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(chunkcoordintpair);
        } else {
            try {
                GenerationChunkHolder generationchunkholder1 = staticcache2d.get(chunkcoordintpair.x, chunkcoordintpair.z);
                IChunkAccess ichunkaccess = generationchunkholder1.getChunkIfPresentUnchecked(chunkstep.targetStatus().getParent());

                if (ichunkaccess == null) {
                    throw new IllegalStateException("Parent chunk missing");
                } else {
                    CompletableFuture<IChunkAccess> completablefuture = chunkstep.apply(this.worldGenContext, staticcache2d, ichunkaccess);

                    this.progressListener.onStatusChange(chunkcoordintpair, chunkstep.targetStatus());
                    return completablefuture;
                }
            } catch (Exception exception) {
                exception.getStackTrace();
                CrashReport crashreport = CrashReport.forThrowable(exception, "Exception generating new chunk");
                CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Chunk to be generated");

                crashreportsystemdetails.setDetail("Status being generated", () -> {
                    return chunkstep.targetStatus().getName();
                });
                crashreportsystemdetails.setDetail("Location", String.format(Locale.ROOT, "%d,%d", chunkcoordintpair.x, chunkcoordintpair.z));
                crashreportsystemdetails.setDetail("Position hash", ChunkCoordIntPair.asLong(chunkcoordintpair.x, chunkcoordintpair.z));
                crashreportsystemdetails.setDetail("Generator", this.generator());
                this.mainThreadExecutor.execute(() -> {
                    throw new ReportedException(crashreport);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus chunkstatus, ChunkCoordIntPair chunkcoordintpair) {
        ChunkGenerationTask chunkgenerationtask = ChunkGenerationTask.create(this, chunkstatus, chunkcoordintpair);

        this.pendingGenerationTasks.add(chunkgenerationtask);
        return chunkgenerationtask;
    }

    private void runGenerationTask(ChunkGenerationTask chunkgenerationtask) {
        GenerationChunkHolder generationchunkholder = chunkgenerationtask.getCenter();
        ChunkTaskDispatcher chunktaskdispatcher = this.worldgenTaskDispatcher;
        Runnable runnable = () -> {
            CompletableFuture<?> completablefuture = chunkgenerationtask.runUntilWait();

            if (completablefuture != null) {
                completablefuture.thenRun(() -> {
                    this.runGenerationTask(chunkgenerationtask);
                });
            }
        };
        long i = generationchunkholder.getPos().toLong();

        Objects.requireNonNull(generationchunkholder);
        chunktaskdispatcher.submit(runnable, i, generationchunkholder::getQueueLevel);
    }

    @Override
    public void runGenerationTasks() {
        this.pendingGenerationTasks.forEach(this::runGenerationTask);
        this.pendingGenerationTasks.clear();
    }

    public CompletableFuture<ChunkResult<Chunk>> prepareTickingChunk(PlayerChunk playerchunk) {
        CompletableFuture<ChunkResult<List<IChunkAccess>>> completablefuture = this.getChunkRangeFuture(playerchunk, 1, (i) -> {
            return ChunkStatus.FULL;
        });
        CompletableFuture<ChunkResult<Chunk>> completablefuture1 = completablefuture.thenApplyAsync((chunkresult) -> {
            return chunkresult.map((list) -> {
                Chunk chunk = (Chunk) list.get(list.size() / 2);

                chunk.postProcessGeneration(this.level);
                this.level.startTickingChunk(chunk);
                CompletableFuture<?> completablefuture2 = playerchunk.getSendSyncFuture();

                if (completablefuture2.isDone()) {
                    this.onChunkReadyToSend(playerchunk, chunk);
                } else {
                    completablefuture2.thenAcceptAsync((object) -> {
                        this.onChunkReadyToSend(playerchunk, chunk);
                    }, this.mainThreadExecutor);
                }

                return chunk;
            });
        }, this.mainThreadExecutor);

        completablefuture1.handle((chunkresult, throwable) -> {
            this.tickingGenerated.getAndIncrement();
            return null;
        });
        return completablefuture1;
    }

    private void onChunkReadyToSend(PlayerChunk playerchunk, Chunk chunk) {
        ChunkCoordIntPair chunkcoordintpair = chunk.getPos();

        for (EntityPlayer entityplayer : this.playerMap.getAllPlayers()) {
            if (entityplayer.getChunkTrackingView().contains(chunkcoordintpair)) {
                markChunkPendingToSend(entityplayer, chunk);
            }
        }

        this.level.getChunkSource().onChunkReadyToSend(playerchunk);
    }

    public CompletableFuture<ChunkResult<Chunk>> prepareAccessibleChunk(PlayerChunk playerchunk) {
        return this.getChunkRangeFuture(playerchunk, 1, ChunkLevel::getStatusAroundFullChunk).thenApply((chunkresult) -> {
            return chunkresult.map((list) -> {
                return (Chunk) list.get(list.size() / 2);
            });
        });
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    private boolean saveChunkIfNeeded(PlayerChunk playerchunk, long i) {
        if (playerchunk.wasAccessibleSinceLastSave() && playerchunk.isReadyForSaving()) {
            IChunkAccess ichunkaccess = playerchunk.getLatestChunk();

            if (!(ichunkaccess instanceof ProtoChunkExtension) && !(ichunkaccess instanceof Chunk)) {
                return false;
            } else if (!ichunkaccess.isUnsaved()) {
                return false;
            } else {
                long j = ichunkaccess.getPos().toLong();
                long k = this.nextChunkSaveTime.getOrDefault(j, -1L);

                if (i < k) {
                    return false;
                } else {
                    boolean flag = this.save(ichunkaccess);

                    playerchunk.refreshAccessibility();
                    if (flag) {
                        this.nextChunkSaveTime.put(j, i + 10000L);
                    }

                    return flag;
                }
            }
        } else {
            return false;
        }
    }

    public boolean save(IChunkAccess ichunkaccess) {
        this.poiManager.flush(ichunkaccess.getPos());
        if (!ichunkaccess.tryMarkSaved()) {
            return false;
        } else {
            ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();

            try {
                ChunkStatus chunkstatus = ichunkaccess.getPersistedStatus();

                if (chunkstatus.getChunkType() != ChunkType.LEVELCHUNK) {
                    if (this.isExistingChunkFull(chunkcoordintpair)) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && ichunkaccess.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                        return false;
                    }
                }

                Profiler.get().incrementCounter("chunkSave");
                this.activeChunkWrites.incrementAndGet();
                SerializableChunkData serializablechunkdata = SerializableChunkData.copyOf(this.level, ichunkaccess);

                Objects.requireNonNull(serializablechunkdata);
                CompletableFuture<NBTTagCompound> completablefuture = CompletableFuture.supplyAsync(serializablechunkdata::write, SystemUtils.backgroundExecutor());

                Objects.requireNonNull(completablefuture);
                this.write(chunkcoordintpair, completablefuture::join).handle((ovoid, throwable) -> {
                    if (throwable != null) {
                        this.level.getServer().reportChunkSaveFailure(throwable, this.storageInfo(), chunkcoordintpair);
                    }

                    this.activeChunkWrites.decrementAndGet();
                    return null;
                });
                this.markPosition(chunkcoordintpair, chunkstatus.getChunkType());
                return true;
            } catch (Exception exception) {
                this.level.getServer().reportChunkSaveFailure(exception, this.storageInfo(), chunkcoordintpair);
                return false;
            }
        }
    }

    private boolean isExistingChunkFull(ChunkCoordIntPair chunkcoordintpair) {
        byte b0 = this.chunkTypeCache.get(chunkcoordintpair.toLong());

        if (b0 != 0) {
            return b0 == 1;
        } else {
            NBTTagCompound nbttagcompound;

            try {
                nbttagcompound = (NBTTagCompound) ((Optional) this.readChunk(chunkcoordintpair).join()).orElse((Object) null);
                if (nbttagcompound == null) {
                    this.markPositionReplaceable(chunkcoordintpair);
                    return false;
                }
            } catch (Exception exception) {
                PlayerChunkMap.LOGGER.error("Failed to read chunk {}", chunkcoordintpair, exception);
                this.markPositionReplaceable(chunkcoordintpair);
                return false;
            }

            ChunkType chunktype = SerializableChunkData.getChunkStatusFromTag(nbttagcompound).getChunkType();

            return this.markPosition(chunkcoordintpair, chunktype) == 1;
        }
    }

    protected void setServerViewDistance(int i) {
        int j = MathHelper.clamp(i, 2, 32);

        if (j != this.serverViewDistance) {
            this.serverViewDistance = j;
            this.distanceManager.updatePlayerTickets(this.serverViewDistance);

            for (EntityPlayer entityplayer : this.playerMap.getAllPlayers()) {
                this.updateChunkTracking(entityplayer);
            }
        }

    }

    int getPlayerViewDistance(EntityPlayer entityplayer) {
        return MathHelper.clamp(entityplayer.requestedViewDistance(), 2, this.serverViewDistance);
    }

    private void markChunkPendingToSend(EntityPlayer entityplayer, ChunkCoordIntPair chunkcoordintpair) {
        Chunk chunk = this.getChunkToSend(chunkcoordintpair.toLong());

        if (chunk != null) {
            markChunkPendingToSend(entityplayer, chunk);
        }

    }

    private static void markChunkPendingToSend(EntityPlayer entityplayer, Chunk chunk) {
        entityplayer.connection.chunkSender.markChunkPendingToSend(chunk);
    }

    private static void dropChunk(EntityPlayer entityplayer, ChunkCoordIntPair chunkcoordintpair) {
        entityplayer.connection.chunkSender.dropChunk(entityplayer, chunkcoordintpair);
    }

    @Nullable
    public Chunk getChunkToSend(long i) {
        PlayerChunk playerchunk = this.getVisibleChunkIfPresent(i);

        return playerchunk == null ? null : playerchunk.getChunkToSend();
    }

    public int size() {
        return this.visibleChunkMap.size();
    }

    public ChunkMapDistance getDistanceManager() {
        return this.distanceManager;
    }

    protected Iterable<PlayerChunk> getChunks() {
        return Iterables.unmodifiableIterable(this.visibleChunkMap.values());
    }

    void dumpChunks(Writer writer) throws IOException {
        CSVWriter csvwriter = CSVWriter.builder().addColumn("x").addColumn("z").addColumn("level").addColumn("in_memory").addColumn("status").addColumn("full_status").addColumn("accessible_ready").addColumn("ticking_ready").addColumn("entity_ticking_ready").addColumn("ticket").addColumn("spawning").addColumn("block_entity_count").addColumn("ticking_ticket").addColumn("ticking_level").addColumn("block_ticks").addColumn("fluid_ticks").build(writer);
        ObjectBidirectionalIterator objectbidirectionaliterator = this.visibleChunkMap.long2ObjectEntrySet().iterator();

        while (objectbidirectionaliterator.hasNext()) {
            Long2ObjectMap.Entry<PlayerChunk> long2objectmap_entry = (Entry) objectbidirectionaliterator.next();
            long i = long2objectmap_entry.getLongKey();
            ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(i);
            PlayerChunk playerchunk = (PlayerChunk) long2objectmap_entry.getValue();
            Optional<IChunkAccess> optional = Optional.ofNullable(playerchunk.getLatestChunk());
            Optional<Chunk> optional1 = optional.flatMap((ichunkaccess) -> {
                return ichunkaccess instanceof Chunk ? Optional.of((Chunk) ichunkaccess) : Optional.empty();
            });

            // CraftBukkit - decompile error
            csvwriter.writeRow(chunkcoordintpair.x, chunkcoordintpair.z, playerchunk.getTicketLevel(), optional.isPresent(), optional.map(IChunkAccess::getPersistedStatus).orElse(null), optional1.map(Chunk::getFullStatus).orElse(null), printFuture(playerchunk.getFullChunkFuture()), printFuture(playerchunk.getTickingChunkFuture()), printFuture(playerchunk.getEntityTickingChunkFuture()), this.ticketStorage.getTicketDebugString(i, false), this.anyPlayerCloseEnoughForSpawning(chunkcoordintpair), optional1.map((chunk) -> {
                return chunk.getBlockEntities().size();
            }).orElse(0), this.ticketStorage.getTicketDebugString(i, true), this.distanceManager.getChunkLevel(i, true), optional1.map((chunk) -> {
                return chunk.getBlockTicks().count();
            }).orElse(0), optional1.map((chunk) -> {
                return chunk.getFluidTicks().count();
            }).orElse(0));
        }

    }

    private static String printFuture(CompletableFuture<ChunkResult<Chunk>> completablefuture) {
        try {
            ChunkResult<Chunk> chunkresult = (ChunkResult) completablefuture.getNow(null); // CraftBukkit - decompile error

            return chunkresult != null ? (chunkresult.isSuccess() ? "done" : "unloaded") : "not completed";
        } catch (CompletionException completionexception) {
            return "failed " + completionexception.getCause().getMessage();
        } catch (CancellationException cancellationexception) {
            return "cancelled";
        }
    }

    private CompletableFuture<Optional<NBTTagCompound>> readChunk(ChunkCoordIntPair chunkcoordintpair) {
        return this.read(chunkcoordintpair).thenApplyAsync((optional) -> {
            return optional.map((nbttagcompound) -> upgradeChunkTag(nbttagcompound, chunkcoordintpair)); // CraftBukkit
        }, SystemUtils.backgroundExecutor().forName("upgradeChunk"));
    }

    // CraftBukkit start
    private NBTTagCompound upgradeChunkTag(NBTTagCompound nbttagcompound, ChunkCoordIntPair chunkcoordintpair) {
        return this.upgradeChunkTag(this.level.getTypeKey(), this.overworldDataStorage, nbttagcompound, this.generator().getTypeNameForDataFixer(), chunkcoordintpair, level);
        // CraftBukkit end
    }

    void collectSpawningChunks(List<Chunk> list) {
        LongIterator longiterator = this.distanceManager.getSpawnCandidateChunks();

        while (longiterator.hasNext()) {
            PlayerChunk playerchunk = (PlayerChunk) this.visibleChunkMap.get(longiterator.nextLong());

            if (playerchunk != null) {
                Chunk chunk = playerchunk.getTickingChunk();

                if (chunk != null && this.anyPlayerCloseEnoughForSpawningInternal(playerchunk.getPos())) {
                    list.add(chunk);
                }
            }
        }

    }

    void forEachBlockTickingChunk(Consumer<Chunk> consumer) {
        this.distanceManager.forEachEntityTickingChunk((i) -> {
            PlayerChunk playerchunk = (PlayerChunk) this.visibleChunkMap.get(i);

            if (playerchunk != null) {
                Chunk chunk = playerchunk.getTickingChunk();

                if (chunk != null) {
                    consumer.accept(chunk);
                }
            }
        });
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkCoordIntPair chunkcoordintpair) {
        // Spigot start
        return anyPlayerCloseEnoughForSpawning(chunkcoordintpair, false);
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkCoordIntPair chunkcoordintpair, boolean reducedRange) {
        // Spigot end
        TriState tristate = this.distanceManager.hasPlayersNearby(chunkcoordintpair.toLong());

        return tristate == TriState.DEFAULT ? this.anyPlayerCloseEnoughForSpawningInternal(chunkcoordintpair, reducedRange) : tristate.toBoolean(true); // Spigot
    }

    private boolean anyPlayerCloseEnoughForSpawningInternal(ChunkCoordIntPair chunkcoordintpair) {
        // Spigot start
        return anyPlayerCloseEnoughForSpawningInternal(chunkcoordintpair, false);
    }

    private boolean anyPlayerCloseEnoughForSpawningInternal(ChunkCoordIntPair chunkcoordintpair, boolean reducedRange) {
        int chunkRange = level.spigotConfig.mobSpawnRange;
        chunkRange = (chunkRange > level.spigotConfig.viewDistance) ? (byte) level.spigotConfig.viewDistance : chunkRange;
        chunkRange = (chunkRange > 8) ? 8 : chunkRange;

        double blockRange = (reducedRange) ? Math.pow(chunkRange << 4, 2) : 16384.0D;
        // Spigot end
        for (EntityPlayer entityplayer : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughForSpawning(entityplayer, chunkcoordintpair, blockRange)) { // Spigot
                return true;
            }
        }

        return false;
    }

    public List<EntityPlayer> getPlayersCloseForSpawning(ChunkCoordIntPair chunkcoordintpair) {
        long i = chunkcoordintpair.toLong();

        if (!this.distanceManager.hasPlayersNearby(i).toBoolean(true)) {
            return List.of();
        } else {
            ImmutableList.Builder<EntityPlayer> immutablelist_builder = ImmutableList.builder();

            for (EntityPlayer entityplayer : this.playerMap.getAllPlayers()) {
                if (this.playerIsCloseEnoughForSpawning(entityplayer, chunkcoordintpair, 16384.0D)) { // Spigot
                    immutablelist_builder.add(entityplayer);
                }
            }

            return immutablelist_builder.build();
        }
    }

    private boolean playerIsCloseEnoughForSpawning(EntityPlayer entityplayer, ChunkCoordIntPair chunkcoordintpair, double range) { // Spigot
        if (entityplayer.isSpectator()) {
            return false;
        } else {
            double d0 = euclideanDistanceSquared(chunkcoordintpair, entityplayer.position());

            return d0 < range; // Spigot
        }
    }

    private static double euclideanDistanceSquared(ChunkCoordIntPair chunkcoordintpair, Vec3D vec3d) {
        double d0 = (double) SectionPosition.sectionToBlockCoord(chunkcoordintpair.x, 8);
        double d1 = (double) SectionPosition.sectionToBlockCoord(chunkcoordintpair.z, 8);
        double d2 = d0 - vec3d.x;
        double d3 = d1 - vec3d.z;

        return d2 * d2 + d3 * d3;
    }

    private boolean skipPlayer(EntityPlayer entityplayer) {
        return entityplayer.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    }

    void updatePlayerStatus(EntityPlayer entityplayer, boolean flag) {
        boolean flag1 = this.skipPlayer(entityplayer);
        boolean flag2 = this.playerMap.ignoredOrUnknown(entityplayer);

        if (flag) {
            this.playerMap.addPlayer(entityplayer, flag1);
            this.updatePlayerPos(entityplayer);
            if (!flag1) {
                this.distanceManager.addPlayer(SectionPosition.of((EntityAccess) entityplayer), entityplayer);
            }

            entityplayer.setChunkTrackingView(ChunkTrackingView.EMPTY);
            this.updateChunkTracking(entityplayer);
        } else {
            SectionPosition sectionposition = entityplayer.getLastSectionPos();

            this.playerMap.removePlayer(entityplayer);
            if (!flag2) {
                this.distanceManager.removePlayer(sectionposition, entityplayer);
            }

            this.applyChunkTrackingView(entityplayer, ChunkTrackingView.EMPTY);
        }

    }

    private void updatePlayerPos(EntityPlayer entityplayer) {
        SectionPosition sectionposition = SectionPosition.of((EntityAccess) entityplayer);

        entityplayer.setLastSectionPos(sectionposition);
    }

    public void move(EntityPlayer entityplayer) {
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();

            if (playerchunkmap_entitytracker.entity == entityplayer) {
                playerchunkmap_entitytracker.updatePlayers(this.level.players());
            } else {
                playerchunkmap_entitytracker.updatePlayer(entityplayer);
            }
        }

        SectionPosition sectionposition = entityplayer.getLastSectionPos();
        SectionPosition sectionposition1 = SectionPosition.of((EntityAccess) entityplayer);
        boolean flag = this.playerMap.ignored(entityplayer);
        boolean flag1 = this.skipPlayer(entityplayer);
        boolean flag2 = sectionposition.asLong() != sectionposition1.asLong();

        if (flag2 || flag != flag1) {
            this.updatePlayerPos(entityplayer);
            if (!flag) {
                this.distanceManager.removePlayer(sectionposition, entityplayer);
            }

            if (!flag1) {
                this.distanceManager.addPlayer(sectionposition1, entityplayer);
            }

            if (!flag && flag1) {
                this.playerMap.ignorePlayer(entityplayer);
            }

            if (flag && !flag1) {
                this.playerMap.unIgnorePlayer(entityplayer);
            }

            this.updateChunkTracking(entityplayer);
        }

    }

    private void updateChunkTracking(EntityPlayer entityplayer) {
        ChunkCoordIntPair chunkcoordintpair = entityplayer.chunkPosition();
        int i = this.getPlayerViewDistance(entityplayer);
        ChunkTrackingView chunktrackingview = entityplayer.getChunkTrackingView();

        if (chunktrackingview instanceof ChunkTrackingView.a chunktrackingview_a) {
            if (chunktrackingview_a.center().equals(chunkcoordintpair) && chunktrackingview_a.viewDistance() == i) {
                return;
            }
        }

        this.applyChunkTrackingView(entityplayer, ChunkTrackingView.of(chunkcoordintpair, i));
    }

    private void applyChunkTrackingView(EntityPlayer entityplayer, ChunkTrackingView chunktrackingview) {
        if (entityplayer.level() == this.level) {
            ChunkTrackingView chunktrackingview1 = entityplayer.getChunkTrackingView();

            if (chunktrackingview instanceof ChunkTrackingView.a) {
                label15:
                {
                    ChunkTrackingView.a chunktrackingview_a = (ChunkTrackingView.a) chunktrackingview;

                    if (chunktrackingview1 instanceof ChunkTrackingView.a) {
                        ChunkTrackingView.a chunktrackingview_a1 = (ChunkTrackingView.a) chunktrackingview1;

                        if (chunktrackingview_a1.center().equals(chunktrackingview_a.center())) {
                            break label15;
                        }
                    }

                    entityplayer.connection.send(new PacketPlayOutViewCentre(chunktrackingview_a.center().x, chunktrackingview_a.center().z));
                }
            }

            ChunkTrackingView.difference(chunktrackingview1, chunktrackingview, (chunkcoordintpair) -> {
                this.markChunkPendingToSend(entityplayer, chunkcoordintpair);
            }, (chunkcoordintpair) -> {
                dropChunk(entityplayer, chunkcoordintpair);
            });
            entityplayer.setChunkTrackingView(chunktrackingview);
        }
    }

    @Override
    public List<EntityPlayer> getPlayers(ChunkCoordIntPair chunkcoordintpair, boolean flag) {
        Set<EntityPlayer> set = this.playerMap.getAllPlayers();
        ImmutableList.Builder<EntityPlayer> immutablelist_builder = ImmutableList.builder();

        for (EntityPlayer entityplayer : set) {
            if (flag && this.isChunkOnTrackedBorder(entityplayer, chunkcoordintpair.x, chunkcoordintpair.z) || !flag && this.isChunkTracked(entityplayer, chunkcoordintpair.x, chunkcoordintpair.z)) {
                immutablelist_builder.add(entityplayer);
            }
        }

        return immutablelist_builder.build();
    }

    protected void addEntity(Entity entity) {
        org.mospigot.AsyncCatcher.catchOp("entity track"); // Spigot
        if (!(entity instanceof EntityComplexPart)) {
            EntityTypes<?> entitytypes = entity.getType();
            int i = entitytypes.clientTrackingRange() * 16;
            i = org.mospigot.TrackingRange.getEntityTrackingRange(entity, i); // Spigot

            if (i != 0) {
                int j = entitytypes.updateInterval();

                if (this.entityMap.containsKey(entity.getId())) {
                    throw (IllegalStateException) SystemUtils.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                } else {
                    PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = new PlayerChunkMap.EntityTracker(entity, i, j, entitytypes.trackDeltas());

                    this.entityMap.put(entity.getId(), playerchunkmap_entitytracker);
                    playerchunkmap_entitytracker.updatePlayers(this.level.players());
                    if (entity instanceof EntityPlayer) {
                        EntityPlayer entityplayer = (EntityPlayer) entity;

                        this.updatePlayerStatus(entityplayer, true);
                        ObjectIterator objectiterator = this.entityMap.values().iterator();

                        while (objectiterator.hasNext()) {
                            PlayerChunkMap.EntityTracker playerchunkmap_entitytracker1 = (PlayerChunkMap.EntityTracker) objectiterator.next();

                            if (playerchunkmap_entitytracker1.entity != entityplayer) {
                                playerchunkmap_entitytracker1.updatePlayer(entityplayer);
                            }
                        }
                    }

                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        org.mospigot.AsyncCatcher.catchOp("entity untrack"); // Spigot
        if (entity instanceof EntityPlayer entityplayer) {
            this.updatePlayerStatus(entityplayer, false);
            ObjectIterator objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();

                playerchunkmap_entitytracker.removePlayer(entityplayer);
            }
        }

        PlayerChunkMap.EntityTracker playerchunkmap_entitytracker1 = (PlayerChunkMap.EntityTracker) this.entityMap.remove(entity.getId());

        if (playerchunkmap_entitytracker1 != null) {
            playerchunkmap_entitytracker1.broadcastRemoved();
        }

    }

    protected void tick() {
        for (EntityPlayer entityplayer : this.playerMap.getAllPlayers()) {
            this.updateChunkTracking(entityplayer);
        }

        List<EntityPlayer> list = Lists.newArrayList();
        List<EntityPlayer> list1 = this.level.players();
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) objectiterator.next();
            SectionPosition sectionposition = playerchunkmap_entitytracker.lastSectionPos;
            SectionPosition sectionposition1 = SectionPosition.of((EntityAccess) playerchunkmap_entitytracker.entity);
            boolean flag = !Objects.equals(sectionposition, sectionposition1);

            if (flag) {
                playerchunkmap_entitytracker.updatePlayers(list1);
                Entity entity = playerchunkmap_entitytracker.entity;

                if (entity instanceof EntityPlayer) {
                    list.add((EntityPlayer) entity);
                }

                playerchunkmap_entitytracker.lastSectionPos = sectionposition1;
            }

            if (flag || this.distanceManager.inEntityTickingRange(sectionposition1.chunk().toLong())) {
                playerchunkmap_entitytracker.serverEntity.sendChanges();
            }
        }

        if (!list.isEmpty()) {
            objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                PlayerChunkMap.EntityTracker playerchunkmap_entitytracker1 = (PlayerChunkMap.EntityTracker) objectiterator.next();

                playerchunkmap_entitytracker1.updatePlayers(list);
            }
        }

    }

    public void broadcast(Entity entity, Packet<?> packet) {
        PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcast(packet);
        }

    }

    protected void broadcastAndSend(Entity entity, Packet<?> packet) {
        PlayerChunkMap.EntityTracker playerchunkmap_entitytracker = (PlayerChunkMap.EntityTracker) this.entityMap.get(entity.getId());

        if (playerchunkmap_entitytracker != null) {
            playerchunkmap_entitytracker.broadcastAndSend(packet);
        }

    }

    public void resendBiomesForChunks(List<IChunkAccess> list) {
        Map<EntityPlayer, List<Chunk>> map = new HashMap();

        for (IChunkAccess ichunkaccess : list) {
            ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
            Chunk chunk;

            if (ichunkaccess instanceof Chunk chunk1) {
                chunk = chunk1;
            } else {
                chunk = this.level.getChunk(chunkcoordintpair.x, chunkcoordintpair.z);
            }

            for (EntityPlayer entityplayer : this.getPlayers(chunkcoordintpair, false)) {
                ((List) map.computeIfAbsent(entityplayer, (entityplayer1) -> {
                    return new ArrayList();
                })).add(chunk);
            }
        }

        map.forEach((entityplayer1, list1) -> {
            entityplayer1.connection.send(ClientboundChunksBiomesPacket.forChunks(list1));
        });
    }

    protected VillagePlace getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkCoordIntPair chunkcoordintpair, FullChunkStatus fullchunkstatus) {
        this.chunkStatusListener.onChunkStatusChange(chunkcoordintpair, fullchunkstatus);
    }

    public void waitForLightBeforeSending(ChunkCoordIntPair chunkcoordintpair, int i) {
        int j = i + 1;

        ChunkCoordIntPair.rangeClosed(chunkcoordintpair, j).forEach((chunkcoordintpair1) -> {
            PlayerChunk playerchunk = this.getVisibleChunkIfPresent(chunkcoordintpair1.toLong());

            if (playerchunk != null) {
                playerchunk.addSendDependency(this.lightEngine.waitForPendingTasks(chunkcoordintpair1.x, chunkcoordintpair1.z));
            }

        });
    }

    private class a extends ChunkMapDistance {

        protected a(final TicketStorage ticketstorage, final Executor executor, final Executor executor1) {
            super(ticketstorage, executor, executor1);
        }

        @Override
        protected boolean isChunkToRemove(long i) {
            return PlayerChunkMap.this.toDrop.contains(i);
        }

        @Nullable
        @Override
        protected PlayerChunk getChunk(long i) {
            return PlayerChunkMap.this.getUpdatingChunkIfPresent(i);
        }

        @Nullable
        @Override
        protected PlayerChunk updateChunkScheduling(long i, int j, @Nullable PlayerChunk playerchunk, int k) {
            return PlayerChunkMap.this.updateChunkScheduling(i, j, playerchunk, k);
        }
    }

    public class EntityTracker {

        public final EntityTrackerEntry serverEntity;
        final Entity entity;
        private final int range;
        SectionPosition lastSectionPos;
        public final Set<ServerPlayerConnection> seenBy = Sets.newIdentityHashSet();

        public EntityTracker(final Entity entity, final int i, final int j, final boolean flag) {
            this.serverEntity = new EntityTrackerEntry(PlayerChunkMap.this.level, entity, j, flag, this::broadcast, this::broadcastIgnorePlayers, seenBy); // CraftBukkit
            this.entity = entity;
            this.range = i;
            this.lastSectionPos = SectionPosition.of((EntityAccess) entity);
        }

        public boolean equals(Object object) {
            return object instanceof PlayerChunkMap.EntityTracker ? ((PlayerChunkMap.EntityTracker) object).entity.getId() == this.entity.getId() : false;
        }

        public int hashCode() {
            return this.entity.getId();
        }

        public void broadcast(Packet<?> packet) {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                serverplayerconnection.send(packet);
            }

        }

        public void broadcastIgnorePlayers(Packet<?> packet, List<UUID> list) {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                if (!list.contains(serverplayerconnection.getPlayer().getUUID())) {
                    serverplayerconnection.send(packet);
                }
            }

        }

        public void broadcastAndSend(Packet<?> packet) {
            this.broadcast(packet);
            if (this.entity instanceof EntityPlayer) {
                ((EntityPlayer) this.entity).connection.send(packet);
            }

        }

        public void broadcastRemoved() {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                this.serverEntity.removePairing(serverplayerconnection.getPlayer());
            }

        }

        public void removePlayer(EntityPlayer entityplayer) {
            org.mospigot.AsyncCatcher.catchOp("player tracker clear"); // Spigot
            if (this.seenBy.remove(entityplayer.connection)) {
                this.serverEntity.removePairing(entityplayer);
            }

        }

        public void updatePlayer(EntityPlayer entityplayer) {
            org.mospigot.AsyncCatcher.catchOp("player tracker update"); // Spigot
            if (entityplayer != this.entity) {
                Vec3D vec3d = entityplayer.position().subtract(this.entity.position());
                int i = PlayerChunkMap.this.getPlayerViewDistance(entityplayer);
                double d0 = (double) Math.min(this.getEffectiveRange(), i * 16);
                double d1 = vec3d.x * vec3d.x + vec3d.z * vec3d.z;
                double d2 = d0 * d0;
                boolean flag = d1 <= d2 && this.entity.broadcastToPlayer(entityplayer) && PlayerChunkMap.this.isChunkTracked(entityplayer, this.entity.chunkPosition().x, this.entity.chunkPosition().z);

                // CraftBukkit start - respect vanish API
                if (!entityplayer.getBukkitEntity().canSee(this.entity.getBukkitEntity())) {
                    flag = false;
                }
                // CraftBukkit end
                if (flag) {
                    if (this.seenBy.add(entityplayer.connection)) {
                        this.serverEntity.addPairing(entityplayer);
                    }
                } else if (this.seenBy.remove(entityplayer.connection)) {
                    this.serverEntity.removePairing(entityplayer);
                }

            }
        }

        private int scaledRange(int i) {
            return PlayerChunkMap.this.level.getServer().getScaledTrackingDistance(i);
        }

        private int getEffectiveRange() {
            int i = this.range;

            for (Entity entity : this.entity.getIndirectPassengers()) {
                int j = entity.getType().clientTrackingRange() * 16;

                if (j > i) {
                    i = j;
                }
            }

            return this.scaledRange(i);
        }

        public void updatePlayers(List<EntityPlayer> list) {
            for (EntityPlayer entityplayer : list) {
                this.updatePlayer(entityplayer);
            }

        }
    }
}
