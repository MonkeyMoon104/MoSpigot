package net.minecraft.world;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AutoRecipeOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class InventorySubcontainer implements IInventory, AutoRecipeOutput {

    private final int size;
    public final NonNullList<ItemStack> items;
    @Nullable
    private List<IInventoryListener> listeners;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;
    protected org.bukkit.inventory.InventoryHolder bukkitOwner;

    public List<ItemStack> getContents() {
        return this.items;
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

    public void setMaxStackSize(int i) {
        maxStack = i;
    }

    public org.bukkit.inventory.InventoryHolder getOwner() {
        return bukkitOwner;
    }

    @Override
    public Location getLocation() {
        return null;
    }

    public InventorySubcontainer(InventorySubcontainer original) {
        this(original.size);
        for (int slot = 0; slot < original.size; slot++) {
            this.items.set(slot, original.items.get(slot).copy());
        }
    }

    public InventorySubcontainer(int i) {
        this(i, null);
    }

    public InventorySubcontainer(int i, org.bukkit.inventory.InventoryHolder owner) {
        this.bukkitOwner = owner;
        // CraftBukkit end
        this.size = i;
        this.items = NonNullList.<ItemStack>withSize(i, ItemStack.EMPTY);
    }

    public InventorySubcontainer(ItemStack... aitemstack) {
        this.size = aitemstack.length;
        this.items = NonNullList.<ItemStack>of(ItemStack.EMPTY, aitemstack);
    }

    public void addListener(IInventoryListener iinventorylistener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(iinventorylistener);
    }

    public void removeListener(IInventoryListener iinventorylistener) {
        if (this.listeners != null) {
            this.listeners.remove(iinventorylistener);
        }

    }

    @Override
    public ItemStack getItem(int i) {
        return i >= 0 && i < this.items.size() ? (ItemStack) this.items.get(i) : ItemStack.EMPTY;
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> list = (List) this.items.stream().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).collect(Collectors.toList());

        this.clearContent();
        return list;
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        ItemStack itemstack = ContainerUtil.removeItem(this.items, i, j);

        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    public ItemStack removeItemType(Item item, int i) {
        ItemStack itemstack = new ItemStack(item, 0);

        for (int j = this.size - 1; j >= 0; --j) {
            ItemStack itemstack1 = this.getItem(j);

            if (itemstack1.getItem().equals(item)) {
                int k = i - itemstack.getCount();
                ItemStack itemstack2 = itemstack1.split(k);

                itemstack.grow(itemstack2.getCount());
                if (itemstack.getCount() == i) {
                    break;
                }
            }
        }

        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    public ItemStack addItem(ItemStack itemstack) {
        if (itemstack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack1 = itemstack.copy();

            this.moveItemToOccupiedSlotsWithSameType(itemstack1);
            if (itemstack1.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                this.moveItemToEmptySlots(itemstack1);
                return itemstack1.isEmpty() ? ItemStack.EMPTY : itemstack1;
            }
        }
    }

    public boolean canAddItem(ItemStack itemstack) {
        boolean flag = false;

        for (ItemStack itemstack1 : this.items) {
            if (itemstack1.isEmpty() || ItemStack.isSameItemSameComponents(itemstack1, itemstack) && itemstack1.getCount() < itemstack1.getMaxStackSize()) {
                flag = true;
                break;
            }
        }

        return flag;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        ItemStack itemstack = this.items.get(i);

        if (itemstack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.items.set(i, ItemStack.EMPTY);
            return itemstack;
        }
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        this.items.set(i, itemstack);
        itemstack.limitSize(this.getMaxStackSize(itemstack));
        this.setChanged();
    }

    @Override
    public int getContainerSize() {
        return this.size;
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
    public void setChanged() {
        if (this.listeners != null) {
            for (IInventoryListener iinventorylistener : this.listeners) {
                iinventorylistener.containerChanged(this);
            }
        }

    }

    @Override
    public boolean stillValid(EntityHuman entityhuman) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    public void fillStackedContents(StackedItemContents stackeditemcontents) {
        for (ItemStack itemstack : this.items) {
            stackeditemcontents.accountStack(itemstack);
        }

    }

    public String toString() {
        return ((List) this.items.stream().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).collect(Collectors.toList())).toString();
    }

    private void moveItemToEmptySlots(ItemStack itemstack) {
        for (int i = 0; i < this.size; ++i) {
            ItemStack itemstack1 = this.getItem(i);

            if (itemstack1.isEmpty()) {
                this.setItem(i, itemstack.copyAndClear());
                return;
            }
        }

    }

    private void moveItemToOccupiedSlotsWithSameType(ItemStack itemstack) {
        for (int i = 0; i < this.size; ++i) {
            ItemStack itemstack1 = this.getItem(i);

            if (ItemStack.isSameItemSameComponents(itemstack1, itemstack)) {
                this.moveItemsBetweenStacks(itemstack, itemstack1);
                if (itemstack.isEmpty()) {
                    return;
                }
            }
        }

    }

    private void moveItemsBetweenStacks(ItemStack itemstack, ItemStack itemstack1) {
        int i = this.getMaxStackSize(itemstack1);
        int j = Math.min(itemstack.getCount(), i - itemstack1.getCount());

        if (j > 0) {
            itemstack1.grow(j);
            itemstack.shrink(j);
            this.setChanged();
        }

    }

    public void fromItemList(ValueInput.a<ItemStack> valueinput_a) {
        this.clearContent();

        for (ItemStack itemstack : valueinput_a) {
            this.addItem(itemstack);
        }

    }

    public void storeAsItemList(ValueOutput.a<ItemStack> valueoutput_a) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (!itemstack.isEmpty()) {
                valueoutput_a.add(itemstack);
            }
        }

    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }
}
