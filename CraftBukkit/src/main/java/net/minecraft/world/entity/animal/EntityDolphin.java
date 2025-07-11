package net.minecraft.world.entity.animal;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagsFluid;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreath;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowBoat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomSwim;
import net.minecraft.world.entity.ai.goal.PathfinderGoalWater;
import net.minecraft.world.entity.ai.goal.PathfinderGoalWaterJump;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.ai.navigation.NavigationGuardian;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityGuardian;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.pathfinder.PathMode;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityDolphin extends AgeableWaterCreature {

    // CraftBukkit start - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Override
    public int getDefaultMaxAirSupply() {
        return TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end
    private static final DataWatcherObject<Boolean> GOT_FISH = DataWatcher.<Boolean>defineId(EntityDolphin.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Integer> MOISTNESS_LEVEL = DataWatcher.<Integer>defineId(EntityDolphin.class, DataWatcherRegistry.INT);
    static final PathfinderTargetCondition SWIM_WITH_PLAYER_TARGETING = PathfinderTargetCondition.forNonCombat().range(10.0D).ignoreLineOfSight();
    public static final int TOTAL_AIR_SUPPLY = 4800;
    private static final int TOTAL_MOISTNESS_LEVEL = 2400;
    public static final Predicate<EntityItem> ALLOWED_ITEMS = (entityitem) -> {
        return !entityitem.hasPickUpDelay() && entityitem.isAlive() && entityitem.isInWater();
    };
    public static final float BABY_SCALE = 0.65F;
    private static final boolean DEFAULT_GOT_FISH = false;
    @Nullable
    BlockPosition treasurePos;

    public EntityDolphin(EntityTypes<? extends EntityDolphin> entitytypes, World world) {
        super(entitytypes, world);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.setCanPickUpLoot(true);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        this.setAirSupply(this.getMaxAirSupply());
        this.setXRot(0.0F);
        GroupDataEntity groupdataentity1 = (GroupDataEntity) Objects.requireNonNullElseGet(groupdataentity, () -> {
            return new EntityAgeable.a(0.1F);
        });

        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity1);
    }

    @Nullable
    @Override
    public EntityDolphin getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        return EntityTypes.DOLPHIN.create(worldserver, EntitySpawnReason.BREEDING);
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.65F : 1.0F;
    }

    @Override
    protected void handleAirSupply(int i) {}

    public boolean gotFish() {
        return (Boolean) this.entityData.get(EntityDolphin.GOT_FISH);
    }

    public void setGotFish(boolean flag) {
        this.entityData.set(EntityDolphin.GOT_FISH, flag);
    }

    public int getMoistnessLevel() {
        return (Integer) this.entityData.get(EntityDolphin.MOISTNESS_LEVEL);
    }

    public void setMoisntessLevel(int i) {
        this.entityData.set(EntityDolphin.MOISTNESS_LEVEL, i);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityDolphin.GOT_FISH, false);
        datawatcher_a.define(EntityDolphin.MOISTNESS_LEVEL, 2400);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("GotFish", this.gotFish());
        valueoutput.putInt("Moistness", this.getMoistnessLevel());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setGotFish(valueinput.getBooleanOr("GotFish", false));
        this.setMoisntessLevel(valueinput.getIntOr("Moistness", 2400));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PathfinderGoalBreath(this));
        this.goalSelector.addGoal(0, new PathfinderGoalWater(this));
        this.goalSelector.addGoal(1, new EntityDolphin.a(this));
        this.goalSelector.addGoal(2, new EntityDolphin.b(this, 4.0D));
        this.goalSelector.addGoal(4, new PathfinderGoalRandomSwim(this, 1.0D, 10));
        this.goalSelector.addGoal(4, new PathfinderGoalRandomLookaround(this));
        this.goalSelector.addGoal(5, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 6.0F));
        this.goalSelector.addGoal(5, new PathfinderGoalWaterJump(this, 10));
        this.goalSelector.addGoal(6, new PathfinderGoalMeleeAttack(this, (double) 1.2F, true));
        this.goalSelector.addGoal(8, new EntityDolphin.c());
        this.goalSelector.addGoal(8, new PathfinderGoalFollowBoat(this));
        this.goalSelector.addGoal(9, new PathfinderGoalAvoidTarget(this, EntityGuardian.class, 8.0F, 1.0D, 1.0D));
        this.targetSelector.addGoal(1, (new PathfinderGoalHurtByTarget(this, new Class[]{EntityGuardian.class})).setAlertOthers());
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityInsentient.createMobAttributes().add(GenericAttributes.MAX_HEALTH, 10.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 1.2F).add(GenericAttributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected NavigationAbstract createNavigation(World world) {
        return new NavigationGuardian(this, world);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEffects.DOLPHIN_ATTACK, 1.0F, 1.0F);
    }

    @Override
    public boolean canAttack(EntityLiving entityliving) {
        return !this.isBaby() && super.canAttack(entityliving);
    }

    @Override
    public int getMaxAirSupply() {
        return maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }

    @Override
    protected int increaseAirSupply(int i) {
        return this.getMaxAirSupply();
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return true;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EnumItemSlot enumitemslot) {
        return enumitemslot == EnumItemSlot.MAINHAND && this.canPickUpLoot();
    }

    @Override
    protected void pickUpItem(WorldServer worldserver, EntityItem entityitem) {
        if (this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty()) {
            ItemStack itemstack = entityitem.getItem();

            if (this.canHoldItem(itemstack)) {
                // CraftBukkit start - call EntityPickupItemEvent
                if (CraftEventFactory.callEntityPickupItemEvent(this, entityitem, 0, false).isCancelled()) {
                    return;
                }
                itemstack = entityitem.getItem(); // CraftBukkit- update ItemStack from event
                // CraftBukkit start
                this.onItemPickup(entityitem);
                this.setItemSlot(EnumItemSlot.MAINHAND, itemstack);
                this.setGuaranteedDrop(EnumItemSlot.MAINHAND);
                this.take(entityitem, itemstack.getCount());
                entityitem.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            }
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isNoAi()) {
            this.setAirSupply(this.getMaxAirSupply());
        } else {
            if (this.isInWaterOrRain()) {
                this.setMoisntessLevel(2400);
            } else {
                this.setMoisntessLevel(this.getMoistnessLevel() - 1);
                if (this.getMoistnessLevel() <= 0) {
                    this.hurt(this.damageSources().dryOut(), 1.0F);
                }

                if (this.onGround()) {
                    this.setDeltaMovement(this.getDeltaMovement().add((double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.2F), 0.5D, (double) ((this.random.nextFloat() * 2.0F - 1.0F) * 0.2F)));
                    this.setYRot(this.random.nextFloat() * 360.0F);
                    this.setOnGround(false);
                    this.hasImpulse = true;
                }
            }

            if (this.level().isClientSide && this.isInWater() && this.getDeltaMovement().lengthSqr() > 0.03D) {
                Vec3D vec3d = this.getViewVector(0.0F);
                float f = MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F)) * 0.3F;
                float f1 = MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)) * 0.3F;
                float f2 = 1.2F - this.random.nextFloat() * 0.7F;

                for (int i = 0; i < 2; ++i) {
                    this.level().addParticle(Particles.DOLPHIN, this.getX() - vec3d.x * (double) f2 + (double) f, this.getY() - vec3d.y, this.getZ() - vec3d.z * (double) f2 + (double) f1, 0.0D, 0.0D, 0.0D);
                    this.level().addParticle(Particles.DOLPHIN, this.getX() - vec3d.x * (double) f2 - (double) f, this.getY() - vec3d.y, this.getZ() - vec3d.z * (double) f2 - (double) f1, 0.0D, 0.0D, 0.0D);
                }
            }

        }
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 38) {
            this.addParticlesAroundSelf(Particles.HAPPY_VILLAGER);
        } else {
            super.handleEntityEvent(b0);
        }

    }

    private void addParticlesAroundSelf(ParticleParam particleparam) {
        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.01D;
            double d1 = this.random.nextGaussian() * 0.01D;
            double d2 = this.random.nextGaussian() * 0.01D;

            this.level().addParticle(particleparam, this.getRandomX(1.0D), this.getRandomY() + 0.2D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    protected EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (!itemstack.isEmpty() && itemstack.is(TagsItem.FISHES)) {
            if (!this.level().isClientSide) {
                this.playSound(SoundEffects.DOLPHIN_EAT, 1.0F, 1.0F);
            }

            if (this.isBaby()) {
                itemstack.consume(1, entityhuman);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-this.age), true);
            } else {
                this.setGotFish(true);
                itemstack.consume(1, entityhuman);
            }

            return EnumInteractionResult.SUCCESS;
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.DOLPHIN_HURT;
    }

    @Nullable
    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.DOLPHIN_DEATH;
    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return this.isInWater() ? SoundEffects.DOLPHIN_AMBIENT_WATER : SoundEffects.DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEffect getSwimSplashSound() {
        return SoundEffects.DOLPHIN_SPLASH;
    }

    @Override
    protected SoundEffect getSwimSound() {
        return SoundEffects.DOLPHIN_SWIM;
    }

    protected boolean closeToNextPos() {
        BlockPosition blockposition = this.getNavigation().getTargetPos();

        return blockposition != null ? blockposition.closerToCenterThan(this.position(), 12.0D) : false;
    }

    @Override
    public void travel(Vec3D vec3d) {
        if (this.isInWater()) {
            this.moveRelative(this.getSpeed(), vec3d);
            this.move(EnumMoveType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
        } else {
            super.travel(vec3d);
        }

    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    private class c extends PathfinderGoal {

        private int cooldown;

        c() {}

        @Override
        public boolean canUse() {
            if (this.cooldown > EntityDolphin.this.tickCount) {
                return false;
            } else {
                List<EntityItem> list = EntityDolphin.this.level().<EntityItem>getEntitiesOfClass(EntityItem.class, EntityDolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), EntityDolphin.ALLOWED_ITEMS);

                return !list.isEmpty() || !EntityDolphin.this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty();
            }
        }

        @Override
        public void start() {
            List<EntityItem> list = EntityDolphin.this.level().<EntityItem>getEntitiesOfClass(EntityItem.class, EntityDolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), EntityDolphin.ALLOWED_ITEMS);

            if (!list.isEmpty()) {
                EntityDolphin.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
                EntityDolphin.this.playSound(SoundEffects.DOLPHIN_PLAY, 1.0F, 1.0F);
            }

            this.cooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack itemstack = EntityDolphin.this.getItemBySlot(EnumItemSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                this.drop(itemstack);
                EntityDolphin.this.setItemSlot(EnumItemSlot.MAINHAND, ItemStack.EMPTY);
                this.cooldown = EntityDolphin.this.tickCount + EntityDolphin.this.random.nextInt(100);
            }

        }

        @Override
        public void tick() {
            List<EntityItem> list = EntityDolphin.this.level().<EntityItem>getEntitiesOfClass(EntityItem.class, EntityDolphin.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), EntityDolphin.ALLOWED_ITEMS);
            ItemStack itemstack = EntityDolphin.this.getItemBySlot(EnumItemSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                this.drop(itemstack);
                EntityDolphin.this.setItemSlot(EnumItemSlot.MAINHAND, ItemStack.EMPTY);
            } else if (!list.isEmpty()) {
                EntityDolphin.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
            }

        }

        private void drop(ItemStack itemstack) {
            if (!itemstack.isEmpty()) {
                double d0 = EntityDolphin.this.getEyeY() - (double) 0.3F;
                EntityItem entityitem = new EntityItem(EntityDolphin.this.level(), EntityDolphin.this.getX(), d0, EntityDolphin.this.getZ(), itemstack);

                entityitem.setPickUpDelay(40);
                entityitem.setThrower(EntityDolphin.this);
                float f = 0.3F;
                float f1 = EntityDolphin.this.random.nextFloat() * ((float) Math.PI * 2F);
                float f2 = 0.02F * EntityDolphin.this.random.nextFloat();

                entityitem.setDeltaMovement((double) (0.3F * -MathHelper.sin(EntityDolphin.this.getYRot() * ((float) Math.PI / 180F)) * MathHelper.cos(EntityDolphin.this.getXRot() * ((float) Math.PI / 180F)) + MathHelper.cos(f1) * f2), (double) (0.3F * MathHelper.sin(EntityDolphin.this.getXRot() * ((float) Math.PI / 180F)) * 1.5F), (double) (0.3F * MathHelper.cos(EntityDolphin.this.getYRot() * ((float) Math.PI / 180F)) * MathHelper.cos(EntityDolphin.this.getXRot() * ((float) Math.PI / 180F)) + MathHelper.sin(f1) * f2));
                EntityDolphin.this.level().addFreshEntity(entityitem);
            }
        }
    }

    private static class b extends PathfinderGoal {

        private final EntityDolphin dolphin;
        private final double speedModifier;
        @Nullable
        private EntityHuman player;

        b(EntityDolphin entitydolphin, double d0) {
            this.dolphin = entitydolphin;
            this.speedModifier = d0;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            this.player = getServerLevel((Entity) this.dolphin).getNearestPlayer(EntityDolphin.SWIM_WITH_PLAYER_TARGETING, this.dolphin);
            return this.player == null ? false : this.player.isSwimming() && this.dolphin.getTarget() != this.player;
        }

        @Override
        public boolean canContinueToUse() {
            return this.player != null && this.player.isSwimming() && this.dolphin.distanceToSqr((Entity) this.player) < 256.0D;
        }

        @Override
        public void start() {
            this.player.addEffect(new MobEffect(MobEffects.DOLPHINS_GRACE, 100), this.dolphin, EntityPotionEffectEvent.Cause.DOLPHIN); // CraftBukkit
        }

        @Override
        public void stop() {
            this.player = null;
            this.dolphin.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.dolphin.getLookControl().setLookAt(this.player, (float) (this.dolphin.getMaxHeadYRot() + 20), (float) this.dolphin.getMaxHeadXRot());
            if (this.dolphin.distanceToSqr((Entity) this.player) < 6.25D) {
                this.dolphin.getNavigation().stop();
            } else {
                this.dolphin.getNavigation().moveTo((Entity) this.player, this.speedModifier);
            }

            if (this.player.isSwimming() && this.player.level().random.nextInt(6) == 0) {
                this.player.addEffect(new MobEffect(MobEffects.DOLPHINS_GRACE, 100), this.dolphin, EntityPotionEffectEvent.Cause.DOLPHIN); // CraftBukkit
            }

        }
    }

    private static class a extends PathfinderGoal {

        private final EntityDolphin dolphin;
        private boolean stuck;

        a(EntityDolphin entitydolphin) {
            this.dolphin = entitydolphin;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public boolean canUse() {
            return this.dolphin.gotFish() && this.dolphin.getAirSupply() >= 100;
        }

        @Override
        public boolean canContinueToUse() {
            BlockPosition blockposition = this.dolphin.treasurePos;

            return blockposition == null ? false : !BlockPosition.containing((double) blockposition.getX(), this.dolphin.getY(), (double) blockposition.getZ()).closerToCenterThan(this.dolphin.position(), 4.0D) && !this.stuck && this.dolphin.getAirSupply() >= 100;
        }

        @Override
        public void start() {
            if (this.dolphin.level() instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) this.dolphin.level();

                this.stuck = false;
                this.dolphin.getNavigation().stop();
                BlockPosition blockposition = this.dolphin.blockPosition();
                BlockPosition blockposition1 = worldserver.findNearestMapStructure(StructureTags.DOLPHIN_LOCATED, blockposition, 50, false);

                if (blockposition1 != null) {
                    this.dolphin.treasurePos = blockposition1;
                    worldserver.broadcastEntityEvent(this.dolphin, (byte) 38);
                } else {
                    this.stuck = true;
                }
            }
        }

        @Override
        public void stop() {
            BlockPosition blockposition = this.dolphin.treasurePos;

            if (blockposition == null || BlockPosition.containing((double) blockposition.getX(), this.dolphin.getY(), (double) blockposition.getZ()).closerToCenterThan(this.dolphin.position(), 4.0D) || this.stuck) {
                this.dolphin.setGotFish(false);
            }

        }

        @Override
        public void tick() {
            if (this.dolphin.treasurePos != null) {
                World world = this.dolphin.level();

                if (this.dolphin.closeToNextPos() || this.dolphin.getNavigation().isDone()) {
                    Vec3D vec3d = Vec3D.atCenterOf(this.dolphin.treasurePos);
                    Vec3D vec3d1 = DefaultRandomPos.getPosTowards(this.dolphin, 16, 1, vec3d, (double) ((float) Math.PI / 8F));

                    if (vec3d1 == null) {
                        vec3d1 = DefaultRandomPos.getPosTowards(this.dolphin, 8, 4, vec3d, (double) ((float) Math.PI / 2F));
                    }

                    if (vec3d1 != null) {
                        BlockPosition blockposition = BlockPosition.containing(vec3d1);

                        if (!world.getFluidState(blockposition).is(TagsFluid.WATER) || !world.getBlockState(blockposition).isPathfindable(PathMode.WATER)) {
                            vec3d1 = DefaultRandomPos.getPosTowards(this.dolphin, 8, 5, vec3d, (double) ((float) Math.PI / 2F));
                        }
                    }

                    if (vec3d1 == null) {
                        this.stuck = true;
                        return;
                    }

                    this.dolphin.getLookControl().setLookAt(vec3d1.x, vec3d1.y, vec3d1.z, (float) (this.dolphin.getMaxHeadYRot() + 20), (float) this.dolphin.getMaxHeadXRot());
                    this.dolphin.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, 1.3D);
                    if (world.random.nextInt(this.adjustedTickDelay(80)) == 0) {
                        world.broadcastEntityEvent(this.dolphin, (byte) 38);
                    }
                }

            }
        }
    }
}
