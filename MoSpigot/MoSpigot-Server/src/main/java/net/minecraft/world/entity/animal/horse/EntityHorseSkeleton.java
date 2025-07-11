package net.minecraft.world.entity.animal.horse;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.RandomSource;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifiable;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityHorseSkeleton extends EntityHorseAbstract {

    private final PathfinderGoalHorseTrap skeletonTrapGoal = new PathfinderGoalHorseTrap(this);
    private static final int TRAP_MAX_LIFE = 18000;
    private static final boolean DEFAULT_IS_TRAP = false;
    private static final int DEFAULT_TRAP_TIME = 0;
    private static final EntitySize BABY_DIMENSIONS = EntityTypes.SKELETON_HORSE.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityTypes.SKELETON_HORSE.getHeight() - 0.03125F, 0.0F)).scale(0.5F);
    private boolean isTrap = false;
    public int trapTime = 0;

    public EntityHorseSkeleton(EntityTypes<? extends EntityHorseSkeleton> entitytypes, World world) {
        super(entitytypes, world);
    }

    public static AttributeProvider.Builder createAttributes() {
        return createBaseHorseAttributes().add(GenericAttributes.MAX_HEALTH, 15.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.2F);
    }

    public static boolean checkSkeletonHorseSpawnRules(EntityTypes<? extends EntityAnimal> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return !EntitySpawnReason.isSpawner(entityspawnreason) ? EntityAnimal.checkAnimalSpawnRules(entitytypes, generatoraccess, entityspawnreason, blockposition, randomsource) : EntitySpawnReason.ignoresLightRequirements(entityspawnreason) || isBrightEnoughToSpawn(generatoraccess, blockposition);
    }

    @Override
    protected void randomizeAttributes(RandomSource randomsource) {
        AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.JUMP_STRENGTH);

        Objects.requireNonNull(randomsource);
        attributemodifiable.setBaseValue(generateJumpStrength(randomsource::nextDouble));
    }

    @Override
    protected void addBehaviourGoals() {}

    @Override
    protected SoundEffect getAmbientSound() {
        return this.isEyeInFluid(TagsFluid.WATER) ? SoundEffects.SKELETON_HORSE_AMBIENT_WATER : SoundEffects.SKELETON_HORSE_AMBIENT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.SKELETON_HORSE_DEATH;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.SKELETON_HORSE_HURT;
    }

    @Override
    protected SoundEffect getSwimSound() {
        if (this.onGround()) {
            if (!this.isVehicle()) {
                return SoundEffects.SKELETON_HORSE_STEP_WATER;
            }

            ++this.gallopSoundCounter;
            if (this.gallopSoundCounter > 5 && this.gallopSoundCounter % 3 == 0) {
                return SoundEffects.SKELETON_HORSE_GALLOP_WATER;
            }

            if (this.gallopSoundCounter <= 5) {
                return SoundEffects.SKELETON_HORSE_STEP_WATER;
            }
        }

        return SoundEffects.SKELETON_HORSE_SWIM;
    }

    @Override
    protected void playSwimSound(float f) {
        if (this.onGround()) {
            super.playSwimSound(0.3F);
        } else {
            super.playSwimSound(Math.min(0.1F, f * 25.0F));
        }

    }

    @Override
    protected void playJumpSound() {
        if (this.isInWater()) {
            this.playSound(SoundEffects.SKELETON_HORSE_JUMP_WATER, 0.4F, 1.0F);
        } else {
            super.playJumpSound();
        }

    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityHorseSkeleton.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isTrap() && this.trapTime++ >= 18000) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("SkeletonTrap", this.isTrap());
        valueoutput.putInt("SkeletonTrapTime", this.trapTime);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setTrap(valueinput.getBooleanOr("SkeletonTrap", false));
        this.trapTime = valueinput.getIntOr("SkeletonTrapTime", 0);
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.96F;
    }

    public boolean isTrap() {
        return this.isTrap;
    }

    public void setTrap(boolean flag) {
        if (flag != this.isTrap) {
            this.isTrap = flag;
            if (flag) {
                this.goalSelector.addGoal(1, this.skeletonTrapGoal);
            } else {
                this.goalSelector.removeGoal(this.skeletonTrapGoal);
            }

        }
    }

    @Nullable
    @Override
    public EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        return EntityTypes.SKELETON_HORSE.create(worldserver, EntitySpawnReason.BREEDING);
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        return (EnumInteractionResult) (!this.isTamed() ? EnumInteractionResult.PASS : super.mobInteract(entityhuman, enumhand));
    }
}
