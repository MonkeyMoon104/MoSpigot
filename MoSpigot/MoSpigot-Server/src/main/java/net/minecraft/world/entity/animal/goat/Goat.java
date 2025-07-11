package net.minecraft.world.entity.animal.goat;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
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
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemLiquidUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.player.PlayerBucketFillEvent;
// CraftBukkit end

public class Goat extends EntityAnimal {

    public static final EntitySize LONG_JUMPING_DIMENSIONS = EntitySize.scalable(0.9F, 1.3F).scale(0.7F);
    private static final int ADULT_ATTACK_DAMAGE = 2;
    private static final int BABY_ATTACK_DAMAGE = 1;
    protected static final ImmutableList<SensorType<? extends Sensor<? super Goat>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.NEAREST_ADULT, SensorType.HURT_BY, SensorType.GOAT_TEMPTATIONS);
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATE_RECENTLY, MemoryModuleType.BREED_TARGET, MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, new MemoryModuleType[]{MemoryModuleType.IS_TEMPTED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryModuleType.RAM_TARGET, MemoryModuleType.IS_PANICKING});
    public static final int GOAT_FALL_DAMAGE_REDUCTION = 10;
    public static final double GOAT_SCREAMING_CHANCE = 0.02D;
    public static final double UNIHORN_CHANCE = (double) 0.1F;
    private static final DataWatcherObject<Boolean> DATA_IS_SCREAMING_GOAT = DataWatcher.<Boolean>defineId(Goat.class, DataWatcherRegistry.BOOLEAN);
    public static final DataWatcherObject<Boolean> DATA_HAS_LEFT_HORN = DataWatcher.<Boolean>defineId(Goat.class, DataWatcherRegistry.BOOLEAN);
    public static final DataWatcherObject<Boolean> DATA_HAS_RIGHT_HORN = DataWatcher.<Boolean>defineId(Goat.class, DataWatcherRegistry.BOOLEAN);
    private static final boolean DEFAULT_IS_SCREAMING = false;
    private static final boolean DEFAULT_HAS_LEFT_HORN = true;
    private static final boolean DEFAULT_HAS_RIGHT_HORN = true;
    private boolean isLoweringHead;
    private int lowerHeadTick;

    public Goat(EntityTypes<? extends Goat> entitytypes, World world) {
        super(entitytypes, world);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    public ItemStack createHorn() {
        RandomSource randomsource = RandomSource.create((long) this.getUUID().hashCode());
        TagKey<Instrument> tagkey = this.isScreamingGoat() ? InstrumentTags.SCREAMING_GOAT_HORNS : InstrumentTags.REGULAR_GOAT_HORNS;

        return (ItemStack) this.level().registryAccess().lookupOrThrow(Registries.INSTRUMENT).getRandomElementOf(tagkey, randomsource).map((holder) -> {
            return InstrumentItem.create(Items.GOAT_HORN, holder);
        }).orElseGet(() -> {
            return new ItemStack(Items.GOAT_HORN);
        });
    }

    @Override
    protected BehaviorController.b<Goat> brainProvider() {
        return BehaviorController.<Goat>provider(Goat.MEMORY_TYPES, Goat.SENSOR_TYPES);
    }

    @Override
    protected BehaviorController<?> makeBrain(Dynamic<?> dynamic) {
        return GoatAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 10.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.2F).add(GenericAttributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.getAttribute(GenericAttributes.ATTACK_DAMAGE).setBaseValue(1.0D);
            this.removeHorns();
        } else {
            this.getAttribute(GenericAttributes.ATTACK_DAMAGE).setBaseValue(2.0D);
            this.addHorns();
        }

    }

    @Override
    protected int calculateFallDamage(double d0, float f) {
        return super.calculateFallDamage(d0, f) - 10;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return this.isScreamingGoat() ? SoundEffects.GOAT_SCREAMING_AMBIENT : SoundEffects.GOAT_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return this.isScreamingGoat() ? SoundEffects.GOAT_SCREAMING_HURT : SoundEffects.GOAT_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return this.isScreamingGoat() ? SoundEffects.GOAT_SCREAMING_DEATH : SoundEffects.GOAT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.GOAT_STEP, 0.15F, 1.0F);
    }

    protected SoundEffect getMilkingSound() {
        return this.isScreamingGoat() ? SoundEffects.GOAT_SCREAMING_MILK : SoundEffects.GOAT_MILK;
    }

    @Nullable
    @Override
    public Goat getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        Goat goat = EntityTypes.GOAT.create(worldserver, EntitySpawnReason.BREEDING);

        if (goat != null) {
            boolean flag;
            label22:
            {
                label21:
                {
                    GoatAi.initMemories(goat, worldserver.getRandom());
                    EntityAgeable entityageable1 = (EntityAgeable) (worldserver.getRandom().nextBoolean() ? this : entityageable);

                    if (entityageable1 instanceof Goat) {
                        Goat goat1 = (Goat) entityageable1;

                        if (goat1.isScreamingGoat()) {
                            break label21;
                        }
                    }

                    if (worldserver.getRandom().nextDouble() >= 0.02D) {
                        flag = false;
                        break label22;
                    }
                }

                flag = true;
            }

            boolean flag1 = flag;

            goat.setScreamingGoat(flag1);
        }

        return goat;
    }

    @Override
    public BehaviorController<Goat> getBrain() {
        return (BehaviorController<Goat>) super.getBrain(); // CraftBukkit - decompile error
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("goatBrain");
        this.getBrain().tick(worldserver, this);
        gameprofilerfiller.pop();
        gameprofilerfiller.push("goatActivityUpdate");
        GoatAi.updateActivity(this);
        gameprofilerfiller.pop();
        super.customServerAiStep(worldserver);
    }

    @Override
    public int getMaxHeadYRot() {
        return 15;
    }

    @Override
    public void setYHeadRot(float f) {
        int i = this.getMaxHeadYRot();
        float f1 = MathHelper.degreesDifference(this.yBodyRot, f);
        float f2 = MathHelper.clamp(f1, (float) (-i), (float) i);

        super.setYHeadRot(this.yBodyRot + f2);
    }

    @Override
    protected void playEatingSound() {
        this.level().playSound((Entity) null, (Entity) this, this.isScreamingGoat() ? SoundEffects.GOAT_SCREAMING_EAT : SoundEffects.GOAT_EAT, SoundCategory.NEUTRAL, 1.0F, MathHelper.randomBetween(this.level().random, 0.8F, 1.2F));
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.GOAT_FOOD);
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(Items.BUCKET) && !this.isBaby()) {
            // CraftBukkit start - Got milk?
            PlayerBucketFillEvent event = CraftEventFactory.callPlayerBucketFillEvent((WorldServer) entityhuman.level(), entityhuman, this.blockPosition(), this.blockPosition(), null, itemstack, Items.MILK_BUCKET, enumhand);

            if (event.isCancelled()) {
                return EnumInteractionResult.PASS;
            }
            // CraftBukkit end
            entityhuman.playSound(this.getMilkingSound(), 1.0F, 1.0F);
            ItemStack itemstack1 = ItemLiquidUtil.createFilledResult(itemstack, entityhuman, CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit

            entityhuman.setItemInHand(enumhand, itemstack1);
            return EnumInteractionResult.SUCCESS;
        } else {
            EnumInteractionResult enuminteractionresult = super.mobInteract(entityhuman, enumhand);

            if (enuminteractionresult.consumesAction() && this.isFood(itemstack)) {
                this.playEatingSound();
            }

            return enuminteractionresult;
        }
    }

    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();

        GoatAi.initMemories(this, randomsource);
        this.setScreamingGoat(randomsource.nextDouble() < 0.02D);
        this.ageBoundaryReached();
        if (!this.isBaby() && (double) randomsource.nextFloat() < (double) 0.1F) {
            DataWatcherObject<Boolean> datawatcherobject = randomsource.nextBoolean() ? Goat.DATA_HAS_LEFT_HORN : Goat.DATA_HAS_RIGHT_HORN;

            this.entityData.set(datawatcherobject, false);
        }

        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        PacketDebug.sendEntityBrain(this);
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return entitypose == EntityPose.LONG_JUMPING ? Goat.LONG_JUMPING_DIMENSIONS.scale(this.getAgeScale()) : super.getDefaultDimensions(entitypose);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("IsScreamingGoat", this.isScreamingGoat());
        valueoutput.putBoolean("HasLeftHorn", this.hasLeftHorn());
        valueoutput.putBoolean("HasRightHorn", this.hasRightHorn());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setScreamingGoat(valueinput.getBooleanOr("IsScreamingGoat", false));
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, valueinput.getBooleanOr("HasLeftHorn", true));
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, valueinput.getBooleanOr("HasRightHorn", true));
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 58) {
            this.isLoweringHead = true;
        } else if (b0 == 59) {
            this.isLoweringHead = false;
        } else {
            super.handleEntityEvent(b0);
        }

    }

    @Override
    public void aiStep() {
        if (this.isLoweringHead) {
            ++this.lowerHeadTick;
        } else {
            this.lowerHeadTick -= 2;
        }

        this.lowerHeadTick = MathHelper.clamp(this.lowerHeadTick, 0, 20);
        super.aiStep();
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(Goat.DATA_IS_SCREAMING_GOAT, false);
        datawatcher_a.define(Goat.DATA_HAS_LEFT_HORN, true);
        datawatcher_a.define(Goat.DATA_HAS_RIGHT_HORN, true);
    }

    public boolean hasLeftHorn() {
        return (Boolean) this.entityData.get(Goat.DATA_HAS_LEFT_HORN);
    }

    public boolean hasRightHorn() {
        return (Boolean) this.entityData.get(Goat.DATA_HAS_RIGHT_HORN);
    }

    public boolean dropHorn() {
        boolean flag = this.hasLeftHorn();
        boolean flag1 = this.hasRightHorn();

        if (!flag && !flag1) {
            return false;
        } else {
            DataWatcherObject<Boolean> datawatcherobject;

            if (!flag) {
                datawatcherobject = Goat.DATA_HAS_RIGHT_HORN;
            } else if (!flag1) {
                datawatcherobject = Goat.DATA_HAS_LEFT_HORN;
            } else {
                datawatcherobject = this.random.nextBoolean() ? Goat.DATA_HAS_LEFT_HORN : Goat.DATA_HAS_RIGHT_HORN;
            }

            this.entityData.set(datawatcherobject, false);
            Vec3D vec3d = this.position();
            ItemStack itemstack = this.createHorn();
            double d0 = (double) MathHelper.randomBetween(this.random, -0.2F, 0.2F);
            double d1 = (double) MathHelper.randomBetween(this.random, 0.3F, 0.7F);
            double d2 = (double) MathHelper.randomBetween(this.random, -0.2F, 0.2F);
            EntityItem entityitem = new EntityItem(this.level(), vec3d.x(), vec3d.y(), vec3d.z(), itemstack, d0, d1, d2);

            this.level().addFreshEntity(entityitem);
            return true;
        }
    }

    public void addHorns() {
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, true);
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, true);
    }

    public void removeHorns() {
        this.entityData.set(Goat.DATA_HAS_LEFT_HORN, false);
        this.entityData.set(Goat.DATA_HAS_RIGHT_HORN, false);
    }

    public boolean isScreamingGoat() {
        return (Boolean) this.entityData.get(Goat.DATA_IS_SCREAMING_GOAT);
    }

    public void setScreamingGoat(boolean flag) {
        this.entityData.set(Goat.DATA_IS_SCREAMING_GOAT, flag);
    }

    public float getRammingXHeadRot() {
        return (float) this.lowerHeadTick / 20.0F * 30.0F * ((float) Math.PI / 180F);
    }

    public static boolean checkGoatSpawnRules(EntityTypes<? extends EntityAnimal> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.GOATS_SPAWNABLE_ON) && isBrightEnoughToSpawn(generatoraccess, blockposition);
    }
}
