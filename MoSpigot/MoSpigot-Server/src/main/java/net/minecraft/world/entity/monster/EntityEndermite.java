package net.minecraft.world.entity.monster;

import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityEndermite extends EntityMonster {

    private static final int MAX_LIFE = 2400;
    private static final int DEFAULT_LIFE = 0;
    private int life = 0;

    public EntityEndermite(EntityTypes<? extends EntityEndermite> entitytypes, World world) {
        super(entitytypes, world);
        this.xpReward = 3;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(2, new PathfinderGoalMeleeAttack(this, 1.0D, false));
        this.goalSelector.addGoal(3, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.goalSelector.addGoal(7, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F));
        this.goalSelector.addGoal(8, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, (new PathfinderGoalHurtByTarget(this, new Class[0])).setAlertOthers());
        this.targetSelector.addGoal(2, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, true));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MAX_HEALTH, 8.0D).add(GenericAttributes.MOVEMENT_SPEED, 0.25D).add(GenericAttributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.ENDERMITE_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.ENDERMITE_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.ENDERMITE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.ENDERMITE_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.life = valueinput.getIntOr("Lifetime", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("Lifetime", this.life);
    }

    @Override
    public void tick() {
        this.yBodyRot = this.getYRot();
        super.tick();
    }

    @Override
    public void setYBodyRot(float f) {
        this.setYRot(f);
        super.setYBodyRot(f);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(Particles.PORTAL, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
            }
        } else {
            if (!this.isPersistenceRequired()) {
                ++this.life;
            }

            if (this.life >= 2400) {
                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }
        }

    }

    public static boolean checkEndermiteSpawnRules(EntityTypes<EntityEndermite> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        if (!checkAnyLightMonsterSpawnRules(entitytypes, generatoraccess, entityspawnreason, blockposition, randomsource)) {
            return false;
        } else if (EntitySpawnReason.isSpawner(entityspawnreason)) {
            return true;
        } else {
            EntityHuman entityhuman = generatoraccess.getNearestPlayer((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, 5.0D, true);

            return entityhuman == null;
        }
    }
}
