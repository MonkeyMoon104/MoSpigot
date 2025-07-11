package net.minecraft.world.entity.animal.axolotl;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.BinaryAnimator;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.BehaviorController;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemLiquidUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

public class Axolotl extends EntityAnimal implements Bucketable {

    // CraftBukkit start - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    @Override
    public int getDefaultMaxAirSupply() {
        return AXOLOTL_TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end
    public static final int TOTAL_PLAYDEAD_TIME = 200;
    private static final int POSE_ANIMATION_TICKS = 10;
    protected static final ImmutableList<? extends SensorType<? extends Sensor<? super Axolotl>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_ADULT, SensorType.HURT_BY, SensorType.AXOLOTL_ATTACKABLES, SensorType.AXOLOTL_TEMPTATIONS);
    // CraftBukkit - decompile error
    protected static final ImmutableList<? extends MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.<MemoryModuleType<?>>of(MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.NEAREST_VISIBLE_ADULT, new MemoryModuleType[]{MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.PLAY_DEAD_TICKS, MemoryModuleType.NEAREST_ATTACKABLE, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.IS_TEMPTED, MemoryModuleType.HAS_HUNTING_COOLDOWN, MemoryModuleType.IS_PANICKING});
    private static final DataWatcherObject<Integer> DATA_VARIANT = DataWatcher.<Integer>defineId(Axolotl.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Boolean> DATA_PLAYING_DEAD = DataWatcher.<Boolean>defineId(Axolotl.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Boolean> FROM_BUCKET = DataWatcher.<Boolean>defineId(Axolotl.class, DataWatcherRegistry.BOOLEAN);
    public static final double PLAYER_REGEN_DETECTION_RANGE = 20.0D;
    public static final int RARE_VARIANT_CHANCE = 1200;
    private static final int AXOLOTL_TOTAL_AIR_SUPPLY = 6000;
    public static final String VARIANT_TAG = "Variant";
    private static final int REHYDRATE_AIR_SUPPLY = 1800;
    private static final int REGEN_BUFF_MAX_DURATION = 2400;
    private static final boolean DEFAULT_FROM_BUCKET = false;
    public final BinaryAnimator playingDeadAnimator = new BinaryAnimator(10, MathHelper::easeInOutSine);
    public final BinaryAnimator inWaterAnimator = new BinaryAnimator(10, MathHelper::easeInOutSine);
    public final BinaryAnimator onGroundAnimator = new BinaryAnimator(10, MathHelper::easeInOutSine);
    public final BinaryAnimator movingAnimator = new BinaryAnimator(10, MathHelper::easeInOutSine);
    private static final int REGEN_BUFF_BASE_DURATION = 100;

    public Axolotl(EntityTypes<? extends Axolotl> entitytypes, World world) {
        super(entitytypes, world);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.moveControl = new Axolotl.d(this);
        this.lookControl = new Axolotl.c(this, 20);
    }

    @Override
    public float getWalkTargetValue(BlockPosition blockposition, IWorldReader iworldreader) {
        return 0.0F;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(Axolotl.DATA_VARIANT, 0);
        datawatcher_a.define(Axolotl.DATA_PLAYING_DEAD, false);
        datawatcher_a.define(Axolotl.FROM_BUCKET, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("Variant", Axolotl.Variant.LEGACY_CODEC, this.getVariant());
        valueoutput.putBoolean("FromBucket", this.fromBucket());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setVariant((Axolotl.Variant) valueinput.read("Variant", Axolotl.Variant.LEGACY_CODEC).orElse(Axolotl.Variant.DEFAULT));
        this.setFromBucket(valueinput.getBooleanOr("FromBucket", false));
    }

    @Override
    public void playAmbientSound() {
        if (!this.isPlayingDead()) {
            super.playAmbientSound();
        }
    }

    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        boolean flag = false;

        if (entityspawnreason == EntitySpawnReason.BUCKET) {
            return groupdataentity;
        } else {
            RandomSource randomsource = worldaccess.getRandom();

            if (groupdataentity instanceof Axolotl.b) {
                if (((Axolotl.b) groupdataentity).getGroupSize() >= 2) {
                    flag = true;
                }
            } else {
                groupdataentity = new Axolotl.b(new Axolotl.Variant[]{Axolotl.Variant.getCommonSpawnVariant(randomsource), Axolotl.Variant.getCommonSpawnVariant(randomsource)});
            }

            this.setVariant(((Axolotl.b) groupdataentity).getVariant(randomsource));
            if (flag) {
                this.setAge(-24000);
            }

            return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
        }
    }

    @Override
    public void baseTick() {
        int i = this.getAirSupply();

        super.baseTick();
        if (!this.isNoAi()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                this.handleAirSupply(worldserver, i);
            }
        }

        if (this.level().isClientSide()) {
            this.tickAnimations();
        }

    }

    private void tickAnimations() {
        Axolotl.a axolotl_a;

        if (this.isPlayingDead()) {
            axolotl_a = Axolotl.a.PLAYING_DEAD;
        } else if (this.isInWater()) {
            axolotl_a = Axolotl.a.IN_WATER;
        } else if (this.onGround()) {
            axolotl_a = Axolotl.a.ON_GROUND;
        } else {
            axolotl_a = Axolotl.a.IN_AIR;
        }

        this.playingDeadAnimator.tick(axolotl_a == Axolotl.a.PLAYING_DEAD);
        this.inWaterAnimator.tick(axolotl_a == Axolotl.a.IN_WATER);
        this.onGroundAnimator.tick(axolotl_a == Axolotl.a.ON_GROUND);
        boolean flag = this.walkAnimation.isMoving() || this.getXRot() != this.xRotO || this.getYRot() != this.yRotO;

        this.movingAnimator.tick(flag);
    }

    protected void handleAirSupply(WorldServer worldserver, int i) {
        if (this.isAlive() && !this.isInWaterOrRain()) {
            this.setAirSupply(i - 1);
            if (this.getAirSupply() == -20) {
                this.setAirSupply(0);
                this.hurtServer(worldserver, this.damageSources().dryOut(), 2.0F);
            }
        } else {
            this.setAirSupply(this.getMaxAirSupply());
        }

    }

    public void rehydrate() {
        int i = this.getAirSupply() + 1800;

        this.setAirSupply(Math.min(i, this.getMaxAirSupply()));
    }

    @Override
    public int getMaxAirSupply() {
        return maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }

    public Axolotl.Variant getVariant() {
        return Axolotl.Variant.byId((Integer) this.entityData.get(Axolotl.DATA_VARIANT));
    }

    public void setVariant(Axolotl.Variant axolotl_variant) {
        this.entityData.set(Axolotl.DATA_VARIANT, axolotl_variant.getId());
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.AXOLOTL_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.AXOLOTL_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.AXOLOTL_VARIANT) {
            this.setVariant((Axolotl.Variant) castComponentValue(DataComponents.AXOLOTL_VARIANT, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    private static boolean useRareVariant(RandomSource randomsource) {
        return randomsource.nextInt(1200) == 0;
    }

    @Override
    public boolean checkSpawnObstruction(IWorldReader iworldreader) {
        return iworldreader.isUnobstructed(this);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    public void setPlayingDead(boolean flag) {
        this.entityData.set(Axolotl.DATA_PLAYING_DEAD, flag);
    }

    public boolean isPlayingDead() {
        return (Boolean) this.entityData.get(Axolotl.DATA_PLAYING_DEAD);
    }

    @Override
    public boolean fromBucket() {
        return (Boolean) this.entityData.get(Axolotl.FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean flag) {
        this.entityData.set(Axolotl.FROM_BUCKET, flag);
    }

    @Nullable
    @Override
    public EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        Axolotl axolotl = EntityTypes.AXOLOTL.create(worldserver, EntitySpawnReason.BREEDING);

        if (axolotl != null) {
            Axolotl.Variant axolotl_variant;

            if (useRareVariant(this.random)) {
                axolotl_variant = Axolotl.Variant.getRareSpawnVariant(this.random);
            } else {
                axolotl_variant = this.random.nextBoolean() ? this.getVariant() : ((Axolotl) entityageable).getVariant();
            }

            axolotl.setVariant(axolotl_variant);
            axolotl.setPersistenceRequired();
        }

        return axolotl;
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.AXOLOTL_FOOD);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("axolotlBrain");
        this.getBrain().tick(worldserver, this);
        gameprofilerfiller.pop();
        gameprofilerfiller.push("axolotlActivityUpdate");
        AxolotlAi.updateActivity(this);
        gameprofilerfiller.pop();
        if (!this.isNoAi()) {
            Optional<Integer> optional = this.getBrain().<Integer>getMemory(MemoryModuleType.PLAY_DEAD_TICKS);

            this.setPlayingDead(optional.isPresent() && (Integer) optional.get() > 0);
        }

    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 14.0D).add(GenericAttributes.MOVEMENT_SPEED, 1.0D).add(GenericAttributes.ATTACK_DAMAGE, 2.0D).add(GenericAttributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected NavigationAbstract createNavigation(World world) {
        return new AmphibiousPathNavigation(this, world);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEffects.AXOLOTL_ATTACK, 1.0F, 1.0F);
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        float f1 = this.getHealth();

        if (!this.isNoAi() && this.level().random.nextInt(3) == 0 && ((float) this.level().random.nextInt(3) < f || f1 / this.getMaxHealth() < 0.5F) && f < f1 && this.isInWater() && (damagesource.getEntity() != null || damagesource.getDirectEntity() != null) && !this.isPlayingDead()) {
            this.brain.setMemory(MemoryModuleType.PLAY_DEAD_TICKS, 200);
        }

        return super.hurtServer(worldserver, damagesource, f);
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
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        return (EnumInteractionResult) Bucketable.bucketMobPickup(entityhuman, enumhand, this).orElse(super.mobInteract(entityhuman, enumhand));
    }

    @Override
    public void saveToBucketTag(ItemStack itemstack) {
        Bucketable.saveDefaultDataToBucketTag(this, itemstack);
        itemstack.copyFrom(DataComponents.AXOLOTL_VARIANT, this);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, itemstack, (nbttagcompound) -> {
            nbttagcompound.putInt("Age", this.getAge());
            BehaviorController<?> behaviorcontroller = this.getBrain();

            if (behaviorcontroller.hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
                nbttagcompound.putLong("HuntingCooldown", behaviorcontroller.getTimeUntilExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN));
            }

        });
    }

    @Override
    public void loadFromBucketTag(NBTTagCompound nbttagcompound) {
        Bucketable.loadDefaultDataFromBucketTag(this, nbttagcompound);
        this.setAge(nbttagcompound.getIntOr("Age", 0));
        nbttagcompound.getLong("HuntingCooldown").ifPresentOrElse((olong) -> {
            this.getBrain().setMemoryWithExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN, true, nbttagcompound.getLongOr("HuntingCooldown", 0L));
        }, () -> {
            this.getBrain().setMemory(MemoryModuleType.HAS_HUNTING_COOLDOWN, Optional.empty());
        });
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.AXOLOTL_BUCKET);
    }

