package net.minecraft.world.level.block.entity;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.ticks.ContainerSingleItem;

// CraftBukkit start
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class DecoratedPotBlockEntity extends TileEntity implements RandomizableContainer, ContainerSingleItem.a {

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return Arrays.asList(this.item);
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
    public void setMaxStackSize(int i) {
        maxStack = i;
    }

    @Override
    public Location getLocation() {
        if (level == null) return null;
        return CraftLocation.toBukkit(worldPosition, level.getWorld());
    }
    // CraftBukkit end

    public static final String TAG_SHERDS = "sherds";
    public static final String TAG_ITEM = "item";
    public static final int EVENT_POT_WOBBLES = 1;
    public long wobbleStartedAtTick;
    @Nullable
    public DecoratedPotBlockEntity.a lastWobbleStyle;
    public PotDecorations decorations;
    private ItemStack item;
    @Nullable
    protected ResourceKey<LootTable> lootTable;
    protected long lootTableSeed;

    public DecoratedPotBlockEntity(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.DECORATED_POT, blockposition, iblockdata);
        this.item = ItemStack.EMPTY;
        this.decorations = PotDecorations.EMPTY;
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        if (!this.decorations.equals(PotDecorations.EMPTY)) {
            valueoutput.store("sherds", PotDecorations.CODEC, this.decorations);
        }

        if (!this.trySaveLootTable(valueoutput) && !this.item.isEmpty()) {
            valueoutput.store("item", ItemStack.CODEC, this.item);
        }

    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.decorations = (PotDecorations) valueinput.read("sherds", PotDecorations.CODEC).orElse(PotDecorations.EMPTY);
        if (!this.tryLoadLootTable(valueinput)) {
            this.item = (ItemStack) valueinput.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }

    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        return this.saveCustomOnly(holderlookup_a);
    }

    public EnumDirection getDirection() {
        return (EnumDirection) this.getBlockState().getValue(BlockProperties.HORIZONTAL_FACING);
    }

    public PotDecorations getDecorations() {
        return this.decorations;
    }

    public static ItemStack createDecoratedPotItem(PotDecorations potdecorations) {
        ItemStack itemstack = Items.DECORATED_POT.getDefaultInstance();

        itemstack.set(DataComponents.POT_DECORATIONS, potdecorations);
        return itemstack;
    }

    @Nullable
    @Override
    public ResourceKey<LootTable> getLootTable() {
        return this.lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> resourcekey) {
        this.lootTable = resourcekey;
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long i) {
        this.lootTableSeed = i;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.a datacomponentmap_a) {
        super.collectImplicitComponents(datacomponentmap_a);
        datacomponentmap_a.set(DataComponents.POT_DECORATIONS, this.decorations);
        datacomponentmap_a.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(this.item)));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        super.applyImplicitComponents(datacomponentgetter);
        this.decorations = (PotDecorations) datacomponentgetter.getOrDefault(DataComponents.POT_DECORATIONS, PotDecorations.EMPTY);
        this.item = ((ItemContainerContents) datacomponentgetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)).copyOne();
    }

    @Override
    public void removeComponentsFromTag(ValueOutput valueoutput) {
        super.removeComponentsFromTag(valueoutput);
        valueoutput.discard("sherds");
        valueoutput.discard("item");
    }

    @Override
    public ItemStack getTheItem() {
        this.unpackLootTable((EntityHuman) null);
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int i) {
        this.unpackLootTable((EntityHuman) null);
        ItemStack itemstack = this.item.split(i);

        if (this.item.isEmpty()) {
            this.item = ItemStack.EMPTY;
        }

        return itemstack;
    }

    @Override
    public void setTheItem(ItemStack itemstack) {
        this.unpackLootTable((EntityHuman) null);
        this.item = itemstack;
    }

    @Override
    public TileEntity getContainerBlockEntity() {
        return this;
    }

    public void wobble(DecoratedPotBlockEntity.a decoratedpotblockentity_a) {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.blockEvent(this.getBlockPos(), this.getBlockState().getBlock(), 1, decoratedpotblockentity_a.ordinal());
        }
    }

    @Override
    public boolean triggerEvent(int i, int j) {
        if (this.level != null && i == 1 && j >= 0 && j < DecoratedPotBlockEntity.a.values().length) {
            this.wobbleStartedAtTick = this.level.getGameTime();
            this.lastWobbleStyle = DecoratedPotBlockEntity.a.values()[j];
            return true;
        } else {
            return super.triggerEvent(i, j);
        }
    }

    public static enum a {

        POSITIVE(7), NEGATIVE(10);

        public final int duration;

        private a(final int i) {
            this.duration = i;
        }
    }
}
