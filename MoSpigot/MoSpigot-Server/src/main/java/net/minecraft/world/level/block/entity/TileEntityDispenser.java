package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPosition;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerUtil;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerDispenser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import java.util.List;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class TileEntityDispenser extends TileEntityLootable {

    public static final int CONTAINER_SIZE = 9;
    private NonNullList<ItemStack> items;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

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

    public void setMaxStackSize(int size) {
        maxStack = size;
    }
    // CraftBukkit end

    protected TileEntityDispenser(TileEntityTypes<?> tileentitytypes, BlockPosition blockposition, IBlockData iblockdata) {
        super(tileentitytypes, blockposition, iblockdata);
        this.items = NonNullList.<ItemStack>withSize(9, ItemStack.EMPTY);
    }

    public TileEntityDispenser(BlockPosition blockposition, IBlockData iblockdata) {
        this(TileEntityTypes.DISPENSER, blockposition, iblockdata);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    public int getRandomSlot(RandomSource randomsource) {
        this.unpackLootTable((EntityHuman) null);
        int i = -1;
        int j = 1;

        for (int k = 0; k < this.items.size(); ++k) {
            if (!((ItemStack) this.items.get(k)).isEmpty() && randomsource.nextInt(j++) == 0) {
                i = k;
            }
        }

        return i;
    }

    public ItemStack insertItem(ItemStack itemstack) {
        int i = this.getMaxStackSize(itemstack);

        for (int j = 0; j < this.items.size(); ++j) {
            ItemStack itemstack1 = this.items.get(j);

            if (itemstack1.isEmpty() || ItemStack.isSameItemSameComponents(itemstack, itemstack1)) {
                int k = Math.min(itemstack.getCount(), i - itemstack1.getCount());

                if (k > 0) {
                    if (itemstack1.isEmpty()) {
                        this.setItem(j, itemstack.split(k));
                    } else {
                        itemstack.shrink(k);
                        itemstack1.grow(k);
                    }
                }

                if (itemstack.isEmpty()) {
                    break;
                }
            }
        }

        return itemstack;
    }

    @Override
    protected IChatBaseComponent getDefaultName() {
        return IChatBaseComponent.translatable("container.dispenser");
    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.items = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(valueinput)) {
            ContainerUtil.loadAllItems(valueinput, this.items);
        }

    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        if (!this.trySaveLootTable(valueoutput)) {
            ContainerUtil.saveAllItems(valueoutput, this.items);
        }

    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.items = nonnulllist;
    }

    @Override
    protected Container createMenu(int i, PlayerInventory playerinventory) {
        return new ContainerDispenser(i, playerinventory, this);
    }
}
