package net.minecraft.world.entity.animal.horse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.goal.PathfinderGoalArrowAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowParent;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLlamaFollow;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTame;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.animal.wolf.EntityWolf;
import net.minecraft.world.entity.monster.IRangedEntity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityLlamaSpit;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

public class EntityLlama extends EntityHorseChestedAbstract implements IRangedEntity {

    private static final int MAX_STRENGTH = 5;
    private static final DataWatcherObject<Integer> DATA_STRENGTH_ID = DataWatcher.<Integer>defineId(EntityLlama.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Integer> DATA_VARIANT_ID = DataWatcher.<Integer>defineId(EntityLlama.class, DataWatcherRegistry.INT);
    private static final EntitySize BABY_DIMENSIONS = EntityTypes.LLAMA.getDimensions().withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, EntityTypes.LLAMA.getHeight() - 0.8125F, -0.3F)).scale(0.5F);
    boolean didSpit;
    @Nullable
    private EntityLlama caravanHead;
    @Nullable
    private EntityLlama caravanTail;

    public EntityLlama(EntityTypes<? extends EntityLlama> entitytypes, World world) {
        super(entitytypes, world);
        this.getNavigation().setRequiredPathLength(40.0F);
    }

    public boolean isTraderLlama() {
        return false;
    }

    // CraftBukkit start
    public void setStrengthPublic(int i) {
        this.setStrength(i);
    }
    // CraftBukkit end
    private void setStrength(int i) {
        this.entityData.set(EntityLlama.DATA_STRENGTH_ID, Math.max(1, Math.min(5, i)));
    }

    private void setRandomStrength(RandomSource randomsource) {
        int i = randomsource.nextFloat() < 0.04F ? 5 : 3;

        this.setStrength(1 + randomsource.nextInt(i));
    }

    public int getStrength() {
        return (Integer) this.entityData.get(EntityLlama.DATA_STRENGTH_ID);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("Variant", EntityLlama.Variant.LEGACY_CODEC, this.getVariant());
        valueoutput.putInt("Strength", this.getStrength());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        this.setStrength(valueinput.getIntOr("Strength", 0));
        super.readAdditionalSaveData(valueinput);
        this.setVariant((EntityLlama.Variant) valueinput.read("Variant", EntityLlama.Variant.LEGACY_CODEC).orElse(EntityLlama.Variant.DEFAULT));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(1, new PathfinderGoalTame(this, 1.2D));
        this.goalSelector.addGoal(2, new PathfinderGoalLlamaFollow(this, (double) 2.1F));
        this.goalSelector.addGoal(3, new PathfinderGoalArrowAttack(this, 1.25D, 40, 20.0F));
        this.goalSelector.addGoal(3, new PathfinderGoalPanic(this, 1.2D));
        this.goalSelector.addGoal(4, new PathfinderGoalBreed(this, 1.0D));
        this.goalSelector.addGoal(5, new PathfinderGoalTempt(this, 1.25D, (itemstack) -> {
            return itemstack.is(TagsItem.LLAMA_TEMPT_ITEMS);
        }, false));
        this.goalSelector.addGoal(6, new PathfinderGoalFollowParent(this, 1.0D));
        this.goalSelector.addGoal(7, new PathfinderGoalRandomStrollLand(this, 0.7D));
        this.goalSelector.addGoal(8, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 6.0F));
        this.goalSelector.addGoal(9, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, new EntityLlama.c(this));
        this.targetSelector.addGoal(2, new EntityLlama.a(this));
    }

    public static AttributeProvider.Builder createAttributes() {
        return createBaseChestedHorseAttributes();
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityLlama.DATA_STRENGTH_ID, 0);
        datawatcher_a.define(EntityLlama.DATA_VARIANT_ID, 0);
    }

    public EntityLlama.Variant getVariant() {
        return EntityLlama.Variant.byId((Integer) this.entityData.get(EntityLlama.DATA_VARIANT_ID));
    }

    public void setVariant(EntityLlama.Variant entityllama_variant) {
        this.entityData.set(EntityLlama.DATA_VARIANT_ID, entityllama_variant.id);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.LLAMA_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.LLAMA_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.LLAMA_VARIANT) {
            this.setVariant((EntityLlama.Variant) castComponentValue(DataComponents.LLAMA_VARIANT, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.LLAMA_FOOD);
    }

    @Override
    protected boolean handleEating(EntityHuman entityhuman, ItemStack itemstack) {
        int i = 0;
        int j = 0;
        float f = 0.0F;
        boolean flag = false;

        if (itemstack.is(Items.WHEAT)) {
            i = 10;
            j = 3;
            f = 2.0F;
        } else if (itemstack.is(Blocks.HAY_BLOCK.asItem())) {
            i = 90;
            j = 6;
            f = 10.0F;
            if (this.isTamed() && this.getAge() == 0 && this.canFallInLove()) {
                flag = true;
                this.setInLove(entityhuman);
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f);
            flag = true;
        }

        if (this.isBaby() && i > 0) {
            this.level().addParticle(Particles.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
            if (!this.level().isClientSide) {
                this.ageUp(i);
                flag = true;
            }
        }

        if (j > 0 && (flag || !this.isTamed()) && this.getTemper() < this.getMaxTemper() && !this.level().isClientSide) {
            this.modifyTemper(j);
            flag = true;
        }

        if (flag && !this.isSilent()) {
            SoundEffect soundeffect = this.getEatingSound();

            if (soundeffect != null) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), this.getEatingSound(), this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

        return flag;
    }

    @Override
    public boolean isImmobile() {
        return this.isDeadOrDying() || this.isEating();
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();

        this.setRandomStrength(randomsource);
        EntityLlama.Variant entityllama_variant;

        if (groupdataentity instanceof EntityLlama.b) {
            entityllama_variant = ((EntityLlama.b) groupdataentity).variant;
        } else {
            entityllama_variant = (EntityLlama.Variant) SystemUtils.getRandom(EntityLlama.Variant.values(), randomsource);
            groupdataentity = new EntityLlama.b(entityllama_variant);
        }

        this.setVariant(entityllama_variant);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    protected boolean canPerformRearing() {
        return false;
    }

    @Override
    protected SoundEffect getAngrySound() {
        return SoundEffects.LLAMA_ANGRY;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.LLAMA_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.LLAMA_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.LLAMA_DEATH;
    }

    @Nullable
    @Override
    protected SoundEffect getEatingSound() {
        return SoundEffects.LLAMA_EAT;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.LLAMA_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playChestEquipsSound() {
        this.playSound(SoundEffects.LLAMA_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    @Override
    public int getInventoryColumns() {
        return this.hasChest() ? this.getStrength() : 0;
    }

    @Override
    public boolean canUseSlot(EnumItemSlot enumitemslot) {
        return true;
    }

    @Override
    public int getMaxTemper() {
        return 30;
    }

    @Override
    public boolean canMate(EntityAnimal entityanimal) {
        return entityanimal != this && entityanimal instanceof EntityLlama && this.canParent() && ((EntityLlama) entityanimal).canParent();
    }

    @Nullable
    @Override
    public EntityLlama getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityLlama entityllama = this.makeNewLlama();

        if (entityllama != null) {
            this.setOffspringAttributes(entityageable, entityllama);
            EntityLlama entityllama1 = (EntityLlama) entityageable;
            int i = this.random.nextInt(Math.max(this.getStrength(), entityllama1.getStrength())) + 1;

            if (this.random.nextFloat() < 0.03F) {
                ++i;
            }

            entityllama.setStrength(i);
            entityllama.setVariant(this.random.nextBoolean() ? this.getVariant() : entityllama1.getVariant());
        }

        return entityllama;
    }

    @Nullable
    protected EntityLlama makeNewLlama() {
        return EntityTypes.LLAMA.create(this.level(), EntitySpawnReason.BREEDING);
    }

    private void spit(EntityLiving entityliving) {
        EntityLlamaSpit entityllamaspit = new EntityLlamaSpit(this.level(), this);
        double d0 = entityliving.getX() - this.getX();
        double d1 = entityliving.getY(0.3333333333333333D) - entityllamaspit.getY();
        double d2 = entityliving.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2) * (double) 0.2F;
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            IProjectile.spawnProjectileUsingShoot(entityllamaspit, worldserver, ItemStack.EMPTY, d0, d1 + d3, d2, 1.5F, 10.0F);
        }

        if (!this.isSilent()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.LLAMA_SPIT, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }

        this.didSpit = true;
    }

    void setDidSpit(boolean flag) {
        this.didSpit = flag;
    }

    @Override
    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        int i = this.calculateFallDamage(d0, f);

        if (i <= 0) {
            return false;
        } else {
            if (d0 >= 6.0D) {
                this.hurt(damagesource, (float) i);
                this.propagateFallToPassengers(d0, f, damagesource);
            }

            this.playBlockFallSound();
            return true;
        }
    }

    public void leaveCaravan() {
        if (this.caravanHead != null) {
            this.caravanHead.caravanTail = null;
        }

        this.caravanHead = null;
    }

    public void joinCaravan(EntityLlama entityllama) {
        this.caravanHead = entityllama;
        this.caravanHead.caravanTail = this;
    }

    public boolean hasCaravanTail() {
        return this.caravanTail != null;
    }

    public boolean inCaravan() {
        return this.caravanHead != null;
    }

    @Nullable
    public EntityLlama getCaravanHead() {
        return this.caravanHead;
    }

    @Override
    protected double followLeashSpeed() {
        return 2.0D;
    }

    @Override
    public boolean supportQuadLeash() {
        return false;
    }

    @Override
    protected void followMommy(WorldServer worldserver) {
        if (!this.inCaravan() && this.isBaby()) {
            super.followMommy(worldserver);
        }

    }

    @Override
    public boolean canEatGrass() {
        return false;
    }

    @Override
    public void performRangedAttack(EntityLiving entityliving, float f) {
        this.spit(entityliving);
    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, 0.75D * (double) this.getEyeHeight(), (double) this.getBbWidth() * 0.5D);
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityLlama.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    @Override
    protected Vec3D getPassengerAttachmentPoint(Entity entity, EntitySize entitysize, float f) {
        return getDefaultPassengerAttachmentPoint(this, entity, entitysize.attachments());
    }

    public static enum Variant implements INamable {

        CREAMY(0, "creamy"), WHITE(1, "white"), BROWN(2, "brown"), GRAY(3, "gray");

        public static final EntityLlama.Variant DEFAULT = EntityLlama.Variant.CREAMY;
        private static final IntFunction<EntityLlama.Variant> BY_ID = ByIdMap.<EntityLlama.Variant>continuous(EntityLlama.Variant::getId, values(), ByIdMap.a.CLAMP);
        public static final Codec<EntityLlama.Variant> CODEC = INamable.<EntityLlama.Variant>fromEnum(EntityLlama.Variant::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<EntityLlama.Variant> LEGACY_CODEC;
        public static final StreamCodec<ByteBuf, EntityLlama.Variant> STREAM_CODEC;
        final int id;
        private final String name;

        private Variant(final int i, final String s) {
            this.id = i;
            this.name = s;
        }

        public int getId() {
            return this.id;
        }

        public static EntityLlama.Variant byId(int i) {
            return (EntityLlama.Variant) EntityLlama.Variant.BY_ID.apply(i);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        static {
            PrimitiveCodec<Integer> primitivecodec = Codec.INT; // CraftBukkit - decompile error
            IntFunction<EntityLlama.Variant> intfunction = EntityLlama.Variant.BY_ID; // CraftBukkit - decompile error

            Objects.requireNonNull(intfunction);
            LEGACY_CODEC = primitivecodec.xmap(intfunction::apply, EntityLlama.Variant::getId);
            STREAM_CODEC = ByteBufCodecs.idMapper(EntityLlama.Variant.BY_ID, EntityLlama.Variant::getId);
        }
    }

    private static class b extends EntityAgeable.a {

        public final EntityLlama.Variant variant;

        b(EntityLlama.Variant entityllama_variant) {
            super(true);
            this.variant = entityllama_variant;
        }
    }

    private static class c extends PathfinderGoalHurtByTarget {

        public c(EntityLlama entityllama) {
            super(entityllama);
        }

        @Override
        public boolean canContinueToUse() {
            EntityInsentient entityinsentient = this.mob;

            if (entityinsentient instanceof EntityLlama entityllama) {
                if (entityllama.didSpit) {
                    entityllama.setDidSpit(false);
                    return false;
                }
            }

            return super.canContinueToUse();
        }
    }

    private static class a extends PathfinderGoalNearestAttackableTarget<EntityWolf> {

        public a(EntityLlama entityllama) {
            super(entityllama, EntityWolf.class, 16, false, true, (entityliving, worldserver) -> {
                return !((EntityWolf) entityliving).isTame();
            });
        }

        @Override
        protected double getFollowDistance() {
            return super.getFollowDistance() * 0.25D;
        }
    }
}
