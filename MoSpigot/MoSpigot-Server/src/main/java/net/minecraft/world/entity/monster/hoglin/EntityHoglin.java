package net.minecraft.world.entity.monster.hoglin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.BehaviorController;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.monster.IMonster;
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

public class EntityHoglin extends EntityAnimal implements IMonster, IOglin {

    private static final DataWatcherObject<Boolean> DATA_IMMUNE_TO_ZOMBIFICATION = DataWatcher.<Boolean>defineId(EntityHoglin.class, DataWatcherRegistry.BOOLEAN);
    private static final int MAX_HEALTH = 40;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.3F;
    private static final int ATTACK_KNOCKBACK = 1;
    private static final float KNOCKBACK_RESISTANCE = 0.6F;
    private static final int ATTACK_DAMAGE = 6;
    private static final float BABY_ATTACK_DAMAGE = 0.5F;
    private static final boolean DEFAULT_IMMUNE_TO_ZOMBIFICATION = false;
    private static final int DEFAULT_TIME_IN_OVERWORLD = 0;
    private static final boolean DEFAULT_CANNOT_BE_HUNTED = false;
    public static final int CONVERSION_TIME = 300;
    private int attackAnimationRemainingTicks;
    public int timeInOverworld = 0;
    public boolean cannotBeHunted = false;
    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super EntityHoglin>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ADULT, SensorType.HOGLIN_SPECIFIC_SENSOR);
    // CraftBukkit - decompile error
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.<MemoryModuleType<?>>of(MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN, new MemoryModuleType[]{MemoryModuleType.AVOID_TARGET, MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_REPELLENT, MemoryModuleType.PACIFIED, MemoryModuleType.IS_PANICKING});

    public EntityHoglin(EntityTypes<? extends EntityHoglin> entitytypes, World world) {
        super(entitytypes, world);
        this.xpReward = 5;
    }

    @VisibleForTesting
    public void setTimeInOverworld(int i) {
        this.timeInOverworld = i;
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MAX_HEALTH, 40.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.KNOCKBACK_RESISTANCE, (double) 0.6F).add(GenericAttributes.ATTACK_KNOCKBACK, 1.0D).add(GenericAttributes.ATTACK_DAMAGE, 6.0D);
    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        if (entity instanceof EntityLiving entityliving) {
            this.attackAnimationRemainingTicks = 10;
            this.level().broadcastEntityEvent(this, (byte) 4);
            this.makeSound(SoundEffects.HOGLIN_ATTACK);
            HoglinAI.onHitTarget(this, entityliving);
            return IOglin.hurtAndThrowTarget(worldserver, this, entityliving);
        } else {
            return false;
        }
    }

    @Override
    protected void blockedByItem(EntityLiving entityliving) {
        if (this.isAdult()) {
            IOglin.throwTarget(this, entityliving);
        }

    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        boolean flag = super.hurtServer(worldserver, damagesource, f);

        if (flag) {
            Entity entity = damagesource.getEntity();

            if (entity instanceof EntityLiving) {
                EntityLiving entityliving = (EntityLiving) entity;

                HoglinAI.wasHurtBy(worldserver, this, entityliving);
            }
        }

        return flag;
    }

    @Override
    protected BehaviorController.b<EntityHoglin> brainProvider() {
        return BehaviorController.<EntityHoglin>provider(EntityHoglin.MEMORY_TYPES, EntityHoglin.SENSOR_TYPES);
    }

    @Override
    protected BehaviorController<?> makeBrain(Dynamic<?> dynamic) {
        return HoglinAI.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public BehaviorController<EntityHoglin> getBrain() {
        return (BehaviorController<EntityHoglin>) super.getBrain(); // CraftBukkit - decompile error
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("hoglinBrain");
        this.getBrain().tick(worldserver, this);
        gameprofilerfiller.pop();
        HoglinAI.updateActivity(this);
        if (this.isConverting()) {
            ++this.timeInOverworld;
            if (this.timeInOverworld > 300) {
                this.makeSound(SoundEffects.HOGLIN_CONVERTED_TO_ZOMBIFIED);
                this.finishConversion();
            }
        } else {
            this.timeInOverworld = 0;
        }

    }

    @Override
    public void aiStep() {
        if (this.attackAnimationRemainingTicks > 0) {
            --this.attackAnimationRemainingTicks;
        }

        super.aiStep();
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.xpReward = 3;
            this.getAttribute(GenericAttributes.ATTACK_DAMAGE).setBaseValue(0.5D);
        } else {
            this.xpReward = 5;
            this.getAttribute(GenericAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
        }

    }

    public static boolean checkHoglinSpawnRules(EntityTypes<EntityHoglin> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return !generatoraccess.getBlockState(blockposition.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        if (worldaccess.getRandom().nextFloat() < 0.2F) {
            this.setBaby(true);
        }

        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isPersistenceRequired();
    }

    @Override
    public float getWalkTargetValue(BlockPosition blockposition, IWorldReader iworldreader) {
        return HoglinAI.isPosNearNearestRepellent(this, blockposition) ? -1.0F : (iworldreader.getBlockState(blockposition.below()).is(Blocks.CRIMSON_NYLIUM) ? 10.0F : 0.0F);
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        EnumInteractionResult enuminteractionresult = super.mobInteract(entityhuman, enumhand);

        if (enuminteractionresult.consumesAction()) {
            this.setPersistenceRequired();
        }

        return enuminteractionresult;
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 4) {
            this.attackAnimationRemainingTicks = 10;
            this.makeSound(SoundEffects.HOGLIN_ATTACK);
        } else {
            super.handleEntityEvent(b0);
        }

    }

    @Override
    public int getAttackAnimationRemainingTicks() {
        return this.attackAnimationRemainingTicks;
    }

    @Override
    public boolean shouldDropExperience() {
        return true;
    }

    @Override
    protected int getBaseExperienceReward(WorldServer worldserver) {
        return this.xpReward;
    }

    private void finishConversion() {
        this.convertTo(EntityTypes.ZOGLIN, ConversionParams.single(this, true, false), (entityzoglin) -> {
            entityzoglin.addEffect(new MobEffect(MobEffects.NAUSEA, 200, 0));
        }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.PIGLIN_ZOMBIFIED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PIGLIN_ZOMBIFIED); // CraftBukkit - add spawn and transform reasons
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.HOGLIN_FOOD);
    }

    public boolean isAdult() {
        return !this.isBaby();
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityHoglin.DATA_IMMUNE_TO_ZOMBIFICATION, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("IsImmuneToZombification", this.isImmuneToZombification());
        valueoutput.putInt("TimeInOverworld", this.timeInOverworld);
        valueoutput.putBoolean("CannotBeHunted", this.cannotBeHunted);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setImmuneToZombification(valueinput.getBooleanOr("IsImmuneToZombification", false));
        this.timeInOverworld = valueinput.getIntOr("TimeInOverworld", 0);
        this.setCannotBeHunted(valueinput.getBooleanOr("CannotBeHunted", false));
    }

    public void setImmuneToZombification(boolean flag) {
        this.getEntityData().set(EntityHoglin.DATA_IMMUNE_TO_ZOMBIFICATION, flag);
    }

    public boolean isImmuneToZombification() {
        return (Boolean) this.getEntityData().get(EntityHoglin.DATA_IMMUNE_TO_ZOMBIFICATION);
    }

    public boolean isConverting() {
        return !this.level().dimensionType().piglinSafe() && !this.isImmuneToZombification() && !this.isNoAi();
    }

    private void setCannotBeHunted(boolean flag) {
        this.cannotBeHunted = flag;
    }

    public boolean canBeHunted() {
        return this.isAdult() && !this.cannotBeHunted;
    }

    @Nullable
    @Override
    public EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityHoglin entityhoglin = EntityTypes.HOGLIN.create(worldserver, EntitySpawnReason.BREEDING);

        if (entityhoglin != null) {
            entityhoglin.setPersistenceRequired();
        }

        return entityhoglin;
    }

    @Override
    public boolean canFallInLove() {
        return !HoglinAI.isPacified(this) && super.canFallInLove();
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.HOSTILE;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return this.level().isClientSide ? null : (SoundEffect) HoglinAI.getSoundForCurrentActivity(this).orElse(null); // CraftBukkit - decompile error
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.HOGLIN_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.HOGLIN_DEATH;
    }

    @Override
    protected SoundEffect getSwimSound() {
        return SoundEffects.HOSTILE_SWIM;
    }

    @Override
    protected SoundEffect getSwimSplashSound() {
        return SoundEffects.HOSTILE_SPLASH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.HOGLIN_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        PacketDebug.sendEntityBrain(this);
    }

    @Nullable
    @Override
    public EntityLiving getTarget() {
        return this.getTargetFromBrain();
    }
}
