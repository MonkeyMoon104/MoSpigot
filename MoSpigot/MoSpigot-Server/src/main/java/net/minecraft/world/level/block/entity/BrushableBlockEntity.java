package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameterSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameters;
import net.minecraft.world.phys.Vec3D;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.Arrays;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockBrushEvent;
// CraftBukkit end

public class BrushableBlockEntity extends TileEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOOT_TABLE_TAG = "LootTable";
    private static final String LOOT_TABLE_SEED_TAG = "LootTableSeed";
    private static final String HIT_DIRECTION_TAG = "hit_direction";
    private static final String ITEM_TAG = "item";
    private static final int BRUSH_COOLDOWN_TICKS = 10;
    private static final int BRUSH_RESET_TICKS = 40;
    private static final int REQUIRED_BRUSHES_TO_BREAK = 10;
    private int brushCount;
    private long brushCountResetsAtTick;
    private long coolDownEndsAtTick;
    public ItemStack item;
    @Nullable
    private EnumDirection hitDirection;
    @Nullable
    public ResourceKey<LootTable> lootTable;
    public long lootTableSeed;

    public BrushableBlockEntity(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.BRUSHABLE_BLOCK, blockposition, iblockdata);
        this.item = ItemStack.EMPTY;
    }

    public boolean brush(long i, WorldServer worldserver, EntityLiving entityliving, EnumDirection enumdirection, ItemStack itemstack) {
        if (this.hitDirection == null) {
            this.hitDirection = enumdirection;
        }

        this.brushCountResetsAtTick = i + 40L;
        if (i < this.coolDownEndsAtTick) {
            return false;
        } else {
            this.coolDownEndsAtTick = i + 10L;
            this.unpackLootTable(worldserver, entityliving, itemstack);
            int j = this.getCompletionState();

            if (++this.brushCount >= 10) {
                this.brushingCompleted(worldserver, entityliving, itemstack);
                return true;
            } else {
                worldserver.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
                int k = this.getCompletionState();

                if (j != k) {
                    IBlockData iblockdata = this.getBlockState();
                    IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockProperties.DUSTED, k);

                    // CraftBukkit start
                    BlockBrushEvent event = CraftEventFactory.callBlockBrushEvent(worldserver, this.getBlockPos(), iblockdata1, 3, (EntityPlayer) entityliving);
                    if (!event.isCancelled()) {
                        event.getNewState().update(true);
                    }
                    // CraftBukkit end
                }

                return false;
            }
        }
    }

    private void unpackLootTable(WorldServer worldserver, EntityLiving entityliving, ItemStack itemstack) {
        if (this.lootTable != null) {
            LootTable loottable = worldserver.getServer().reloadableRegistries().getLootTable(this.lootTable);

            if (entityliving instanceof EntityPlayer) {
                EntityPlayer entityplayer = (EntityPlayer) entityliving;

                CriterionTriggers.GENERATE_LOOT.trigger(entityplayer, this.lootTable);
            }

            LootParams lootparams = (new LootParams.a(worldserver)).withParameter(LootContextParameters.ORIGIN, Vec3D.atCenterOf(this.worldPosition)).withLuck(entityliving.getLuck()).withParameter(LootContextParameters.THIS_ENTITY, entityliving).withParameter(LootContextParameters.TOOL, itemstack).create(LootContextParameterSets.ARCHAEOLOGY);
            ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams, this.lootTableSeed);
            ItemStack itemstack1;

            switch (objectarraylist.size()) {
                case 0:
                    itemstack1 = ItemStack.EMPTY;
                    break;
                case 1:
                    itemstack1 = (ItemStack) objectarraylist.getFirst();
                    break;
                default:
                    BrushableBlockEntity.LOGGER.warn("Expected max 1 loot from loot table {}, but got {}", this.lootTable.location(), objectarraylist.size());
                    itemstack1 = (ItemStack) objectarraylist.getFirst();
            }

            this.item = itemstack1;
            this.lootTable = null;
            this.setChanged();
        }
    }

    private void brushingCompleted(WorldServer worldserver, EntityLiving entityliving, ItemStack itemstack) {
        // this.dropContent(worldserver, entityliving, itemstack); // CraftBukkit - moved down
        IBlockData iblockdata = this.getBlockState();

        // worldserver.levelEvent(3008, this.getBlockPos(), Block.getId(iblockdata)); // CraftBukkit - moved down
        Block block = this.getBlockState().getBlock();
        Block block1;

        if (block instanceof BrushableBlock brushableblock) {
            block1 = brushableblock.getTurnsInto();
        } else {
            block1 = Blocks.AIR;
        }

        // CraftBukkit start
        BlockBrushEvent event = CraftEventFactory.callBlockBrushEvent(worldserver, this.worldPosition, block1.defaultBlockState(), 3, (EntityPlayer) entityliving);
        if (!event.isCancelled()) {
            this.dropContent(worldserver, entityliving, itemstack); // CraftBukkit - from above
            worldserver.levelEvent(3008, this.getBlockPos(), Block.getId(iblockdata)); // CraftBukkit - from above

            event.getNewState().update(true);
        }
        // CraftBukkit end
    }

    private void dropContent(WorldServer worldserver, EntityLiving entityliving, ItemStack itemstack) {
        this.unpackLootTable(worldserver, entityliving, itemstack);
        if (!this.item.isEmpty()) {
            double d0 = (double) EntityTypes.ITEM.getWidth();
            double d1 = 1.0D - d0;
            double d2 = d0 / 2.0D;
            EnumDirection enumdirection = (EnumDirection) Objects.requireNonNullElse(this.hitDirection, EnumDirection.UP);
            BlockPosition blockposition = this.worldPosition.relative(enumdirection, 1);
            double d3 = (double) blockposition.getX() + 0.5D * d1 + d2;
            double d4 = (double) blockposition.getY() + 0.5D + (double) (EntityTypes.ITEM.getHeight() / 2.0F);
            double d5 = (double) blockposition.getZ() + 0.5D * d1 + d2;
            EntityItem entityitem = new EntityItem(worldserver, d3, d4, d5, this.item.split(worldserver.random.nextInt(21) + 10));

            entityitem.setDeltaMovement(Vec3D.ZERO);
            // CraftBukkit start
            if (entityliving instanceof EntityPlayer entityplayer) {
                org.bukkit.block.Block bblock = CraftBlock.at(this.level, this.worldPosition);
                CraftEventFactory.handleBlockDropItemEvent(bblock, bblock.getState(), entityplayer, Arrays.asList(entityitem));
            }
            // CraftBukkit end
            this.item = ItemStack.EMPTY;
        }

    }

    public void checkReset(WorldServer worldserver) {
        if (this.brushCount != 0 && worldserver.getGameTime() >= this.brushCountResetsAtTick) {
            int i = this.getCompletionState();

            this.brushCount = Math.max(0, this.brushCount - 2);
            int j = this.getCompletionState();

            if (i != j) {
                worldserver.setBlock(this.getBlockPos(), (IBlockData) this.getBlockState().setValue(BlockProperties.DUSTED, j), 3);
            }

            int k = 4;

            this.brushCountResetsAtTick = worldserver.getGameTime() + 4L;
        }

        if (this.brushCount == 0) {
            this.hitDirection = null;
            this.brushCountResetsAtTick = 0L;
            this.coolDownEndsAtTick = 0L;
        } else {
            worldserver.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
        }

    }

    private boolean tryLoadLootTable(ValueInput valueinput) {
        this.lootTable = (ResourceKey) valueinput.read("LootTable", LootTable.KEY_CODEC).orElse(null); // CraftBukkit - decompile error
        this.lootTableSeed = valueinput.getLongOr("LootTableSeed", 0L);
        return this.lootTable != null;
    }

    private boolean trySaveLootTable(ValueOutput valueoutput) {
        if (this.lootTable == null) {
            return false;
        } else {
            valueoutput.store("LootTable", LootTable.KEY_CODEC, this.lootTable);
            if (this.lootTableSeed != 0L) {
                valueoutput.putLong("LootTableSeed", this.lootTableSeed);
            }

            return true;
        }
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = super.getUpdateTag(holderlookup_a);

        nbttagcompound.storeNullable("hit_direction", EnumDirection.LEGACY_ID_CODEC, this.hitDirection);
        if (!this.item.isEmpty()) {
            RegistryOps<NBTBase> registryops = holderlookup_a.<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

            nbttagcompound.store("item", ItemStack.CODEC, registryops, this.item);
        }

        return nbttagcompound;
    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        if (!this.tryLoadLootTable(valueinput)) {
            this.item = (ItemStack) valueinput.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }

        this.hitDirection = (EnumDirection) valueinput.read("hit_direction", EnumDirection.LEGACY_ID_CODEC).orElse(null); // CraftBukkit - decompile error
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        if (!this.trySaveLootTable(valueoutput) && !this.item.isEmpty()) {
            valueoutput.store("item", ItemStack.CODEC, this.item);
        }

    }

    public void setLootTable(ResourceKey<LootTable> resourcekey, long i) {
        this.lootTable = resourcekey;
        this.lootTableSeed = i;
    }

    private int getCompletionState() {
        return this.brushCount == 0 ? 0 : (this.brushCount < 3 ? 1 : (this.brushCount < 6 ? 2 : 3));
    }

    @Nullable
    public EnumDirection getHitDirection() {
        return this.hitDirection;
    }

    public ItemStack getItem() {
        return this.item;
    }
}