    @Override
    public SoundEffect getPickupSound() {
        return SoundEffects.BUCKET_FILL_AXOLOTL;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.isPlayingDead() && super.canBeSeenAsEnemy();
    }

    public static void onStopAttacking(WorldServer worldserver, Axolotl axolotl, EntityLiving entityliving) {
        if (entityliving.isDeadOrDying()) {
            DamageSource damagesource = entityliving.getLastDamageSource();

            if (damagesource != null) {
                Entity entity = damagesource.getEntity();

                if (entity != null && entity.getType() == EntityTypes.PLAYER) {
                    EntityHuman entityhuman = (EntityHuman) entity;
                    List<EntityHuman> list = worldserver.<EntityHuman>getEntitiesOfClass(EntityHuman.class, axolotl.getBoundingBox().inflate(20.0D));

                    if (list.contains(entityhuman)) {
                        axolotl.applySupportingEffects(entityhuman);
                    }
                }
            }
        }

    }

    public void applySupportingEffects(EntityHuman entityhuman) {
        MobEffect mobeffect = entityhuman.getEffect(MobEffects.REGENERATION);

        if (mobeffect == null || mobeffect.endsWithin(2399)) {
            int i = mobeffect != null ? mobeffect.getDuration() : 0;
            int j = Math.min(2400, 100 + i);

            entityhuman.addEffect(new MobEffect(MobEffects.REGENERATION, j, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.AXOLOTL); // CraftBukkit
        }

        entityhuman.removeEffect(MobEffects.MINING_FATIGUE);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.fromBucket();
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.AXOLOTL_HURT;
    }

    @Nullable
    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.AXOLOTL_DEATH;
    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return this.isInWater() ? SoundEffects.AXOLOTL_IDLE_WATER : SoundEffects.AXOLOTL_IDLE_AIR;
    }

