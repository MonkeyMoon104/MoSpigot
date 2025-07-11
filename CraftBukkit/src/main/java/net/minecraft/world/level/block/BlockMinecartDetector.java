package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.IInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.vehicle.EntityMinecartAbstract;
import net.minecraft.world.entity.vehicle.EntityMinecartCommandBlock;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyTrackPosition;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AxisAlignedBB;

import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class BlockMinecartDetector extends BlockMinecartTrackAbstract {

    public static final MapCodec<BlockMinecartDetector> CODEC = simpleCodec(BlockMinecartDetector::new);
    public static final BlockStateEnum<BlockPropertyTrackPosition> SHAPE = BlockProperties.RAIL_SHAPE_STRAIGHT;
    public static final BlockStateBoolean POWERED = BlockProperties.POWERED;
    private static final int PRESSED_CHECK_PERIOD = 20;

    @Override
    public MapCodec<BlockMinecartDetector> codec() {
        return BlockMinecartDetector.CODEC;
    }

    public BlockMinecartDetector(BlockBase.Info blockbase_info) {
        super(true, blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(BlockMinecartDetector.POWERED, false)).setValue(BlockMinecartDetector.SHAPE, BlockPropertyTrackPosition.NORTH_SOUTH)).setValue(BlockMinecartDetector.WATERLOGGED, false));
    }

    @Override
    protected boolean isSignalSource(IBlockData iblockdata) {
        return true;
    }

    @Override
    protected void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, InsideBlockEffectApplier insideblockeffectapplier) {
        if (!world.isClientSide) {
            if (!(Boolean) iblockdata.getValue(BlockMinecartDetector.POWERED)) {
                this.checkPressed(world, blockposition, iblockdata);
            }
        }
    }

    @Override
    protected void tick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if ((Boolean) iblockdata.getValue(BlockMinecartDetector.POWERED)) {
            this.checkPressed(worldserver, blockposition, iblockdata);
        }
    }

    @Override
    protected int getSignal(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        return (Boolean) iblockdata.getValue(BlockMinecartDetector.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        return !(Boolean) iblockdata.getValue(BlockMinecartDetector.POWERED) ? 0 : (enumdirection == EnumDirection.UP ? 15 : 0);
    }

    private void checkPressed(World world, BlockPosition blockposition, IBlockData iblockdata) {
        if (this.canSurvive(iblockdata, world, blockposition)) {
            boolean flag = (Boolean) iblockdata.getValue(BlockMinecartDetector.POWERED);
            boolean flag1 = false;
            List<EntityMinecartAbstract> list = this.<EntityMinecartAbstract>getInteractingMinecartOfType(world, blockposition, EntityMinecartAbstract.class, (entity) -> {
                return true;
            });

            if (!list.isEmpty()) {
                flag1 = true;
            }

            // CraftBukkit start
            if (flag != flag1) {
                org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());

                BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, flag ? 15 : 0, flag1 ? 15 : 0);
                world.getCraftServer().getPluginManager().callEvent(eventRedstone);

                flag1 = eventRedstone.getNewCurrent() > 0;
            }
            // CraftBukkit end

            if (flag1 && !flag) {
                IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockMinecartDetector.POWERED, true);

                world.setBlock(blockposition, iblockdata1, 3);
                this.updatePowerToConnected(world, blockposition, iblockdata1, true);
                world.updateNeighborsAt(blockposition, this);
                world.updateNeighborsAt(blockposition.below(), this);
                world.setBlocksDirty(blockposition, iblockdata, iblockdata1);
            }

            if (!flag1 && flag) {
                IBlockData iblockdata2 = (IBlockData) iblockdata.setValue(BlockMinecartDetector.POWERED, false);

                world.setBlock(blockposition, iblockdata2, 3);
                this.updatePowerToConnected(world, blockposition, iblockdata2, false);
                world.updateNeighborsAt(blockposition, this);
                world.updateNeighborsAt(blockposition.below(), this);
                world.setBlocksDirty(blockposition, iblockdata, iblockdata2);
            }

            if (flag1) {
                world.scheduleTick(blockposition, (Block) this, 20);
            }

            world.updateNeighbourForOutputSignal(blockposition, this);
        }
    }

    protected void updatePowerToConnected(World world, BlockPosition blockposition, IBlockData iblockdata, boolean flag) {
        MinecartTrackLogic minecarttracklogic = new MinecartTrackLogic(world, blockposition, iblockdata);

        for (BlockPosition blockposition1 : minecarttracklogic.getConnections()) {
            IBlockData iblockdata1 = world.getBlockState(blockposition1);

            world.neighborChanged(iblockdata1, blockposition1, iblockdata1.getBlock(), (Orientation) null, false);
        }

    }

    @Override
    protected void onPlace(IBlockData iblockdata, World world, BlockPosition blockposition, IBlockData iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            IBlockData iblockdata2 = this.updateState(iblockdata, world, blockposition, flag);

            this.checkPressed(world, blockposition, iblockdata2);
        }
    }

    @Override
    public IBlockState<BlockPropertyTrackPosition> getShapeProperty() {
        return BlockMinecartDetector.SHAPE;
    }

    @Override
    protected boolean hasAnalogOutputSignal(IBlockData iblockdata) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(IBlockData iblockdata, World world, BlockPosition blockposition) {
        if ((Boolean) iblockdata.getValue(BlockMinecartDetector.POWERED)) {
            List<EntityMinecartCommandBlock> list = this.<EntityMinecartCommandBlock>getInteractingMinecartOfType(world, blockposition, EntityMinecartCommandBlock.class, (entity) -> {
                return true;
            });

            if (!list.isEmpty()) {
                return ((EntityMinecartCommandBlock) list.get(0)).getCommandBlock().getSuccessCount();
            }

            List<EntityMinecartAbstract> list1 = this.<EntityMinecartAbstract>getInteractingMinecartOfType(world, blockposition, EntityMinecartAbstract.class, IEntitySelector.CONTAINER_ENTITY_SELECTOR);

            if (!list1.isEmpty()) {
                return Container.getRedstoneSignalFromContainer((IInventory) list1.get(0));
            }
        }

        return 0;
    }

    private <T extends EntityMinecartAbstract> List<T> getInteractingMinecartOfType(World world, BlockPosition blockposition, Class<T> oclass, Predicate<Entity> predicate) {
        return world.<T>getEntitiesOfClass(oclass, this.getSearchBB(blockposition), predicate);
    }

    private AxisAlignedBB getSearchBB(BlockPosition blockposition) {
        double d0 = 0.2D;

        return new AxisAlignedBB((double) blockposition.getX() + 0.2D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.2D, (double) (blockposition.getX() + 1) - 0.2D, (double) (blockposition.getY() + 1) - 0.2D, (double) (blockposition.getZ() + 1) - 0.2D);
    }

    @Override
    protected IBlockData rotate(IBlockData iblockdata, EnumBlockRotation enumblockrotation) {
        BlockPropertyTrackPosition blockpropertytrackposition = (BlockPropertyTrackPosition) iblockdata.getValue(BlockMinecartDetector.SHAPE);
        BlockPropertyTrackPosition blockpropertytrackposition1 = this.rotate(blockpropertytrackposition, enumblockrotation);

        return (IBlockData) iblockdata.setValue(BlockMinecartDetector.SHAPE, blockpropertytrackposition1);
    }

    @Override
    protected IBlockData mirror(IBlockData iblockdata, EnumBlockMirror enumblockmirror) {
        BlockPropertyTrackPosition blockpropertytrackposition = (BlockPropertyTrackPosition) iblockdata.getValue(BlockMinecartDetector.SHAPE);
        BlockPropertyTrackPosition blockpropertytrackposition1 = this.mirror(blockpropertytrackposition, enumblockmirror);

        return (IBlockData) iblockdata.setValue(BlockMinecartDetector.SHAPE, blockpropertytrackposition1);
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockMinecartDetector.SHAPE, BlockMinecartDetector.POWERED, BlockMinecartDetector.WATERLOGGED);
    }
}
