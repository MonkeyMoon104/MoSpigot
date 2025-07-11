package net.minecraft.world.entity.decoration;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketListenerPlayOut;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityTrackerEntry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemWorldMap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockDiodeAbstract;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.WorldMap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import org.apache.commons.lang3.Validate;

public class EntityItemFrame extends EntityHanging {

    public static final DataWatcherObject<ItemStack> DATA_ITEM = DataWatcher.<ItemStack>defineId(EntityItemFrame.class, DataWatcherRegistry.ITEM_STACK);
    public static final DataWatcherObject<Integer> DATA_ROTATION = DataWatcher.<Integer>defineId(EntityItemFrame.class, DataWatcherRegistry.INT);
    public static final int NUM_ROTATIONS = 8;
    private static final float DEPTH = 0.0625F;
    private static final float WIDTH = 0.75F;
    private static final float HEIGHT = 0.75F;
    private static final byte DEFAULT_ROTATION = 0;
    private static final float DEFAULT_DROP_CHANCE = 1.0F;
    private static final boolean DEFAULT_INVISIBLE = false;
    private static final boolean DEFAULT_FIXED = false;
    public float dropChance;
    public boolean fixed;

    public EntityItemFrame(EntityTypes<? extends EntityItemFrame> entitytypes, World world) {
        super(entitytypes, world);
        this.dropChance = 1.0F;
        this.fixed = false;
        this.setInvisible(false);
    }

    public EntityItemFrame(World world, BlockPosition blockposition, EnumDirection enumdirection) {
        this(EntityTypes.ITEM_FRAME, world, blockposition, enumdirection);
    }

