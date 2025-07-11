package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.MathHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.MovingObjectPositionEntity;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityThrownTrident extends EntityArrow {

    private static final DataWatcherObject<Byte> ID_LOYALTY = DataWatcher.<Byte>defineId(EntityThrownTrident.class, DataWatcherRegistry.BYTE);
    private static final DataWatcherObject<Boolean> ID_FOIL = DataWatcher.<Boolean>defineId(EntityThrownTrident.class, DataWatcherRegistry.BOOLEAN);
    private static final float WATER_INERTIA = 0.99F;
    private static final boolean DEFAULT_DEALT_DAMAGE = false;
    private boolean dealtDamage = false;
    public int clientSideReturnTridentTickCount;

    public EntityThrownTrident(EntityTypes<? extends EntityThrownTrident> entitytypes, World world) {
        super(entitytypes, world);
    }

    public EntityThrownTrident(World world, EntityLiving entityliving, ItemStack itemstack) {
        super(EntityTypes.TRIDENT, entityliving, world, itemstack, (ItemStack) null);
        this.entityData.set(EntityThrownTrident.ID_LOYALTY, this.getLoyaltyFromItem(itemstack));
        this.entityData.set(EntityThrownTrident.ID_FOIL, itemstack.hasFoil());
    }

    public EntityThrownTrident(World world, double d0, double d1, double d2, ItemStack itemstack) {
        super(EntityTypes.TRIDENT, d0, d1, d2, world, itemstack, itemstack);
        this.entityData.set(EntityThrownTrident.ID_LOYALTY, this.getLoyaltyFromItem(itemstack));
        this.entityData.set(EntityThrownTrident.ID_FOIL, itemstack.hasFoil());
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityThrownTrident.ID_LOYALTY, (byte) 0);
        datawatcher_a.define(EntityThrownTrident.ID_FOIL, false);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity entity = this.getOwner();
        int i = (Byte) this.entityData.get(EntityThrownTrident.ID_LOYALTY);

        if (i > 0 && (this.dealtDamage || this.isNoPhysics()) && entity != null) {
            if (!this.isAcceptibleReturnOwner()) {
                World world = this.level();

                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;

                    if (this.pickup == EntityArrow.PickupStatus.ALLOWED) {
                        this.spawnAtLocation(worldserver, this.getPickupItem(), 0.1F);
                    }
                }

                this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
            } else {
                if (!(entity instanceof EntityHuman) && this.position().distanceTo(entity.getEyePosition()) < (double) entity.getBbWidth() + 1.0D) {
                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                    return;
                }

                this.setNoPhysics(true);
                Vec3D vec3d = entity.getEyePosition().subtract(this.position());

                this.setPosRaw(this.getX(), this.getY() + vec3d.y * 0.015D * (double) i, this.getZ());
                double d0 = 0.05D * (double) i;

                this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(vec3d.normalize().scale(d0)));
                if (this.clientSideReturnTridentTickCount == 0) {
                    this.playSound(SoundEffects.TRIDENT_RETURN, 10.0F, 1.0F);
                }

                ++this.clientSideReturnTridentTickCount;
            }
        }

        super.tick();
    }

    private boolean isAcceptibleReturnOwner() {
        Entity entity = this.getOwner();

        return entity != null && entity.isAlive() ? !(entity instanceof EntityPlayer) || !entity.isSpectator() : false;
    }

    public boolean isFoil() {
        return (Boolean) this.entityData.get(EntityThrownTrident.ID_FOIL);
    }

    @Nullable
    @Override
    protected MovingObjectPositionEntity findHitEntity(Vec3D vec3d, Vec3D vec3d1) {
        return this.dealtDamage ? null : super.findHitEntity(vec3d, vec3d1);
    }

    @Override
    protected void onHitEntity(MovingObjectPositionEntity movingobjectpositionentity) {
        Entity entity = movingobjectpositionentity.getEntity();
        float f = 8.0F;
        Entity entity1 = this.getOwner();
        DamageSource damagesource = this.damageSources().trident(this, (Entity) (entity1 == null ? this : entity1));
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            f = EnchantmentManager.modifyDamage(worldserver, this.getWeaponItem(), entity, damagesource, f);
        }

        this.dealtDamage = true;
        if (entity.hurtOrSimulate(damagesource, f)) {
            if (entity.getType() == EntityTypes.ENDERMAN) {
                return;
            }

            world = this.level();
            if (world instanceof WorldServer) {
                WorldServer worldserver1 = (WorldServer) world;

                EnchantmentManager.doPostAttackEffectsWithItemSourceOnBreak(worldserver1, entity, damagesource, this.getWeaponItem(), (item) -> {
                    this.kill(worldserver1);
                });
            }

            if (entity instanceof EntityLiving) {
                EntityLiving entityliving = (EntityLiving) entity;

                this.doKnockback(entityliving, damagesource);
                this.doPostHurtEffects(entityliving);
            }
        }

        this.deflect(ProjectileDeflection.REVERSE, entity, this.getOwner(), false);
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.02D, 0.2D, 0.02D));
        this.playSound(SoundEffects.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void hitBlockEnchantmentEffects(WorldServer worldserver, MovingObjectPositionBlock movingobjectpositionblock, ItemStack itemstack) {
        Vec3D vec3d = movingobjectpositionblock.getBlockPos().clampLocationWithin(movingobjectpositionblock.getLocation());
        Entity entity = this.getOwner();
        EntityLiving entityliving;

        if (entity instanceof EntityLiving entityliving1) {
            entityliving = entityliving1;
        } else {
            entityliving = null;
        }

        EnchantmentManager.onHitBlock(worldserver, itemstack, entityliving, this, (EnumItemSlot) null, vec3d, worldserver.getBlockState(movingobjectpositionblock.getBlockPos()), (item) -> {
            this.kill(worldserver);
        });
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected boolean tryPickup(EntityHuman entityhuman) {
        return super.tryPickup(entityhuman) || this.isNoPhysics() && this.ownedBy(entityhuman) && entityhuman.getInventory().add(this.getPickupItem());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.TRIDENT);
    }

    @Override
    protected SoundEffect getDefaultHitGroundSoundEvent() {
        return SoundEffects.TRIDENT_HIT_GROUND;
    }

    @Override
    public void playerTouch(EntityHuman entityhuman) {
        if (this.ownedBy(entityhuman) || this.getOwner() == null) {
            super.playerTouch(entityhuman);
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.dealtDamage = valueinput.getBooleanOr("DealtDamage", false);
        this.entityData.set(EntityThrownTrident.ID_LOYALTY, this.getLoyaltyFromItem(this.getPickupItemStackOrigin()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("DealtDamage", this.dealtDamage);
    }

    private byte getLoyaltyFromItem(ItemStack itemstack) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            return (byte) MathHelper.clamp(EnchantmentManager.getTridentReturnToOwnerAcceleration(worldserver, itemstack, this), 0, 127);
        } else {
            return 0;
        }
    }

    @Override
    public void tickDespawn() {
        int i = (Byte) this.entityData.get(EntityThrownTrident.ID_LOYALTY);

        if (this.pickup != EntityArrow.PickupStatus.ALLOWED || i <= 0) {
            super.tickDespawn();
        }

    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    @Override
    public boolean shouldRender(double d0, double d1, double d2) {
        return true;
    }
}
