package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.levelgen.feature.WorldGenFeatureConfigured;
import net.minecraft.world.level.levelgen.feature.WorldGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.WorldGenEndGatewayConfiguration;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.world.level.dimension.WorldDimension;
// CraftBukkit end

public class TileEntityEndGateway extends TileEntityEnderPortal {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SPAWN_TIME = 200;
    private static final int COOLDOWN_TIME = 40;
    private static final int ATTENTION_INTERVAL = 2400;
    private static final int EVENT_COOLDOWN = 1;
    private static final int GATEWAY_HEIGHT_ABOVE_SURFACE = 10;
    private static final long DEFAULT_AGE = 0L;
    private static final boolean DEFAULT_EXACT_TELEPORT = false;
    public long age = 0L;
    private int teleportCooldown;
    @Nullable
    public BlockPosition exitPortal;
    public boolean exactTeleport = false;

    public TileEntityEndGateway(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.END_GATEWAY, blockposition, iblockdata);
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        valueoutput.putLong("Age", this.age);
        valueoutput.storeNullable("exit_portal", BlockPosition.CODEC, this.exitPortal);
        if (this.exactTeleport) {
            valueoutput.putBoolean("ExactTeleport", true);
        }

    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.age = valueinput.getLongOr("Age", 0L);
        this.exitPortal = (BlockPosition) valueinput.read("exit_portal", BlockPosition.CODEC).filter(World::isInSpawnableBounds).orElse(null); // CraftBukkit - decompile error
        this.exactTeleport = valueinput.getBooleanOr("ExactTeleport", false);
    }

    public static void beamAnimationTick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityEndGateway tileentityendgateway) {
        ++tileentityendgateway.age;
        if (tileentityendgateway.isCoolingDown()) {
            --tileentityendgateway.teleportCooldown;
        }

    }

    public static void portalTick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityEndGateway tileentityendgateway) {
        boolean flag = tileentityendgateway.isSpawning();
        boolean flag1 = tileentityendgateway.isCoolingDown();

        ++tileentityendgateway.age;
        if (flag1) {
            --tileentityendgateway.teleportCooldown;
        } else if (tileentityendgateway.age % 2400L == 0L) {
            triggerCooldown(world, blockposition, iblockdata, tileentityendgateway);
        }

        if (flag != tileentityendgateway.isSpawning() || flag1 != tileentityendgateway.isCoolingDown()) {
            setChanged(world, blockposition, iblockdata);
        }

    }

    public boolean isSpawning() {
        return this.age < 200L;
    }

    public boolean isCoolingDown() {
        return this.teleportCooldown > 0;
    }

    public float getSpawnPercent(float f) {
        return MathHelper.clamp(((float) this.age + f) / 200.0F, 0.0F, 1.0F);
    }

    public float getCooldownPercent(float f) {
        return 1.0F - MathHelper.clamp(((float) this.teleportCooldown - f) / 40.0F, 0.0F, 1.0F);
    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        return this.saveCustomOnly(holderlookup_a);
    }

    public static void triggerCooldown(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityEndGateway tileentityendgateway) {
        if (!world.isClientSide) {
            tileentityendgateway.teleportCooldown = 40;
            world.blockEvent(blockposition, iblockdata.getBlock(), 1, 0);
            setChanged(world, blockposition, iblockdata);
        }

    }

    @Override
    public boolean triggerEvent(int i, int j) {
        if (i == 1) {
            this.teleportCooldown = 40;
            return true;
        } else {
            return super.triggerEvent(i, j);
        }
    }

    @Nullable
    public Vec3D getPortalPosition(WorldServer worldserver, BlockPosition blockposition) {
        if (this.exitPortal == null && worldserver.getTypeKey() == WorldDimension.END) { // CraftBukkit - work in alternate worlds
            BlockPosition blockposition1 = findOrCreateValidTeleportPos(worldserver, blockposition);

            blockposition1 = blockposition1.above(10);
            TileEntityEndGateway.LOGGER.debug("Creating portal at {}", blockposition1);
            spawnGatewayPortal(worldserver, blockposition1, WorldGenEndGatewayConfiguration.knownExit(blockposition, false));
            this.setExitPosition(blockposition1, this.exactTeleport);
        }

        if (this.exitPortal != null) {
            BlockPosition blockposition2 = this.exactTeleport ? this.exitPortal : findExitPosition(worldserver, this.exitPortal);

            return blockposition2.getBottomCenter();
        } else {
            return null;
        }
    }

    private static BlockPosition findExitPosition(World world, BlockPosition blockposition) {
        BlockPosition blockposition1 = findTallestBlock(world, blockposition.offset(0, 2, 0), 5, false);

        TileEntityEndGateway.LOGGER.debug("Best exit position for portal at {} is {}", blockposition, blockposition1);
        return blockposition1.above();
    }

    private static BlockPosition findOrCreateValidTeleportPos(WorldServer worldserver, BlockPosition blockposition) {
        Vec3D vec3d = findExitPortalXZPosTentative(worldserver, blockposition);
        Chunk chunk = getChunk(worldserver, vec3d);
        BlockPosition blockposition1 = findValidSpawnInChunk(chunk);

        if (blockposition1 == null) {
            BlockPosition blockposition2 = BlockPosition.containing(vec3d.x + 0.5D, 75.0D, vec3d.z + 0.5D);

            TileEntityEndGateway.LOGGER.debug("Failed to find a suitable block to teleport to, spawning an island on {}", blockposition2);
            worldserver.registryAccess().lookup(Registries.CONFIGURED_FEATURE).flatMap((iregistry) -> {
                return iregistry.get(EndFeatures.END_ISLAND);
            }).ifPresent((holder_c) -> {
                ((WorldGenFeatureConfigured) holder_c.value()).place(worldserver, worldserver.getChunkSource().getGenerator(), RandomSource.create(blockposition2.asLong()), blockposition2);
            });
            blockposition1 = blockposition2;
        } else {
            TileEntityEndGateway.LOGGER.debug("Found suitable block to teleport to: {}", blockposition1);
        }

        return findTallestBlock(worldserver, blockposition1, 16, true);
    }

    private static Vec3D findExitPortalXZPosTentative(WorldServer worldserver, BlockPosition blockposition) {
        Vec3D vec3d = (new Vec3D((double) blockposition.getX(), 0.0D, (double) blockposition.getZ())).normalize();
        int i = 1024;
        Vec3D vec3d1 = vec3d.scale(1024.0D);

        for (int j = 16; !isChunkEmpty(worldserver, vec3d1) && j-- > 0; vec3d1 = vec3d1.add(vec3d.scale(-16.0D))) {
            TileEntityEndGateway.LOGGER.debug("Skipping backwards past nonempty chunk at {}", vec3d1);
        }

        for (int k = 16; isChunkEmpty(worldserver, vec3d1) && k-- > 0; vec3d1 = vec3d1.add(vec3d.scale(16.0D))) {
            TileEntityEndGateway.LOGGER.debug("Skipping forward past empty chunk at {}", vec3d1);
        }

        TileEntityEndGateway.LOGGER.debug("Found chunk at {}", vec3d1);
        return vec3d1;
    }

    private static boolean isChunkEmpty(WorldServer worldserver, Vec3D vec3d) {
        return getChunk(worldserver, vec3d).getHighestFilledSectionIndex() == -1;
    }

    private static BlockPosition findTallestBlock(IBlockAccess iblockaccess, BlockPosition blockposition, int i, boolean flag) {
        BlockPosition blockposition1 = null;

        for (int j = -i; j <= i; ++j) {
            for (int k = -i; k <= i; ++k) {
                if (j != 0 || k != 0 || flag) {
                    for (int l = iblockaccess.getMaxY(); l > (blockposition1 == null ? iblockaccess.getMinY() : blockposition1.getY()); --l) {
                        BlockPosition blockposition2 = new BlockPosition(blockposition.getX() + j, l, blockposition.getZ() + k);
                        IBlockData iblockdata = iblockaccess.getBlockState(blockposition2);

                        if (iblockdata.isCollisionShapeFullBlock(iblockaccess, blockposition2) && (flag || !iblockdata.is(Blocks.BEDROCK))) {
                            blockposition1 = blockposition2;
                            break;
                        }
                    }
                }
            }
        }

        return blockposition1 == null ? blockposition : blockposition1;
    }

    private static Chunk getChunk(World world, Vec3D vec3d) {
        return world.getChunk(MathHelper.floor(vec3d.x / 16.0D), MathHelper.floor(vec3d.z / 16.0D));
    }

    @Nullable
    private static BlockPosition findValidSpawnInChunk(Chunk chunk) {
        ChunkCoordIntPair chunkcoordintpair = chunk.getPos();
        BlockPosition blockposition = new BlockPosition(chunkcoordintpair.getMinBlockX(), 30, chunkcoordintpair.getMinBlockZ());
        int i = chunk.getHighestSectionPosition() + 16 - 1;
        BlockPosition blockposition1 = new BlockPosition(chunkcoordintpair.getMaxBlockX(), i, chunkcoordintpair.getMaxBlockZ());
        BlockPosition blockposition2 = null;
        double d0 = 0.0D;

        for (BlockPosition blockposition3 : BlockPosition.betweenClosed(blockposition, blockposition1)) {
            IBlockData iblockdata = chunk.getBlockState(blockposition3);
            BlockPosition blockposition4 = blockposition3.above();
            BlockPosition blockposition5 = blockposition3.above(2);

            if (iblockdata.is(Blocks.END_STONE) && !chunk.getBlockState(blockposition4).isCollisionShapeFullBlock(chunk, blockposition4) && !chunk.getBlockState(blockposition5).isCollisionShapeFullBlock(chunk, blockposition5)) {
                double d1 = blockposition3.distToCenterSqr(0.0D, 0.0D, 0.0D);

                if (blockposition2 == null || d1 < d0) {
                    blockposition2 = blockposition3;
                    d0 = d1;
                }
            }
        }

        return blockposition2;
    }

    private static void spawnGatewayPortal(WorldServer worldserver, BlockPosition blockposition, WorldGenEndGatewayConfiguration worldgenendgatewayconfiguration) {
        WorldGenerator.END_GATEWAY.place(worldgenendgatewayconfiguration, worldserver, worldserver.getChunkSource().getGenerator(), RandomSource.create(), blockposition);
    }

    @Override
    public boolean shouldRenderFace(EnumDirection enumdirection) {
        return Block.shouldRenderFace(this.getBlockState(), this.level.getBlockState(this.getBlockPos().relative(enumdirection)), enumdirection);
    }

    public int getParticleAmount() {
        int i = 0;

        for (EnumDirection enumdirection : EnumDirection.values()) {
            i += this.shouldRenderFace(enumdirection) ? 1 : 0;
        }

        return i;
    }

    public void setExitPosition(BlockPosition blockposition, boolean flag) {
        this.exactTeleport = flag;
        this.exitPortal = blockposition;
        this.setChanged();
    }
}
