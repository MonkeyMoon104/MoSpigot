package net.minecraft.world.entity.monster;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeRange;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.IEntityAngerable;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeModifiable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalZombieAttack;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalUniversalAngerReset;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;

public class EntityPigZombie extends EntityZombie implements IEntityAngerable {

    private static final EntitySize BABY_DIMENSIONS = EntityTypes.ZOMBIFIED_PIGLIN.getDimensions().scale(0.5F).withEyeHeight(0.97F);
    private static final MinecraftKey SPEED_MODIFIER_ATTACKING_ID = MinecraftKey.withDefaultNamespace("attacking");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(EntityPigZombie.SPEED_MODIFIER_ATTACKING_ID, 0.05D, AttributeModifier.Operation.ADD_VALUE);
    private static final UniformInt FIRST_ANGER_SOUND_DELAY = TimeRange.rangeOfSeconds(0, 1);
    private int playFirstAngerSoundIn;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeRange.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;
    private static final int ALERT_RANGE_Y = 10;
    private static final UniformInt ALERT_INTERVAL = TimeRange.rangeOfSeconds(4, 6);
    private int ticksUntilNextAlert;

    public EntityPigZombie(EntityTypes<? extends EntityPigZombie> entitytypes, World world) {
        super(entitytypes, world);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID uuid) {
        this.persistentAngerTarget = uuid;
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new PathfinderGoalZombieAttack(this, 1.0D, false));
        this.goalSelector.addGoal(7, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.targetSelector.addGoal(1, (new PathfinderGoalHurtByTarget(this, new Class[0])).setAlertOthers());
        this.targetSelector.addGoal(2, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new PathfinderGoalUniversalAngerReset(this, true));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityZombie.createAttributes().add(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.23F).add(GenericAttributes.ATTACK_DAMAGE, 5.0D);
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityPigZombie.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.MOVEMENT_SPEED);

        if (this.isAngry()) {
            if (!this.isBaby() && !attributemodifiable.hasModifier(EntityPigZombie.SPEED_MODIFIER_ATTACKING_ID)) {
                attributemodifiable.addTransientModifier(EntityPigZombie.SPEED_MODIFIER_ATTACKING);
            }

            this.maybePlayFirstAngerSound();
        } else if (attributemodifiable.hasModifier(EntityPigZombie.SPEED_MODIFIER_ATTACKING_ID)) {
            attributemodifiable.removeModifier(EntityPigZombie.SPEED_MODIFIER_ATTACKING_ID);
        }

        this.updatePersistentAnger(worldserver, true);
        if (this.getTarget() != null) {
            this.maybeAlertOthers();
        }

        super.customServerAiStep(worldserver);
    }

    private void maybePlayFirstAngerSound() {
        if (this.playFirstAngerSoundIn > 0) {
            --this.playFirstAngerSoundIn;
            if (this.playFirstAngerSoundIn == 0) {
                this.playAngerSound();
            }
        }

    }

    private void maybeAlertOthers() {
        if (this.ticksUntilNextAlert > 0) {
            --this.ticksUntilNextAlert;
        } else {
            if (this.getSensing().hasLineOfSight(this.getTarget())) {
                this.alertOthers();
            }

            this.ticksUntilNextAlert = EntityPigZombie.ALERT_INTERVAL.sample(this.random);
        }
    }

    private void alertOthers() {
        double d0 = this.getAttributeValue(GenericAttributes.FOLLOW_RANGE);
        AxisAlignedBB axisalignedbb = AxisAlignedBB.unitCubeFromLowerCorner(this.position()).inflate(d0, 10.0D, d0);

        this.level().getEntitiesOfClass(EntityPigZombie.class, axisalignedbb, IEntitySelector.NO_SPECTATORS).stream().filter((entitypigzombie) -> {
            return entitypigzombie != this;
        }).filter((entitypigzombie) -> {
            return entitypigzombie.getTarget() == null;
        }).filter((entitypigzombie) -> {
            return !entitypigzombie.isAlliedTo((Entity) this.getTarget());
        }).forEach((entitypigzombie) -> {
            entitypigzombie.setTarget(this.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY, true); // CraftBukkit
        });
    }

    private void playAngerSound() {
        this.playSound(SoundEffects.ZOMBIFIED_PIGLIN_ANGRY, this.getSoundVolume() * 2.0F, this.getVoicePitch() * 1.8F);
    }

    @Override
    public boolean setTarget(@Nullable EntityLiving entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason reason, boolean fireEvent) { // CraftBukkit - signature
        if (this.getTarget() == null && entityliving != null) {
            this.playFirstAngerSoundIn = EntityPigZombie.FIRST_ANGER_SOUND_DELAY.sample(this.random);
            this.ticksUntilNextAlert = EntityPigZombie.ALERT_INTERVAL.sample(this.random);
        }

        return super.setTarget(entityliving, reason, fireEvent); // CraftBukkit
    }

    @Override
    public void startPersistentAngerTimer() {
        // CraftBukkit start
        Entity entity = ((WorldServer) this.level()).getEntity(getPersistentAngerTarget());
        org.bukkit.event.entity.PigZombieAngerEvent event = new org.bukkit.event.entity.PigZombieAngerEvent((org.bukkit.entity.PigZombie) this.getBukkitEntity(), (entity == null) ? null : entity.getBukkitEntity(), EntityPigZombie.PERSISTENT_ANGER_TIME.sample(this.random));
        this.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.setPersistentAngerTarget(null);
            return;
        }
        this.setRemainingPersistentAngerTime(event.getNewAnger());
        // CraftBukkit end
    }

    public static boolean checkZombifiedPiglinSpawnRules(EntityTypes<EntityPigZombie> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getDifficulty() != EnumDifficulty.PEACEFUL && !generatoraccess.getBlockState(blockposition.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Override
    public boolean checkSpawnObstruction(IWorldReader iworldreader) {
        return iworldreader.isUnobstructed(this) && !iworldreader.containsAnyLiquid(this.getBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        this.addPersistentAngerSaveData(valueoutput);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.readPersistentAngerSaveData(this.level(), valueinput);
    }

    @Override
    public void setRemainingPersistentAngerTime(int i) {
        this.remainingPersistentAngerTime = i;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return this.isAngry() ? SoundEffects.ZOMBIFIED_PIGLIN_ANGRY : SoundEffects.ZOMBIFIED_PIGLIN_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.ZOMBIFIED_PIGLIN_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.ZOMBIFIED_PIGLIN_DEATH;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        this.setItemSlot(EnumItemSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void randomizeReinforcementsChance() {
        this.getAttribute(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0D);
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public boolean isPreventingPlayerRest(WorldServer worldserver, EntityHuman entityhuman) {
        return this.isAngryAt(entityhuman, worldserver);
    }

    @Override
    public boolean wantsToPickUp(WorldServer worldserver, ItemStack itemstack) {
        return this.canHoldItem(itemstack);
    }
}
