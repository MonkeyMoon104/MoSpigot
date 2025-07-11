package net.minecraft.world.level.chunk.status;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.LightEngineThreaded;
import net.minecraft.server.level.RegionLimitedWorldAccess;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunkExtension;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.slf4j.Logger;

public class ChunkStatusTasks {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ChunkStatusTasks() {}

    private static boolean isLighted(IChunkAccess ichunkaccess) {
        return ichunkaccess.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT) && ichunkaccess.isLightCorrect();
    }

    static CompletableFuture<IChunkAccess> passThrough(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> generateStructureStarts(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        WorldServer worldserver = worldgencontext.level();

        if (worldserver.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            worldgencontext.generator().createStructures(worldserver.registryAccess(), worldserver.getChunkSource().getGeneratorState(), worldserver.structureManager(), ichunkaccess, worldgencontext.structureManager(), worldserver.dimension());
        }

        worldserver.onStructureStartsAvailable(ichunkaccess);
        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> loadStructureStarts(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        worldgencontext.level().onStructureStartsAvailable(ichunkaccess);
        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> generateStructureReferences(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        WorldServer worldserver = worldgencontext.level();
        RegionLimitedWorldAccess regionlimitedworldaccess = new RegionLimitedWorldAccess(worldserver, staticcache2d, chunkstep, ichunkaccess);

        worldgencontext.generator().createReferences(regionlimitedworldaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess);
        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> generateBiomes(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        WorldServer worldserver = worldgencontext.level();
        RegionLimitedWorldAccess regionlimitedworldaccess = new RegionLimitedWorldAccess(worldserver, staticcache2d, chunkstep, ichunkaccess);

        return worldgencontext.generator().createBiomes(worldserver.getChunkSource().randomState(), Blender.of(regionlimitedworldaccess), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> generateNoise(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        WorldServer worldserver = worldgencontext.level();
        RegionLimitedWorldAccess regionlimitedworldaccess = new RegionLimitedWorldAccess(worldserver, staticcache2d, chunkstep, ichunkaccess);

        return worldgencontext.generator().fillFromNoise(Blender.of(regionlimitedworldaccess), worldserver.getChunkSource().randomState(), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess).thenApply((ichunkaccess1) -> {
            if (ichunkaccess1 instanceof ProtoChunk protochunk) {
                BelowZeroRetrogen belowzeroretrogen = protochunk.getBelowZeroRetrogen();

                if (belowzeroretrogen != null) {
                    BelowZeroRetrogen.replaceOldBedrock(protochunk);
                    if (belowzeroretrogen.hasBedrockHoles()) {
                        belowzeroretrogen.applyBedrockMask(protochunk);
                    }
                }
            }

            return ichunkaccess1;
        });
    }

    static CompletableFuture<IChunkAccess> generateSurface(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        WorldServer worldserver = worldgencontext.level();
        RegionLimitedWorldAccess regionlimitedworldaccess = new RegionLimitedWorldAccess(worldserver, staticcache2d, chunkstep, ichunkaccess);

        worldgencontext.generator().buildSurface(regionlimitedworldaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), worldserver.getChunkSource().randomState(), ichunkaccess);
        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> generateCarvers(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        WorldServer worldserver = worldgencontext.level();
        RegionLimitedWorldAccess regionlimitedworldaccess = new RegionLimitedWorldAccess(worldserver, staticcache2d, chunkstep, ichunkaccess);

        if (ichunkaccess instanceof ProtoChunk protochunk) {
            Blender.addAroundOldChunksCarvingMaskFilter(regionlimitedworldaccess, protochunk);
        }

        worldgencontext.generator().applyCarvers(regionlimitedworldaccess, worldserver.getSeed(), worldserver.getChunkSource().randomState(), worldserver.getBiomeManager(), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), ichunkaccess);
        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> generateFeatures(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        WorldServer worldserver = worldgencontext.level();

        HeightMap.primeHeightmaps(ichunkaccess, EnumSet.of(HeightMap.Type.MOTION_BLOCKING, HeightMap.Type.MOTION_BLOCKING_NO_LEAVES, HeightMap.Type.OCEAN_FLOOR, HeightMap.Type.WORLD_SURFACE));
        RegionLimitedWorldAccess regionlimitedworldaccess = new RegionLimitedWorldAccess(worldserver, staticcache2d, chunkstep, ichunkaccess);

        worldgencontext.generator().applyBiomeDecoration(regionlimitedworldaccess, ichunkaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess));
        Blender.generateBorderTicks(regionlimitedworldaccess, ichunkaccess);
        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> initializeLight(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        LightEngineThreaded lightenginethreaded = worldgencontext.lightEngine();

        ichunkaccess.initializeLightSources();
        ((ProtoChunk) ichunkaccess).setLightEngine(lightenginethreaded);
        boolean flag = isLighted(ichunkaccess);

        return lightenginethreaded.initializeLight(ichunkaccess, flag);
    }

    static CompletableFuture<IChunkAccess> light(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        boolean flag = isLighted(ichunkaccess);

        return worldgencontext.lightEngine().lightChunk(ichunkaccess, flag);
    }

    static CompletableFuture<IChunkAccess> generateSpawn(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        if (!ichunkaccess.isUpgrading()) {
            worldgencontext.generator().spawnOriginalMobs(new RegionLimitedWorldAccess(worldgencontext.level(), staticcache2d, chunkstep, ichunkaccess));
        }

        return CompletableFuture.completedFuture(ichunkaccess);
    }

    static CompletableFuture<IChunkAccess> full(WorldGenContext worldgencontext, ChunkStep chunkstep, StaticCache2D<GenerationChunkHolder> staticcache2d, IChunkAccess ichunkaccess) {
        ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
        GenerationChunkHolder generationchunkholder = staticcache2d.get(chunkcoordintpair.x, chunkcoordintpair.z);

        return CompletableFuture.supplyAsync(() -> {
            ProtoChunk protochunk = (ProtoChunk) ichunkaccess;
            WorldServer worldserver = worldgencontext.level();
            Chunk chunk;

            if (protochunk instanceof ProtoChunkExtension protochunkextension) {
                chunk = protochunkextension.getWrapped();
            } else {
                chunk = new Chunk(worldserver, protochunk, (chunk1) -> {
                    try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(ichunkaccess.problemPath(), ChunkStatusTasks.LOGGER)) {
                        postLoadProtoChunk(worldserver, TagValueInput.create(problemreporter_j, worldserver.registryAccess(), protochunk.getEntities()));
                    }

                });
                generationchunkholder.replaceProtoChunk(new ProtoChunkExtension(chunk, false));
            }

            Objects.requireNonNull(generationchunkholder);
            chunk.setFullStatus(generationchunkholder::getFullStatus);
            chunk.runPostLoad();
            chunk.setLoaded(true);
            chunk.registerAllBlockEntitiesAfterLevelLoad();
            chunk.registerTickContainerInLevel(worldserver);
            chunk.setUnsavedListener(worldgencontext.unsavedListener());
            return chunk;
        }, worldgencontext.mainThreadExecutor());
    }

    private static void postLoadProtoChunk(WorldServer worldserver, ValueInput.b valueinput_b) {
        if (!valueinput_b.isEmpty()) {
            // CraftBukkit start - these are spawned serialized (DefinedStructure) and we don't call an add event below at the moment due to ordering complexities
            worldserver.addWorldGenChunkEntities(EntityTypes.loadEntitiesRecursive(valueinput_b, worldserver, EntitySpawnReason.LOAD).filter((entity) -> {
                boolean needsRemoval = false;
                net.minecraft.server.dedicated.DedicatedServer server = worldserver.getCraftServer().getServer();
                if (!worldserver.getChunkSource().spawnFriendlies && (entity instanceof net.minecraft.world.entity.animal.EntityAnimal || entity instanceof net.minecraft.world.entity.animal.EntityWaterAnimal)) {
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    needsRemoval = true;
                }
                return !needsRemoval;
            }));
            // CraftBukkit end
        }

    }
}
