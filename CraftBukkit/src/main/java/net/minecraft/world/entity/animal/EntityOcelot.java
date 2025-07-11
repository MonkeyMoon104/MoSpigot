package net.minecraft.world.entity.animal;

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
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLeapAtTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalOcelotAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

public class EntityOcelot extends EntityAnimal {

    public static final double CROUCH_SPEED_MOD = 0.6D;
    public static final double WALK_SPEED_MOD = 0.8D;
    public static final double SPRINT_SPEED_MOD = 1.33D;
    private static final DataWatcherObject<Boolean> DATA_TRUSTING = DataWatcher.<Boolean>defineId(EntityOcelot.class, DataWatcherRegistry.BOOLEAN);
    private static final boolean DEFAULT_TRUSTING = false;
    @Nullable
    private EntityOcelot.a<EntityHuman> ocelotAvoidPlayersGoal;
    @Nullable
    private EntityOcelot.b temptGoal;

    public EntityOcelot(EntityTypes<? extends EntityOcelot> entitytypes, World world) {
        super(entitytypes, world);
        this.reassessTrustingGoals();
    }

    public boolean isTrusting() {
        return (Boolean) this.entityData.get(EntityOcelot.DATA_TRUSTING);
    }

    public void setTrusting(boolean flag) {
        this.entityData.set(EntityOcelot.DATA_TRUSTING, flag);
        this.reassessTrustingGoals();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("Trusting", this.isTrusting());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setTrusting(valueinput.getBooleanOr("Trusting", false));
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityOcelot.DATA_TRUSTING, false);
    }

    @Override
    protected void registerGoals() {
        this.temptGoal = new EntityOcelot.b(this, 0.6D, (itemstack) -> {
            return itemstack.is(TagsItem.OCELOT_FOOD);
        }, true);
        this.goalSelector.addGoal(1, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(3, this.temptGoal);
        this.goalSelector.addGoal(7, new PathfinderGoalLeapAtTarget(this, 0.3F));
        this.goalSelector.addGoal(8, new PathfinderGoalOcelotAttack(this));
        this.goalSelector.addGoal(9, new PathfinderGoalBreed(this, 0.8D));
        this.goalSelector.addGoal(10, new PathfinderGoalRandomStrollLand(this, 0.8D, 1.0000001E-5F));
        this.goalSelector.addGoal(11, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 10.0F));
        this.targetSelector.addGoal(1, new PathfinderGoalNearestAttackableTarget(this, EntityChicken.class, false));
        this.targetSelector.addGoal(1, new PathfinderGoalNearestAttackableTarget(this, EntityTurtle.class, 10, false, false, EntityTurtle.BABY_ON_LAND_SELECTOR));
    }

    @Override
    public void customServerAiStep(WorldServer worldserver) {
        if (this.getMoveControl().hasWanted()) {
            double d0 = this.getMoveControl().getSpeedModifier();

            if (d0 == 0.6D) {
                this.setPose(EntityPose.CROUCHING);
                this.setSprinting(false);
            } else if (d0 == 1.33D) {
                this.setPose(EntityPose.STANDING);
                this.setSprinting(true);
            } else {
                this.setPose(EntityPose.STANDING);
                this.setSprinting(false);
            }
        } else {
            this.setPose(EntityPose.STANDING);
            this.setSprinting(false);
        }

    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isTrusting() && this.tickCount > 2400;
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 10.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.ATTACK_DAMAGE, 3.0D);
    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.OCELOT_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 900;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.OCELOT_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.OCELOT_DEATH;
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if ((this.temptGoal == null || this.temptGoal.isRunning()) && !this.isTrusting() && this.isFood(itemstack) && entityhuman.distanceToSqr((Entity) this) < 9.0D) {
            this.usePlayerItem(entityhuman, enumhand, itemstack);
            if (!this.level().isClientSide) {
                if (this.random.nextInt(3) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) { // CraftBukkit - added event call and isCancelled check
                    this.setTrusting(true);
                    this.spawnTrustingParticles(true);
                    this.level().broadcastEntityEvent(this, (byte) 41);
                } else {
                    this.spawnTrustingParticles(false);
                    this.level().broadcastEntityEvent(this, (byte) 40);
                }
            }

            return EnumInteractionResult.SUCCESS;
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 41) {
            this.spawnTrustingParticles(true);
        } else if (b0 == 40) {
            this.spawnTrustingParticles(false);
        } else {
            super.handleEntityEvent(b0);
        }

    }

    private void spawnTrustingParticles(boolean flag) {
        ParticleParam particleparam = Particles.HEART;

        if (!flag) {
            particleparam = Particles.SMOKE;
        }

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(particleparam, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    protected void reassessTrustingGoals() {
        if (this.ocelotAvoidPlayersGoal == null) {
            this.ocelotAvoidPlayersGoal = new EntityOcelot.a<EntityHuman>(this, EntityHuman.class, 16.0F, 0.8D, 1.33D);
        }

        this.goalSelector.removeGoal(this.ocelotAvoidPlayersGoal);
        if (!this.isTrusting()) {
            this.goalSelector.addGoal(4, this.ocelotAvoidPlayersGoal);
        }

    }

    @Nullable
    @Override
    public EntityOcelot getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        return EntityTypes.OCELOT.create(worldserver, EntitySpawnReason.BREEDING);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.OCELOT_FOOD);
    }

    public static boolean checkOcelotSpawnRules(EntityTypes<EntityOcelot> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return randomsource.nextInt(3) != 0;
    }

    @Override
    public boolean checkSpawnObstruction(IWorldReader iworldreader) {
        if (iworldreader.isUnobstructed(this) && !iworldreader.containsAnyLiquid(this.getBoundingBox())) {
            BlockPosition blockposition = this.blockPosition();

            if (blockposition.getY() < iworldreader.getSeaLevel()) {
                return false;
            }

            IBlockData iblockdata = iworldreader.getBlockState(blockposition.below());

            if (iblockdata.is(Blocks.GRASS_BLOCK) || iblockdata.is(TagsBlock.LEAVES)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        if (groupdataentity == null) {
            groupdataentity = new EntityAgeable.a(1.0F);
        }

        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) (0.5F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    @Override
    public boolean isSteppingCarefully() {
        return this.isCrouching() || super.isSteppingCarefully();
    }

    private static class a<T extends EntityLiving> extends PathfinderGoalAvoidTarget<T> {

        private final EntityOcelot ocelot;

        public a(EntityOcelot entityocelot, Class<T> oclass, float f, double d0, double d1) {
            // Predicate predicate = IEntitySelector.NO_CREATIVE_OR_SPECTATOR; // CraftBukkit - decompile error

            // Objects.requireNonNull(predicate); // CraftBukkit - decompile error
            super(entityocelot, oclass, f, d0, d1, IEntitySelector.NO_CREATIVE_OR_SPECTATOR::test); // CraftBukkit - decompile error
            this.ocelot = entityocelot;
        }

        @Override
        public boolean canUse() {
            return !this.ocelot.isTrusting() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.ocelot.isTrusting() && super.canContinueToUse();
        }
    }

    private static class b extends PathfinderGoalTempt {

        private final EntityOcelot ocelot;

        public b(EntityOcelot entityocelot, double d0, Predicate<ItemStack> predicate, boolean flag) {
            super(entityocelot, d0, predicate, flag);
            this.ocelot = entityocelot;
        }

        @Override
        protected boolean canScare() {
            return super.canScare() && !this.ocelot.isTrusting();
        }
    }
}
