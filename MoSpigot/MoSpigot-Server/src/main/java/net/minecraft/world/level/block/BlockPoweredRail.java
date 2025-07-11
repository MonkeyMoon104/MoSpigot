package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyTrackPosition;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;
import net.minecraft.world.level.block.state.properties.IBlockState;

import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class BlockPoweredRail extends BlockMinecartTrackAbstract {

    public static final MapCodec<BlockPoweredRail> CODEC = simpleCodec(BlockPoweredRail::new);
    public static final BlockStateEnum<BlockPropertyTrackPosition> SHAPE = BlockProperties.RAIL_SHAPE_STRAIGHT;
    public static final BlockStateBoolean POWERED = BlockProperties.POWERED;

    @Override
    public MapCodec<BlockPoweredRail> codec() {
        return BlockPoweredRail.CODEC;
    }

    protected BlockPoweredRail(BlockBase.Info blockbase_info) {
        super(true, blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(BlockPoweredRail.SHAPE, BlockPropertyTrackPosition.NORTH_SOUTH)).setValue(BlockPoweredRail.POWERED, false)).setValue(BlockPoweredRail.WATERLOGGED, false));
    }

    protected boolean findPoweredRailSignal(World world, BlockPosition blockposition, IBlockData iblockdata, boolean flag, int i) {
        if (i >= 8) {
            return false;
        } else {
            int j = blockposition.getX();
            int k = blockposition.getY();
            int l = blockposition.getZ();
            boolean flag1 = true;
            BlockPropertyTrackPosition blockpropertytrackposition = (BlockPropertyTrackPosition) iblockdata.getValue(BlockPoweredRail.SHAPE);

            switch (blockpropertytrackposition) {
                case NORTH_SOUTH:
                    if (flag) {
                        ++l;
                    } else {
                        --l;
                    }
                    break;
                case EAST_WEST:
                    if (flag) {
                        --j;
                    } else {
                        ++j;
                    }
                    break;
                case ASCENDING_EAST:
                    if (flag) {
                        --j;
                    } else {
                        ++j;
                        ++k;
                        flag1 = false;
                    }

                    blockpropertytrackposition = BlockPropertyTrackPosition.EAST_WEST;
                    break;
                case ASCENDING_WEST:
                    if (flag) {
                        --j;
                        ++k;
                        flag1 = false;
                    } else {
                        ++j;
                    }

                    blockpropertytrackposition = BlockPropertyTrackPosition.EAST_WEST;
                    break;
                case ASCENDING_NORTH:
                    if (flag) {
                        ++l;
                    } else {
                        --l;
                        ++k;
                        flag1 = false;
                    }

                    blockpropertytrackposition = BlockPropertyTrackPosition.NORTH_SOUTH;
                    break;
                case ASCENDING_SOUTH:
                    if (flag) {
                        ++l;
                        ++k;
                        flag1 = false;
                    } else {
                        --l;
                    }

                    blockpropertytrackposition = BlockPropertyTrackPosition.NORTH_SOUTH;
            }

            return this.isSameRailWithPower(world, new BlockPosition(j, k, l), flag, i, blockpropertytrackposition) ? true : flag1 && this.isSameRailWithPower(world, new BlockPosition(j, k - 1, l), flag, i, blockpropertytrackposition);
        }
    }

    protected boolean isSameRailWithPower(World world, BlockPosition blockposition, boolean flag, int i, BlockPropertyTrackPosition blockpropertytrackposition) {
        IBlockData iblockdata = world.getBlockState(blockposition);

        if (!iblockdata.is(this)) {
            return false;
        } else {
            BlockPropertyTrackPosition blockpropertytrackposition1 = (BlockPropertyTrackPosition) iblockdata.getValue(BlockPoweredRail.SHAPE);

            return blockpropertytrackposition != BlockPropertyTrackPosition.EAST_WEST || blockpropertytrackposition1 != BlockPropertyTrackPosition.NORTH_SOUTH && blockpropertytrackposition1 != BlockPropertyTrackPosition.ASCENDING_NORTH && blockpropertytrackposition1 != BlockPropertyTrackPosition.ASCENDING_SOUTH ? (blockpropertytrackposition != BlockPropertyTrackPosition.NORTH_SOUTH || blockpropertytrackposition1 != BlockPropertyTrackPosition.EAST_WEST && blockpropertytrackposition1 != BlockPropertyTrackPosition.ASCENDING_EAST && blockpropertytrackposition1 != BlockPropertyTrackPosition.ASCENDING_WEST ? ((Boolean) iblockdata.getValue(BlockPoweredRail.POWERED) ? (world.hasNeighborSignal(blockposition) ? true : this.findPoweredRailSignal(world, blockposition, iblockdata, flag, i + 1)) : false) : false) : false;
        }
    }

    @Override
    protected void updateState(IBlockData iblockdata, World world, BlockPosition blockposition, Block block) {
        boolean flag = (Boolean) iblockdata.getValue(BlockPoweredRail.POWERED);
        boolean flag1 = world.hasNeighborSignal(blockposition) || this.findPoweredRailSignal(world, blockposition, iblockdata, true, 0) || this.findPoweredRailSignal(world, blockposition, iblockdata, false, 0);

        if (flag1 != flag) {
            // CraftBukkit start
            int power = flag ? 15 : 0;
            int newPower = CraftEventFactory.callRedstoneChange(world, blockposition, power, 15 - power).getNewCurrent();
            if (newPower == power) {
                return;
            }
            // CraftBukkit end
            world.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockPoweredRail.POWERED, flag1), 3);
            world.updateNeighborsAt(blockposition.below(), this);
            if (((BlockPropertyTrackPosition) iblockdata.getValue(BlockPoweredRail.SHAPE)).isSlope()) {
                world.updateNeighborsAt(blockposition.above(), this);
            }
        }

    }

    @Override
    public IBlockState<BlockPropertyTrackPosition> getShapeProperty() {
        return BlockPoweredRail.SHAPE;
    }

    @Override
    protected IBlockData rotate(IBlockData iblockdata, EnumBlockRotation enumblockrotation) {
        BlockPropertyTrackPosition blockpropertytrackposition = (BlockPropertyTrackPosition) iblockdata.getValue(BlockPoweredRail.SHAPE);
        BlockPropertyTrackPosition blockpropertytrackposition1 = this.rotate(blockpropertytrackposition, enumblockrotation);

        return (IBlockData) iblockdata.setValue(BlockPoweredRail.SHAPE, blockpropertytrackposition1);
    }

    @Override
    protected IBlockData mirror(IBlockData iblockdata, EnumBlockMirror enumblockmirror) {
        BlockPropertyTrackPosition blockpropertytrackposition = (BlockPropertyTrackPosition) iblockdata.getValue(BlockPoweredRail.SHAPE);
        BlockPropertyTrackPosition blockpropertytrackposition1 = this.mirror(blockpropertytrackposition, enumblockmirror);

        return (IBlockData) iblockdata.setValue(BlockPoweredRail.SHAPE, blockpropertytrackposition1);
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockPoweredRail.SHAPE, BlockPoweredRail.POWERED, BlockPoweredRail.WATERLOGGED);
    }
}
