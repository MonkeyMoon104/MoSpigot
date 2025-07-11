package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ContainerUtil;
import net.minecraft.world.IWorldInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.monster.EntityShulker;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerShulkerBox;
import net.minecraft.world.item.EnumColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockShulkerBox;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.EnumPistonReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class TileEntityShulkerBox extends TileEntityLootable implements IWorldInventory {

    public static final int COLUMNS = 9;
    public static final int ROWS = 3;
    public static final int CONTAINER_SIZE = 27;
    public static final int EVENT_SET_OPEN_COUNT = 1;
    public static final int OPENING_TICK_LENGTH = 10;
    public static final float MAX_LID_HEIGHT = 0.5F;
    public static final float MAX_LID_ROTATION = 270.0F;
    private static final int[] SLOTS = IntStream.range(0, 27).toArray();
    private NonNullList<ItemStack> itemStacks;
    public int openCount;
    private TileEntityShulkerBox.AnimationPhase animationStatus;
    private float progress;
    private float progressOld;
    @Nullable
    private final EnumColor color;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;
    public boolean opened;

    public List<ItemStack> getContents() {
        return this.itemStacks;
    }

    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }
    // CraftBukkit end

    public TileEntityShulkerBox(@Nullable EnumColor enumcolor, BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.SHULKER_BOX, blockposition, iblockdata);
        this.itemStacks = NonNullList.<ItemStack>withSize(27, ItemStack.EMPTY);
        this.animationStatus = TileEntityShulkerBox.AnimationPhase.CLOSED;
        this.color = enumcolor;
    }

    public TileEntityShulkerBox(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.SHULKER_BOX, blockposition, iblockdata);
        this.itemStacks = NonNullList.<ItemStack>withSize(27, ItemStack.EMPTY);
        this.animationStatus = TileEntityShulkerBox.AnimationPhase.CLOSED;
        Block block = iblockdata.getBlock();
        EnumColor enumcolor;

        if (block instanceof BlockShulkerBox blockshulkerbox) {
            enumcolor = blockshulkerbox.getColor();
        } else {
            enumcolor = null;
        }

        this.color = enumcolor;
    }

    public static void tick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityShulkerBox tileentityshulkerbox) {
        tileentityshulkerbox.updateAnimation(world, blockposition, iblockdata);
    }

    private void updateAnimation(World world, BlockPosition blockposition, IBlockData iblockdata) {
        this.progressOld = this.progress;
        switch (this.animationStatus.ordinal()) {
            case 0:
                this.progress = 0.0F;
                break;
            case 1:
                this.progress += 0.1F;
                if (this.progressOld == 0.0F) {
                    doNeighborUpdates(world, blockposition, iblockdata);
                }

                if (this.progress >= 1.0F) {
                    this.animationStatus = TileEntityShulkerBox.AnimationPhase.OPENED;
                    this.progress = 1.0F;
                    doNeighborUpdates(world, blockposition, iblockdata);
                }

                this.moveCollidedEntities(world, blockposition, iblockdata);
                break;
            case 2:
                this.progress = 1.0F;
                break;
            case 3:
                this.progress -= 0.1F;
                if (this.progressOld == 1.0F) {
                    doNeighborUpdates(world, blockposition, iblockdata);
                }

                if (this.progress <= 0.0F) {
                    this.animationStatus = TileEntityShulkerBox.AnimationPhase.CLOSED;
                    this.progress = 0.0F;
                    doNeighborUpdates(world, blockposition, iblockdata);
                }
        }

    }

    public TileEntityShulkerBox.AnimationPhase getAnimationStatus() {
        return this.animationStatus;
    }

    public AxisAlignedBB getBoundingBox(IBlockData iblockdata) {
        Vec3D vec3d = new Vec3D(0.5D, 0.0D, 0.5D);

        return EntityShulker.getProgressAabb(1.0F, (EnumDirection) iblockdata.getValue(BlockShulkerBox.FACING), 0.5F * this.getProgress(1.0F), vec3d);
    }

    private void moveCollidedEntities(World world, BlockPosition blockposition, IBlockData iblockdata) {
        if (iblockdata.getBlock() instanceof BlockShulkerBox) {
            EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(BlockShulkerBox.FACING);
            AxisAlignedBB axisalignedbb = EntityShulker.getProgressDeltaAabb(1.0F, enumdirection, this.progressOld, this.progress, blockposition.getBottomCenter());
            List<Entity> list = world.getEntities((Entity) null, axisalignedbb);

            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (entity.getPistonPushReaction() != EnumPistonReaction.IGNORE) {
                        entity.move(EnumMoveType.SHULKER_BOX, new Vec3D((axisalignedbb.getXsize() + 0.01D) * (double) enumdirection.getStepX(), (axisalignedbb.getYsize() + 0.01D) * (double) enumdirection.getStepY(), (axisalignedbb.getZsize() + 0.01D) * (double) enumdirection.getStepZ()));
                    }
                }

            }
        }
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean triggerEvent(int i, int j) {
        if (i == 1) {
            this.openCount = j;
            if (j == 0) {
                this.animationStatus = TileEntityShulkerBox.AnimationPhase.CLOSING;
            }

            if (j == 1) {
                this.animationStatus = TileEntityShulkerBox.AnimationPhase.OPENING;
            }

            return true;
        } else {
            return super.triggerEvent(i, j);
        }
    }

    private static void doNeighborUpdates(World world, BlockPosition blockposition, IBlockData iblockdata) {
        iblockdata.updateNeighbourShapes(world, blockposition, 3);
        world.updateNeighborsAt(blockposition, iblockdata.getBlock());
    }

    @Override
    public void preRemoveSideEffects(BlockPosition blockposition, IBlockData iblockdata) {}

    @Override
    public void startOpen(EntityHuman entityhuman) {
        if (!this.remove && !entityhuman.isSpectator()) {
            if (this.openCount < 0) {
                this.openCount = 0;
            }

            ++this.openCount;
            if (opened) return; // CraftBukkit - only animate if the ShulkerBox hasn't been forced open already by an API call.
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, this.openCount);
            if (this.openCount == 1) {
                this.level.gameEvent(entityhuman, (Holder) GameEvent.CONTAINER_OPEN, this.worldPosition);
                this.level.playSound((Entity) null, this.worldPosition, SoundEffects.SHULKER_BOX_OPEN, SoundCategory.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }
        }

    }

    @Override
    public void stopOpen(EntityHuman entityhuman) {
        if (!this.remove && !entityhuman.isSpectator()) {
            --this.openCount;
            if (opened) return; // CraftBukkit - only animate if the ShulkerBox hasn't been forced open already by an API call.
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, this.openCount);
            if (this.openCount <= 0) {
                this.level.gameEvent(entityhuman, (Holder) GameEvent.CONTAINER_CLOSE, this.worldPosition);
                this.level.playSound((Entity) null, this.worldPosition, SoundEffects.SHULKER_BOX_CLOSE, SoundCategory.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
            }
        }

    }

    @Override
    protected IChatBaseComponent getDefaultName() {
        return IChatBaseComponent.translatable("container.shulkerBox");
    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.loadFromTag(valueinput);
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        if (!this.trySaveLootTable(valueoutput)) {
            ContainerUtil.saveAllItems(valueoutput, this.itemStacks, false);
        }

    }

    public void loadFromTag(ValueInput valueinput) {
        this.itemStacks = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(valueinput)) {
            ContainerUtil.loadAllItems(valueinput, this.itemStacks);
        }

    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.itemStacks;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.itemStacks = nonnulllist;
    }

    @Override
    public int[] getSlotsForFace(EnumDirection enumdirection) {
        return TileEntityShulkerBox.SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable EnumDirection enumdirection) {
        return !(Block.byItem(itemstack.getItem()) instanceof BlockShulkerBox);
    }

    @Override
    public boolean canTakeItemThroughFace(int i, ItemStack itemstack, EnumDirection enumdirection) {
        return true;
    }

    public float getProgress(float f) {
        return MathHelper.lerp(f, this.progressOld, this.progress);
    }

    @Nullable
    public EnumColor getColor() {
        return this.color;
    }

    @Override
    protected Container createMenu(int i, PlayerInventory playerinventory) {
        return new ContainerShulkerBox(i, playerinventory, this);
    }

    public boolean isClosed() {
        return this.animationStatus == TileEntityShulkerBox.AnimationPhase.CLOSED;
    }

    public static enum AnimationPhase {

        CLOSED, OPENING, OPENED, CLOSING;

        private AnimationPhase() {}
    }
}
