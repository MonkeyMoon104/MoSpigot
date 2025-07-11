package net.minecraft.world.entity.animal;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.TimeRange;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.valueproviders.UniformInt;
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
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.IEntityAngerable;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerLook;
import net.minecraft.world.entity.ai.control.ControllerMoveFlying;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowParent;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSelector;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalUniversalAngerReset;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.ai.navigation.NavigationFlying;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.entity.ai.village.poi.VillagePlaceRecord;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockCrops;
import net.minecraft.world.level.block.BlockFlowers;
import net.minecraft.world.level.block.BlockStem;
import net.minecraft.world.level.block.BlockSweetBerryBush;
import net.minecraft.world.level.block.BlockTallPlant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IBlockFragilePlantElement;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityBeehive;
import net.minecraft.world.level.block.entity.TileEntityTypes;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyDoubleBlockHalf;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.pathfinder.PathEntity;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class EntityBee extends EntityAnimal implements IEntityAngerable, EntityBird {

    public static final float FLAP_DEGREES_PER_TICK = 120.32113F;
    public static final int TICKS_PER_FLAP = MathHelper.ceil(1.4959966F);
    private static final DataWatcherObject<Byte> DATA_FLAGS_ID = DataWatcher.<Byte>defineId(EntityBee.class, DataWatcherRegistry.BYTE);
    private static final DataWatcherObject<Integer> DATA_REMAINING_ANGER_TIME = DataWatcher.<Integer>defineId(EntityBee.class, DataWatcherRegistry.INT);
    private static final int FLAG_ROLL = 2;
    private static final int FLAG_HAS_STUNG = 4;
    private static final int FLAG_HAS_NECTAR = 8;
    private static final int STING_DEATH_COUNTDOWN = 1200;
    private static final int TICKS_BEFORE_GOING_TO_KNOWN_FLOWER = 600;
    private static final int TICKS_WITHOUT_NECTAR_BEFORE_GOING_HOME = 3600;
    private static final int MIN_ATTACK_DIST = 4;
    private static final int MAX_CROPS_GROWABLE = 10;
    private static final int POISON_SECONDS_NORMAL = 10;
    private static final int POISON_SECONDS_HARD = 18;
    private static final int TOO_FAR_DISTANCE = 48;
    private static final int HIVE_CLOSE_ENOUGH_DISTANCE = 2;
    private static final int RESTRICTED_WANDER_DISTANCE_REDUCTION = 24;
    private static final int DEFAULT_WANDER_DISTANCE_REDUCTION = 16;
    private static final int PATHFIND_TO_HIVE_WHEN_CLOSER_THAN = 16;
    private static final int HIVE_SEARCH_DISTANCE = 20;
    public static final String TAG_CROPS_GROWN_SINCE_POLLINATION = "CropsGrownSincePollination";
    public static final String TAG_CANNOT_ENTER_HIVE_TICKS = "CannotEnterHiveTicks";
    public static final String TAG_TICKS_SINCE_POLLINATION = "TicksSincePollination";
    public static final String TAG_HAS_STUNG = "HasStung";
    public static final String TAG_HAS_NECTAR = "HasNectar";
    public static final String TAG_FLOWER_POS = "flower_pos";
    public static final String TAG_HIVE_POS = "hive_pos";
    public static final boolean DEFAULT_HAS_NECTAR = false;
    private static final boolean DEFAULT_HAS_STUNG = false;
    private static final int DEFAULT_TICKS_SINCE_POLLINATION = 0;
    private static final int DEFAULT_CANNOT_ENTER_HIVE_TICKS = 0;
    private static final int DEFAULT_CROPS_GROWN_SINCE_POLLINATION = 0;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeRange.rangeOfSeconds(20, 39);
    @Nullable
    private UUID persistentAngerTarget;
    private float rollAmount;
    private float rollAmountO;
    private int timeSinceSting;
    int ticksWithoutNectarSinceExitingHive = 0;
    public int stayOutOfHiveCountdown = 0;
    private int numCropsGrownSincePollination = 0;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_HIVE = 200;
    int remainingCooldownBeforeLocatingNewHive;
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_FLOWER = 200;
    private static final int MIN_FIND_FLOWER_RETRY_COOLDOWN = 20;
    private static final int MAX_FIND_FLOWER_RETRY_COOLDOWN = 60;
    int remainingCooldownBeforeLocatingNewFlower;
    @Nullable
    BlockPosition savedFlowerPos;
    @Nullable
    public BlockPosition hivePos;
    EntityBee.k beePollinateGoal;
    EntityBee.e goToHiveGoal;
    private EntityBee.f goToKnownFlowerGoal;
    private int underWaterTicks;

    public EntityBee(EntityTypes<? extends EntityBee> entitytypes, World world) {
        super(entitytypes, world);
        this.remainingCooldownBeforeLocatingNewFlower = MathHelper.nextInt(this.random, 20, 60);
        this.moveControl = new ControllerMoveFlying(this, 20, true);
        this.lookControl = new EntityBee.j(this);
        this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        this.setPathfindingMalus(PathType.COCOA, -1.0F);
        this.setPathfindingMalus(PathType.FENCE, -1.0F);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityBee.DATA_FLAGS_ID, (byte) 0);
        datawatcher_a.define(EntityBee.DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    public float getWalkTargetValue(BlockPosition blockposition, IWorldReader iworldreader) {
        return iworldreader.getBlockState(blockposition).isAir() ? 10.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EntityBee.b(this, (double) 1.4F, true));
        this.goalSelector.addGoal(1, new EntityBee.d());
        this.goalSelector.addGoal(2, new PathfinderGoalBreed(this, 1.0D));
        this.goalSelector.addGoal(3, new PathfinderGoalTempt(this, 1.25D, (itemstack) -> {
            return itemstack.is(TagsItem.BEE_FOOD);
        }, false));
        this.goalSelector.addGoal(3, new EntityBee.n());
        this.goalSelector.addGoal(3, new EntityBee.m());
        this.beePollinateGoal = new EntityBee.k();
        this.goalSelector.addGoal(4, this.beePollinateGoal);
        this.goalSelector.addGoal(5, new PathfinderGoalFollowParent(this, 1.25D));
        this.goalSelector.addGoal(5, new EntityBee.i());
        this.goToHiveGoal = new EntityBee.e();
        this.goalSelector.addGoal(5, this.goToHiveGoal);
        this.goToKnownFlowerGoal = new EntityBee.f();
        this.goalSelector.addGoal(6, this.goToKnownFlowerGoal);
        this.goalSelector.addGoal(7, new EntityBee.g());
        this.goalSelector.addGoal(8, new EntityBee.l());
        this.goalSelector.addGoal(9, new PathfinderGoalFloat(this));
        this.targetSelector.addGoal(1, (new EntityBee.h(this)).setAlertOthers(new Class[0]));
        this.targetSelector.addGoal(2, new EntityBee.c(this));
        this.targetSelector.addGoal(3, new PathfinderGoalUniversalAngerReset(this, true));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        // CraftBukkit start - selectively save data
        addAdditionalSaveData(valueoutput, true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput, boolean includeAll) {
        // CraftBukkit end
        super.addAdditionalSaveData(valueoutput);
        // CraftBukkit start - selectively save hive
        if (includeAll) {
            valueoutput.storeNullable("hive_pos", BlockPosition.CODEC, this.hivePos);
        }
        // CraftBukkit end
        // CraftBukkit start - selectively save flower
        if (includeAll) {
            valueoutput.storeNullable("flower_pos", BlockPosition.CODEC, this.savedFlowerPos);
        }
        // CraftBukkit end
        valueoutput.putBoolean("HasNectar", this.hasNectar());
        valueoutput.putBoolean("HasStung", this.hasStung());
        valueoutput.putInt("TicksSincePollination", this.ticksWithoutNectarSinceExitingHive);
        valueoutput.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        valueoutput.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
        this.addPersistentAngerSaveData(valueoutput);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setHasNectar(valueinput.getBooleanOr("HasNectar", false));
        this.setHasStung(valueinput.getBooleanOr("HasStung", false));
        this.ticksWithoutNectarSinceExitingHive = valueinput.getIntOr("TicksSincePollination", 0);
        this.stayOutOfHiveCountdown = valueinput.getIntOr("CannotEnterHiveTicks", 0);
        this.numCropsGrownSincePollination = valueinput.getIntOr("CropsGrownSincePollination", 0);
        this.hivePos = (BlockPosition) valueinput.read("hive_pos", BlockPosition.CODEC).orElse(null); // CraftBukkit - decompile error
        this.savedFlowerPos = (BlockPosition) valueinput.read("flower_pos", BlockPosition.CODEC).orElse(null); // CraftBukkit - decompile error
        this.readPersistentAngerSaveData(this.level(), valueinput);
    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        DamageSource damagesource = this.damageSources().sting(this);
        boolean flag = entity.hurtServer(worldserver, damagesource, (float) ((int) this.getAttributeValue(GenericAttributes.ATTACK_DAMAGE)));

        if (flag) {
            EnchantmentManager.doPostAttackEffects(worldserver, entity, damagesource);
            if (entity instanceof EntityLiving) {
                EntityLiving entityliving = (EntityLiving) entity;

                entityliving.setStingerCount(entityliving.getStingerCount() + 1);
                int i = 0;

                if (this.level().getDifficulty() == EnumDifficulty.NORMAL) {
                    i = 10;
                } else if (this.level().getDifficulty() == EnumDifficulty.HARD) {
                    i = 18;
                }

                if (i > 0) {
                    entityliving.addEffect(new MobEffect(MobEffects.POISON, i * 20, 0), this, EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

            this.setHasStung(true);
            this.stopBeingAngry();
            this.playSound(SoundEffects.BEE_STING, 1.0F, 1.0F);
        }

        return flag;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasNectar() && this.getCropsGrownSincePollination() < 10 && this.random.nextFloat() < 0.05F) {
            for (int i = 0; i < this.random.nextInt(2) + 1; ++i) {
                this.spawnFluidParticle(this.level(), this.getX() - (double) 0.3F, this.getX() + (double) 0.3F, this.getZ() - (double) 0.3F, this.getZ() + (double) 0.3F, this.getY(0.5D), Particles.FALLING_NECTAR);
            }
        }

        this.updateRollAmount();
    }

    private void spawnFluidParticle(World world, double d0, double d1, double d2, double d3, double d4, ParticleParam particleparam) {
        world.addParticle(particleparam, MathHelper.lerp(world.random.nextDouble(), d0, d1), d4, MathHelper.lerp(world.random.nextDouble(), d2, d3), 0.0D, 0.0D, 0.0D);
    }

    void pathfindRandomlyTowards(BlockPosition blockposition) {
        Vec3D vec3d = Vec3D.atBottomCenterOf(blockposition);
        int i = 0;
        BlockPosition blockposition1 = this.blockPosition();
        int j = (int) vec3d.y - blockposition1.getY();

        if (j > 2) {
            i = 4;
        } else if (j < -2) {
            i = -4;
        }

        int k = 6;
        int l = 8;
        int i1 = blockposition1.distManhattan(blockposition);

        if (i1 < 15) {
            k = i1 / 2;
            l = i1 / 2;
        }

        Vec3D vec3d1 = AirRandomPos.getPosTowards(this, k, l, i, vec3d, (double) ((float) Math.PI / 10F));

        if (vec3d1 != null) {
            this.navigation.setMaxVisitedNodesMultiplier(0.5F);
            this.navigation.moveTo(vec3d1.x, vec3d1.y, vec3d1.z, 1.0D);
        }
    }

    @Nullable
    public BlockPosition getSavedFlowerPos() {
        return this.savedFlowerPos;
    }

    public boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    public void setSavedFlowerPos(BlockPosition blockposition) {
        this.savedFlowerPos = blockposition;
    }

    @VisibleForDebug
    public int getTravellingTicks() {
        return Math.max(this.goToHiveGoal.travellingTicks, this.goToKnownFlowerGoal.travellingTicks);
    }

    @VisibleForDebug
    public List<BlockPosition> getBlacklistedHives() {
        return this.goToHiveGoal.blacklistedTargets;
    }

    private boolean isTiredOfLookingForNectar() {
        return this.ticksWithoutNectarSinceExitingHive > 3600;
    }

    void dropHive() {
        this.hivePos = null;
        this.remainingCooldownBeforeLocatingNewHive = 200;
    }

    void dropFlower() {
        this.savedFlowerPos = null;
        this.remainingCooldownBeforeLocatingNewFlower = MathHelper.nextInt(this.random, 20, 60);
    }

    boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.beePollinateGoal.isPollinating() && !this.hasStung() && this.getTarget() == null) {
            boolean flag = this.isTiredOfLookingForNectar() || isNightOrRaining(this.level()) || this.hasNectar();

            return flag && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    public static boolean isNightOrRaining(World world) {
        return world.dimensionType().hasSkyLight() && (world.isDarkOutside() || world.isRaining());
    }

    public void setStayOutOfHiveCountdown(int i) {
        this.stayOutOfHiveCountdown = i;
    }

    public float getRollAmount(float f) {
        return MathHelper.lerp(f, this.rollAmountO, this.rollAmount);
    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }

    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        boolean flag = this.hasStung();

        if (this.isInWater()) {
            ++this.underWaterTicks;
        } else {
            this.underWaterTicks = 0;
        }

        if (this.underWaterTicks > 20) {
            this.hurtServer(worldserver, this.damageSources().drown(), 1.0F);
        }

        if (flag) {
            ++this.timeSinceSting;
            if (this.timeSinceSting % 5 == 0 && this.random.nextInt(MathHelper.clamp(1200 - this.timeSinceSting, 1, 1200)) == 0) {
                this.hurtServer(worldserver, this.damageSources().generic(), this.getHealth());
            }
        }

        if (!this.hasNectar()) {
            ++this.ticksWithoutNectarSinceExitingHive;
        }

        this.updatePersistentAnger(worldserver, false);
    }

    public void resetTicksWithoutNectarSinceExitingHive() {
        this.ticksWithoutNectarSinceExitingHive = 0;
    }

    private boolean isHiveNearFire() {
        TileEntityBeehive tileentitybeehive = this.getBeehiveBlockEntity();

        return tileentitybeehive != null && tileentitybeehive.isFireNearby();
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return (Integer) this.entityData.get(EntityBee.DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int i) {
        this.entityData.set(EntityBee.DATA_REMAINING_ANGER_TIME, i);
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID uuid) {
        this.persistentAngerTarget = uuid;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(EntityBee.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    private boolean doesHiveHaveSpace(BlockPosition blockposition) {
        TileEntity tileentity = this.level().getBlockEntity(blockposition);

        return tileentity instanceof TileEntityBeehive ? !((TileEntityBeehive) tileentity).isFull() : false;
    }

    @VisibleForDebug
    public boolean hasHive() {
        return this.hivePos != null;
    }

    @Nullable
    @VisibleForDebug
    public BlockPosition getHivePos() {
        return this.hivePos;
    }

    @VisibleForDebug
    public PathfinderGoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        PacketDebug.sendBeeInfo(this);
    }

    int getCropsGrownSincePollination() {
        return this.numCropsGrownSincePollination;
    }

    private void resetNumCropsGrownSincePollination() {
        this.numCropsGrownSincePollination = 0;
    }

    void incrementNumCropsGrownSincePollination() {
        ++this.numCropsGrownSincePollination;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            if (this.stayOutOfHiveCountdown > 0) {
                --this.stayOutOfHiveCountdown;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                --this.remainingCooldownBeforeLocatingNewHive;
            }

            if (this.remainingCooldownBeforeLocatingNewFlower > 0) {
                --this.remainingCooldownBeforeLocatingNewFlower;
            }

            boolean flag = this.isAngry() && !this.hasStung() && this.getTarget() != null && this.getTarget().distanceToSqr((Entity) this) < 4.0D;

            this.setRolling(flag);
            if (this.tickCount % 20 == 0 && !this.isHiveValid()) {
                this.hivePos = null;
            }
        }

    }

    @Nullable
    TileEntityBeehive getBeehiveBlockEntity() {
        return this.hivePos == null ? null : (this.isTooFarAway(this.hivePos) ? null : (TileEntityBeehive) this.level().getBlockEntity(this.hivePos, TileEntityTypes.BEEHIVE).orElse(null)); // CraftBukkit - decompile error
    }

    boolean isHiveValid() {
        return this.getBeehiveBlockEntity() != null;
    }

    public boolean hasNectar() {
        return this.getFlag(8);
    }

    public void setHasNectar(boolean flag) {
        if (flag) {
            this.resetTicksWithoutNectarSinceExitingHive();
        }

        this.setFlag(8, flag);
    }

    public boolean hasStung() {
        return this.getFlag(4);
    }

    public void setHasStung(boolean flag) {
        this.setFlag(4, flag);
    }

    private boolean isRolling() {
        return this.getFlag(2);
    }

    private void setRolling(boolean flag) {
        this.setFlag(2, flag);
    }

    boolean isTooFarAway(BlockPosition blockposition) {
        return !this.closerThan(blockposition, 48);
    }

    private void setFlag(int i, boolean flag) {
        if (flag) {
            this.entityData.set(EntityBee.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(EntityBee.DATA_FLAGS_ID) | i));
        } else {
            this.entityData.set(EntityBee.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(EntityBee.DATA_FLAGS_ID) & ~i));
        }

    }

    private boolean getFlag(int i) {
        return ((Byte) this.entityData.get(EntityBee.DATA_FLAGS_ID) & i) != 0;
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 10.0D).add(GenericAttributes.FLYING_SPEED, (double) 0.6F).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    protected NavigationAbstract createNavigation(World world) {
        NavigationFlying navigationflying = new NavigationFlying(this, world) {
            @Override
            public boolean isStableDestination(BlockPosition blockposition) {
                return !this.level.getBlockState(blockposition.below()).isAir();
            }

            @Override
            public void tick() {
                if (!EntityBee.this.beePollinateGoal.isPollinating()) {
                    super.tick();
                }
            }
        };

        navigationflying.setCanOpenDoors(false);
        navigationflying.setCanFloat(false);
        navigationflying.setRequiredPathLength(48.0F);
        return navigationflying;
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (this.isFood(itemstack)) {
            Item item = itemstack.getItem();

            if (item instanceof ItemBlock) {
                ItemBlock itemblock = (ItemBlock) item;
                Block block = itemblock.getBlock();

                if (block instanceof BlockFlowers) {
                    BlockFlowers blockflowers = (BlockFlowers) block;
                    MobEffect mobeffect = blockflowers.getBeeInteractionEffect();

                    if (mobeffect != null) {
                        this.usePlayerItem(entityhuman, enumhand, itemstack);
                        if (!this.level().isClientSide) {
                            this.addEffect(mobeffect);
                        }

                        return EnumInteractionResult.SUCCESS;
                    }
                }
            }
        }

        return super.mobInteract(entityhuman, enumhand);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.BEE_FOOD);
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {}

    @Override
    protected SoundEffect getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.BEE_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.BEE_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Nullable
    @Override
    public EntityBee getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        return EntityTypes.BEE.create(worldserver, EntitySpawnReason.BREEDING);
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, IBlockData iblockdata, BlockPosition blockposition) {}

    @Override
    public boolean isFlapping() {
        return this.isFlying() && this.tickCount % EntityBee.TICKS_PER_FLAP == 0;
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    public void dropOffNectar() {
        this.setHasNectar(false);
        this.resetNumCropsGrownSincePollination();
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else {
            // CraftBukkit start - Only stop pollinating if entity was damaged
            boolean result = super.hurtServer(worldserver, damagesource, f);
            if (!result) {
                return result;
            }
            // CraftBukkit end
            this.beePollinateGoal.stopPollinating();
            return result; // CraftBukkit
        }
    }

    @Override
    protected void jumpInLiquid(TagKey<FluidType> tagkey) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.01D, 0.0D));
    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) (0.5F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.2F));
    }

    boolean closerThan(BlockPosition blockposition, int i) {
        return blockposition.closerThan(this.blockPosition(), (double) i);
    }

    public void setHivePos(BlockPosition blockposition) {
        this.hivePos = blockposition;
    }

    public static boolean attractsBees(IBlockData iblockdata) {
        return iblockdata.is(TagsBlock.BEE_ATTRACTIVE) ? ((Boolean) iblockdata.getValueOrElse(BlockProperties.WATERLOGGED, false) ? false : (iblockdata.is(Blocks.SUNFLOWER) ? iblockdata.getValue(BlockTallPlant.HALF) == BlockPropertyDoubleBlockHalf.UPPER : true)) : false;
    }

    private class h extends PathfinderGoalHurtByTarget {

        h(final EntityBee entitybee) {
            super(entitybee);
        }

        @Override
        public boolean canContinueToUse() {
            return EntityBee.this.isAngry() && super.canContinueToUse();
        }

        @Override
        protected void alertOther(EntityInsentient entityinsentient, EntityLiving entityliving) {
            if (entityinsentient instanceof EntityBee && this.mob.hasLineOfSight(entityliving)) {
                entityinsentient.setTarget(entityliving, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY, true); // CraftBukkit - reason
            }

        }
    }

    private static class c extends PathfinderGoalNearestAttackableTarget<EntityHuman> {

        c(EntityBee entitybee) {
            // Objects.requireNonNull(entitybee); // CraftBukkit - decompile error
            super(entitybee, EntityHuman.class, 10, true, false, entitybee::isAngryAt);
        }

        @Override
        public boolean canUse() {
            return this.beeCanTarget() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            boolean flag = this.beeCanTarget();

            if (flag && this.mob.getTarget() != null) {
                return super.canContinueToUse();
            } else {
                this.targetMob = null;
                return false;
            }
        }

        private boolean beeCanTarget() {
            EntityBee entitybee = (EntityBee) this.mob;

            return entitybee.isAngry() && !entitybee.hasStung();
        }
    }

    private abstract class a extends PathfinderGoal {

        a() {}

        public abstract boolean canBeeUse();

        public abstract boolean canBeeContinueToUse();

        @Override
        public boolean canUse() {
            return this.canBeeUse() && !EntityBee.this.isAngry();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canBeeContinueToUse() && !EntityBee.this.isAngry();
        }
    }

    private class l extends PathfinderGoal {

        l() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            return EntityBee.this.navigation.isDone() && EntityBee.this.random.nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return EntityBee.this.navigation.isInProgress();
        }

        @Override
        public void start() {
            Vec3D vec3d = this.findPos();

            if (vec3d != null) {
                EntityBee.this.navigation.moveTo(EntityBee.this.navigation.createPath(BlockPosition.containing(vec3d), 1), 1.0D);
            }

        }

        @Nullable
        private Vec3D findPos() {
            Vec3D vec3d;

            if (EntityBee.this.isHiveValid() && !EntityBee.this.closerThan(EntityBee.this.hivePos, this.getWanderThreshold())) {
                Vec3D vec3d1 = Vec3D.atCenterOf(EntityBee.this.hivePos);

                vec3d = vec3d1.subtract(EntityBee.this.position()).normalize();
            } else {
                vec3d = EntityBee.this.getViewVector(0.0F);
            }

            int i = 8;
            Vec3D vec3d2 = HoverRandomPos.getPos(EntityBee.this, 8, 7, vec3d.x, vec3d.z, ((float) Math.PI / 2F), 3, 1);

            return vec3d2 != null ? vec3d2 : AirAndWaterRandomPos.getPos(EntityBee.this, 8, 4, -2, vec3d.x, vec3d.z, (double) ((float) Math.PI / 2F));
        }

        private int getWanderThreshold() {
            int i = !EntityBee.this.hasHive() && !EntityBee.this.hasSavedFlowerPos() ? 16 : 24;

            return 48 - i;
        }
    }

    @VisibleForDebug
    public class e extends EntityBee.a {

        public static final int MAX_TRAVELLING_TICKS = 2400;
        int travellingTicks;
        private static final int MAX_BLACKLISTED_TARGETS = 3;
        final List<BlockPosition> blacklistedTargets = Lists.newArrayList();
        @Nullable
        private PathEntity lastPath;
        private static final int TICKS_BEFORE_HIVE_DROP = 60;
        private int ticksStuck;

        e() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            return EntityBee.this.hivePos != null && !EntityBee.this.isTooFarAway(EntityBee.this.hivePos) && !EntityBee.this.hasHome() && EntityBee.this.wantsToEnterHive() && !this.hasReachedTarget(EntityBee.this.hivePos) && EntityBee.this.level().getBlockState(EntityBee.this.hivePos).is(TagsBlock.BEEHIVES);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            this.ticksStuck = 0;
            EntityBee.this.navigation.stop();
            EntityBee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (EntityBee.this.hivePos != null) {
                ++this.travellingTicks;
                if (this.travellingTicks > this.adjustedTickDelay(2400)) {
                    this.dropAndBlacklistHive();
                } else if (!EntityBee.this.navigation.isInProgress()) {
                    if (!EntityBee.this.closerThan(EntityBee.this.hivePos, 16)) {
                        if (EntityBee.this.isTooFarAway(EntityBee.this.hivePos)) {
                            EntityBee.this.dropHive();
                        } else {
                            EntityBee.this.pathfindRandomlyTowards(EntityBee.this.hivePos);
                        }
                    } else {
                        boolean flag = this.pathfindDirectlyTowards(EntityBee.this.hivePos);

                        if (!flag) {
                            this.dropAndBlacklistHive();
                        } else if (this.lastPath != null && EntityBee.this.navigation.getPath().sameAs(this.lastPath)) {
                            ++this.ticksStuck;
                            if (this.ticksStuck > 60) {
                                EntityBee.this.dropHive();
                                this.ticksStuck = 0;
                            }
                        } else {
                            this.lastPath = EntityBee.this.navigation.getPath();
                        }

                    }
                }
            }
        }

        private boolean pathfindDirectlyTowards(BlockPosition blockposition) {
            int i = EntityBee.this.closerThan(blockposition, 3) ? 1 : 2;

            EntityBee.this.navigation.setMaxVisitedNodesMultiplier(10.0F);
            EntityBee.this.navigation.moveTo((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), i, 1.0D);
            return EntityBee.this.navigation.getPath() != null && EntityBee.this.navigation.getPath().canReach();
        }

        boolean isTargetBlacklisted(BlockPosition blockposition) {
            return this.blacklistedTargets.contains(blockposition);
        }

        private void blacklistTarget(BlockPosition blockposition) {
            this.blacklistedTargets.add(blockposition);

            while (this.blacklistedTargets.size() > 3) {
                this.blacklistedTargets.remove(0);
            }

        }

        void clearBlacklist() {
            this.blacklistedTargets.clear();
        }

        private void dropAndBlacklistHive() {
            if (EntityBee.this.hivePos != null) {
                this.blacklistTarget(EntityBee.this.hivePos);
            }

            EntityBee.this.dropHive();
        }

        private boolean hasReachedTarget(BlockPosition blockposition) {
            if (EntityBee.this.closerThan(blockposition, 2)) {
                return true;
            } else {
                PathEntity pathentity = EntityBee.this.navigation.getPath();

                return pathentity != null && pathentity.getTarget().equals(blockposition) && pathentity.canReach() && pathentity.isDone();
            }
        }
    }

    public class f extends EntityBee.a {

        private static final int MAX_TRAVELLING_TICKS = 2400;
        int travellingTicks;

        f() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            return EntityBee.this.savedFlowerPos != null && !EntityBee.this.hasHome() && this.wantsToGoToKnownFlower() && !EntityBee.this.closerThan(EntityBee.this.savedFlowerPos, 2);
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void start() {
            this.travellingTicks = 0;
            super.start();
        }

        @Override
        public void stop() {
            this.travellingTicks = 0;
            EntityBee.this.navigation.stop();
            EntityBee.this.navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (EntityBee.this.savedFlowerPos != null) {
                ++this.travellingTicks;
                if (this.travellingTicks > this.adjustedTickDelay(2400)) {
                    EntityBee.this.dropFlower();
                } else if (!EntityBee.this.navigation.isInProgress()) {
                    if (EntityBee.this.isTooFarAway(EntityBee.this.savedFlowerPos)) {
                        EntityBee.this.dropFlower();
                    } else {
                        EntityBee.this.pathfindRandomlyTowards(EntityBee.this.savedFlowerPos);
                    }
                }
            }
        }

        private boolean wantsToGoToKnownFlower() {
            return EntityBee.this.ticksWithoutNectarSinceExitingHive > 600;
        }
    }

    private class j extends ControllerLook {

        j(final EntityInsentient entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void tick() {
            if (!EntityBee.this.isAngry()) {
                super.tick();
            }
        }

        @Override
        protected boolean resetXRotOnTick() {
            return !EntityBee.this.beePollinateGoal.isPollinating();
        }
    }

    private class k extends EntityBee.a {

        private static final int MIN_POLLINATION_TICKS = 400;
        private static final double ARRIVAL_THRESHOLD = 0.1D;
        private static final int POSITION_CHANGE_CHANCE = 25;
        private static final float SPEED_MODIFIER = 0.35F;
        private static final float HOVER_HEIGHT_WITHIN_FLOWER = 0.6F;
        private static final float HOVER_POS_OFFSET = 0.33333334F;
        private static final int FLOWER_SEARCH_RADIUS = 5;
        private int successfulPollinatingTicks;
        private int lastSoundPlayedTick;
        private boolean pollinating;
        @Nullable
        private Vec3D hoverPos;
        private int pollinatingTicks;
        private static final int MAX_POLLINATING_TICKS = 600;
        private Long2LongOpenHashMap unreachableFlowerCache = new Long2LongOpenHashMap();

        k() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canBeeUse() {
            if (EntityBee.this.remainingCooldownBeforeLocatingNewFlower > 0) {
                return false;
            } else if (EntityBee.this.hasNectar()) {
                return false;
            } else if (EntityBee.this.level().isRaining()) {
                return false;
            } else {
                Optional<BlockPosition> optional = this.findNearbyFlower();

                if (optional.isPresent()) {
                    EntityBee.this.savedFlowerPos = (BlockPosition) optional.get();
                    EntityBee.this.navigation.moveTo((double) EntityBee.this.savedFlowerPos.getX() + 0.5D, (double) EntityBee.this.savedFlowerPos.getY() + 0.5D, (double) EntityBee.this.savedFlowerPos.getZ() + 0.5D, (double) 1.2F);
                    return true;
                } else {
                    EntityBee.this.remainingCooldownBeforeLocatingNewFlower = MathHelper.nextInt(EntityBee.this.random, 20, 60);
                    return false;
                }
            }
        }

        @Override
        public boolean canBeeContinueToUse() {
            return !this.pollinating ? false : (!EntityBee.this.hasSavedFlowerPos() ? false : (EntityBee.this.level().isRaining() ? false : (this.hasPollinatedLongEnough() ? EntityBee.this.random.nextFloat() < 0.2F : true)));
        }

        private boolean hasPollinatedLongEnough() {
            return this.successfulPollinatingTicks > 400;
        }

        boolean isPollinating() {
            return this.pollinating;
        }

        void stopPollinating() {
            this.pollinating = false;
        }

        @Override
        public void start() {
            this.successfulPollinatingTicks = 0;
            this.pollinatingTicks = 0;
            this.lastSoundPlayedTick = 0;
            this.pollinating = true;
            EntityBee.this.resetTicksWithoutNectarSinceExitingHive();
        }

        @Override
        public void stop() {
            if (this.hasPollinatedLongEnough()) {
                EntityBee.this.setHasNectar(true);
            }

            this.pollinating = false;
            EntityBee.this.navigation.stop();
            EntityBee.this.remainingCooldownBeforeLocatingNewFlower = 200;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (EntityBee.this.hasSavedFlowerPos()) {
                ++this.pollinatingTicks;
                if (this.pollinatingTicks > 600) {
                    EntityBee.this.dropFlower();
                    this.pollinating = false;
                    EntityBee.this.remainingCooldownBeforeLocatingNewFlower = 200;
                } else {
                    Vec3D vec3d = Vec3D.atBottomCenterOf(EntityBee.this.savedFlowerPos).add(0.0D, (double) 0.6F, 0.0D);

                    if (vec3d.distanceTo(EntityBee.this.position()) > 1.0D) {
                        this.hoverPos = vec3d;
                        this.setWantedPos();
                    } else {
                        if (this.hoverPos == null) {
                            this.hoverPos = vec3d;
                        }

                        boolean flag = EntityBee.this.position().distanceTo(this.hoverPos) <= 0.1D;
                        boolean flag1 = true;

                        if (!flag && this.pollinatingTicks > 600) {
                            EntityBee.this.dropFlower();
                        } else {
                            if (flag) {
                                boolean flag2 = EntityBee.this.random.nextInt(25) == 0;

                                if (flag2) {
                                    this.hoverPos = new Vec3D(vec3d.x() + (double) this.getOffset(), vec3d.y(), vec3d.z() + (double) this.getOffset());
                                    EntityBee.this.navigation.stop();
                                } else {
                                    flag1 = false;
                                }

                                EntityBee.this.getLookControl().setLookAt(vec3d.x(), vec3d.y(), vec3d.z());
                            }

                            if (flag1) {
                                this.setWantedPos();
                            }

                            ++this.successfulPollinatingTicks;
                            if (EntityBee.this.random.nextFloat() < 0.05F && this.successfulPollinatingTicks > this.lastSoundPlayedTick + 60) {
                                this.lastSoundPlayedTick = this.successfulPollinatingTicks;
                                EntityBee.this.playSound(SoundEffects.BEE_POLLINATE, 1.0F, 1.0F);
                            }

                        }
                    }
                }
            }
        }

        private void setWantedPos() {
            EntityBee.this.getMoveControl().setWantedPosition(this.hoverPos.x(), this.hoverPos.y(), this.hoverPos.z(), (double) 0.35F);
        }

        private float getOffset() {
            return (EntityBee.this.random.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
        }

        private Optional<BlockPosition> findNearbyFlower() {
            Iterable<BlockPosition> iterable = BlockPosition.withinManhattan(EntityBee.this.blockPosition(), 5, 5, 5);
            Long2LongOpenHashMap long2longopenhashmap = new Long2LongOpenHashMap();

            for (BlockPosition blockposition : iterable) {
                long i = this.unreachableFlowerCache.getOrDefault(blockposition.asLong(), Long.MIN_VALUE);

                if (EntityBee.this.level().getGameTime() < i) {
                    long2longopenhashmap.put(blockposition.asLong(), i);
                } else if (EntityBee.attractsBees(EntityBee.this.level().getBlockState(blockposition))) {
                    PathEntity pathentity = EntityBee.this.navigation.createPath(blockposition, 1);

                    if (pathentity != null && pathentity.canReach()) {
                        return Optional.of(blockposition);
                    }

                    long2longopenhashmap.put(blockposition.asLong(), EntityBee.this.level().getGameTime() + 600L);
                }
            }

            this.unreachableFlowerCache = long2longopenhashmap;
            return Optional.empty();
        }
    }

    private class i extends EntityBee.a {

        i() {}

        @Override
        public boolean canBeeUse() {
            return EntityBee.this.remainingCooldownBeforeLocatingNewHive == 0 && !EntityBee.this.hasHive() && EntityBee.this.wantsToEnterHive();
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            EntityBee.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPosition> list = this.findNearbyHivesWithSpace();

            if (!list.isEmpty()) {
                for (BlockPosition blockposition : list) {
                    if (!EntityBee.this.goToHiveGoal.isTargetBlacklisted(blockposition)) {
                        EntityBee.this.hivePos = blockposition;
                        return;
                    }
                }

                EntityBee.this.goToHiveGoal.clearBlacklist();
                EntityBee.this.hivePos = (BlockPosition) list.get(0);
            }
        }

        private List<BlockPosition> findNearbyHivesWithSpace() {
            BlockPosition blockposition = EntityBee.this.blockPosition();
            VillagePlace villageplace = ((WorldServer) EntityBee.this.level()).getPoiManager();
            Stream<VillagePlaceRecord> stream = villageplace.getInRange((holder) -> {
                return holder.is(PoiTypeTags.BEE_HOME);
            }, blockposition, 20, VillagePlace.Occupancy.ANY);

            return (List) stream.map(VillagePlaceRecord::getPos).filter(EntityBee.this::doesHiveHaveSpace).sorted(Comparator.comparingDouble((blockposition1) -> {
                return blockposition1.distSqr(blockposition);
            })).collect(Collectors.toList());
        }
    }

    private class g extends EntityBee.a {

        static final int GROW_CHANCE = 30;

        g() {}

        @Override
        public boolean canBeeUse() {
            return EntityBee.this.getCropsGrownSincePollination() >= 10 ? false : (EntityBee.this.random.nextFloat() < 0.3F ? false : EntityBee.this.hasNectar() && EntityBee.this.isHiveValid());
        }

        @Override
        public boolean canBeeContinueToUse() {
            return this.canBeeUse();
        }

        @Override
        public void tick() {
            if (EntityBee.this.random.nextInt(this.adjustedTickDelay(30)) == 0) {
                for (int i = 1; i <= 2; ++i) {
                    BlockPosition blockposition = EntityBee.this.blockPosition().below(i);
                    IBlockData iblockdata = EntityBee.this.level().getBlockState(blockposition);
                    Block block = iblockdata.getBlock();
                    IBlockData iblockdata1 = null;

                    if (iblockdata.is(TagsBlock.BEE_GROWABLES)) {
                        if (block instanceof BlockCrops) {
                            BlockCrops blockcrops = (BlockCrops) block;

                            if (!blockcrops.isMaxAge(iblockdata)) {
                                iblockdata1 = blockcrops.getStateForAge(blockcrops.getAge(iblockdata) + 1);
                            }
                        } else if (block instanceof BlockStem) {
                            int j = (Integer) iblockdata.getValue(BlockStem.AGE);

                            if (j < 7) {
                                iblockdata1 = (IBlockData) iblockdata.setValue(BlockStem.AGE, j + 1);
                            }
                        } else if (iblockdata.is(Blocks.SWEET_BERRY_BUSH)) {
                            int k = (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE);

                            if (k < 3) {
                                iblockdata1 = (IBlockData) iblockdata.setValue(BlockSweetBerryBush.AGE, k + 1);
                            }
                        } else if (iblockdata.is(Blocks.CAVE_VINES) || iblockdata.is(Blocks.CAVE_VINES_PLANT)) {
                            IBlockFragilePlantElement iblockfragileplantelement = (IBlockFragilePlantElement) iblockdata.getBlock();

                            if (iblockfragileplantelement.isValidBonemealTarget(EntityBee.this.level(), blockposition, iblockdata)) {
                                iblockfragileplantelement.performBonemeal((WorldServer) EntityBee.this.level(), EntityBee.this.random, blockposition, iblockdata);
                                iblockdata1 = EntityBee.this.level().getBlockState(blockposition);
                            }
                        }

                        if (iblockdata1 != null && CraftEventFactory.callEntityChangeBlockEvent(EntityBee.this, blockposition, iblockdata1)) { // CraftBukkit
                            EntityBee.this.level().levelEvent(2011, blockposition, 15);
                            EntityBee.this.level().setBlockAndUpdate(blockposition, iblockdata1);
                            EntityBee.this.incrementNumCropsGrownSincePollination();
                        }
                    }
                }

            }
        }
    }

    private class b extends PathfinderGoalMeleeAttack {

        b(final EntityCreature entitycreature, final double d0, final boolean flag) {
            super(entitycreature, d0, flag);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && EntityBee.this.isAngry() && !EntityBee.this.hasStung();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && EntityBee.this.isAngry() && !EntityBee.this.hasStung();
        }
    }

    private class d extends EntityBee.a {

        d() {}

        @Override
        public boolean canBeeUse() {
            if (EntityBee.this.hivePos != null && EntityBee.this.wantsToEnterHive() && EntityBee.this.hivePos.closerToCenterThan(EntityBee.this.position(), 2.0D)) {
                TileEntityBeehive tileentitybeehive = EntityBee.this.getBeehiveBlockEntity();

                if (tileentitybeehive != null) {
                    if (!tileentitybeehive.isFull()) {
                        return true;
                    }

                    EntityBee.this.hivePos = null;
                }
            }

            return false;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            TileEntityBeehive tileentitybeehive = EntityBee.this.getBeehiveBlockEntity();

            if (tileentitybeehive != null) {
                tileentitybeehive.addOccupant(EntityBee.this);
            }

        }
    }

    private class m extends EntityBee.a {

        private final int validateFlowerCooldown;
        private long lastValidateTick;

        m() {
            this.validateFlowerCooldown = MathHelper.nextInt(EntityBee.this.random, 20, 40);
            this.lastValidateTick = -1L;
        }

        @Override
        public void start() {
            if (EntityBee.this.savedFlowerPos != null && EntityBee.this.level().isLoaded(EntityBee.this.savedFlowerPos) && !this.isFlower(EntityBee.this.savedFlowerPos)) {
                EntityBee.this.dropFlower();
            }

            this.lastValidateTick = EntityBee.this.level().getGameTime();
        }

        @Override
        public boolean canBeeUse() {
            return EntityBee.this.level().getGameTime() > this.lastValidateTick + (long) this.validateFlowerCooldown;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }

        private boolean isFlower(BlockPosition blockposition) {
            return EntityBee.attractsBees(EntityBee.this.level().getBlockState(blockposition));
        }
    }

    private class n extends EntityBee.a {

        private final int VALIDATE_HIVE_COOLDOWN;
        private long lastValidateTick;

        n() {
            this.VALIDATE_HIVE_COOLDOWN = MathHelper.nextInt(EntityBee.this.random, 20, 40);
            this.lastValidateTick = -1L;
        }

        @Override
        public void start() {
            if (EntityBee.this.hivePos != null && EntityBee.this.level().isLoaded(EntityBee.this.hivePos) && !EntityBee.this.isHiveValid()) {
                EntityBee.this.dropHive();
            }

            this.lastValidateTick = EntityBee.this.level().getGameTime();
        }

        @Override
        public boolean canBeeUse() {
            return EntityBee.this.level().getGameTime() > this.lastValidateTick + (long) this.VALIDATE_HIVE_COOLDOWN;
        }

        @Override
        public boolean canBeeContinueToUse() {
            return false;
        }
    }
}
