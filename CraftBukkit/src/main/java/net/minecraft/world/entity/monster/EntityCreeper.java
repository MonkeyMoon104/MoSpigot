package net.minecraft.world.entity.monster;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAreaEffectCloud;
import net.minecraft.world.entity.EntityLightning;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSwell;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.EntityCat;
import net.minecraft.world.entity.animal.EntityOcelot;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.IMaterial;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EntityCreeper extends EntityMonster {

    private static final DataWatcherObject<Integer> DATA_SWELL_DIR = DataWatcher.<Integer>defineId(EntityCreeper.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Boolean> DATA_IS_POWERED = DataWatcher.<Boolean>defineId(EntityCreeper.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Boolean> DATA_IS_IGNITED = DataWatcher.<Boolean>defineId(EntityCreeper.class, DataWatcherRegistry.BOOLEAN);
    private static final boolean DEFAULT_IGNITED = false;
    private static final boolean DEFAULT_POWERED = false;
    private static final short DEFAULT_MAX_SWELL = 30;
    private static final byte DEFAULT_EXPLOSION_RADIUS = 3;
    private int oldSwell;
    public int swell;
    public int maxSwell = 30;
    public int explosionRadius = 3;
    private int droppedSkulls;
    public Entity entityIgniter; // CraftBukkit

    public EntityCreeper(EntityTypes<? extends EntityCreeper> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(2, new PathfinderGoalSwell(this));
        this.goalSelector.addGoal(3, new PathfinderGoalAvoidTarget(this, EntityOcelot.class, 6.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(3, new PathfinderGoalAvoidTarget(this, EntityCat.class, 6.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(4, new PathfinderGoalMeleeAttack(this, 1.0D, false));
        this.goalSelector.addGoal(5, new PathfinderGoalRandomStrollLand(this, 0.8D));
        this.goalSelector.addGoal(6, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F));
        this.goalSelector.addGoal(6, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, true));
        this.targetSelector.addGoal(2, new PathfinderGoalHurtByTarget(this, new Class[0]));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public int getMaxFallDistance() {
        return this.getTarget() == null ? this.getComfortableFallDistance(0.0F) : this.getComfortableFallDistance(this.getHealth() - 1.0F);
    }

    @Override
    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        boolean flag = super.causeFallDamage(d0, f, damagesource);

        this.swell += (int) (d0 * 1.5D);
        if (this.swell > this.maxSwell - 5) {
            this.swell = this.maxSwell - 5;
        }

        return flag;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityCreeper.DATA_SWELL_DIR, -1);
        datawatcher_a.define(EntityCreeper.DATA_IS_POWERED, false);
        datawatcher_a.define(EntityCreeper.DATA_IS_IGNITED, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("powered", this.isPowered());
        valueoutput.putShort("Fuse", (short) this.maxSwell);
        valueoutput.putByte("ExplosionRadius", (byte) this.explosionRadius);
        valueoutput.putBoolean("ignited", this.isIgnited());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.entityData.set(EntityCreeper.DATA_IS_POWERED, valueinput.getBooleanOr("powered", false));
        this.maxSwell = valueinput.getShortOr("Fuse", (short) 30);
        this.explosionRadius = valueinput.getByteOr("ExplosionRadius", (byte) 3);
        if (valueinput.getBooleanOr("ignited", false)) {
            this.ignite();
        }

    }

    @Override
    public void tick() {
        if (this.isAlive()) {
            this.oldSwell = this.swell;
            if (this.isIgnited()) {
                this.setSwellDir(1);
            }

            int i = this.getSwellDir();

            if (i > 0 && this.swell == 0) {
                this.playSound(SoundEffects.CREEPER_PRIMED, 1.0F, 0.5F);
                this.gameEvent(GameEvent.PRIME_FUSE);
            }

            this.swell += i;
            if (this.swell < 0) {
                this.swell = 0;
            }

            if (this.swell >= this.maxSwell) {
                this.swell = this.maxSwell;
                this.explodeCreeper();
            }
        }

        super.tick();
    }

    @Override
    public void setTarget(@Nullable EntityLiving entityliving) {
        if (!(entityliving instanceof Goat)) {
            super.setTarget(entityliving);
        }
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.CREEPER_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.CREEPER_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        super.dropCustomDeathLoot(worldserver, damagesource, flag);
        Entity entity = damagesource.getEntity();

        if (entity != this && entity instanceof EntityCreeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                entitycreeper.increaseDroppedSkulls();
                this.spawnAtLocation(worldserver, (IMaterial) Items.CREEPER_HEAD);
            }
        }

    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        return true;
    }

    public boolean isPowered() {
        return (Boolean) this.entityData.get(EntityCreeper.DATA_IS_POWERED);
    }

    public float getSwelling(float f) {
        return MathHelper.lerp(f, (float) this.oldSwell, (float) this.swell) / (float) (this.maxSwell - 2);
    }

    public int getSwellDir() {
        return (Integer) this.entityData.get(EntityCreeper.DATA_SWELL_DIR);
    }

    public void setSwellDir(int i) {
        this.entityData.set(EntityCreeper.DATA_SWELL_DIR, i);
    }

    @Override
    public void thunderHit(WorldServer worldserver, EntityLightning entitylightning) {
        super.thunderHit(worldserver, entitylightning);
        // CraftBukkit start
        if (CraftEventFactory.callCreeperPowerEvent(this, entitylightning, org.bukkit.event.entity.CreeperPowerEvent.PowerCause.LIGHTNING).isCancelled()) {
            return;
        }
        // CraftBukkit end
        this.entityData.set(EntityCreeper.DATA_IS_POWERED, true);
    }

    // CraftBukkit start
    public void setPowered(boolean powered) {
        this.entityData.set(EntityCreeper.DATA_IS_POWERED, powered);
    }
    // CraftBukkit end

    @Override
    protected EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(TagsItem.CREEPER_IGNITERS)) {
            SoundEffect soundeffect = itemstack.is(Items.FIRE_CHARGE) ? SoundEffects.FIRECHARGE_USE : SoundEffects.FLINTANDSTEEL_USE;

            this.level().playSound(entityhuman, this.getX(), this.getY(), this.getZ(), soundeffect, this.getSoundSource(), 1.0F, this.random.nextFloat() * 0.4F + 0.8F);
            if (!this.level().isClientSide) {
                this.entityIgniter = entityhuman; // CraftBukkit
                this.ignite();
                if (itemstack.getMaxDamage() == 0) { // CraftBukkit - fix MC-264285: unbreakable flint and steels are completely consumed when igniting a creeper
                    itemstack.shrink(1);
                } else {
                    itemstack.hurtAndBreak(1, entityhuman, getSlotForHand(enumhand));
                }
            }

            return EnumInteractionResult.SUCCESS;
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    public void explodeCreeper() {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            float f = this.isPowered() ? 2.0F : 1.0F;

            // CraftBukkit start
            ExplosionPrimeEvent event = CraftEventFactory.callExplosionPrimeEvent(this, this.explosionRadius * f, false);
            if (!event.isCancelled()) {
            // CraftBukkit end
            this.dead = true;
            worldserver.explode(this, net.minecraft.world.level.Explosion.getDefaultDamageSource(this.level(), this).customCausingEntityDamager(this.entityIgniter), null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), World.a.MOB); // CraftBukkit
            this.spawnLingeringCloud();
            this.triggerOnDeathMobEffects(worldserver, Entity.RemovalReason.KILLED);
            this.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
            // CraftBukkit start
            } else {
                swell = 0;
            }
            // CraftBukkit end
        }

    }

    private void spawnLingeringCloud() {
        Collection<MobEffect> collection = this.getActiveEffects();

        if (!collection.isEmpty()) {
            EntityAreaEffectCloud entityareaeffectcloud = new EntityAreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());

            entityareaeffectcloud.setOwner(this); // CraftBukkit
            entityareaeffectcloud.setRadius(2.5F);
            entityareaeffectcloud.setRadiusOnUse(-0.5F);
            entityareaeffectcloud.setWaitTime(10);
            entityareaeffectcloud.setDuration(300);
            entityareaeffectcloud.setPotionDurationScale(0.25F);
            entityareaeffectcloud.setRadiusPerTick(-entityareaeffectcloud.getRadius() / (float) entityareaeffectcloud.getDuration());

            for (MobEffect mobeffect : collection) {
                entityareaeffectcloud.addEffect(new MobEffect(mobeffect));
            }

            this.level().addFreshEntity(entityareaeffectcloud, CreatureSpawnEvent.SpawnReason.EXPLOSION); // CraftBukkit
        }

    }

    public boolean isIgnited() {
        return (Boolean) this.entityData.get(EntityCreeper.DATA_IS_IGNITED);
    }

    public void ignite() {
        this.entityData.set(EntityCreeper.DATA_IS_IGNITED, true);
    }

    public boolean canDropMobsSkull() {
        return this.isPowered() && this.droppedSkulls < 1;
    }

    public void increaseDroppedSkulls() {
        ++this.droppedSkulls;
    }
}
