package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsFluid;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityCreature;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntityPositionTypes;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeModifiable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreakDoor;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMoveThroughVillage;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRemoveBlock;
import net.minecraft.world.entity.ai.goal.PathfinderGoalZombieAttack;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.EntityChicken;
import net.minecraft.world.entity.animal.EntityIronGolem;
import net.minecraft.world.entity.animal.EntityTurtle;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.entity.npc.EntityVillagerAbstract;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class EntityZombie extends EntityMonster {

    private static final MinecraftKey SPEED_MODIFIER_BABY_ID = MinecraftKey.withDefaultNamespace("baby");
    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(EntityZombie.SPEED_MODIFIER_BABY_ID, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    private static final MinecraftKey REINFORCEMENT_CALLER_CHARGE_ID = MinecraftKey.withDefaultNamespace("reinforcement_caller_charge");
    private static final AttributeModifier ZOMBIE_REINFORCEMENT_CALLEE_CHARGE = new AttributeModifier(MinecraftKey.withDefaultNamespace("reinforcement_callee_charge"), (double) -0.05F, AttributeModifier.Operation.ADD_VALUE);
    private static final MinecraftKey LEADER_ZOMBIE_BONUS_ID = MinecraftKey.withDefaultNamespace("leader_zombie_bonus");
    private static final MinecraftKey ZOMBIE_RANDOM_SPAWN_BONUS_ID = MinecraftKey.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final DataWatcherObject<Boolean> DATA_BABY_ID = DataWatcher.<Boolean>defineId(EntityZombie.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Integer> DATA_SPECIAL_TYPE_ID = DataWatcher.<Integer>defineId(EntityZombie.class, DataWatcherRegistry.INT);
    public static final DataWatcherObject<Boolean> DATA_DROWNED_CONVERSION_ID = DataWatcher.<Boolean>defineId(EntityZombie.class, DataWatcherRegistry.BOOLEAN);
    public static final float ZOMBIE_LEADER_CHANCE = 0.05F;
    public static final int REINFORCEMENT_ATTEMPTS = 50;
    public static final int REINFORCEMENT_RANGE_MAX = 40;
    public static final int REINFORCEMENT_RANGE_MIN = 7;
    private static final int NOT_CONVERTING = -1;
    private static final EntitySize BABY_DIMENSIONS = EntityTypes.ZOMBIE.getDimensions().scale(0.5F).withEyeHeight(0.93F);
    private static final float BREAK_DOOR_CHANCE = 0.1F;
    private static final Predicate<EnumDifficulty> DOOR_BREAKING_PREDICATE = (enumdifficulty) -> {
        return enumdifficulty == EnumDifficulty.HARD;
    };
    private static final boolean DEFAULT_BABY = false;
    private static final boolean DEFAULT_CAN_BREAK_DOORS = false;
    private static final int DEFAULT_IN_WATER_TIME = 0;
    private final PathfinderGoalBreakDoor breakDoorGoal;
    private boolean canBreakDoors;
    private int inWaterTime;
    public int conversionTime;
    private int lastTick = MinecraftServer.currentTick; // CraftBukkit - add field

    public EntityZombie(EntityTypes<? extends EntityZombie> entitytypes, World world) {
        super(entitytypes, world);
        this.breakDoorGoal = new PathfinderGoalBreakDoor(this, EntityZombie.DOOR_BREAKING_PREDICATE);
        this.canBreakDoors = false;
        this.inWaterTime = 0;
    }

    public EntityZombie(World world) {
        this(EntityTypes.ZOMBIE, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(4, new EntityZombie.a(this, 1.0D, 3));
        this.goalSelector.addGoal(8, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F));
        this.goalSelector.addGoal(8, new PathfinderGoalRandomLookaround(this));
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new PathfinderGoalZombieAttack(this, 1.0D, false));
        this.goalSelector.addGoal(6, new PathfinderGoalMoveThroughVillage(this, 1.0D, true, 4, this::canBreakDoors));
        this.goalSelector.addGoal(7, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.targetSelector.addGoal(1, (new PathfinderGoalHurtByTarget(this, new Class[0])).setAlertOthers(EntityPigZombie.class));
        this.targetSelector.addGoal(2, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, true));
        if ( this.level().spigotConfig.zombieAggressiveTowardsVillager ) this.targetSelector.addGoal(3, new PathfinderGoalNearestAttackableTarget(this, EntityVillagerAbstract.class, false)); // Spigot
        this.targetSelector.addGoal(3, new PathfinderGoalNearestAttackableTarget(this, EntityIronGolem.class, true));
        this.targetSelector.addGoal(5, new PathfinderGoalNearestAttackableTarget(this, EntityTurtle.class, 10, true, false, EntityTurtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.FOLLOW_RANGE, 35.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.23F).add(GenericAttributes.ATTACK_DAMAGE, 3.0D).add(GenericAttributes.ARMOR, 2.0D).add(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityZombie.DATA_BABY_ID, false);
        datawatcher_a.define(EntityZombie.DATA_SPECIAL_TYPE_ID, 0);
        datawatcher_a.define(EntityZombie.DATA_DROWNED_CONVERSION_ID, false);
    }

    public boolean isUnderWaterConverting() {
        return (Boolean) this.getEntityData().get(EntityZombie.DATA_DROWNED_CONVERSION_ID);
    }

    public boolean canBreakDoors() {
        return this.canBreakDoors;
    }

    public void setCanBreakDoors(boolean flag) {
        if (this.navigation.canNavigateGround()) {
            if (this.canBreakDoors != flag) {
                this.canBreakDoors = flag;
                this.navigation.setCanOpenDoors(flag);
                if (flag) {
                    this.goalSelector.addGoal(1, this.breakDoorGoal);
                } else {
                    this.goalSelector.removeGoal(this.breakDoorGoal);
                }
            }
        } else if (this.canBreakDoors) {
            this.goalSelector.removeGoal(this.breakDoorGoal);
            this.canBreakDoors = false;
        }

    }

    @Override
    public boolean isBaby() {
        return (Boolean) this.getEntityData().get(EntityZombie.DATA_BABY_ID);
    }

    @Override
    protected int getBaseExperienceReward(WorldServer worldserver) {
        if (this.isBaby()) {
            this.xpReward = (int) ((double) this.xpReward * 2.5D);
        }

        return super.getBaseExperienceReward(worldserver);
    }

    @Override
    public void setBaby(boolean flag) {
        this.getEntityData().set(EntityZombie.DATA_BABY_ID, flag);
        if (this.level() != null && !this.level().isClientSide) {
            AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.MOVEMENT_SPEED);

            attributemodifiable.removeModifier(EntityZombie.SPEED_MODIFIER_BABY_ID);
            if (flag) {
                attributemodifiable.addTransientModifier(EntityZombie.SPEED_MODIFIER_BABY);
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityZombie.DATA_BABY_ID.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    protected boolean convertsInWater() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isUnderWaterConverting()) {
                // CraftBukkit start - Use wall time instead of ticks for conversion
                int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
                this.conversionTime -= elapsedTicks;
                // CraftBukkit end
                if (this.conversionTime < 0) {
                    this.doUnderWaterConversion();
                }
            } else if (this.convertsInWater()) {
                if (this.isEyeInFluid(TagsFluid.WATER)) {
                    ++this.inWaterTime;
                    if (this.inWaterTime >= 600) {
                        this.startUnderWaterConversion(300);
                    }
                } else {
                    this.inWaterTime = -1;
                }
            }
        }

        super.tick();
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
    }

    @Override
    public void aiStep() {
        if (this.isAlive()) {
            boolean flag = this.isSunSensitive() && this.isSunBurnTick();

            if (flag) {
                ItemStack itemstack = this.getItemBySlot(EnumItemSlot.HEAD);

                if (!itemstack.isEmpty()) {
                    if (itemstack.isDamageableItem()) {
                        Item item = itemstack.getItem();

                        itemstack.setDamageValue(itemstack.getDamageValue() + this.random.nextInt(2));
                        if (itemstack.getDamageValue() >= itemstack.getMaxDamage()) {
                            this.onEquippedItemBroken(item, EnumItemSlot.HEAD);
                            this.setItemSlot(EnumItemSlot.HEAD, ItemStack.EMPTY);
                        }
                    }

                    flag = false;
                }

                if (flag) {
                    this.igniteForSeconds(8.0F);
                }
            }
        }

        super.aiStep();
    }

    public void startUnderWaterConversion(int i) {
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
        this.conversionTime = i;
        this.getEntityData().set(EntityZombie.DATA_DROWNED_CONVERSION_ID, true);
    }

    protected void doUnderWaterConversion() {
        this.convertToZombieType(EntityTypes.DROWNED);
        if (!this.isSilent()) {
            this.level().levelEvent((Entity) null, 1040, this.blockPosition(), 0);
        }

    }

    protected void convertToZombieType(EntityTypes<? extends EntityZombie> entitytypes) {
        EntityZombie converted = this.convertTo(entitytypes, ConversionParams.single(this, true, true), (entityzombie) -> { // CraftBukkit
            entityzombie.handleAttributes(entityzombie.level().getCurrentDifficultyAt(entityzombie.blockPosition()).getSpecialMultiplier());
        // CraftBukkit start
        }, EntityTransformEvent.TransformReason.DROWNED, CreatureSpawnEvent.SpawnReason.DROWNED);
        if (converted == null) {
            ((Zombie) getBukkitEntity()).setConversionTime(-1); // CraftBukkit - SPIGOT-5208: End conversion to stop event spam
        }
        // CraftBukkit end
    }

    @VisibleForTesting
    public boolean convertVillagerToZombieVillager(WorldServer worldserver, EntityVillager entityvillager) {
        // CraftBukkit start
        return convertVillagerToZombieVillager(worldserver, entityvillager, this.blockPosition(), this.isSilent(), EntityTransformEvent.TransformReason.INFECTION, CreatureSpawnEvent.SpawnReason.INFECTION) != null;
    }

    public static EntityZombieVillager convertVillagerToZombieVillager(WorldServer worldserver, EntityVillager entityvillager, net.minecraft.core.BlockPosition blockPosition, boolean silent, EntityTransformEvent.TransformReason transformReason, CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        EntityZombieVillager entityzombievillager = (EntityZombieVillager) entityvillager.convertTo(EntityTypes.ZOMBIE_VILLAGER, ConversionParams.single(entityvillager, true, true), (entityzombievillager1) -> {
            entityzombievillager1.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(entityzombievillager1.blockPosition()), EntitySpawnReason.CONVERSION, new EntityZombie.GroupDataZombie(false, true));
            entityzombievillager1.setVillagerData(entityvillager.getVillagerData());
            entityzombievillager1.setGossips(entityvillager.getGossips().copy());
            entityzombievillager1.setTradeOffers(entityvillager.getOffers().copy());
            entityzombievillager1.setVillagerXp(entityvillager.getVillagerXp());
            // CraftBukkit start
            if (!silent) {
                worldserver.levelEvent((Entity) null, 1026, blockPosition, 0);
            }

        }, transformReason, spawnReason);

        return entityzombievillager;
        // CraftBukkit end
    }

    protected boolean isSunSensitive() {
        return true;
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (!super.hurtServer(worldserver, damagesource, f)) {
            return false;
        } else {
            EntityLiving entityliving = this.getTarget();

            if (entityliving == null && damagesource.getEntity() instanceof EntityLiving) {
                entityliving = (EntityLiving) damagesource.getEntity();
            }

            if (entityliving != null && worldserver.getDifficulty() == EnumDifficulty.HARD && (double) this.random.nextFloat() < this.getAttributeValue(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE) && worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                int i = MathHelper.floor(this.getX());
                int j = MathHelper.floor(this.getY());
                int k = MathHelper.floor(this.getZ());
                EntityTypes<? extends EntityZombie> entitytypes = this.getType();
                EntityZombie entityzombie = entitytypes.create(worldserver, EntitySpawnReason.REINFORCEMENT);

                if (entityzombie == null) {
                    return true;
                }

                for (int l = 0; l < 50; ++l) {
                    int i1 = i + MathHelper.nextInt(this.random, 7, 40) * MathHelper.nextInt(this.random, -1, 1);
                    int j1 = j + MathHelper.nextInt(this.random, 7, 40) * MathHelper.nextInt(this.random, -1, 1);
                    int k1 = k + MathHelper.nextInt(this.random, 7, 40) * MathHelper.nextInt(this.random, -1, 1);
                    BlockPosition blockposition = new BlockPosition(i1, j1, k1);

                    if (EntityPositionTypes.isSpawnPositionOk(entitytypes, worldserver, blockposition) && EntityPositionTypes.checkSpawnRules(entitytypes, worldserver, EntitySpawnReason.REINFORCEMENT, blockposition, worldserver.random)) {
                        entityzombie.setPos((double) i1, (double) j1, (double) k1);
                        if (!worldserver.hasNearbyAlivePlayer((double) i1, (double) j1, (double) k1, 7.0D) && worldserver.isUnobstructed(entityzombie) && worldserver.noCollision((Entity) entityzombie) && (entityzombie.canSpawnInLiquids() || !worldserver.containsAnyLiquid(entityzombie.getBoundingBox()))) {
                            entityzombie.setTarget(entityliving, EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET, true); // CraftBukkit
                            entityzombie.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(entityzombie.blockPosition()), EntitySpawnReason.REINFORCEMENT, (GroupDataEntity) null);
                            worldserver.addFreshEntityWithPassengers(entityzombie, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS); // CraftBukkit
                            AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE);
                            AttributeModifier attributemodifier = attributemodifiable.getModifier(EntityZombie.REINFORCEMENT_CALLER_CHARGE_ID);
                            double d0 = attributemodifier != null ? attributemodifier.amount() : 0.0D;

                            attributemodifiable.removeModifier(EntityZombie.REINFORCEMENT_CALLER_CHARGE_ID);
                            attributemodifiable.addPermanentModifier(new AttributeModifier(EntityZombie.REINFORCEMENT_CALLER_CHARGE_ID, d0 - 0.05D, AttributeModifier.Operation.ADD_VALUE));
                            entityzombie.getAttribute(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(EntityZombie.ZOMBIE_REINFORCEMENT_CALLEE_CHARGE);
                            break;
                        }
                    }
                }
            }

            return true;
        }
    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        boolean flag = super.doHurtTarget(worldserver, entity);

        if (flag) {
            float f = this.level().getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();

            if (this.getMainHandItem().isEmpty() && this.isOnFire() && this.random.nextFloat() < f * 0.3F) {
                // CraftBukkit start
                EntityCombustByEntityEvent event = new EntityCombustByEntityEvent(this.getBukkitEntity(), entity.getBukkitEntity(), (float) (2 * (int) f)); // PAIL: fixme
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    entity.igniteForSeconds(event.getDuration(), false);
                }
                // CraftBukkit end
            }
        }

        return flag;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.ZOMBIE_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.ZOMBIE_DEATH;
    }

    protected SoundEffect getStepSound() {
        return SoundEffects.ZOMBIE_STEP;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }

    @Override
    public EntityTypes<? extends EntityZombie> getType() {
        return (EntityTypes<? extends EntityZombie>) super.getType(); // CraftBukkit - decompile error
    }

    protected boolean canSpawnInLiquids() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        super.populateDefaultEquipmentSlots(randomsource, difficultydamagescaler);
        if (randomsource.nextFloat() < (this.level().getDifficulty() == EnumDifficulty.HARD ? 0.05F : 0.01F)) {
            int i = randomsource.nextInt(3);

            if (i == 0) {
                this.setItemSlot(EnumItemSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            } else {
                this.setItemSlot(EnumItemSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("IsBaby", this.isBaby());
        valueoutput.putBoolean("CanBreakDoors", this.canBreakDoors());
        valueoutput.putInt("InWaterTime", this.isInWater() ? this.inWaterTime : -1);
        valueoutput.putInt("DrownedConversionTime", this.isUnderWaterConverting() ? this.conversionTime : -1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setBaby(valueinput.getBooleanOr("IsBaby", false));
        this.setCanBreakDoors(valueinput.getBooleanOr("CanBreakDoors", false));
        this.inWaterTime = valueinput.getIntOr("InWaterTime", 0);
        int i = valueinput.getIntOr("DrownedConversionTime", -1);

        if (i != -1) {
            this.startUnderWaterConversion(i);
        } else {
            this.getEntityData().set(EntityZombie.DATA_DROWNED_CONVERSION_ID, false);
        }

    }

    @Override
    public boolean killedEntity(WorldServer worldserver, EntityLiving entityliving) {
        boolean flag = super.killedEntity(worldserver, entityliving);

        if ((worldserver.getDifficulty() == EnumDifficulty.NORMAL || worldserver.getDifficulty() == EnumDifficulty.HARD) && entityliving instanceof EntityVillager entityvillager) {
            if (worldserver.getDifficulty() != EnumDifficulty.HARD && this.random.nextBoolean()) {
                return flag;
            }

            if (this.convertVillagerToZombieVillager(worldserver, entityvillager)) {
                flag = false;
            }
        }

        return flag;
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityZombie.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    @Override
    public boolean canHoldItem(ItemStack itemstack) {
        return itemstack.is(TagsItem.EGGS) && this.isBaby() && this.isPassenger() ? false : super.canHoldItem(itemstack);
    }

    @Override
    public boolean wantsToPickUp(WorldServer worldserver, ItemStack itemstack) {
        return itemstack.is(Items.GLOW_INK_SAC) ? false : super.wantsToPickUp(worldserver, itemstack);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();

        groupdataentity = super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
        float f = difficultydamagescaler.getSpecialMultiplier();

        if (entityspawnreason != EntitySpawnReason.CONVERSION) {
            this.setCanPickUpLoot(randomsource.nextFloat() < 0.55F * f);
        }

        if (groupdataentity == null) {
            groupdataentity = new EntityZombie.GroupDataZombie(getSpawnAsBabyOdds(randomsource), true);
        }

        if (groupdataentity instanceof EntityZombie.GroupDataZombie entityzombie_groupdatazombie) {
            if (entityzombie_groupdatazombie.isBaby) {
                this.setBaby(true);
                if (entityzombie_groupdatazombie.canSpawnJockey) {
                    if ((double) randomsource.nextFloat() < 0.05D) {
                        List<EntityChicken> list = worldaccess.<EntityChicken>getEntitiesOfClass(EntityChicken.class, this.getBoundingBox().inflate(5.0D, 3.0D, 5.0D), IEntitySelector.ENTITY_NOT_BEING_RIDDEN);

                        if (!list.isEmpty()) {
                            EntityChicken entitychicken = (EntityChicken) list.get(0);

                            entitychicken.setChickenJockey(true);
                            this.startRiding(entitychicken);
                        }
                    } else if ((double) randomsource.nextFloat() < 0.05D) {
                        EntityChicken entitychicken1 = EntityTypes.CHICKEN.create(this.level(), EntitySpawnReason.JOCKEY);

                        if (entitychicken1 != null) {
                            entitychicken1.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            entitychicken1.finalizeSpawn(worldaccess, difficultydamagescaler, EntitySpawnReason.JOCKEY, (GroupDataEntity) null);
                            entitychicken1.setChickenJockey(true);
                            this.startRiding(entitychicken1);
                            worldaccess.addFreshEntity(entitychicken1, CreatureSpawnEvent.SpawnReason.MOUNT); // CraftBukkit
                        }
                    }
                }
            }

            this.setCanBreakDoors(randomsource.nextFloat() < f * 0.1F);
            if (entityspawnreason != EntitySpawnReason.CONVERSION) {
                this.populateDefaultEquipmentSlots(randomsource, difficultydamagescaler);
                this.populateDefaultEquipmentEnchantments(worldaccess, randomsource, difficultydamagescaler);
            }
        }

        if (this.getItemBySlot(EnumItemSlot.HEAD).isEmpty()) {
            LocalDate localdate = LocalDate.now();
            int i = localdate.get(ChronoField.DAY_OF_MONTH);
            int j = localdate.get(ChronoField.MONTH_OF_YEAR);

            if (j == 10 && i == 31 && randomsource.nextFloat() < 0.25F) {
                this.setItemSlot(EnumItemSlot.HEAD, new ItemStack(randomsource.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
                this.setDropChance(EnumItemSlot.HEAD, 0.0F);
            }
        }

        this.handleAttributes(f);
        return groupdataentity;
    }

    @VisibleForTesting
    public void setInWaterTime(int i) {
        this.inWaterTime = i;
    }

    @VisibleForTesting
    public void setConversionTime(int i) {
        this.conversionTime = i;
    }

    public static boolean getSpawnAsBabyOdds(RandomSource randomsource) {
        return randomsource.nextFloat() < 0.05F;
    }

    protected void handleAttributes(float f) {
        this.randomizeReinforcementsChance();
        this.getAttribute(GenericAttributes.KNOCKBACK_RESISTANCE).addOrReplacePermanentModifier(new AttributeModifier(EntityZombie.RANDOM_SPAWN_BONUS_ID, this.random.nextDouble() * (double) 0.05F, AttributeModifier.Operation.ADD_VALUE));
        double d0 = this.random.nextDouble() * 1.5D * (double) f;

        if (d0 > 1.0D) {
            this.getAttribute(GenericAttributes.FOLLOW_RANGE).addOrReplacePermanentModifier(new AttributeModifier(EntityZombie.ZOMBIE_RANDOM_SPAWN_BONUS_ID, d0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (this.random.nextFloat() < f * 0.05F) {
            this.getAttribute(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE).addOrReplacePermanentModifier(new AttributeModifier(EntityZombie.LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 0.25D + 0.5D, AttributeModifier.Operation.ADD_VALUE));
            this.getAttribute(GenericAttributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(EntityZombie.LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 3.0D + 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            this.setCanBreakDoors(true);
        }

    }

    protected void randomizeReinforcementsChance() {
        this.getAttribute(GenericAttributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * (double) 0.1F);
    }

    @Override
    protected void dropCustomDeathLoot(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        super.dropCustomDeathLoot(worldserver, damagesource, flag);
        Entity entity = damagesource.getEntity();

        if (entity instanceof EntityCreeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                ItemStack itemstack = this.getSkull();

                if (!itemstack.isEmpty()) {
                    entitycreeper.increaseDroppedSkulls();
                    this.spawnAtLocation(worldserver, itemstack);
                }
            }
        }

    }

    protected ItemStack getSkull() {
        return new ItemStack(Items.ZOMBIE_HEAD);
    }

    public static class GroupDataZombie implements GroupDataEntity {

        public final boolean isBaby;
        public final boolean canSpawnJockey;

        public GroupDataZombie(boolean flag, boolean flag1) {
            this.isBaby = flag;
            this.canSpawnJockey = flag1;
        }
    }

    private class a extends PathfinderGoalRemoveBlock {

        a(final EntityCreature entitycreature, final double d0, final int i) {
            super(Blocks.TURTLE_EGG, entitycreature, d0, i);
        }

        @Override
        public void playDestroyProgressSound(GeneratorAccess generatoraccess, BlockPosition blockposition) {
            generatoraccess.playSound((Entity) null, blockposition, SoundEffects.ZOMBIE_DESTROY_EGG, SoundCategory.HOSTILE, 0.5F, 0.9F + EntityZombie.this.random.nextFloat() * 0.2F);
        }

        @Override
        public void playBreakSound(World world, BlockPosition blockposition) {
            world.playSound((Entity) null, blockposition, SoundEffects.TURTLE_EGG_BREAK, SoundCategory.BLOCKS, 0.7F, 0.9F + world.random.nextFloat() * 0.2F);
        }

        @Override
        public double acceptedDistance() {
            return 1.14D;
        }
    }
}
