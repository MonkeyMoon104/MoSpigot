package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.ICommandListener;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Clearable;
import net.minecraft.world.IInventory;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerLectern;
import net.minecraft.world.inventory.IContainerProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockLectern;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2F;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Lectern;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public class TileEntityLectern extends TileEntity implements Clearable, ITileInventory {

    public static final int DATA_PAGE = 0;
    public static final int NUM_DATA = 1;
    public static final int SLOT_BOOK = 0;
    public static final int NUM_SLOTS = 1;
    // CraftBukkit start - add fields and methods
    public final IInventory bookAccess = new LecternInventory();
    public class LecternInventory implements IInventory {

        public List<HumanEntity> transaction = new ArrayList<>();
        private int maxStack = 1;

        @Override
        public List<ItemStack> getContents() {
            return Arrays.asList(book);
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
        public void setMaxStackSize(int i) {
            maxStack = i;
        }

        @Override
        public Location getLocation() {
            if (level == null) return null;
            return CraftLocation.toBukkit(worldPosition, level.getWorld());
        }

        @Override
        public InventoryHolder getOwner() {
            return (Lectern) TileEntityLectern.this.getOwner();
        }

        public TileEntityLectern getLectern() {
            return TileEntityLectern.this;
        }
        // CraftBukkit end

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return TileEntityLectern.this.book.isEmpty();
        }

        @Override
        public ItemStack getItem(int i) {
            return i == 0 ? TileEntityLectern.this.book : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int i, int j) {
            if (i == 0) {
                ItemStack itemstack = TileEntityLectern.this.book.split(j);

                if (TileEntityLectern.this.book.isEmpty()) {
                    TileEntityLectern.this.onBookItemRemove();
                }

                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            if (i == 0) {
                ItemStack itemstack = TileEntityLectern.this.book;

                TileEntityLectern.this.book = ItemStack.EMPTY;
                TileEntityLectern.this.onBookItemRemove();
                return itemstack;
            } else {
                return ItemStack.EMPTY;
            }
        }

        @Override
        // CraftBukkit start
        public void setItem(int i, ItemStack itemstack) {
            if (i == 0) {
                TileEntityLectern.this.setBook(itemstack);
                if (TileEntityLectern.this.getLevel() != null) {
                    BlockLectern.resetBookState(null, TileEntityLectern.this.getLevel(), TileEntityLectern.this.getBlockPos(), TileEntityLectern.this.getBlockState(), TileEntityLectern.this.hasBook());
                }
            }
        }
        // CraftBukkit end

        @Override
        public int getMaxStackSize() {
            return maxStack; // CraftBukkit
        }

        @Override
        public void setChanged() {
            TileEntityLectern.this.setChanged();
        }

        @Override
        public boolean stillValid(EntityHuman entityhuman) {
            return IInventory.stillValidBlockEntity(TileEntityLectern.this, entityhuman) && TileEntityLectern.this.hasBook();
        }

        @Override
        public boolean canPlaceItem(int i, ItemStack itemstack) {
            return false;
        }

        @Override
        public void clearContent() {}
    };
    private final IContainerProperties dataAccess = new IContainerProperties() {
        @Override
        public int get(int i) {
            return i == 0 ? TileEntityLectern.this.page : 0;
        }

        @Override
        public void set(int i, int j) {
            if (i == 0) {
                TileEntityLectern.this.setPage(j);
            }

        }

        @Override
        public int getCount() {
            return 1;
        }
    };
    ItemStack book;
    int page;
    private int pageCount;

    public TileEntityLectern(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.LECTERN, blockposition, iblockdata);
        this.book = ItemStack.EMPTY;
    }

    public ItemStack getBook() {
        return this.book;
    }

    public boolean hasBook() {
        return this.book.has(DataComponents.WRITABLE_BOOK_CONTENT) || this.book.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    public void setBook(ItemStack itemstack) {
        this.setBook(itemstack, (EntityHuman) null);
    }

    void onBookItemRemove() {
        this.page = 0;
        this.pageCount = 0;
        BlockLectern.resetBookState((Entity) null, this.getLevel(), this.getBlockPos(), this.getBlockState(), false);
    }

    public void setBook(ItemStack itemstack, @Nullable EntityHuman entityhuman) {
        this.book = this.resolveBook(itemstack, entityhuman);
        this.page = 0;
        this.pageCount = getPageCount(this.book);
        this.setChanged();
    }

    public void setPage(int i) {
        int j = MathHelper.clamp(i, 0, this.pageCount - 1);

        if (j != this.page) {
            this.page = j;
            this.setChanged();
            if (this.level != null) BlockLectern.signalPageChange(this.getLevel(), this.getBlockPos(), this.getBlockState()); // CraftBukkit
        }

    }

    public int getPage() {
        return this.page;
    }

    public int getRedstoneSignal() {
        float f = this.pageCount > 1 ? (float) this.getPage() / ((float) this.pageCount - 1.0F) : 1.0F;

        return MathHelper.floor(f * 14.0F) + (this.hasBook() ? 1 : 0);
    }

    private ItemStack resolveBook(ItemStack itemstack, @Nullable EntityHuman entityhuman) {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            WrittenBookContent.resolveForItem(itemstack, this.createCommandSourceStack(entityhuman, worldserver), entityhuman);
        }

        return itemstack;
    }

    // CraftBukkit start
    private final ICommandListener commandSource = new ICommandListener() {

        @Override
        public void sendSystemMessage(IChatBaseComponent ichatbasecomponent) {
        }

        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandListenerWrapper wrapper) {
            return wrapper.getEntity() != null ? wrapper.getEntity().getBukkitEntity() : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, TileEntityLectern.this);
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };
    // CraftBukkit end

    private CommandListenerWrapper createCommandSourceStack(@Nullable EntityHuman entityhuman, WorldServer worldserver) {
        String s;
        IChatBaseComponent ichatbasecomponent;

        if (entityhuman == null) {
            s = "Lectern";
            ichatbasecomponent = IChatBaseComponent.literal("Lectern");
        } else {
            s = entityhuman.getName().getString();
            ichatbasecomponent = entityhuman.getDisplayName();
        }

        Vec3D vec3d = Vec3D.atCenterOf(this.worldPosition);

        // CraftBukkit - commandSource
        return new CommandListenerWrapper(commandSource, vec3d, Vec2F.ZERO, worldserver, 2, s, ichatbasecomponent, worldserver.getServer(), entityhuman);
    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.book = (ItemStack) valueinput.read("Book", ItemStack.CODEC).map((itemstack) -> {
            return this.resolveBook(itemstack, (EntityHuman) null);
        }).orElse(ItemStack.EMPTY);
        this.pageCount = getPageCount(this.book);
        this.page = MathHelper.clamp(valueinput.getIntOr("Page", 0), 0, this.pageCount - 1);
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        if (!this.getBook().isEmpty()) {
            valueoutput.store("Book", ItemStack.CODEC, this.getBook());
            valueoutput.putInt("Page", this.page);
        }

    }

    @Override
    public void clearContent() {
        this.setBook(ItemStack.EMPTY);
    }

    @Override
    public void preRemoveSideEffects(BlockPosition blockposition, IBlockData iblockdata) {
        if ((Boolean) iblockdata.getValue(BlockLectern.HAS_BOOK) && this.level != null) {
            EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(BlockLectern.FACING);
            ItemStack itemstack = this.getBook().copy();
            if (itemstack.isEmpty()) return; // CraftBukkit - SPIGOT-5500
            float f = 0.25F * (float) enumdirection.getStepX();
            float f1 = 0.25F * (float) enumdirection.getStepZ();
            EntityItem entityitem = new EntityItem(this.level, (double) blockposition.getX() + 0.5D + (double) f, (double) (blockposition.getY() + 1), (double) blockposition.getZ() + 0.5D + (double) f1, itemstack);

            entityitem.setDefaultPickUpDelay();
            this.level.addFreshEntity(entityitem);
        }

    }

    @Override
    public Container createMenu(int i, PlayerInventory playerinventory, EntityHuman entityhuman) {
        return new ContainerLectern(i, this.bookAccess, this.dataAccess, playerinventory); // CraftBukkit
    }

    @Override
    public IChatBaseComponent getDisplayName() {
        return IChatBaseComponent.translatable("container.lectern");
    }

    private static int getPageCount(ItemStack itemstack) {
        WrittenBookContent writtenbookcontent = (WrittenBookContent) itemstack.get(DataComponents.WRITTEN_BOOK_CONTENT);

        if (writtenbookcontent != null) {
            return writtenbookcontent.pages().size();
        } else {
            WritableBookContent writablebookcontent = (WritableBookContent) itemstack.get(DataComponents.WRITABLE_BOOK_CONTENT);

            return writablebookcontent != null ? writablebookcontent.pages().size() : 0;
        }
    }
}
