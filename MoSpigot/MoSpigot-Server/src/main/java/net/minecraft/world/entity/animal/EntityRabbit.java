package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerJump;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalGotoTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.wolf.EntityWolf;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockCarrots;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EntityRabbit extends EntityAnimal {

    public static final double STROLL_SPEED_MOD = 0.6D;
    public static final double BREED_SPEED_MOD = 0.8D;
    public static final double FOLLOW_SPEED_MOD = 1.0D;
    public static final double FLEE_SPEED_MOD = 2.2D;
    public static final double ATTACK_SPEED_MOD = 1.4D;
    private static final DataWatcherObject<Integer> DATA_TYPE_ID = DataWatcher.<Integer>defineId(EntityRabbit.class, DataWatcherRegistry.INT);
    private static final int DEFAULT_MORE_CARROT_TICKS = 0;
    private static final MinecraftKey KILLER_BUNNY = MinecraftKey.withDefaultNamespace("killer_bunny");
    private static final int DEFAULT_ATTACK_POWER = 3;
    private static final int EVIL_ATTACK_POWER_INCREMENT = 5;
    private static final MinecraftKey EVIL_ATTACK_POWER_MODIFIER = MinecraftKey.withDefaultNamespace("evil");
    private static final int EVIL_ARMOR_VALUE = 8;
    private static final int MORE_CARROTS_DELAY = 40;
    private int jumpTicks;
    private int jumpDuration;
    private boolean wasOnGround;
    private int jumpDelayTicks;
    int moreCarrotTicks = 0;

    public EntityRabbit(EntityTypes<? extends EntityRabbit> entitytypes, World world) {
        super(entitytypes, world);
        this.jumpControl = new EntityRabbit.ControllerJumpRabbit(this);
        this.moveControl = new EntityRabbit.ControllerMoveRabbit(this);
    }

    @Override
    public void registerGoals() {
        this.goalSelector.addGoal(1, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new EntityRabbit.PathfinderGoalRabbitPanic(this, 2.2D));
        this.goalSelector.addGoal(2, new PathfinderGoalBreed(this, 0.8D));
        this.goalSelector.addGoal(3, new PathfinderGoalTempt(this, 1.0D, (itemstack) -> {
            return itemstack.is(TagsItem.RABBIT_FOOD);
        }, false));
        this.goalSelector.addGoal(4, new EntityRabbit.PathfinderGoalRabbitAvoidTarget(this, EntityHuman.class, 8.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(4, new EntityRabbit.PathfinderGoalRabbitAvoidTarget(this, EntityWolf.class, 10.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(4, new EntityRabbit.PathfinderGoalRabbitAvoidTarget(this, EntityMonster.class, 4.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(5, new EntityRabbit.PathfinderGoalEatCarrots(this));
        this.goalSelector.addGoal(6, new PathfinderGoalRandomStrollLand(this, 0.6D));
        this.goalSelector.addGoal(11, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 10.0F));
    }

    @Override
    protected float getJumpPower() {
        float f = 0.3F;

        if (this.moveControl.getSpeedModifier() <= 0.6D) {
            f = 0.2F;
        }

        PathEntity pathentity = this.navigation.getPath();

        if (pathentity != null && !pathentity.isDone()) {
            Vec3D vec3d = pathentity.getNextEntityPos(this);

            if (vec3d.y > this.getY() + 0.5D) {
                f = 0.5F;
            }
        }

        if (this.horizontalCollision || this.jumping && this.moveControl.getWantedY() > this.getY() + 0.5D) {
            f = 0.5F;
        }

        return super.getJumpPower(f / 0.42F);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        double d0 = this.moveControl.getSpeedModifier();

        if (d0 > 0.0D) {
            double d1 = this.getDeltaMovement().horizontalDistanceSqr();

            if (d1 < 0.01D) {
                this.moveRelative(0.1F, new Vec3D(0.0D, 0.0D, 1.0D));
            }
        }

        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte) 1);
        }

    }

    public float getJumpCompletion(float f) {
        return this.jumpDuration == 0 ? 0.0F : ((float) this.jumpTicks + f) / (float) this.jumpDuration;
    }

    public void setSpeedModifier(double d0) {
        this.getNavigation().setSpeedModifier(d0);
        this.moveControl.setWantedPosition(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ(), d0);
    }

    @Override
    public void setJumping(boolean flag) {
        super.setJumping(flag);
        if (flag) {
            this.playSound(this.getJumpSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
        }

    }

    public void startJumping() {
        this.setJumping(true);
        this.jumpDuration = 10;
        this.jumpTicks = 0;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityRabbit.DATA_TYPE_ID, EntityRabbit.Variant.DEFAULT.id);
    }

    @Override
    public void customServerAiStep(WorldServer worldserver) {
        if (this.jumpDelayTicks > 0) {
            --this.jumpDelayTicks;
        }

        if (this.moreCarrotTicks > 0) {
            this.moreCarrotTicks -= this.random.nextInt(3);
            if (this.moreCarrotTicks < 0) {
                this.moreCarrotTicks = 0;
            }
        }

        if (this.onGround()) {
            if (!this.wasOnGround) {
                this.setJumping(false);
                this.checkLandingDelay();
            }

            if (this.getVariant() == EntityRabbit.Variant.EVIL && this.jumpDelayTicks == 0) {
                EntityLiving entityliving = this.getTarget();

                if (entityliving != null && this.distanceToSqr((Entity) entityliving) < 16.0D) {
                    this.facePoint(entityliving.getX(), entityliving.getZ());
                    this.moveControl.setWantedPosition(entityliving.getX(), entityliving.getY(), entityliving.getZ(), this.moveControl.getSpeedModifier());
                    this.startJumping();
                    this.wasOnGround = true;
                }
            }

            EntityRabbit.ControllerJumpRabbit entityrabbit_controllerjumprabbit = (EntityRabbit.ControllerJumpRabbit) this.jumpControl;

            if (!entityrabbit_controllerjumprabbit.wantJump()) {
                if (this.moveControl.hasWanted() && this.jumpDelayTicks == 0) {
                    PathEntity pathentity = this.navigation.getPath();
                    Vec3D vec3d = new Vec3D(this.moveControl.getWantedX(), this.moveControl.getWantedY(), this.moveControl.getWantedZ());

                    if (pathentity != null && !pathentity.isDone()) {
                        vec3d = pathentity.getNextEntityPos(this);
                    }

                    this.facePoint(vec3d.x, vec3d.z);
                    this.startJumping();
                }
            } else if (!entityrabbit_controllerjumprabbit.canJump()) {
                this.enableJumpControl();
            }
        }

        this.wasOnGround = this.onGround();
    }

    @Override
    public boolean canSpawnSprintParticle() {
        return false;
    }

    private void facePoint(double d0, double d1) {
        this.setYRot((float) (MathHelper.atan2(d1 - this.getZ(), d0 - this.getX()) * (double) (180F / (float) Math.PI)) - 90.0F);
    }

    private void enableJumpControl() {
        ((EntityRabbit.ControllerJumpRabbit) this.jumpControl).setCanJump(true);
    }

    private void disableJumpControl() {
        ((EntityRabbit.ControllerJumpRabbit) this.jumpControl).setCanJump(false);
    }

    private void setLandingDelay() {
        if (this.moveControl.getSpeedModifier() < 2.2D) {
            this.jumpDelayTicks = 10;
        } else {
            this.jumpDelayTicks = 1;
        }

    }

    private void checkLandingDelay() {
        this.setLandingDelay();
        this.disableJumpControl();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.jumpTicks != this.jumpDuration) {
            ++this.jumpTicks;
        } else if (this.jumpDuration != 0) {
            this.jumpTicks = 0;
            this.jumpDuration = 0;
            this.setJumping(false);
        }

    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 3.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("RabbitType", EntityRabbit.Variant.LEGACY_CODEC, this.getVariant());
        valueoutput.putInt("MoreCarrotTicks", this.moreCarrotTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setVariant((EntityRabbit.Variant) valueinput.read("RabbitType", EntityRabbit.Variant.LEGACY_CODEC).orElse(EntityRabbit.Variant.DEFAULT));
        this.moreCarrotTicks = valueinput.getIntOr("MoreCarrotTicks", 0);
    }

    protected SoundEffect getJumpSound() {
        return SoundEffects.RABBIT_JUMP;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.RABBIT_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.RABBIT_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.RABBIT_DEATH;
    }

    @Override
    public void playAttackSound() {
        if (this.getVariant() == EntityRabbit.Variant.EVIL) {
            this.playSound(SoundEffects.RABBIT_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

    }

    @Override
    public SoundCategory getSoundSource() {
        return this.getVariant() == EntityRabbit.Variant.EVIL ? SoundCategory.HOSTILE : SoundCategory.NEUTRAL;
    }

    @Nullable
    @Override
    public EntityRabbit getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityRabbit entityrabbit = EntityTypes.RABBIT.create(worldserver, EntitySpawnReason.BREEDING);

        if (entityrabbit != null) {
            EntityRabbit.Variant entityrabbit_variant = getRandomRabbitVariant(worldserver, this.blockPosition());

            if (this.random.nextInt(20) != 0) {
                label22:
                {
                    if (entityageable instanceof EntityRabbit) {
                        EntityRabbit entityrabbit1 = (EntityRabbit) entityageable;

                        if (this.random.nextBoolean()) {
                            entityrabbit_variant = entityrabbit1.getVariant();
                            break label22;
                        }
                    }

                    entityrabbit_variant = this.getVariant();
                }
            }

            entityrabbit.setVariant(entityrabbit_variant);
        }

        return entityrabbit;
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.RABBIT_FOOD);
    }

    public EntityRabbit.Variant getVariant() {
        return EntityRabbit.Variant.byId((Integer) this.entityData.get(EntityRabbit.DATA_TYPE_ID));
    }

    public void setVariant(EntityRabbit.Variant entityrabbit_variant) {
        if (entityrabbit_variant == EntityRabbit.Variant.EVIL) {
            this.getAttribute(GenericAttributes.ARMOR).setBaseValue(8.0D);
            this.goalSelector.addGoal(4, new PathfinderGoalMeleeAttack(this, 1.4D, true));
            this.targetSelector.addGoal(1, (new PathfinderGoalHurtByTarget(this, new Class[0])).setAlertOthers());
            this.targetSelector.addGoal(2, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, true));
            this.targetSelector.addGoal(2, new PathfinderGoalNearestAttackableTarget(this, EntityWolf.class, true));
            this.getAttribute(GenericAttributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(new AttributeModifier(EntityRabbit.EVIL_ATTACK_POWER_MODIFIER, 5.0D, AttributeModifier.Operation.ADD_VALUE));
            if (!this.hasCustomName()) {
                this.setCustomName(IChatBaseComponent.translatable(SystemUtils.makeDescriptionId("entity", EntityRabbit.KILLER_BUNNY)));
            }
        } else {
            this.getAttribute(GenericAttributes.ATTACK_DAMAGE).removeModifier(EntityRabbit.EVIL_ATTACK_POWER_MODIFIER);
        }

        this.entityData.set(EntityRabbit.DATA_TYPE_ID, entityrabbit_variant.id);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.RABBIT_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.RABBIT_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.RABBIT_VARIANT) {
            this.setVariant((EntityRabbit.Variant) castComponentValue(DataComponents.RABBIT_VARIANT, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        EntityRabbit.Variant entityrabbit_variant = getRandomRabbitVariant(worldaccess, this.blockPosition());

        if (groupdataentity instanceof EntityRabbit.GroupDataRabbit) {
            entityrabbit_variant = ((EntityRabbit.GroupDataRabbit) groupdataentity).variant;
        } else {
            groupdataentity = new EntityRabbit.GroupDataRabbit(entityrabbit_variant);
        }

        this.setVariant(entityrabbit_variant);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    private static EntityRabbit.Variant getRandomRabbitVariant(GeneratorAccess generatoraccess, BlockPosition blockposition) {
        Holder<BiomeBase> holder = generatoraccess.getBiome(blockposition);
        int i = generatoraccess.getRandom().nextInt(100);

        return holder.is(BiomeTags.SPAWNS_WHITE_RABBITS) ? (i < 80 ? EntityRabbit.Variant.WHITE : EntityRabbit.Variant.WHITE_SPLOTCHED) : (holder.is(BiomeTags.SPAWNS_GOLD_RABBITS) ? EntityRabbit.Variant.GOLD : (i < 50 ? EntityRabbit.Variant.BROWN : (i < 90 ? EntityRabbit.Variant.SALT : EntityRabbit.Variant.BLACK)));
    }

    public static boolean checkRabbitSpawnRules(EntityTypes<EntityRabbit> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.RABBITS_SPAWNABLE_ON) && isBrightEnoughToSpawn(generatoraccess, blockposition);
    }

    boolean wantsMoreFood() {
        return this.moreCarrotTicks <= 0;
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 1) {
            this.spawnSprintParticle();
            this.jumpDuration = 10;
            this.jumpTicks = 0;
        } else {
            super.handleEntityEvent(b0);
        }

    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) (0.6F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static enum Variant implements INamable {

        BROWN(0, "brown"), WHITE(1, "white"), BLACK(2, "black"), WHITE_SPLOTCHED(3, "white_splotched"), GOLD(4, "gold"), SALT(5, "salt"), EVIL(99, "evil");

        public static final EntityRabbit.Variant DEFAULT = EntityRabbit.Variant.BROWN;
        private static final IntFunction<EntityRabbit.Variant> BY_ID = ByIdMap.<EntityRabbit.Variant>sparse(EntityRabbit.Variant::id, values(), EntityRabbit.Variant.DEFAULT);
        public static final Codec<EntityRabbit.Variant> CODEC = INamable.<EntityRabbit.Variant>fromEnum(EntityRabbit.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<EntityRabbit.Variant> LEGACY_CODEC;
        public static final StreamCodec<ByteBuf, EntityRabbit.Variant> STREAM_CODEC;
        final int id;
        private final String name;

        private Variant(final int i, final String s) {
            this.id = i;
            this.name = s;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int id() {
            return this.id;
        }

        public static EntityRabbit.Variant byId(int i) {
            return (EntityRabbit.Variant) EntityRabbit.Variant.BY_ID.apply(i);
        }

        static {
            PrimitiveCodec<Integer> primitivecodec = Codec.INT; // CraftBukkit - decompile error
            IntFunction<EntityRabbit.Variant> intfunction = EntityRabbit.Variant.BY_ID; // CraftBukkit - decompile error

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, EntityRabbit.Variant::id);
            STREAM_CODEC = ByteBufCodecs.idMapper(EntityRabbit.Variant.BY_ID, EntityRabbit.Variant::id);
        }
    }

    public static class GroupDataRabbit extends EntityAgeable.a {

        public final EntityRabbit.Variant variant;

        public GroupDataRabbit(EntityRabbit.Variant entityrabbit_variant) {
            super(1.0F);
            this.variant = entityrabbit_variant;
        }
    }

    public static class ControllerJumpRabbit extends ControllerJump {

        private final EntityRabbit rabbit;
        private boolean canJump;

        public ControllerJumpRabbit(EntityRabbit entityrabbit) {
            super(entityrabbit);
            this.rabbit = entityrabbit;
        }

        public boolean wantJump() {
            return this.jump;
        }

        public boolean canJump() {
            return this.canJump;
        }

        public void setCanJump(boolean flag) {
            this.canJump = flag;
        }

        @Override
        public void tick() {
            if (this.jump) {
                this.rabbit.startJumping();
                this.jump = false;
            }

        }
    }

    private static class ControllerMoveRabbit extends ControllerMove {

        private final EntityRabbit rabbit;
        private double nextJumpSpeed;

        public ControllerMoveRabbit(EntityRabbit entityrabbit) {
            super(entityrabbit);
            this.rabbit = entityrabbit;
        }

        @Override
        public void tick() {
            if (this.rabbit.onGround() && !this.rabbit.jumping && !((EntityRabbit.ControllerJumpRabbit) this.rabbit.jumpControl).wantJump()) {
                this.rabbit.setSpeedModifier(0.0D);
            } else if (this.hasWanted() || this.operation == ControllerMove.Operation.JUMPING) {
                this.rabbit.setSpeedModifier(this.nextJumpSpeed);
            }

            super.tick();
        }

        @Override
        public void setWantedPosition(double d0, double d1, double d2, double d3) {
            if (this.rabbit.isInWater()) {
                d3 = 1.5D;
            }

            super.setWantedPosition(d0, d1, d2, d3);
            if (d3 > 0.0D) {
                this.nextJumpSpeed = d3;
            }

        }
    }

    private static class PathfinderGoalRabbitAvoidTarget<T extends EntityLiving> extends PathfinderGoalAvoidTarget<T> {

        private final EntityRabbit rabbit;

        public PathfinderGoalRabbitAvoidTarget(EntityRabbit entityrabbit, Class<T> oclass, float f, double d0, double d1) {
            super(entityrabbit, oclass, f, d0, d1);
            this.rabbit = entityrabbit;
        }

        @Override
        public boolean canUse() {
            return this.rabbit.getVariant() != EntityRabbit.Variant.EVIL && super.canUse();
        }
    }

    private static class PathfinderGoalEatCarrots extends PathfinderGoalGotoTarget {

        private final EntityRabbit rabbit;
        private boolean wantsToRaid;
        private boolean canRaid;

        public PathfinderGoalEatCarrots(EntityRabbit entityrabbit) {
            super(entityrabbit, (double) 0.7F, 16);
            this.rabbit = entityrabbit;
        }

        @Override
        public boolean canUse() {
            if (this.nextStartTick <= 0) {
                if (!getServerLevel((Entity) this.rabbit).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    return false;
                }

                this.canRaid = false;
                this.wantsToRaid = this.rabbit.wantsMoreFood();
            }

            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canRaid && super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit.getLookControl().setLookAt((double) this.blockPos.getX() + 0.5D, (double) (this.blockPos.getY() + 1), (double) this.blockPos.getZ() + 0.5D, 10.0F, (float) this.rabbit.getMaxHeadXRot());
            if (this.isReachedTarget()) {
                World world = this.rabbit.level();
                BlockPosition blockposition = this.blockPos.above();
                IBlockData iblockdata = world.getBlockState(blockposition);
                Block block = iblockdata.getBlock();

                if (this.canRaid && block instanceof BlockCarrots) {
                    int i = (Integer) iblockdata.getValue(BlockCarrots.AGE);

                    if (i == 0) {
                        // CraftBukkit start
                        if (!CraftEventFactory.callEntityChangeBlockEvent(this.rabbit, blockposition, Blocks.AIR.defaultBlockState())) {
                            return;
                        }
                        // CraftBukkit end
                        world.setBlock(blockposition, Blocks.AIR.defaultBlockState(), 2);
                        world.destroyBlock(blockposition, true, this.rabbit);
                    } else {
                        // CraftBukkit start
                        if (!CraftEventFactory.callEntityChangeBlockEvent(this.rabbit, blockposition, iblockdata.setValue(BlockCarrots.AGE, i - 1))) {
                            return;
                        }
                        // CraftBukkit end
                        world.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockCarrots.AGE, i - 1), 2);
                        world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of((Entity) this.rabbit));
                        world.levelEvent(2001, blockposition, Block.getId(iblockdata));
                    }

                    this.rabbit.moreCarrotTicks = 40;
                }

                this.canRaid = false;
                this.nextStartTick = 10;
            }

        }

        @Override
        protected boolean isValidTarget(IWorldReader iworldreader, BlockPosition blockposition) {
            IBlockData iblockdata = iworldreader.getBlockState(blockposition);

            if (iblockdata.is(Blocks.FARMLAND) && this.wantsToRaid && !this.canRaid) {
                iblockdata = iworldreader.getBlockState(blockposition.above());
                if (iblockdata.getBlock() instanceof BlockCarrots && ((BlockCarrots) iblockdata.getBlock()).isMaxAge(iblockdata)) {
                    this.canRaid = true;
                    return true;
                }
            }

            return false;
        }
    }

    private static class PathfinderGoalRabbitPanic extends PathfinderGoalPanic {

        private final EntityRabbit rabbit;

        public PathfinderGoalRabbitPanic(EntityRabbit entityrabbit, double d0) {
            super(entityrabbit, d0);
            this.rabbit = entityrabbit;
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit.setSpeedModifier(this.speedModifier);
        }
    }
}
