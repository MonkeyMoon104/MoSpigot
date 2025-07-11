package net.minecraft.world.entity.animal;

import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
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
import net.minecraft.world.entity.EntityTameableAnimal;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalCatSitOnBed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowOwner;
import net.minecraft.world.entity.ai.goal.PathfinderGoalJumpOnBlock;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLeapAtTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalOcelotAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSit;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalRandomTargetNonTamed;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.food.FoodInfo;
import net.minecraft.world.item.EnumColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDye;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.BlockBed;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.phys.AxisAlignedBB;

public class EntityCat extends EntityTameableAnimal {

    public static final double TEMPT_SPEED_MOD = 0.6D;
    public static final double WALK_SPEED_MOD = 0.8D;
    public static final double SPRINT_SPEED_MOD = 1.33D;
    private static final DataWatcherObject<Holder<CatVariant>> DATA_VARIANT_ID = DataWatcher.<Holder<CatVariant>>defineId(EntityCat.class, DataWatcherRegistry.CAT_VARIANT);
    private static final DataWatcherObject<Boolean> IS_LYING = DataWatcher.<Boolean>defineId(EntityCat.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Boolean> RELAX_STATE_ONE = DataWatcher.<Boolean>defineId(EntityCat.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Integer> DATA_COLLAR_COLOR = DataWatcher.<Integer>defineId(EntityCat.class, DataWatcherRegistry.INT);
    private static final ResourceKey<CatVariant> DEFAULT_VARIANT = CatVariants.BLACK;
    private static final EnumColor DEFAULT_COLLAR_COLOR = EnumColor.RED;
    @Nullable
    private EntityCat.a<EntityHuman> avoidPlayersGoal;
    @Nullable
    private PathfinderGoalTempt temptGoal;
    private float lieDownAmount;
    private float lieDownAmountO;
    private float lieDownAmountTail;
    private float lieDownAmountOTail;
    private boolean isLyingOnTopOfSleepingPlayer;
    private float relaxStateOneAmount;
    private float relaxStateOneAmountO;

    public EntityCat(EntityTypes<? extends EntityCat> entitytypes, World world) {
        super(entitytypes, world);
        this.reassessTameGoals();
    }

    @Override
    protected void registerGoals() {
        this.temptGoal = new EntityCat.PathfinderGoalTemptChance(this, 0.6D, (itemstack) -> {
            return itemstack.is(TagsItem.CAT_FOOD);
        }, true);
        this.goalSelector.addGoal(1, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(1, new EntityTameableAnimal.a(1.5D));
        this.goalSelector.addGoal(2, new PathfinderGoalSit(this));
        this.goalSelector.addGoal(3, new EntityCat.b(this));
        this.goalSelector.addGoal(4, this.temptGoal);
        this.goalSelector.addGoal(5, new PathfinderGoalCatSitOnBed(this, 1.1D, 8));
        this.goalSelector.addGoal(6, new PathfinderGoalFollowOwner(this, 1.0D, 10.0F, 5.0F));
        this.goalSelector.addGoal(7, new PathfinderGoalJumpOnBlock(this, 0.8D));
        this.goalSelector.addGoal(8, new PathfinderGoalLeapAtTarget(this, 0.3F));
        this.goalSelector.addGoal(9, new PathfinderGoalOcelotAttack(this));
        this.goalSelector.addGoal(10, new PathfinderGoalBreed(this, 0.8D));
        this.goalSelector.addGoal(11, new PathfinderGoalRandomStrollLand(this, 0.8D, 1.0000001E-5F));
        this.goalSelector.addGoal(12, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 10.0F));
        this.targetSelector.addGoal(1, new PathfinderGoalRandomTargetNonTamed(this, EntityRabbit.class, false, (PathfinderTargetCondition.a) null));
        this.targetSelector.addGoal(1, new PathfinderGoalRandomTargetNonTamed(this, EntityTurtle.class, false, EntityTurtle.BABY_ON_LAND_SELECTOR));
    }

    public Holder<CatVariant> getVariant() {
        return (Holder) this.entityData.get(EntityCat.DATA_VARIANT_ID);
    }

    public void setVariant(Holder<CatVariant> holder) {
        this.entityData.set(EntityCat.DATA_VARIANT_ID, holder);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.CAT_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : (datacomponenttype == DataComponents.CAT_COLLAR ? castComponentValue(datacomponenttype, this.getCollarColor()) : super.get(datacomponenttype)));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.CAT_VARIANT);
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.CAT_COLLAR);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.CAT_VARIANT) {
            this.setVariant((Holder) castComponentValue(DataComponents.CAT_VARIANT, t0));
            return true;
        } else if (datacomponenttype == DataComponents.CAT_COLLAR) {
            this.setCollarColor((EnumColor) castComponentValue(DataComponents.CAT_COLLAR, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    public void setLying(boolean flag) {
        this.entityData.set(EntityCat.IS_LYING, flag);
    }

    public boolean isLying() {
        return (Boolean) this.entityData.get(EntityCat.IS_LYING);
    }

    void setRelaxStateOne(boolean flag) {
        this.entityData.set(EntityCat.RELAX_STATE_ONE, flag);
    }

    boolean isRelaxStateOne() {
        return (Boolean) this.entityData.get(EntityCat.RELAX_STATE_ONE);
    }

    public EnumColor getCollarColor() {
        return EnumColor.byId((Integer) this.entityData.get(EntityCat.DATA_COLLAR_COLOR));
    }

    public void setCollarColor(EnumColor enumcolor) {
        this.entityData.set(EntityCat.DATA_COLLAR_COLOR, enumcolor.getId());
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityCat.DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), EntityCat.DEFAULT_VARIANT));
        datawatcher_a.define(EntityCat.IS_LYING, false);
        datawatcher_a.define(EntityCat.RELAX_STATE_ONE, false);
        datawatcher_a.define(EntityCat.DATA_COLLAR_COLOR, EntityCat.DEFAULT_COLLAR_COLOR.getId());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        VariantUtils.writeVariant(valueoutput, this.getVariant());
        valueoutput.store("CollarColor", EnumColor.LEGACY_ID_CODEC, this.getCollarColor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        VariantUtils.readVariant(valueinput, Registries.CAT_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor((EnumColor) valueinput.read("CollarColor", EnumColor.LEGACY_ID_CODEC).orElse(EntityCat.DEFAULT_COLLAR_COLOR));
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

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return this.isTame() ? (this.isInLove() ? SoundEffects.CAT_PURR : (this.random.nextInt(4) == 0 ? SoundEffects.CAT_PURREOW : SoundEffects.CAT_AMBIENT)) : SoundEffects.CAT_STRAY_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    public void hiss() {
        this.makeSound(SoundEffects.CAT_HISS);
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.CAT_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.CAT_DEATH;
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 10.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void playEatingSound() {
        this.playSound(SoundEffects.CAT_EAT, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.temptGoal != null && this.temptGoal.isRunning() && !this.isTame() && this.tickCount % 100 == 0) {
            this.playSound(SoundEffects.CAT_BEG_FOR_FOOD, 1.0F, 1.0F);
        }

        this.handleLieDown();
    }

    private void handleLieDown() {
        if ((this.isLying() || this.isRelaxStateOne()) && this.tickCount % 5 == 0) {
            this.playSound(SoundEffects.CAT_PURR, 0.6F + 0.4F * (this.random.nextFloat() - this.random.nextFloat()), 1.0F);
        }

        this.updateLieDownAmount();
        this.updateRelaxStateOneAmount();
        this.isLyingOnTopOfSleepingPlayer = false;
        if (this.isLying()) {
            BlockPosition blockposition = this.blockPosition();

            for (EntityHuman entityhuman : this.level().getEntitiesOfClass(EntityHuman.class, (new AxisAlignedBB(blockposition)).inflate(2.0D, 2.0D, 2.0D))) {
                if (entityhuman.isSleeping()) {
                    this.isLyingOnTopOfSleepingPlayer = true;
                    break;
                }
            }
        }

    }

    public boolean isLyingOnTopOfSleepingPlayer() {
        return this.isLyingOnTopOfSleepingPlayer;
    }

    private void updateLieDownAmount() {
        this.lieDownAmountO = this.lieDownAmount;
        this.lieDownAmountOTail = this.lieDownAmountTail;
        if (this.isLying()) {
            this.lieDownAmount = Math.min(1.0F, this.lieDownAmount + 0.15F);
            this.lieDownAmountTail = Math.min(1.0F, this.lieDownAmountTail + 0.08F);
        } else {
            this.lieDownAmount = Math.max(0.0F, this.lieDownAmount - 0.22F);
            this.lieDownAmountTail = Math.max(0.0F, this.lieDownAmountTail - 0.13F);
        }

    }

    private void updateRelaxStateOneAmount() {
        this.relaxStateOneAmountO = this.relaxStateOneAmount;
        if (this.isRelaxStateOne()) {
            this.relaxStateOneAmount = Math.min(1.0F, this.relaxStateOneAmount + 0.1F);
        } else {
            this.relaxStateOneAmount = Math.max(0.0F, this.relaxStateOneAmount - 0.13F);
        }

    }

    public float getLieDownAmount(float f) {
        return MathHelper.lerp(f, this.lieDownAmountO, this.lieDownAmount);
    }

    public float getLieDownAmountTail(float f) {
        return MathHelper.lerp(f, this.lieDownAmountOTail, this.lieDownAmountTail);
    }

    public float getRelaxStateOneAmount(float f) {
        return MathHelper.lerp(f, this.relaxStateOneAmountO, this.relaxStateOneAmount);
    }

    @Nullable
    @Override
    public EntityCat getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityCat entitycat = EntityTypes.CAT.create(worldserver, EntitySpawnReason.BREEDING);

        if (entitycat != null && entityageable instanceof EntityCat entitycat1) {
            if (this.random.nextBoolean()) {
                entitycat.setVariant(this.getVariant());
            } else {
                entitycat.setVariant(entitycat1.getVariant());
            }

            if (this.isTame()) {
                entitycat.setOwnerReference(this.getOwnerReference());
                entitycat.setTame(true, true);
                EnumColor enumcolor = this.getCollarColor();
                EnumColor enumcolor1 = entitycat1.getCollarColor();

                entitycat.setCollarColor(EnumColor.getMixedColor(worldserver, enumcolor, enumcolor1));
            }
        }

        return entitycat;
    }

    @Override
    public boolean canMate(EntityAnimal entityanimal) {
        if (!this.isTame()) {
            return false;
        } else if (!(entityanimal instanceof EntityCat)) {
            return false;
        } else {
            EntityCat entitycat = (EntityCat) entityanimal;

            return entitycat.isTame() && super.canMate(entityanimal);
        }
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        groupdataentity = super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
        VariantUtils.selectVariantToSpawn(SpawnContext.create(worldaccess, this.blockPosition()), Registries.CAT_VARIANT).ifPresent(this::setVariant);
        return groupdataentity;
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        Item item = itemstack.getItem();

        if (this.isTame()) {
            if (this.isOwnedBy(entityhuman)) {
                if (item instanceof ItemDye) {
                    ItemDye itemdye = (ItemDye) item;
                    EnumColor enumcolor = itemdye.getDyeColor();

                    if (enumcolor != this.getCollarColor()) {
                        if (!this.level().isClientSide()) {
                            this.setCollarColor(enumcolor);
                            itemstack.consume(1, entityhuman);
                            this.setPersistenceRequired();
                        }

                        return EnumInteractionResult.SUCCESS;
                    }
                } else if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                    if (!this.level().isClientSide()) {
                        this.usePlayerItem(entityhuman, enumhand, itemstack);
                        FoodInfo foodinfo = (FoodInfo) itemstack.get(DataComponents.FOOD);

                        this.heal(foodinfo != null ? (float) foodinfo.nutrition() : 1.0F);
                        this.playEatingSound();
                    }

                    return EnumInteractionResult.SUCCESS;
                }

                EnumInteractionResult enuminteractionresult = super.mobInteract(entityhuman, enumhand);

                if (!enuminteractionresult.consumesAction()) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    return EnumInteractionResult.SUCCESS;
                }

                return enuminteractionresult;
            }
        } else if (this.isFood(itemstack)) {
            if (!this.level().isClientSide()) {
                this.usePlayerItem(entityhuman, enumhand, itemstack);
                this.tryToTame(entityhuman);
                this.setPersistenceRequired();
                this.playEatingSound();
            }

            return EnumInteractionResult.SUCCESS;
        }

        EnumInteractionResult enuminteractionresult1 = super.mobInteract(entityhuman, enumhand);

        if (enuminteractionresult1.consumesAction()) {
            this.setPersistenceRequired();
        }

        return enuminteractionresult1;
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.CAT_FOOD);
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isTame() && this.tickCount > 2400;
    }

    @Override
    public void setTame(boolean flag, boolean flag1) {
        super.setTame(flag, flag1);
        this.reassessTameGoals();
    }

    protected void reassessTameGoals() {
        if (this.avoidPlayersGoal == null) {
            this.avoidPlayersGoal = new EntityCat.a<EntityHuman>(this, EntityHuman.class, 16.0F, 0.8D, 1.33D);
        }

        this.goalSelector.removeGoal(this.avoidPlayersGoal);
        if (!this.isTame()) {
            this.goalSelector.addGoal(4, this.avoidPlayersGoal);
        }

    }

    private void tryToTame(EntityHuman entityhuman) {
        if (this.random.nextInt(3) == 0 && !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) { // CraftBukkit
            this.tame(entityhuman);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }

    }

    @Override
    public boolean isSteppingCarefully() {
        return this.isCrouching() || super.isSteppingCarefully();
    }

    private static class a<T extends EntityLiving> extends PathfinderGoalAvoidTarget<T> {

        private final EntityCat cat;

        public a(EntityCat entitycat, Class<T> oclass, float f, double d0, double d1) {
            // Predicate predicate = IEntitySelector.NO_CREATIVE_OR_SPECTATOR; // CraftBukkit - decompile error

            // Objects.requireNonNull(predicate); // CraftBukkit - decompile error
            super(entitycat, oclass, f, d0, d1, IEntitySelector.NO_CREATIVE_OR_SPECTATOR::test); // CraftBukkit - decompile error
            this.cat = entitycat;
        }

        @Override
        public boolean canUse() {
            return !this.cat.isTame() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.cat.isTame() && super.canContinueToUse();
        }
    }

    private static class PathfinderGoalTemptChance extends PathfinderGoalTempt {

        @Nullable
        private EntityLiving selectedPlayer; // CraftBukkit
        private final EntityCat cat;

        public PathfinderGoalTemptChance(EntityCat entitycat, double d0, Predicate<ItemStack> predicate, boolean flag) {
            super(entitycat, d0, predicate, flag);
            this.cat = entitycat;
        }

        @Override
        public void tick() {
            super.tick();
            if (this.selectedPlayer == null && this.mob.getRandom().nextInt(this.adjustedTickDelay(600)) == 0) {
                this.selectedPlayer = this.player;
            } else if (this.mob.getRandom().nextInt(this.adjustedTickDelay(500)) == 0) {
                this.selectedPlayer = null;
            }

        }

        @Override
        protected boolean canScare() {
            return this.selectedPlayer != null && this.selectedPlayer.equals(this.player) ? false : super.canScare();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.cat.isTame();
        }
    }

    private static class b extends PathfinderGoal {

        private final EntityCat cat;
        @Nullable
        private EntityHuman ownerPlayer;
        @Nullable
        private BlockPosition goalPos;
        private int onBedTicks;

        public b(EntityCat entitycat) {
            this.cat = entitycat;
        }

        @Override
        public boolean canUse() {
            if (!this.cat.isTame()) {
                return false;
            } else if (this.cat.isOrderedToSit()) {
                return false;
            } else {
                EntityLiving entityliving = this.cat.getOwner();

                if (entityliving instanceof EntityHuman) {
                    EntityHuman entityhuman = (EntityHuman) entityliving;

                    this.ownerPlayer = entityhuman;
                    if (!entityliving.isSleeping()) {
                        return false;
                    }

                    if (this.cat.distanceToSqr((Entity) this.ownerPlayer) > 100.0D) {
                        return false;
                    }

                    BlockPosition blockposition = this.ownerPlayer.blockPosition();
                    IBlockData iblockdata = this.cat.level().getBlockState(blockposition);

                    if (iblockdata.is(TagsBlock.BEDS)) {
                        this.goalPos = (BlockPosition) iblockdata.getOptionalValue(BlockBed.FACING).map((enumdirection) -> {
                            return blockposition.relative(enumdirection.getOpposite());
                        }).orElseGet(() -> {
                            return new BlockPosition(blockposition);
                        });
                        return !this.spaceIsOccupied();
                    }
                }

                return false;
            }
        }

        private boolean spaceIsOccupied() {
            for (EntityCat entitycat : this.cat.level().getEntitiesOfClass(EntityCat.class, (new AxisAlignedBB(this.goalPos)).inflate(2.0D))) {
                if (entitycat != this.cat && (entitycat.isLying() || entitycat.isRelaxStateOne())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.cat.isTame() && !this.cat.isOrderedToSit() && this.ownerPlayer != null && this.ownerPlayer.isSleeping() && this.goalPos != null && !this.spaceIsOccupied();
        }

        @Override
        public void start() {
            if (this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo((double) this.goalPos.getX(), (double) this.goalPos.getY(), (double) this.goalPos.getZ(), (double) 1.1F);
            }

        }

        @Override
        public void stop() {
            this.cat.setLying(false);
            float f = this.cat.level().getTimeOfDay(1.0F);

            if (this.ownerPlayer.getSleepTimer() >= 100 && (double) f > 0.77D && (double) f < 0.8D && (double) this.cat.level().getRandom().nextFloat() < 0.7D) {
                this.giveMorningGift();
            }

            this.onBedTicks = 0;
            this.cat.setRelaxStateOne(false);
            this.cat.getNavigation().stop();
        }

        private void giveMorningGift() {
            RandomSource randomsource = this.cat.getRandom();
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();

            blockposition_mutableblockposition.set(this.cat.isLeashed() ? this.cat.getLeashHolder().blockPosition() : this.cat.blockPosition());
            this.cat.randomTeleport((double) (blockposition_mutableblockposition.getX() + randomsource.nextInt(11) - 5), (double) (blockposition_mutableblockposition.getY() + randomsource.nextInt(5) - 2), (double) (blockposition_mutableblockposition.getZ() + randomsource.nextInt(11) - 5), false);
            blockposition_mutableblockposition.set(this.cat.blockPosition());
            this.cat.dropFromGiftLootTable(getServerLevel((Entity) this.cat), LootTables.CAT_MORNING_GIFT, (worldserver, itemstack) -> {
                // CraftBukkit start
                EntityItem entityitem = new EntityItem(worldserver, (double) blockposition_mutableblockposition.getX() - (double) MathHelper.sin(this.cat.yBodyRot * ((float) Math.PI / 180F)), (double) blockposition_mutableblockposition.getY(), (double) blockposition_mutableblockposition.getZ() + (double) MathHelper.cos(this.cat.yBodyRot * ((float) Math.PI / 180F)), itemstack);
                org.bukkit.event.entity.EntityDropItemEvent event = new org.bukkit.event.entity.EntityDropItemEvent(this.cat.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
                entityitem.level().getCraftServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
                worldserver.addFreshEntity(entityitem);
                // CraftBukkit end
            });
        }

        @Override
        public void tick() {
            if (this.ownerPlayer != null && this.goalPos != null) {
                this.cat.setInSittingPose(false);
                this.cat.getNavigation().moveTo((double) this.goalPos.getX(), (double) this.goalPos.getY(), (double) this.goalPos.getZ(), (double) 1.1F);
                if (this.cat.distanceToSqr((Entity) this.ownerPlayer) < 2.5D) {
                    ++this.onBedTicks;
                    if (this.onBedTicks > this.adjustedTickDelay(16)) {
                        this.cat.setLying(true);
                        this.cat.setRelaxStateOne(false);
                    } else {
                        this.cat.lookAt(this.ownerPlayer, 45.0F, 45.0F);
                        this.cat.setRelaxStateOne(true);
                    }
                } else {
                    this.cat.setLying(false);
                }
            }

        }
    }
}
