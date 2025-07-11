package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateInteger;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.material.FluidTypes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.event.block.CauldronLevelChangeEvent;
// CraftBukkit end

public class LayeredCauldronBlock extends AbstractCauldronBlock {

    public static final MapCodec<LayeredCauldronBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BiomeBase.Precipitation.CODEC.fieldOf("precipitation").forGetter((layeredcauldronblock) -> {
            return layeredcauldronblock.precipitationType;
        }), CauldronInteraction.CODEC.fieldOf("interactions").forGetter((layeredcauldronblock) -> {
            return layeredcauldronblock.interactions;
        }), propertiesCodec()).apply(instance, LayeredCauldronBlock::new);
    });
    public static final int MIN_FILL_LEVEL = 1;
    public static final int MAX_FILL_LEVEL = 3;
    public static final BlockStateInteger LEVEL = BlockProperties.LEVEL_CAULDRON;
    private static final int BASE_CONTENT_HEIGHT = 6;
    private static final double HEIGHT_PER_LEVEL = 3.0D;
    private static final VoxelShape[] FILLED_SHAPES = (VoxelShape[]) SystemUtils.make(() -> {
        return Block.boxes(2, (i) -> {
            return VoxelShapes.or(AbstractCauldronBlock.SHAPE, Block.column(12.0D, 4.0D, getPixelContentHeight(i + 1)));
        });
    });
    private final BiomeBase.Precipitation precipitationType;

    @Override
    public MapCodec<LayeredCauldronBlock> codec() {
        return LayeredCauldronBlock.CODEC;
    }

    public LayeredCauldronBlock(BiomeBase.Precipitation biomebase_precipitation, CauldronInteraction.a cauldroninteraction_a, BlockBase.Info blockbase_info) {
        super(blockbase_info, cauldroninteraction_a);
        this.precipitationType = biomebase_precipitation;
        this.registerDefaultState((IBlockData) (this.stateDefinition.any()).setValue(LayeredCauldronBlock.LEVEL, 1));
    }

    @Override
    public boolean isFull(IBlockData iblockdata) {
        return (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) == 3;
    }

    @Override
    protected boolean canReceiveStalactiteDrip(FluidType fluidtype) {
        return fluidtype == FluidTypes.WATER && this.precipitationType == BiomeBase.Precipitation.RAIN;
    }

    @Override
    protected double getContentHeight(IBlockData iblockdata) {
        return getPixelContentHeight((Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL)) / 16.0D;
    }

    private static double getPixelContentHeight(int i) {
        return 6.0D + (double) i * 3.0D;
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, Entity entity) {
        return LayeredCauldronBlock.FILLED_SHAPES[(Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) - 1];
    }

    @Override
    protected void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, InsideBlockEffectApplier insideblockeffectapplier) {
        if (world instanceof WorldServer worldserver) {
            BlockPosition blockposition1 = blockposition.immutable();

            insideblockeffectapplier.runBefore(InsideBlockEffectType.EXTINGUISH, (entity1) -> {
                if (entity1.isOnFire() && entity1.mayInteract(worldserver, blockposition1)) {
                    // CraftBukkit start
                    if (this.handleEntityOnFireInside(iblockdata, world, blockposition1, entity)) {
                        insideblockeffectapplier.apply(InsideBlockEffectType.EXTINGUISH);
                    }
                    // CraftBukkit end
                }

            });
        }

        // insideblockeffectapplier.apply(InsideBlockEffectType.EXTINGUISH); // CraftBukkit start - moved up
    }

    // CraftBukkit start
    private boolean handleEntityOnFireInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity) {
        if (this.precipitationType == BiomeBase.Precipitation.SNOW) {
            return lowerFillLevel((IBlockData) Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL)), world, blockposition, entity, CauldronLevelChangeEvent.ChangeReason.EXTINGUISH);
        } else {
            return lowerFillLevel(iblockdata, world, blockposition, entity, CauldronLevelChangeEvent.ChangeReason.EXTINGUISH);
            // CraftBukkit end
        }

    }

    public static void lowerFillLevel(IBlockData iblockdata, World world, BlockPosition blockposition) {
        // CraftBukkit start
        lowerFillLevel(iblockdata, world, blockposition, null, CauldronLevelChangeEvent.ChangeReason.UNKNOWN);
    }

    public static boolean lowerFillLevel(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, CauldronLevelChangeEvent.ChangeReason reason) {
        int i = (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) - 1;
        IBlockData iblockdata1 = i == 0 ? Blocks.CAULDRON.defaultBlockState() : (IBlockData) iblockdata.setValue(LayeredCauldronBlock.LEVEL, i);

        return changeLevel(iblockdata, world, blockposition, iblockdata1, entity, reason);
    }

    // CraftBukkit start
    public static boolean changeLevel(IBlockData iblockdata, World world, BlockPosition blockposition, IBlockData newBlock, Entity entity, CauldronLevelChangeEvent.ChangeReason reason) {
        CraftBlockState newState = CraftBlockStates.getBlockState(world, blockposition);
        newState.setData(newBlock);

        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(
                world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()),
                (entity == null) ? null : entity.getBukkitEntity(), reason, newState
        );
        world.getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        newState.update(true);
        world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of(newBlock));
        return true;
    }
    // CraftBukkit end

    @Override
    public void handlePrecipitation(IBlockData iblockdata, World world, BlockPosition blockposition, BiomeBase.Precipitation biomebase_precipitation) {
        if (BlockCauldron.shouldHandlePrecipitation(world, biomebase_precipitation) && (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) != 3 && biomebase_precipitation == this.precipitationType) {
            IBlockData iblockdata1 = (IBlockData) iblockdata.cycle(LayeredCauldronBlock.LEVEL);

            changeLevel(iblockdata, world, blockposition, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL); // CraftBukkit
        }
    }

    @Override
    protected int getAnalogOutputSignal(IBlockData iblockdata, World world, BlockPosition blockposition) {
        return (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void receiveStalactiteDrip(IBlockData iblockdata, World world, BlockPosition blockposition, FluidType fluidtype) {
        if (!this.isFull(iblockdata)) {
            IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(LayeredCauldronBlock.LEVEL, (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) + 1);

            // CraftBukkit start
            if (!changeLevel(iblockdata, world, blockposition, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) {
                return;
            }
            // CraftBukkit end
            world.levelEvent(1047, blockposition, 0);
        }
    }
}