    public EntityItemFrame(EntityTypes<? extends EntityItemFrame> entitytypes, World world, BlockPosition blockposition, EnumDirection enumdirection) {
        super(entitytypes, world, blockposition);
        this.dropChance = 1.0F;
        this.fixed = false;
        this.setDirection(enumdirection);
        this.setInvisible(false);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityItemFrame.DATA_ITEM, ItemStack.EMPTY);
        datawatcher_a.define(EntityItemFrame.DATA_ROTATION, 0);
    }

    @Override
    public void setDirection(EnumDirection enumdirection) {
        Validate.notNull(enumdirection);
        super.setDirectionRaw(enumdirection);
        if (enumdirection.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot((float) (enumdirection.get2DDataValue() * 90));
        } else {
            this.setXRot((float) (-90 * enumdirection.getAxisDirection().getStep()));
            this.setYRot(0.0F);
        }

        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected final void recalculateBoundingBox() {
        super.recalculateBoundingBox();
        this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected AxisAlignedBB calculateBoundingBox(BlockPosition blockposition, EnumDirection enumdirection) {
        // CraftBukkit start - break out BB calc into own method
        return calculateBoundingBoxStatic(blockposition, enumdirection);
    }

    public static AxisAlignedBB calculateBoundingBoxStatic(BlockPosition blockposition, EnumDirection enumdirection) {
        // CraftBukkit end
        float f = 0.46875F;
        Vec3D vec3d = Vec3D.atCenterOf(blockposition).relative(enumdirection, -0.46875D);
        EnumDirection.EnumAxis enumdirection_enumaxis = enumdirection.getAxis();
        double d0 = enumdirection_enumaxis == EnumDirection.EnumAxis.X ? 0.0625D : 0.75D;
        double d1 = enumdirection_enumaxis == EnumDirection.EnumAxis.Y ? 0.0625D : 0.75D;
        double d2 = enumdirection_enumaxis == EnumDirection.EnumAxis.Z ? 0.0625D : 0.75D;

        return AxisAlignedBB.ofSize(vec3d, d0, d1, d2);
    }

    @Override
    public boolean survives() {
        if (this.fixed) {
            return true;
        } else if (!this.level().noCollision((Entity) this)) {
            return false;
        } else {
            IBlockData iblockdata = this.level().getBlockState(this.pos.relative(this.getDirection().getOpposite()));

            return iblockdata.isSolid() || this.getDirection().getAxis().isHorizontal() && BlockDiodeAbstract.isDiode(iblockdata) ? this.level().getEntities(this, this.getBoundingBox(), EntityItemFrame.HANGING_ENTITY).isEmpty() : false;
        }
    }

    @Override
    public void move(EnumMoveType enummovetype, Vec3D vec3d) {
        if (!this.fixed) {
            super.move(enummovetype, vec3d);
        }

    }

    @Override
    public void push(double d0, double d1, double d2) {
        if (!this.fixed) {
            super.push(d0, d1, d2);
        }

    }

    @Override
    public void kill(WorldServer worldserver) {
        this.removeFramedMap(this.getItem());
        super.kill(worldserver);
    }

    private boolean shouldDamageDropItem(DamageSource damagesource) {
        return !damagesource.is(DamageTypeTags.IS_EXPLOSION) && !this.getItem().isEmpty();
    }

    private static boolean canHurtWhenFixed(DamageSource damagesource) {
        return damagesource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || damagesource.isCreativePlayer();
    }

    @Override
    public boolean hurtClient(DamageSource damagesource) {
        return this.fixed && !canHurtWhenFixed(damagesource) ? false : !this.isInvulnerableToBase(damagesource);
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (!this.fixed) {
            if (this.isInvulnerableToBase(damagesource)) {
                return false;
            } else if (this.shouldDamageDropItem(damagesource)) {
                // CraftBukkit start - fire EntityDamageEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f, false) || this.isRemoved()) {
                    return true;
                }
                // CraftBukkit end
                this.dropItem(worldserver, damagesource.getEntity(), false);
                this.gameEvent(GameEvent.BLOCK_CHANGE, damagesource.getEntity());
                this.playSound(this.getRemoveItemSound(), 1.0F, 1.0F);
                return true;
            } else {
                return super.hurtServer(worldserver, damagesource, f);
            }
        } else {
            return canHurtWhenFixed(damagesource) && super.hurtServer(worldserver, damagesource, f);
        }
    }

    public SoundEffect getRemoveItemSound() {
        return SoundEffects.ITEM_FRAME_REMOVE_ITEM;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d0) {
        double d1 = 16.0D;

        d1 *= 64.0D * getViewScale();
        return d0 < d1 * d1;
    }

    @Override
    public void dropItem(WorldServer worldserver, @Nullable Entity entity) {
        this.playSound(this.getBreakSound(), 1.0F, 1.0F);
        this.dropItem(worldserver, entity, true);
        this.gameEvent(GameEvent.BLOCK_CHANGE, entity);
    }

    public SoundEffect getBreakSound() {
        return SoundEffects.ITEM_FRAME_BREAK;
    }

    @Override
    public void playPlacementSound() {
        this.playSound(this.getPlaceSound(), 1.0F, 1.0F);
    }

    public SoundEffect getPlaceSound() {
        return SoundEffects.ITEM_FRAME_PLACE;
    }

    private void dropItem(WorldServer worldserver, @Nullable Entity entity, boolean flag) {
        if (!this.fixed) {
            ItemStack itemstack = this.getItem();

            this.setItem(ItemStack.EMPTY);
            if (!worldserver.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                if (entity == null) {
                    this.removeFramedMap(itemstack);
                }

            } else {
                if (entity instanceof EntityHuman) {
                    EntityHuman entityhuman = (EntityHuman) entity;

                    if (entityhuman.hasInfiniteMaterials()) {
                        this.removeFramedMap(itemstack);
                        return;
                    }
                }

                if (flag) {
                    this.spawnAtLocation(worldserver, this.getFrameItemStack());
                }

                if (!itemstack.isEmpty()) {
                    itemstack = itemstack.copy();
                    this.removeFramedMap(itemstack);
                    if (this.random.nextFloat() < this.dropChance) {
                        this.spawnAtLocation(worldserver, itemstack);
                    }
                }

            }
        }
    }

    private void removeFramedMap(ItemStack itemstack) {
        MapId mapid = this.getFramedMapId(itemstack);

        if (mapid != null) {
            WorldMap worldmap = ItemWorldMap.getSavedData(mapid, this.level());

            if (worldmap != null) {
                worldmap.removedFromFrame(this.pos, this.getId());
            }
        }

        itemstack.setEntityRepresentation((Entity) null);
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(EntityItemFrame.DATA_ITEM);
    }

    @Nullable
    public MapId getFramedMapId(ItemStack itemstack) {
        return (MapId) itemstack.get(DataComponents.MAP_ID);
    }

    public boolean hasFramedMap() {
        return this.getItem().has(DataComponents.MAP_ID);
    }

    public void setItem(ItemStack itemstack) {
        this.setItem(itemstack, true);
    }

    public void setItem(ItemStack itemstack, boolean flag) {
        // CraftBukkit start
        this.setItem(itemstack, flag, true);
    }

    public void setItem(ItemStack itemstack, boolean flag, boolean playSound) {
        // CraftBukkit end
        if (!itemstack.isEmpty()) {
            itemstack = itemstack.copyWithCount(1);
        }

        this.onItemChanged(itemstack);
        this.getEntityData().set(EntityItemFrame.DATA_ITEM, itemstack);
        if (!itemstack.isEmpty() && playSound) { // CraftBukkit
            this.playSound(this.getAddItemSound(), 1.0F, 1.0F);
        }

        if (flag && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    public SoundEffect getAddItemSound() {
        return SoundEffects.ITEM_FRAME_ADD_ITEM;
    }

    @Override
    public SlotAccess getSlot(int i) {
        return i == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(i);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (datawatcherobject.equals(EntityItemFrame.DATA_ITEM)) {
            this.onItemChanged(this.getItem());
        }

    }

    private void onItemChanged(ItemStack itemstack) {
        if (!itemstack.isEmpty() && itemstack.getFrame() != this) {
            itemstack.setEntityRepresentation(this);
        }

        this.recalculateBoundingBox();
    }

    public int getRotation() {
        return (Integer) this.getEntityData().get(EntityItemFrame.DATA_ROTATION);
    }

    public void setRotation(int i) {
        this.setRotation(i, true);
    }

    private void setRotation(int i, boolean flag) {
        this.getEntityData().set(EntityItemFrame.DATA_ROTATION, i % 8);
        if (flag && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        ItemStack itemstack = this.getItem();

        if (!itemstack.isEmpty()) {
            valueoutput.store("Item", ItemStack.CODEC, itemstack);
        }

        valueoutput.putByte("ItemRotation", (byte) this.getRotation());
        valueoutput.putFloat("ItemDropChance", this.dropChance);
        valueoutput.store("Facing", EnumDirection.LEGACY_ID_CODEC, this.getDirection());
        valueoutput.putBoolean("Invisible", this.isInvisible());
        valueoutput.putBoolean("Fixed", this.fixed);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        ItemStack itemstack = (ItemStack) valueinput.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        ItemStack itemstack1 = this.getItem();

        if (!itemstack1.isEmpty() && !ItemStack.matches(itemstack, itemstack1)) {
            this.removeFramedMap(itemstack1);
        }

        this.setItem(itemstack, false);
        this.setRotation(valueinput.getByteOr("ItemRotation", (byte) 0), false);
        this.dropChance = valueinput.getFloatOr("ItemDropChance", 1.0F);
        this.setDirection((EnumDirection) valueinput.read("Facing", EnumDirection.LEGACY_ID_CODEC).orElse(EnumDirection.DOWN));
        this.setInvisible(valueinput.getBooleanOr("Invisible", false));
        this.fixed = valueinput.getBooleanOr("Fixed", false);
    }

    @Override
    public EnumInteractionResult interact(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        boolean flag = !this.getItem().isEmpty();
        boolean flag1 = !itemstack.isEmpty();

        if (this.fixed) {
            return EnumInteractionResult.PASS;
        } else if (!entityhuman.level().isClientSide) {
            if (!flag) {
                if (flag1 && !this.isRemoved()) {
                    WorldMap worldmap = ItemWorldMap.getSavedData(itemstack, this.level());

                    if (worldmap != null && worldmap.isTrackedCountOverLimit(256)) {
                        return EnumInteractionResult.FAIL;
                    } else {
                        this.setItem(itemstack);
                        this.gameEvent(GameEvent.BLOCK_CHANGE, entityhuman);
                        itemstack.consume(1, entityhuman);
                        return EnumInteractionResult.SUCCESS;
                    }
                } else {
                    return EnumInteractionResult.PASS;
                }
            } else {
                this.playSound(this.getRotateItemSound(), 1.0F, 1.0F);
                this.setRotation(this.getRotation() + 1);
                this.gameEvent(GameEvent.BLOCK_CHANGE, entityhuman);
                return EnumInteractionResult.SUCCESS;
            }
        } else {
            return (EnumInteractionResult) (!flag && !flag1 ? EnumInteractionResult.PASS : EnumInteractionResult.SUCCESS);
        }
    }

    public SoundEffect getRotateItemSound() {
        return SoundEffects.ITEM_FRAME_ROTATE_ITEM;
    }

    public int getAnalogOutput() {
        return this.getItem().isEmpty() ? 0 : this.getRotation() % 8 + 1;
    }

    @Override
    public Packet<PacketListenerPlayOut> getAddEntityPacket(EntityTrackerEntry entitytrackerentry) {
        return new PacketPlayOutSpawnEntity(this, this.getDirection().get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        super.recreateFromPacket(packetplayoutspawnentity);
        this.setDirection(EnumDirection.from3DDataValue(packetplayoutspawnentity.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack itemstack = this.getItem();

        return itemstack.isEmpty() ? this.getFrameItemStack() : itemstack.copy();
    }

    protected ItemStack getFrameItemStack() {
        return new ItemStack(Items.ITEM_FRAME);
    }

    @Override
    public float getVisualRotationYInDegrees() {
        EnumDirection enumdirection = this.getDirection();
        int i = enumdirection.getAxis().isVertical() ? 90 * enumdirection.getAxisDirection().getStep() : 0;

        return (float) MathHelper.wrapDegrees(180 + enumdirection.get2DDataValue() * 90 + this.getRotation() * 45 + i);
    }
}
