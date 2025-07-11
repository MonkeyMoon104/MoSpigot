package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.EntityIronGolem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.GeneratorAccessSeed;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.levelgen.SeededRandom;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.scores.ScoreboardTeam;

// CraftBukkit start
import java.util.ArrayList;
import java.util.List;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
// CraftBukkit end

public class EntitySlime extends EntityInsentient implements IMonster {

    private static final DataWatcherObject<Integer> ID_SIZE = DataWatcher.<Integer>defineId(EntitySlime.class, DataWatcherRegistry.INT);
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 127;
    public static final int MAX_NATURAL_SIZE = 4;
    private static final boolean DEFAULT_WAS_ON_GROUND = false;
    public float targetSquish;
    public float squish;
    public float oSquish;
    private boolean wasOnGround = false;

    public EntitySlime(EntityTypes<? extends EntitySlime> entitytypes, World world) {
        super(entitytypes, world);
        this.fixupDimensions();
        this.moveControl = new EntitySlime.ControllerMoveSlime(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new EntitySlime.PathfinderGoalSlimeRandomJump(this));
        this.goalSelector.addGoal(2, new EntitySlime.PathfinderGoalSlimeNearestPlayer(this));
        this.goalSelector.addGoal(3, new EntitySlime.PathfinderGoalSlimeRandomDirection(this));
        this.goalSelector.addGoal(5, new EntitySlime.PathfinderGoalSlimeIdle(this));
        this.targetSelector.addGoal(1, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, 10, true, false, (entityliving, worldserver) -> {
            return Math.abs(entityliving.getY() - this.getY()) <= 4.0D;
        }));
        this.targetSelector.addGoal(3, new PathfinderGoalNearestAttackableTarget(this, EntityIronGolem.class, true));
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.HOSTILE;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntitySlime.ID_SIZE, 1);
    }

    @VisibleForTesting
    public void setSize(int i, boolean flag) {
        int j = MathHelper.clamp(i, 1, 127);

        this.entityData.set(EntitySlime.ID_SIZE, j);
        this.reapplyPosition();
        this.refreshDimensions();
        this.getAttribute(GenericAttributes.MAX_HEALTH).setBaseValue((double) (j * j));
        this.getAttribute(GenericAttributes.MOVEMENT_SPEED).setBaseValue((double) (0.2F + 0.1F * (float) j));
        this.getAttribute(GenericAttributes.ATTACK_DAMAGE).setBaseValue((double) j);
        if (flag) {
            this.setHealth(this.getMaxHealth());
        }

        this.xpReward = j;
    }

    public int getSize() {
        return (Integer) this.entityData.get(EntitySlime.ID_SIZE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("Size", this.getSize() - 1);
        valueoutput.putBoolean("wasOnGround", this.wasOnGround);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        this.setSize(valueinput.getIntOr("Size", 0) + 1, false);
        super.readAdditionalSaveData(valueinput);
        this.wasOnGround = valueinput.getBooleanOr("wasOnGround", false);
    }

    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    protected ParticleParam getParticleType() {
        return Particles.ITEM_SLIME;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return this.getSize() > 0;
    }

    @Override
    public void tick() {
        this.oSquish = this.squish;
        this.squish += (this.targetSquish - this.squish) * 0.5F;
        super.tick();
        if (this.onGround() && !this.wasOnGround) {
            float f = this.getDimensions(this.getPose()).width() * 2.0F;
            float f1 = f / 2.0F;

            for (int i = 0; (float) i < f * 16.0F; ++i) {
                float f2 = this.random.nextFloat() * ((float) Math.PI * 2F);
                float f3 = this.random.nextFloat() * 0.5F + 0.5F;
                float f4 = MathHelper.sin(f2) * f1 * f3;
                float f5 = MathHelper.cos(f2) * f1 * f3;

                this.level().addParticle(this.getParticleType(), this.getX() + (double) f4, this.getY(), this.getZ() + (double) f5, 0.0D, 0.0D, 0.0D);
            }

            this.playSound(this.getSquishSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            this.targetSquish = -0.5F;
        } else if (!this.onGround() && this.wasOnGround) {
            this.targetSquish = 1.0F;
        }

        this.wasOnGround = this.onGround();
        this.decreaseSquish();
    }

    protected void decreaseSquish() {
        this.targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntitySlime.ID_SIZE.equals(datawatcherobject)) {
            this.refreshDimensions();
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public EntityTypes<? extends EntitySlime> getType() {
        return (EntityTypes<? extends EntitySlime>) super.getType(); // CraftBukkit - decompile error
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(entity_removalreason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        // CraftBukkit end
        int i = this.getSize();

        if (!this.level().isClientSide && i > 1 && this.isDeadOrDying()) {
            float f = this.getDimensions(this.getPose()).width();
            float f1 = f / 2.0F;
            int j = i / 2;
            int k = 2 + this.random.nextInt(3);
            ScoreboardTeam scoreboardteam = this.getTeam();

            // CraftBukkit start
            SlimeSplitEvent event = new SlimeSplitEvent((org.bukkit.entity.Slime) this.getBukkitEntity(), k);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled() && event.getCount() > 0) {
                k = event.getCount();
            } else {
                super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
                return;
            }
            List<EntityLiving> slimes = new ArrayList<>(j);
            // CraftBukkit end

            for (int l = 0; l < k; ++l) {
                float f2 = ((float) (l % 2) - 0.5F) * f1;
                float f3 = ((float) (l / 2) - 0.5F) * f1;

                EntitySlime converted = this.convertTo(this.getType(), new ConversionParams(ConversionType.SPLIT_ON_DEATH, false, false, scoreboardteam), EntitySpawnReason.TRIGGERED, (entityslime) -> { // CraftBukkit
                    entityslime.setSize(j, true);
                    entityslime.snapTo(this.getX() + (double) f2, this.getY() + 0.5D, this.getZ() + (double) f3, this.random.nextFloat() * 360.0F, 0.0F);
                // CraftBukkit start
                }, null, null);
                if (converted != null) {
                    slimes.add(converted);
                }
                // CraftBukkit end
            }
            // CraftBukkit start
            if (CraftEventFactory.callEntityTransformEvent(this, slimes, EntityTransformEvent.TransformReason.SPLIT).isCancelled()) {
                super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
                return;
            }
            for (EntityLiving living : slimes) {
                this.level().addFreshEntity(living, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SLIME_SPLIT); // CraftBukkit - SpawnReason
            }
            // CraftBukkit end
        }

        super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof EntityIronGolem && this.isDealsDamage()) {
            this.dealDamage((EntityLiving) entity);
        }

    }

    @Override
    public void playerTouch(EntityHuman entityhuman) {
        if (this.isDealsDamage()) {
            this.dealDamage(entityhuman);
        }

    }

    protected void dealDamage(EntityLiving entityliving) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.isAlive() && this.isWithinMeleeAttackRange(entityliving) && this.hasLineOfSight(entityliving)) {
                DamageSource damagesource = this.damageSources().mobAttack(this);

                if (entityliving.hurtServer(worldserver, damagesource, this.getAttackDamage())) {
                    this.playSound(SoundEffects.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    EnchantmentManager.doPostAttackEffects(worldserver, entityliving, damagesource);
                }
            }
        }

    }

    @Override
    protected Vec3D getPassengerAttachmentPoint(Entity entity, EntitySize entitysize, float f) {
        return new Vec3D(0.0D, (double) entitysize.height() - 0.015625D * (double) this.getSize() * (double) f, 0.0D);
    }

    protected boolean isDealsDamage() {
        return !this.isTiny() && this.isEffectiveAi();
    }

    protected float getAttackDamage() {
        return (float) this.getAttributeValue(GenericAttributes.ATTACK_DAMAGE);
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return this.isTiny() ? SoundEffects.SLIME_HURT_SMALL : SoundEffects.SLIME_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return this.isTiny() ? SoundEffects.SLIME_DEATH_SMALL : SoundEffects.SLIME_DEATH;
    }

    protected SoundEffect getSquishSound() {
        return this.isTiny() ? SoundEffects.SLIME_SQUISH_SMALL : SoundEffects.SLIME_SQUISH;
    }

    public static boolean checkSlimeSpawnRules(EntityTypes<EntitySlime> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        if (generatoraccess.getDifficulty() != EnumDifficulty.PEACEFUL) {
            if (EntitySpawnReason.isSpawner(entityspawnreason)) {
                return checkMobSpawnRules(entitytypes, generatoraccess, entityspawnreason, blockposition, randomsource);
            }

            if (generatoraccess.getBiome(blockposition).is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS) && blockposition.getY() > 50 && blockposition.getY() < 70 && randomsource.nextFloat() < 0.5F && randomsource.nextFloat() < generatoraccess.getMoonBrightness() && generatoraccess.getMaxLocalRawBrightness(blockposition) <= randomsource.nextInt(8)) {
                return checkMobSpawnRules(entitytypes, generatoraccess, entityspawnreason, blockposition, randomsource);
            }

            if (!(generatoraccess instanceof GeneratorAccessSeed)) {
                return false;
            }

            ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(blockposition);
            boolean flag = SeededRandom.seedSlimeChunk(chunkcoordintpair.x, chunkcoordintpair.z, ((GeneratorAccessSeed) generatoraccess).getSeed(), generatoraccess.getMinecraftWorld().spigotConfig.slimeSeed).nextInt(10) == 0; // Spigot

            if (randomsource.nextInt(10) == 0 && flag && blockposition.getY() < 40) {
                return checkMobSpawnRules(entitytypes, generatoraccess, entityspawnreason, blockposition, randomsource);
            }
        }

        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F * (float) this.getSize();
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    protected boolean doPlayJumpSound() {
        return this.getSize() > 0;
    }

    @Override
    public void jumpFromGround() {
        Vec3D vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x, (double) this.getJumpPower(), vec3d.z);
        this.hasImpulse = true;
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();
        int i = randomsource.nextInt(3);

        if (i < 2 && randomsource.nextFloat() < 0.5F * difficultydamagescaler.getSpecialMultiplier()) {
            ++i;
        }

        int j = 1 << i;

        this.setSize(j, true);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    float getSoundPitch() {
        float f = this.isTiny() ? 1.4F : 0.8F;

        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * f;
    }

    protected SoundEffect getJumpSound() {
        return this.isTiny() ? SoundEffects.SLIME_JUMP_SMALL : SoundEffects.SLIME_JUMP;
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return super.getDefaultDimensions(entitypose).scale((float) this.getSize());
    }

    private static class ControllerMoveSlime extends ControllerMove {

        private float yRot;
        private int jumpDelay;
        private final EntitySlime slime;
        private boolean isAggressive;

        public ControllerMoveSlime(EntitySlime entityslime) {
            super(entityslime);
            this.slime = entityslime;
            this.yRot = 180.0F * entityslime.getYRot() / (float) Math.PI;
        }

        public void setDirection(float f, boolean flag) {
            this.yRot = f;
            this.isAggressive = flag;
        }

        public void setWantedMovement(double d0) {
            this.speedModifier = d0;
            this.operation = ControllerMove.Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.yBodyRot = this.mob.getYRot();
            if (this.operation != ControllerMove.Operation.MOVE_TO) {
                this.mob.setZza(0.0F);
            } else {
                this.operation = ControllerMove.Operation.WAIT;
                if (this.mob.onGround()) {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(GenericAttributes.MOVEMENT_SPEED)));
                    if (this.jumpDelay-- <= 0) {
                        this.jumpDelay = this.slime.getJumpDelay();
                        if (this.isAggressive) {
                            this.jumpDelay /= 3;
                        }

                        this.slime.getJumpControl().jump();
                        if (this.slime.doPlayJumpSound()) {
                            this.slime.playSound(this.slime.getJumpSound(), this.slime.getSoundVolume(), this.slime.getSoundPitch());
                        }
                    } else {
                        this.slime.xxa = 0.0F;
                        this.slime.zza = 0.0F;
                        this.mob.setSpeed(0.0F);
                    }
                } else {
                    this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(GenericAttributes.MOVEMENT_SPEED)));
                }

            }
        }
    }

    private static class PathfinderGoalSlimeNearestPlayer extends PathfinderGoal {

        private final EntitySlime slime;
        private int growTiredTimer;

        public PathfinderGoalSlimeNearestPlayer(EntitySlime entityslime) {
            this.slime = entityslime;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            EntityLiving entityliving = this.slime.getTarget();

            return entityliving == null ? false : (!this.slime.canAttack(entityliving) ? false : this.slime.getMoveControl() instanceof EntitySlime.ControllerMoveSlime);
        }

        @Override
        public void start() {
            this.growTiredTimer = reducedTickDelay(300);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            EntityLiving entityliving = this.slime.getTarget();

            return entityliving == null ? false : (!this.slime.canAttack(entityliving) ? false : --this.growTiredTimer > 0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            EntityLiving entityliving = this.slime.getTarget();

            if (entityliving != null) {
                this.slime.lookAt(entityliving, 10.0F, 10.0F);
            }

            ControllerMove controllermove = this.slime.getMoveControl();

            if (controllermove instanceof EntitySlime.ControllerMoveSlime entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setDirection(this.slime.getYRot(), this.slime.isDealsDamage());
            }

        }
    }

    private static class PathfinderGoalSlimeRandomDirection extends PathfinderGoal {

        private final EntitySlime slime;
        private float chosenDegrees;
        private int nextRandomizeTime;

        public PathfinderGoalSlimeRandomDirection(EntitySlime entityslime) {
            this.slime = entityslime;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.slime.getTarget() == null && (this.slime.onGround() || this.slime.isInWater() || this.slime.isInLava() || this.slime.hasEffect(MobEffects.LEVITATION)) && this.slime.getMoveControl() instanceof EntitySlime.ControllerMoveSlime;
        }

        @Override
        public void tick() {
            if (--this.nextRandomizeTime <= 0) {
                this.nextRandomizeTime = this.adjustedTickDelay(40 + this.slime.getRandom().nextInt(60));
                this.chosenDegrees = (float) this.slime.getRandom().nextInt(360);
            }

            ControllerMove controllermove = this.slime.getMoveControl();

            if (controllermove instanceof EntitySlime.ControllerMoveSlime entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setDirection(this.chosenDegrees, false);
            }

        }
    }

    private static class PathfinderGoalSlimeRandomJump extends PathfinderGoal {

        private final EntitySlime slime;

        public PathfinderGoalSlimeRandomJump(EntitySlime entityslime) {
            this.slime = entityslime;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.JUMP, PathfinderGoal.Type.MOVE));
            entityslime.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return (this.slime.isInWater() || this.slime.isInLava()) && this.slime.getMoveControl() instanceof EntitySlime.ControllerMoveSlime;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.slime.getRandom().nextFloat() < 0.8F) {
                this.slime.getJumpControl().jump();
            }

            ControllerMove controllermove = this.slime.getMoveControl();

            if (controllermove instanceof EntitySlime.ControllerMoveSlime entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setWantedMovement(1.2D);
            }

        }
    }

    private static class PathfinderGoalSlimeIdle extends PathfinderGoal {

        private final EntitySlime slime;

        public PathfinderGoalSlimeIdle(EntitySlime entityslime) {
            this.slime = entityslime;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.JUMP, PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.slime.isPassenger();
        }

        @Override
        public void tick() {
            ControllerMove controllermove = this.slime.getMoveControl();

            if (controllermove instanceof EntitySlime.ControllerMoveSlime entityslime_controllermoveslime) {
                entityslime_controllermoveslime.setWantedMovement(1.0D);
            }

        }
    }
}
