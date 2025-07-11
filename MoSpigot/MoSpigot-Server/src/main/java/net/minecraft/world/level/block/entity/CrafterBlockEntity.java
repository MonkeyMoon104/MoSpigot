package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.world.ContainerUtil;
import net.minecraft.world.IInventory;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.IContainerProperties;
import net.minecraft.world.inventory.InventoryCrafting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class CrafterBlockEntity extends TileEntityLootable implements InventoryCrafting {

    public static final int CONTAINER_WIDTH = 3;
    public static final int CONTAINER_HEIGHT = 3;
    public static final int CONTAINER_SIZE = 9;
    public static final int SLOT_DISABLED = 1;
    public static final int SLOT_ENABLED = 0;
    public static final int DATA_TRIGGERED = 9;
    public static final int NUM_DATA = 10;
    private static final int DEFAULT_CRAFTING_TICKS_REMAINING = 0;
    private static final int DEFAULT_TRIGGERED = 0;
    private NonNullList<ItemStack> items;
    public int craftingTicksRemaining;
    protected final IContainerProperties containerData;
    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        maxStack = size;
    }

    @Override
    public Location getLocation() {
        if (level == null) return null;
        return new org.bukkit.Location(level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }
    // CraftBukkit end

    public CrafterBlockEntity(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.CRAFTER, blockposition, iblockdata);
        this.items = NonNullList.<ItemStack>withSize(9, ItemStack.EMPTY);
        this.craftingTicksRemaining = 0;
        this.containerData = new IContainerProperties() {
            private final int[] slotStates = new int[9];
            private int triggered = 0;

            @Override
            public int get(int i) {
                return i == 9 ? this.triggered : this.slotStates[i];
            }

            @Override
            public void set(int i, int j) {
                if (i == 9) {
                    this.triggered = j;
                } else {
                    this.slotStates[i] = j;
                }

            }

            @Override
            public int getCount() {
                return 10;
            }
        };
    }

    @Override
    protected IChatBaseComponent getDefaultName() {
        return IChatBaseComponent.translatable("container.crafter");
    }

    @Override
    protected Container createMenu(int i, PlayerInventory playerinventory) {
        return new CrafterMenu(i, playerinventory, this, this.containerData);
    }

    public void setSlotState(int i, boolean flag) {
        if (this.slotCanBeDisabled(i)) {
            this.containerData.set(i, flag ? 0 : 1);
            this.setChanged();
        }
    }

    public boolean isSlotDisabled(int i) {
        return i >= 0 && i < 9 ? this.containerData.get(i) == 1 : false;
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        if (this.containerData.get(i) == 1) {
            return false;
        } else {
            ItemStack itemstack1 = this.items.get(i);
            int j = itemstack1.getCount();

            return j >= itemstack1.getMaxStackSize() ? false : (itemstack1.isEmpty() ? true : !this.smallerStackExist(j, itemstack1, i));
        }
    }

    private boolean smallerStackExist(int i, ItemStack itemstack, int j) {
        for (int k = j + 1; k < 9; ++k) {
            if (!this.isSlotDisabled(k)) {
                ItemStack itemstack1 = this.getItem(k);

                if (itemstack1.isEmpty() || itemstack1.getCount() < i && ItemStack.isSameItemSameComponents(itemstack1, itemstack)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.craftingTicksRemaining = valueinput.getIntOr("crafting_ticks_remaining", 0);
        this.items = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(valueinput)) {
            ContainerUtil.loadAllItems(valueinput, this.items);
        }

        for (int i = 0; i < 9; ++i) {
            this.containerData.set(i, 0);
        }

        valueinput.getIntArray("disabled_slots").ifPresent((aint) -> {
            for (int j : aint) {
                if (this.slotCanBeDisabled(j)) {
                    this.containerData.set(j, 1);
                }
            }

        });
        this.containerData.set(9, valueinput.getIntOr("triggered", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        valueoutput.putInt("crafting_ticks_remaining", this.craftingTicksRemaining);
        if (!this.trySaveLootTable(valueoutput)) {
            ContainerUtil.saveAllItems(valueoutput, this.items);
        }

        this.addDisabledSlots(valueoutput);
        this.addTriggered(valueoutput);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return this.items.get(i);
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        if (this.isSlotDisabled(i)) {
            this.setSlotState(i, true);
        }

        super.setItem(i, itemstack);
    }

    @Override
    public boolean stillValid(EntityHuman entityhuman) {
        return IInventory.stillValidBlockEntity(this, entityhuman);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.items = nonnulllist;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public void fillStackedContents(StackedItemContents stackeditemcontents) {
        for (ItemStack itemstack : this.items) {
            stackeditemcontents.accountSimpleStack(itemstack);
        }

    }

    private void addDisabledSlots(ValueOutput valueoutput) {
        IntList intlist = new IntArrayList();

        for (int i = 0; i < 9; ++i) {
            if (this.isSlotDisabled(i)) {
                intlist.add(i);
            }
        }

        valueoutput.putIntArray("disabled_slots", intlist.toIntArray());
    }

    private void addTriggered(ValueOutput valueoutput) {
        valueoutput.putInt("triggered", this.containerData.get(9));
    }

    public void setTriggered(boolean flag) {
        this.containerData.set(9, flag ? 1 : 0);
    }

    @VisibleForTesting
    public boolean isTriggered() {
        return this.containerData.get(9) == 1;
    }

    public static void serverTick(World world, BlockPosition blockposition, IBlockData iblockdata, CrafterBlockEntity crafterblockentity) {
        int i = crafterblockentity.craftingTicksRemaining - 1;

        if (i >= 0) {
            crafterblockentity.craftingTicksRemaining = i;
            if (i == 0) {
                world.setBlock(blockposition, (IBlockData) iblockdata.setValue(CrafterBlock.CRAFTING, false), 3);
            }

        }
    }

    public void setCraftingTicksRemaining(int i) {
        this.craftingTicksRemaining = i;
    }

    public int getRedstoneSignal() {
        int i = 0;

        for (int j = 0; j < this.getContainerSize(); ++j) {
            ItemStack itemstack = this.getItem(j);

            if (!itemstack.isEmpty() || this.isSlotDisabled(j)) {
                ++i;
            }
        }

        return i;
    }

    private boolean slotCanBeDisabled(int i) {
        return i > -1 && i < 9 && ((ItemStack) this.items.get(i)).isEmpty();
    }
}
