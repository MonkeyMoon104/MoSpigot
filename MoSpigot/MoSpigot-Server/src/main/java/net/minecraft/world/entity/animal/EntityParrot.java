package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityCreature;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTameableAnimal;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerMoveFlying;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowEntity;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowOwner;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPerch;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomFly;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSit;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.ai.navigation.NavigationFlying;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.BlockLeaves;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

public class EntityParrot extends EntityPerchable implements EntityBird {

    private static final DataWatcherObject<Integer> DATA_VARIANT_ID = DataWatcher.<Integer>defineId(EntityParrot.class, DataWatcherRegistry.INT);
    private static final Predicate<EntityInsentient> NOT_PARROT_PREDICATE = new Predicate<EntityInsentient>() {
        public boolean test(@Nullable EntityInsentient entityinsentient) {
            return entityinsentient != null && EntityParrot.MOB_SOUND_MAP.containsKey(entityinsentient.getType());
        }
    };
    static final Map<EntityTypes<?>, SoundEffect> MOB_SOUND_MAP = (Map) SystemUtils.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put(EntityTypes.BLAZE, SoundEffects.PARROT_IMITATE_BLAZE);
        hashmap.put(EntityTypes.BOGGED, SoundEffects.PARROT_IMITATE_BOGGED);
        hashmap.put(EntityTypes.BREEZE, SoundEffects.PARROT_IMITATE_BREEZE);
        hashmap.put(EntityTypes.CAVE_SPIDER, SoundEffects.PARROT_IMITATE_SPIDER);
        hashmap.put(EntityTypes.CREAKING, SoundEffects.PARROT_IMITATE_CREAKING);
        hashmap.put(EntityTypes.CREEPER, SoundEffects.PARROT_IMITATE_CREEPER);
        hashmap.put(EntityTypes.DROWNED, SoundEffects.PARROT_IMITATE_DROWNED);
        hashmap.put(EntityTypes.ELDER_GUARDIAN, SoundEffects.PARROT_IMITATE_ELDER_GUARDIAN);
        hashmap.put(EntityTypes.ENDER_DRAGON, SoundEffects.PARROT_IMITATE_ENDER_DRAGON);
        hashmap.put(EntityTypes.ENDERMITE, SoundEffects.PARROT_IMITATE_ENDERMITE);
        hashmap.put(EntityTypes.EVOKER, SoundEffects.PARROT_IMITATE_EVOKER);
        hashmap.put(EntityTypes.GHAST, SoundEffects.PARROT_IMITATE_GHAST);
        hashmap.put(EntityTypes.HAPPY_GHAST, SoundEffects.EMPTY);
        hashmap.put(EntityTypes.GUARDIAN, SoundEffects.PARROT_IMITATE_GUARDIAN);
        hashmap.put(EntityTypes.HOGLIN, SoundEffects.PARROT_IMITATE_HOGLIN);
        hashmap.put(EntityTypes.HUSK, SoundEffects.PARROT_IMITATE_HUSK);
        hashmap.put(EntityTypes.ILLUSIONER, SoundEffects.PARROT_IMITATE_ILLUSIONER);
        hashmap.put(EntityTypes.MAGMA_CUBE, SoundEffects.PARROT_IMITATE_MAGMA_CUBE);
        hashmap.put(EntityTypes.PHANTOM, SoundEffects.PARROT_IMITATE_PHANTOM);
        hashmap.put(EntityTypes.PIGLIN, SoundEffects.PARROT_IMITATE_PIGLIN);
        hashmap.put(EntityTypes.PIGLIN_BRUTE, SoundEffects.PARROT_IMITATE_PIGLIN_BRUTE);
        hashmap.put(EntityTypes.PILLAGER, SoundEffects.PARROT_IMITATE_PILLAGER);
        hashmap.put(EntityTypes.RAVAGER, SoundEffects.PARROT_IMITATE_RAVAGER);
        hashmap.put(EntityTypes.SHULKER, SoundEffects.PARROT_IMITATE_SHULKER);
        hashmap.put(EntityTypes.SILVERFISH, SoundEffects.PARROT_IMITATE_SILVERFISH);
        hashmap.put(EntityTypes.SKELETON, SoundEffects.PARROT_IMITATE_SKELETON);
        hashmap.put(EntityTypes.SLIME, SoundEffects.PARROT_IMITATE_SLIME);
        hashmap.put(EntityTypes.SPIDER, SoundEffects.PARROT_IMITATE_SPIDER);
        hashmap.put(EntityTypes.STRAY, SoundEffects.PARROT_IMITATE_STRAY);
        hashmap.put(EntityTypes.VEX, SoundEffects.PARROT_IMITATE_VEX);
        hashmap.put(EntityTypes.VINDICATOR, SoundEffects.PARROT_IMITATE_VINDICATOR);
        hashmap.put(EntityTypes.WARDEN, SoundEffects.PARROT_IMITATE_WARDEN);
        hashmap.put(EntityTypes.WITCH, SoundEffects.PARROT_IMITATE_WITCH);
        hashmap.put(EntityTypes.WITHER, SoundEffects.PARROT_IMITATE_WITHER);
        hashmap.put(EntityTypes.WITHER_SKELETON, SoundEffects.PARROT_IMITATE_WITHER_SKELETON);
        hashmap.put(EntityTypes.ZOGLIN, SoundEffects.PARROT_IMITATE_ZOGLIN);
        hashmap.put(EntityTypes.ZOMBIE, SoundEffects.PARROT_IMITATE_ZOMBIE);
        hashmap.put(EntityTypes.ZOMBIE_VILLAGER, SoundEffects.PARROT_IMITATE_ZOMBIE_VILLAGER);
    });
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    private float flapping = 1.0F;
    private float nextFlap = 1.0F;
    private boolean partyParrot;
    @Nullable
    private BlockPosition jukebox;

    public EntityParrot(EntityTypes<? extends EntityParrot> entitytypes, World world) {
        super(entitytypes, world);
        this.moveControl = new ControllerMoveFlying(this, 10, false);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        this.setVariant((EntityParrot.Variant) SystemUtils.getRandom(EntityParrot.Variant.values(), worldaccess.getRandom()));
        if (groupdataentity == null) {
            groupdataentity = new EntityAgeable.a(false);
        }

        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EntityTameableAnimal.a(1.25D));
        this.goalSelector.addGoal(0, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(1, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F));
        this.goalSelector.addGoal(2, new PathfinderGoalSit(this));
        this.goalSelector.addGoal(2, new PathfinderGoalFollowOwner(this, 1.0D, 5.0F, 1.0F));
        this.goalSelector.addGoal(2, new EntityParrot.a(this, 1.0D));
        this.goalSelector.addGoal(3, new PathfinderGoalPerch(this));
        this.goalSelector.addGoal(3, new PathfinderGoalFollowEntity(this, 1.0D, 3.0F, 7.0F));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 6.0D).add(GenericAttributes.FLYING_SPEED, (double) 0.4F).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.2F).add(GenericAttributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected NavigationAbstract createNavigation(World world) {
        NavigationFlying navigationflying = new NavigationFlying(this, world);

        navigationflying.setCanOpenDoors(false);
        navigationflying.setCanFloat(true);
        return navigationflying;
    }

    @Override
    public void aiStep() {
        if (this.jukebox == null || !this.jukebox.closerToCenterThan(this.position(), 3.46D) || !this.level().getBlockState(this.jukebox).is(Blocks.JUKEBOX)) {
            this.partyParrot = false;
            this.jukebox = null;
        }

        if (this.level().random.nextInt(400) == 0) {
            imitateNearbyMobs(this.level(), this);
        }

        super.aiStep();
        this.calculateFlapping();
    }

    @Override
    public void setRecordPlayingNearby(BlockPosition blockposition, boolean flag) {
        this.jukebox = blockposition;
        this.partyParrot = flag;
    }

    public boolean isPartyParrot() {
        return this.partyParrot;
    }

    private void calculateFlapping() {
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed += (float) (!this.onGround() && !this.isPassenger() ? 4 : -1) * 0.3F;
        this.flapSpeed = MathHelper.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround() && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping *= 0.9F;
        Vec3D vec3d = this.getDeltaMovement();

        if (!this.onGround() && vec3d.y < 0.0D) {
            this.setDeltaMovement(vec3d.multiply(1.0D, 0.6D, 1.0D));
        }

        this.flap += this.flapping * 2.0F;
    }

    public static boolean imitateNearbyMobs(World world, Entity entity) {
        if (entity.isAlive() && !entity.isSilent() && world.random.nextInt(2) == 0) {
            List<EntityInsentient> list = world.<EntityInsentient>getEntitiesOfClass(EntityInsentient.class, entity.getBoundingBox().inflate(20.0D), EntityParrot.NOT_PARROT_PREDICATE);

            if (!list.isEmpty()) {
                EntityInsentient entityinsentient = (EntityInsentient) list.get(world.random.nextInt(list.size()));

                if (!entityinsentient.isSilent()) {
                    SoundEffect soundeffect = getImitatedSound(entityinsentient.getType());

                    world.playSound((Entity) null, entity.getX(), entity.getY(), entity.getZ(), soundeffect, entity.getSoundSource(), 0.7F, getPitch(world.random));
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (!this.isTame() && itemstack.is(TagsItem.PARROT_FOOD)) {
            this.usePlayerItem(entityhuman, enumhand, itemstack);
            if (!this.isSilent()) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PARROT_EAT, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }

            if (!this.level().isClientSide) {
                if (this.random.nextInt(10) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) { // CraftBukkit
                    this.tame(entityhuman);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }

            return EnumInteractionResult.SUCCESS;
        } else if (!itemstack.is(TagsItem.PARROT_POISONOUS_FOOD)) {
            if (!this.isFlying() && this.isTame() && this.isOwnedBy(entityhuman)) {
                if (!this.level().isClientSide) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                }

                return EnumInteractionResult.SUCCESS;
            } else {
                return super.mobInteract(entityhuman, enumhand);
            }
        } else {
            this.usePlayerItem(entityhuman, enumhand, itemstack);
            this.addEffect(new MobEffect(MobEffects.POISON, 900), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD); // CraftBukkit
            if (entityhuman.isCreative() || !this.isInvulnerable()) {
                this.hurt(this.damageSources().playerAttack(entityhuman), Float.MAX_VALUE);
            }

            return EnumInteractionResult.SUCCESS;
        }
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return false;
    }

    public static boolean checkParrotSpawnRules(EntityTypes<EntityParrot> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.PARROTS_SPAWNABLE_ON) && isBrightEnoughToSpawn(generatoraccess, blockposition);
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, IBlockData iblockdata, BlockPosition blockposition) {}

    @Override
    public boolean canMate(EntityAnimal entityanimal) {
        return false;
    }

    @Nullable
    @Override
    public EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        return null;
    }

    @Nullable
    @Override
    public SoundEffect getAmbientSound() {
        return getAmbient(this.level(), this.level().random);
    }

    public static SoundEffect getAmbient(World world, RandomSource randomsource) {
        if (world.getDifficulty() != EnumDifficulty.PEACEFUL && randomsource.nextInt(1000) == 0) {
            List<EntityTypes<?>> list = Lists.newArrayList(EntityParrot.MOB_SOUND_MAP.keySet());

            return getImitatedSound((EntityTypes) list.get(randomsource.nextInt(list.size())));
        } else {
            return SoundEffects.PARROT_AMBIENT;
        }
    }

    private static SoundEffect getImitatedSound(EntityTypes<?> entitytypes) {
        return (SoundEffect) EntityParrot.MOB_SOUND_MAP.getOrDefault(entitytypes, SoundEffects.PARROT_AMBIENT);
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.PARROT_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.PARROT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.PARROT_STEP, 0.15F, 1.0F);
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.playSound(SoundEffects.PARROT_FLY, 0.15F, 1.0F);
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    @Override
    public float getVoicePitch() {
        return getPitch(this.random);
    }

    public static float getPitch(RandomSource randomsource) {
        return (randomsource.nextFloat() - randomsource.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.NEUTRAL;
    }

    @Override
    public boolean isPushable() {
        return super.isPushable(); // CraftBukkit - collidable API
    }

    @Override
    protected void doPush(Entity entity) {
        if (!(entity instanceof EntityHuman)) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else {
            // CraftBukkit start
            boolean result = super.hurtServer(worldserver, damagesource, f);
            if (!result) {
                return result;
            }
            // CraftBukkit end
            this.setOrderedToSit(false);
            return result; // CraftBukkit
        }
    }

    public EntityParrot.Variant getVariant() {
        return EntityParrot.Variant.byId((Integer) this.entityData.get(EntityParrot.DATA_VARIANT_ID));
    }

    public void setVariant(EntityParrot.Variant entityparrot_variant) {
        this.entityData.set(EntityParrot.DATA_VARIANT_ID, entityparrot_variant.id);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.PARROT_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.PARROT_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.PARROT_VARIANT) {
            this.setVariant((EntityParrot.Variant) castComponentValue(DataComponents.PARROT_VARIANT, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityParrot.DATA_VARIANT_ID, EntityParrot.Variant.DEFAULT.id);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("Variant", EntityParrot.Variant.LEGACY_CODEC, this.getVariant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setVariant((EntityParrot.Variant) valueinput.read("Variant", EntityParrot.Variant.LEGACY_CODEC).orElse(EntityParrot.Variant.DEFAULT));
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected boolean canFlyToOwner() {
        return true;
    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) (0.5F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static enum Variant implements INamable {

        RED_BLUE(0, "red_blue"), BLUE(1, "blue"), GREEN(2, "green"), YELLOW_BLUE(3, "yellow_blue"), GRAY(4, "gray");

        public static final EntityParrot.Variant DEFAULT = EntityParrot.Variant.RED_BLUE;
        private static final IntFunction<EntityParrot.Variant> BY_ID = ByIdMap.<EntityParrot.Variant>continuous(EntityParrot.Variant::getId, values(), ByIdMap.a.CLAMP);
        public static final Codec<EntityParrot.Variant> CODEC = INamable.<EntityParrot.Variant>fromEnum(EntityParrot.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<EntityParrot.Variant> LEGACY_CODEC;
        public static final StreamCodec<ByteBuf, EntityParrot.Variant> STREAM_CODEC;
        final int id;
        private final String name;

        private Variant(final int i, final String s) {
            this.id = i;
            this.name = s;
        }

        public int getId() {
            return this.id;
        }

        public static EntityParrot.Variant byId(int i) {
            return (EntityParrot.Variant) EntityParrot.Variant.BY_ID.apply(i);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        static {
            PrimitiveCodec<Integer> primitivecodec = Codec.INT; // CraftBukkit - decompile error
            IntFunction<EntityParrot.Variant> intfunction = EntityParrot.Variant.BY_ID; // CraftBukkit - decompile error

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, EntityParrot.Variant::getId);
            STREAM_CODEC = ByteBufCodecs.idMapper(EntityParrot.Variant.BY_ID, EntityParrot.Variant::getId);
        }
    }

    private static class a extends PathfinderGoalRandomFly {

        public a(EntityCreature entitycreature, double d0) {
            super(entitycreature, d0);
        }

        @Nullable
        @Override
        protected Vec3D getPosition() {
            Vec3D vec3d = null;

            if (this.mob.isInWater()) {
                vec3d = LandRandomPos.getPos(this.mob, 15, 15);
            }

            if (this.mob.getRandom().nextFloat() >= this.probability) {
                vec3d = this.getTreePos();
            }

            return vec3d == null ? super.getPosition() : vec3d;
        }

        @Nullable
        private Vec3D getTreePos() {
            BlockPosition blockposition = this.mob.blockPosition();
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition1 = new BlockPosition.MutableBlockPosition();

            for (BlockPosition blockposition1 : BlockPosition.betweenClosed(MathHelper.floor(this.mob.getX() - 3.0D), MathHelper.floor(this.mob.getY() - 6.0D), MathHelper.floor(this.mob.getZ() - 3.0D), MathHelper.floor(this.mob.getX() + 3.0D), MathHelper.floor(this.mob.getY() + 6.0D), MathHelper.floor(this.mob.getZ() + 3.0D))) {
                if (!blockposition.equals(blockposition1)) {
                    IBlockData iblockdata = this.mob.level().getBlockState(blockposition_mutableblockposition1.setWithOffset(blockposition1, EnumDirection.DOWN));
                    boolean flag = iblockdata.getBlock() instanceof BlockLeaves || iblockdata.is(TagsBlock.LOGS);

                    if (flag && this.mob.level().isEmptyBlock(blockposition1) && this.mob.level().isEmptyBlock(blockposition_mutableblockposition.setWithOffset(blockposition1, EnumDirection.UP))) {
                        return Vec3D.atBottomCenterOf(blockposition1);
                    }
                }
            }

            return null;
        }
    }
}
