package net.minecraft.world.entity.item;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.TagsFluid;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
// CraftBukkit end

public class EntityItem extends Entity implements TraceableEntity {

    private static final DataWatcherObject<ItemStack> DATA_ITEM = DataWatcher.<ItemStack>defineId(EntityItem.class, DataWatcherRegistry.ITEM_STACK);
    private static final float FLOAT_HEIGHT = 0.1F;
    public static final float EYE_HEIGHT = 0.2125F;
    private static final int LIFETIME = 6000;
    private static final int INFINITE_PICKUP_DELAY = 32767;
    private static final int INFINITE_LIFETIME = -32768;
    private static final int DEFAULT_HEALTH = 5;
    private static final short DEFAULT_AGE = 0;
    private static final short DEFAULT_PICKUP_DELAY = 0;
    public int age;
    public int pickupDelay;
    private int health;
    @Nullable
    public EntityReference<Entity> thrower;
    @Nullable
    public UUID target;
    public final float bobOffs;
    private int lastTick = MinecraftServer.currentTick - 1; // CraftBukkit

    public EntityItem(EntityTypes<? extends EntityItem> entitytypes, World world) {
        super(entitytypes, world);
        this.age = 0;
        this.pickupDelay = 0;
        this.health = 5;
        this.bobOffs = this.random.nextFloat() * (float) Math.PI * 2.0F;
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    public EntityItem(World world, double d0, double d1, double d2, ItemStack itemstack) {
        this(world, d0, d1, d2, itemstack, world.random.nextDouble() * 0.2D - 0.1D, 0.2D, world.random.nextDouble() * 0.2D - 0.1D);
    }

    public EntityItem(World world, double d0, double d1, double d2, ItemStack itemstack, double d3, double d4, double d5) {
        this(EntityTypes.ITEM, world);
        this.setPos(d0, d1, d2);
        this.setDeltaMovement(d3, d4, d5);
        this.setItem(itemstack);
    }

    private EntityItem(EntityItem entityitem) {
        super(entityitem.getType(), entityitem.level());
        this.age = 0;
        this.pickupDelay = 0;
        this.health = 5;
        this.setItem(entityitem.getItem().copy());
        this.copyPosition(entityitem);
        this.age = entityitem.age;
        this.bobOffs = entityitem.bobOffs;
    }

    @Override
    public boolean dampensVibrations() {
        return this.getItem().is(TagsItem.DAMPENS_VIBRATIONS);
    }

    @Nullable
    @Override
    public Entity getOwner() {
        return (Entity) EntityReference.get(this.thrower, this.level(), Entity.class);
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof EntityItem entityitem) {
            this.thrower = entityitem.thrower;
        }

    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(EntityItem.DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
            // CraftBukkit start - Use wall time for pickup and despawn timers
            int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
            if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
            if (this.age != -32768) this.age += elapsedTicks;
            this.lastTick = MinecraftServer.currentTick;
            // CraftBukkit end

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3D vec3d = this.getDeltaMovement();

            if (this.isInWater() && this.getFluidHeight(TagsFluid.WATER) > (double) 0.1F) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(TagsFluid.LAVA) > (double) 0.1F) {
                this.setUnderLavaMovement();
            } else {
                this.applyGravity();
            }

            if (this.level().isClientSide) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7D));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
                }
            }

            if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > (double) 1.0E-5F || (this.tickCount + this.getId()) % 4 == 0) {
                this.move(EnumMoveType.SELF, this.getDeltaMovement());
                this.applyEffectsFromBlocks();
                float f = 0.98F;

                if (this.onGround()) {
                    f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 0.98D, (double) f));
                if (this.onGround()) {
                    Vec3D vec3d1 = this.getDeltaMovement();

                    if (vec3d1.y < 0.0D) {
                        this.setDeltaMovement(vec3d1.multiply(1.0D, -0.5D, 1.0D));
                    }
                }
            }

            boolean flag = MathHelper.floor(this.xo) != MathHelper.floor(this.getX()) || MathHelper.floor(this.yo) != MathHelper.floor(this.getY()) || MathHelper.floor(this.zo) != MathHelper.floor(this.getZ());
            int i = flag ? 2 : 40;

            if (this.tickCount % i == 0 && !this.level().isClientSide && this.isMergable()) {
                this.mergeWithNeighbours();
            }

            /* CraftBukkit start - moved up
            if (this.age != -32768) {
                ++this.age;
            }
            // CraftBukkit end */

            this.hasImpulse |= this.updateInWaterStateAndDoFluidPushing();
            if (!this.level().isClientSide) {
                double d0 = this.getDeltaMovement().subtract(vec3d).lengthSqr();

                if (d0 > 0.01D) {
                    this.hasImpulse = true;
                }
            }

            if (!this.level().isClientSide && this.age >= this.level().spigotConfig.itemDespawnRate) { // Spigot
                // CraftBukkit start - fire ItemDespawnEvent
                if (CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                    this.age = 0;
                    return;
                }
                // CraftBukkit end
                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }

        }
    }

    // Spigot start - copied from above
    @Override
    public void inactiveTick() {
        // CraftBukkit start - Use wall time for pickup and despawn timers
        int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
        if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
        if (this.age != -32768) this.age += elapsedTicks;
        this.lastTick = MinecraftServer.currentTick;
        // CraftBukkit end

        if (!this.level().isClientSide && this.age >= this.level().spigotConfig.itemDespawnRate) { // Spigot
            // CraftBukkit start - fire ItemDespawnEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                this.age = 0;
                return;
            }
            // CraftBukkit end
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }
    // Spigot end

    @Override
    public BlockPosition getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        this.setFluidMovement((double) 0.99F);
    }

    private void setUnderLavaMovement() {
        this.setFluidMovement((double) 0.95F);
    }

    private void setFluidMovement(double d0) {
        Vec3D vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * d0, vec3d.y + (double) (vec3d.y < (double) 0.06F ? 5.0E-4F : 0.0F), vec3d.z * d0);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            // Spigot start
            double radius = this.level().spigotConfig.itemMerge;
            for (EntityItem entityitem : this.level().getEntitiesOfClass(EntityItem.class, this.getBoundingBox().inflate(radius, radius - 0.5D, radius), (entityitem1) -> {
                // Spigot end
                return entityitem1 != this && entityitem1.isMergable();
            })) {
                if (entityitem.isMergable()) {
                    this.tryToMerge(entityitem);
                    if (this.isRemoved()) {
                        break;
                    }
                }
            }

        }
    }

    private boolean isMergable() {
        ItemStack itemstack = this.getItem();

        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < 6000 && itemstack.getCount() < itemstack.getMaxStackSize();
    }

    private void tryToMerge(EntityItem entityitem) {
        ItemStack itemstack = this.getItem();
        ItemStack itemstack1 = entityitem.getItem();

        if (Objects.equals(this.target, entityitem.target) && areMergable(itemstack, itemstack1)) {
            if (true || itemstack1.getCount() < itemstack.getCount()) { // Spigot
                merge(this, itemstack, entityitem, itemstack1);
            } else {
                merge(entityitem, itemstack1, this, itemstack);
            }

        }
    }

    public static boolean areMergable(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack1.getCount() + itemstack.getCount() > itemstack1.getMaxStackSize() ? false : ItemStack.isSameItemSameComponents(itemstack, itemstack1);
    }

    public static ItemStack merge(ItemStack itemstack, ItemStack itemstack1, int i) {
        int j = Math.min(Math.min(itemstack.getMaxStackSize(), i) - itemstack.getCount(), itemstack1.getCount());
        ItemStack itemstack2 = itemstack.copyWithCount(itemstack.getCount() + j);

        itemstack1.shrink(j);
        return itemstack2;
    }

    private static void merge(EntityItem entityitem, ItemStack itemstack, ItemStack itemstack1) {
        ItemStack itemstack2 = merge(itemstack, itemstack1, 64);

        entityitem.setItem(itemstack2);
    }

    private static void merge(EntityItem entityitem, ItemStack itemstack, EntityItem entityitem1, ItemStack itemstack1) {
        // CraftBukkit start
        if (!CraftEventFactory.callItemMergeEvent(entityitem1, entityitem)) {
            return;
        }
        // CraftBukkit end
        merge(entityitem, itemstack, itemstack1);
        entityitem.pickupDelay = Math.max(entityitem.pickupDelay, entityitem1.pickupDelay);
        entityitem.age = Math.min(entityitem.age, entityitem1.age);
        if (itemstack1.isEmpty()) {
            entityitem1.discard(EntityRemoveEvent.Cause.MERGE); // CraftBukkit - add Bukkit remove cause);
        }

    }

    @Override
    public boolean fireImmune() {
        return !this.getItem().canBeHurtBy(this.damageSources().inFire()) || super.fireImmune();
    }

    @Override
    protected boolean shouldPlayLavaHurtSound() {
        return this.health <= 0 ? true : this.tickCount % 10 == 0;
    }

    @Override
    public final boolean hurtClient(DamageSource damagesource) {
        return this.isInvulnerableToBase(damagesource) ? false : this.getItem().canBeHurtBy(damagesource);
    }

    @Override
    public final boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableToBase(damagesource)) {
            return false;
        } else if (!worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && damagesource.getEntity() instanceof EntityInsentient) {
            return false;
        } else if (!this.getItem().canBeHurtBy(damagesource)) {
            return false;
        } else {
            // CraftBukkit start
            if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f)) {
                return false;
            }
            // CraftBukkit end
            this.markHurt();
            this.health = (int) ((float) this.health - f);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damagesource.getEntity());
            if (this.health <= 0) {
                this.getItem().onDestroyed(this);
                this.discard(EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
            }

            return true;
        }
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return explosion.shouldAffectBlocklikeEntities() ? super.ignoreExplosion(explosion) : true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        valueoutput.putShort("Health", (short) this.health);
        valueoutput.putShort("Age", (short) this.age);
        valueoutput.putShort("PickupDelay", (short) this.pickupDelay);
        EntityReference.store(this.thrower, valueoutput, "Thrower");
        valueoutput.storeNullable("Owner", UUIDUtil.CODEC, this.target);
        if (!this.getItem().isEmpty()) {
            valueoutput.store("Item", ItemStack.CODEC, this.getItem());
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        this.health = valueinput.getShortOr("Health", (short) 5);
        this.age = valueinput.getShortOr("Age", (short) 0);
        this.pickupDelay = valueinput.getShortOr("PickupDelay", (short) 0);
        this.target = (UUID) valueinput.read("Owner", UUIDUtil.CODEC).orElse(null); // CraftBukkit - decompile error
        this.thrower = EntityReference.<Entity>read(valueinput, "Thrower");
        this.setItem((ItemStack) valueinput.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        if (this.getItem().isEmpty()) {
            this.discard(null); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public void playerTouch(EntityHuman entityhuman) {
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Item item = itemstack.getItem();
            int i = itemstack.getCount();

            // CraftBukkit start - fire PlayerPickupItemEvent
            int canHold = entityhuman.getInventory().canHold(itemstack);
            int remaining = i - canHold;

            if (this.pickupDelay <= 0 && canHold > 0) {
                itemstack.setCount(canHold);
                // Call legacy event
                PlayerPickupItemEvent playerEvent = new PlayerPickupItemEvent((Player) entityhuman.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                playerEvent.setCancelled(!playerEvent.getPlayer().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(playerEvent);
                if (playerEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    return;
                }

                // Call newer event afterwards
                EntityPickupItemEvent entityEvent = new EntityPickupItemEvent((Player) entityhuman.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                entityEvent.setCancelled(!entityEvent.getEntity().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(entityEvent);
                if (entityEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    return;
                }

                // Update the ItemStack if it was changed in the event
                ItemStack current = this.getItem();
                if (!itemstack.equals(current)) {
                    itemstack = current;
                } else {
                    itemstack.setCount(canHold + remaining); // = i
                }

                // Possibly < 0; fix here so we do not have to modify code below
                this.pickupDelay = 0;
            } else if (this.pickupDelay == 0) {
                // ensure that the code below isn't triggered if canHold says we can't pick the items up
                this.pickupDelay = -1;
            }
            // CraftBukkit end

            if (this.pickupDelay == 0 && (this.target == null || this.target.equals(entityhuman.getUUID())) && entityhuman.getInventory().add(itemstack)) {
                entityhuman.take(this, i);
                if (itemstack.isEmpty()) {
                    this.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
                    itemstack.setCount(i);
                }

                entityhuman.awardStat(StatisticList.ITEM_PICKED_UP.get(item), i);
                entityhuman.onItemPickup(this);
            }

        }
    }

    @Override
    public IChatBaseComponent getName() {
        IChatBaseComponent ichatbasecomponent = this.getCustomName();

        return ichatbasecomponent != null ? ichatbasecomponent : this.getItem().getItemName();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Nullable
    @Override
    public Entity teleport(TeleportTransition teleporttransition) {
        Entity entity = super.teleport(teleporttransition);

        if (!this.level().isClientSide && entity instanceof EntityItem entityitem) {
            entityitem.mergeWithNeighbours();
        }

        return entity;
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(EntityItem.DATA_ITEM);
    }

    public void setItem(ItemStack itemstack) {
        this.getEntityData().set(EntityItem.DATA_ITEM, itemstack);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (EntityItem.DATA_ITEM.equals(datawatcherobject)) {
            this.getItem().setEntityRepresentation(this);
        }

    }

    public void setTarget(@Nullable UUID uuid) {
        this.target = uuid;
    }

    public void setThrower(Entity entity) {
        this.thrower = new EntityReference<Entity>(entity);
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int i) {
        this.pickupDelay = i;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setUnlimitedLifetime() {
        this.age = -32768;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = this.level().spigotConfig.itemDespawnRate - 1; // Spigot
    }

    public static float getSpin(float f, float f1) {
        return f / 20.0F + f1;
    }

    public EntityItem copy() {
        return new EntityItem(this);
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.AMBIENT;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return 180.0F - getSpin((float) this.getAge() + 0.5F, this.bobOffs) / ((float) Math.PI * 2F) * 360.0F;
    }

    @Override
    public SlotAccess getSlot(int i) {
        return i == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(i);
    }
}
