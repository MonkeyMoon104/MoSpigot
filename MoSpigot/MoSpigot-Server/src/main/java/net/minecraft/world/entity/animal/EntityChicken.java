package net.minecraft.world.entity.animal;

import java.util.Optional;
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
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowParent;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.phys.Vec3D;

public class EntityChicken extends EntityAnimal {

    private static final EntitySize BABY_DIMENSIONS = EntityTypes.CHICKEN.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
    private static final DataWatcherObject<Holder<ChickenVariant>> DATA_VARIANT_ID = DataWatcher.<Holder<ChickenVariant>>defineId(EntityChicken.class, DataWatcherRegistry.CHICKEN_VARIANT);
    private static final boolean DEFAULT_CHICKEN_JOCKEY = false;
    public float flap;
    public float flapSpeed;
    public float oFlapSpeed;
    public float oFlap;
    public float flapping = 1.0F;
    private float nextFlap = 1.0F;
    public int eggTime;
    public boolean isChickenJockey = false;

    public EntityChicken(EntityTypes<? extends EntityChicken> entitytypes, World world) {
        super(entitytypes, world);
        this.eggTime = this.random.nextInt(6000) + 6000;
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(1, new PathfinderGoalPanic(this, 1.4D));
        this.goalSelector.addGoal(2, new PathfinderGoalBreed(this, 1.0D));
        this.goalSelector.addGoal(3, new PathfinderGoalTempt(this, 1.0D, (itemstack) -> {
            return itemstack.is(TagsItem.CHICKEN_FOOD);
        }, false));
        this.goalSelector.addGoal(4, new PathfinderGoalFollowParent(this, 1.1D));
        this.goalSelector.addGoal(5, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.goalSelector.addGoal(6, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 6.0F));
        this.goalSelector.addGoal(7, new PathfinderGoalRandomLookaround(this));
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityChicken.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 4.0D).add(GenericAttributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.oFlap = this.flap;
        this.oFlapSpeed = this.flapSpeed;
        this.flapSpeed += (this.onGround() ? -1.0F : 4.0F) * 0.3F;
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
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.isAlive() && !this.isBaby() && !this.isChickenJockey() && --this.eggTime <= 0) {
                this.forceDrops = true; // CraftBukkit
                if (this.dropFromGiftLootTable(worldserver, LootTables.CHICKEN_LAY, this::spawnAtLocation)) {
                    this.playSound(SoundEffects.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    this.gameEvent(GameEvent.ENTITY_PLACE);
                }
                this.forceDrops = false; // CraftBukkit

                this.eggTime = this.random.nextInt(6000) + 6000;
            }
        }

    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.nextFlap;
    }

    @Override
    protected void onFlap() {
        this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.CHICKEN_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.CHICKEN_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.CHICKEN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.CHICKEN_STEP, 0.15F, 1.0F);
    }

    @Nullable
    @Override
    public EntityChicken getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityChicken entitychicken = EntityTypes.CHICKEN.create(worldserver, EntitySpawnReason.BREEDING);

        if (entitychicken != null && entityageable instanceof EntityChicken entitychicken1) {
            entitychicken.setVariant(this.random.nextBoolean() ? this.getVariant() : entitychicken1.getVariant());
        }

        return entitychicken;
    }

    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        VariantUtils.selectVariantToSpawn(SpawnContext.create(worldaccess, this.blockPosition()), Registries.CHICKEN_VARIANT).ifPresent(this::setVariant);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.CHICKEN_FOOD);
    }

    @Override
    protected int getBaseExperienceReward(WorldServer worldserver) {
        return this.isChickenJockey() ? 10 : super.getBaseExperienceReward(worldserver);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityChicken.DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), ChickenVariants.TEMPERATE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.isChickenJockey = valueinput.getBooleanOr("IsChickenJockey", false);
        valueinput.getInt("EggLayTime").ifPresent((integer) -> {
            this.eggTime = integer;
        });
        VariantUtils.readVariant(valueinput, Registries.CHICKEN_VARIANT).ifPresent(this::setVariant);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("IsChickenJockey", this.isChickenJockey);
        valueoutput.putInt("EggLayTime", this.eggTime);
        VariantUtils.writeVariant(valueoutput, this.getVariant());
    }

    public void setVariant(Holder<ChickenVariant> holder) {
        this.entityData.set(EntityChicken.DATA_VARIANT_ID, holder);
    }

    public Holder<ChickenVariant> getVariant() {
        return (Holder) this.entityData.get(EntityChicken.DATA_VARIANT_ID);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.CHICKEN_VARIANT ? castComponentValue(datacomponenttype, new EitherHolder(this.getVariant())) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.CHICKEN_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.CHICKEN_VARIANT) {
            Optional<Holder<ChickenVariant>> optional = ((EitherHolder) castComponentValue(DataComponents.CHICKEN_VARIANT, t0)).unwrap(this.registryAccess());

            if (optional.isPresent()) {
                this.setVariant((Holder) optional.get());
                return true;
            } else {
                return false;
            }
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return this.isChickenJockey();
    }

    @Override
    protected void positionRider(Entity entity, Entity.MoveFunction entity_movefunction) {
        super.positionRider(entity, entity_movefunction);
        if (entity instanceof EntityLiving) {
            ((EntityLiving) entity).yBodyRot = this.yBodyRot;
        }

    }

    public boolean isChickenJockey() {
        return this.isChickenJockey;
    }

    public void setChickenJockey(boolean flag) {
        this.isChickenJockey = flag;
    }
}