    @Override
    protected SoundEffect getSwimSplashSound() {
        return SoundEffects.AXOLOTL_SPLASH;
    }

    @Override
    protected SoundEffect getSwimSound() {
        return SoundEffects.AXOLOTL_SWIM;
    }

    @Override
    protected BehaviorController.b<Axolotl> brainProvider() {
        return BehaviorController.<Axolotl>provider(Axolotl.MEMORY_TYPES, Axolotl.SENSOR_TYPES);
    }

    @Override
    protected BehaviorController<?> makeBrain(Dynamic<?> dynamic) {
        return AxolotlAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public BehaviorController<Axolotl> getBrain() {
        return (BehaviorController<Axolotl>) super.getBrain(); // CraftBukkit - decompile error
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        PacketDebug.sendEntityBrain(this);
    }

    @Override
    public void travel(Vec3D vec3d) {
        if (this.isInWater()) {
            this.moveRelative(this.getSpeed(), vec3d);
            this.move(EnumMoveType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
        } else {
            super.travel(vec3d);
        }

    }

    @Override
    protected void usePlayerItem(EntityHuman entityhuman, EnumHand enumhand, ItemStack itemstack) {
        if (itemstack.is(Items.TROPICAL_FISH_BUCKET)) {
            entityhuman.setItemInHand(enumhand, ItemLiquidUtil.createFilledResult(itemstack, entityhuman, new ItemStack(Items.WATER_BUCKET)));
        } else {
            super.usePlayerItem(entityhuman, enumhand, itemstack);
        }

    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.fromBucket() && !this.hasCustomName();
    }

    @Nullable
    @Override
    public EntityLiving getTarget() {
        return this.getTargetFromBrain();
    }

    public static boolean checkAxolotlSpawnRules(EntityTypes<? extends EntityLiving> entitytypes, WorldAccess worldaccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return worldaccess.getBlockState(blockposition.below()).is(TagsBlock.AXOLOTLS_SPAWNABLE_ON);
    }

    public static enum Variant implements INamable {

        LUCY(0, "lucy", true), WILD(1, "wild", true), GOLD(2, "gold", true), CYAN(3, "cyan", true), BLUE(4, "blue", false);

        public static final Axolotl.Variant DEFAULT = Axolotl.Variant.LUCY;
        private static final IntFunction<Axolotl.Variant> BY_ID = ByIdMap.<Axolotl.Variant>continuous(Axolotl.Variant::getId, values(), ByIdMap.a.ZERO);
        public static final StreamCodec<ByteBuf, Axolotl.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(Axolotl.Variant.BY_ID, Axolotl.Variant::getId);
        public static final Codec<Axolotl.Variant> CODEC = INamable.<Axolotl.Variant>fromEnum(Axolotl.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<Axolotl.Variant> LEGACY_CODEC;
        private final int id;
        private final String name;
        private final boolean common;

        private Variant(final int i, final String s, final boolean flag) {
            this.id = i;
            this.name = s;
            this.common = flag;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static Axolotl.Variant byId(int i) {
            return (Axolotl.Variant) Axolotl.Variant.BY_ID.apply(i);
        }

        public static Axolotl.Variant getCommonSpawnVariant(RandomSource randomsource) {
            return getSpawnVariant(randomsource, true);
        }

        public static Axolotl.Variant getRareSpawnVariant(RandomSource randomsource) {
            return getSpawnVariant(randomsource, false);
        }

        private static Axolotl.Variant getSpawnVariant(RandomSource randomsource, boolean flag) {
            Axolotl.Variant[] aaxolotl_variant = (Axolotl.Variant[]) Arrays.stream(values()).filter((axolotl_variant) -> {
                return axolotl_variant.common == flag;
            }).toArray((i) -> {
                return new Axolotl.Variant[i];
            });

            return (Axolotl.Variant) SystemUtils.getRandom(aaxolotl_variant, randomsource);
        }

        static {
            PrimitiveCodec<Integer> primitivecodec = Codec.INT; // CraftBukkit - decompile error
            IntFunction<Axolotl.Variant> intfunction = Axolotl.Variant.BY_ID; // CraftBukkit - decompile error

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, Axolotl.Variant::getId);
        }
    }

    private static class d extends SmoothSwimmingMoveControl {

        private final Axolotl axolotl;

        public d(Axolotl axolotl) {
            super(axolotl, 85, 10, 0.1F, 0.5F, false);
            this.axolotl = axolotl;
        }

        @Override
        public void tick() {
            if (!this.axolotl.isPlayingDead()) {
                super.tick();
            }

        }
    }

    private class c extends SmoothSwimmingLookControl {

        public c(final Axolotl axolotl, final int i) {
            super(axolotl, i);
        }

        @Override
        public void tick() {
            if (!Axolotl.this.isPlayingDead()) {
                super.tick();
            }

        }
    }

    public static class b extends EntityAgeable.a {

        public final Axolotl.Variant[] types;

        public b(Axolotl.Variant... aaxolotl_variant) {
            super(false);
            this.types = aaxolotl_variant;
        }

        public Axolotl.Variant getVariant(RandomSource randomsource) {
            return this.types[randomsource.nextInt(this.types.length)];
        }
    }

    public static enum a {

        PLAYING_DEAD, IN_WATER, ON_GROUND, IN_AIR;

        private a() {}
    }
}
