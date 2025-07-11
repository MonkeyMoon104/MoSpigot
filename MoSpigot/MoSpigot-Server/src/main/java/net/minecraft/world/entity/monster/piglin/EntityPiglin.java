package net.minecraft.world.entity.monster.piglin;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.InventorySubcontainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.BehaviorController;
import net.minecraft.world.entity.ai.attributes.AttributeModifiable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityCreeper;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.monster.ICrossbow;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCrossbow;
import net.minecraft.world.item.ItemProjectileWeapon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import com.mojang.serialization.Codec;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.world.item.Item;
// CraftBukkit end

public class EntityPiglin extends EntityPiglinAbstract implements ICrossbow, InventoryCarrier {

    private static final DataWatcherObject<Boolean> DATA_BABY_ID = DataWatcher.<Boolean>defineId(EntityPiglin.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Boolean> DATA_IS_CHARGING_CROSSBOW = DataWatcher.<Boolean>defineId(EntityPiglin.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Boolean> DATA_IS_DANCING = DataWatcher.<Boolean>defineId(EntityPiglin.class, DataWatcherRegistry.BOOLEAN);
    private static final MinecraftKey SPEED_MODIFIER_BABY_ID = MinecraftKey.withDefaultNamespace("baby");
    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(EntityPiglin.SPEED_MODIFIER_BABY_ID, (double) 0.2F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    private static final int MAX_HEALTH = 16;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.35F;
    private static final int ATTACK_DAMAGE = 5;
    private static final float CHANCE_OF_WEARING_EACH_ARMOUR_ITEM = 0.1F;
    private static final int MAX_PASSENGERS_ON_ONE_HOGLIN = 3;
    private static final float PROBABILITY_OF_SPAWNING_AS_BABY = 0.2F;
    private static final EntitySize BABY_DIMENSIONS = EntityTypes.PIGLIN.getDimensions().scale(0.5F).withEyeHeight(0.97F);
    private static final double PROBABILITY_OF_SPAWNING_WITH_CROSSBOW_INSTEAD_OF_SWORD = 0.5D;
    private static final boolean DEFAULT_IS_BABY = false;
    private static final boolean DEFAULT_CANNOT_HUNT = false;
    public final InventorySubcontainer inventory = new InventorySubcontainer(8);
    public boolean cannotHunt = false;
    protected static final ImmutableList<SensorType<? extends Sensor<? super EntityPiglin>>> SENSOR_TYPES = ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ITEMS, SensorType.HURT_BY, SensorType.PIGLIN_SPECIFIC_SENSOR);
    protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS, MemoryModuleType.NEARBY_ADULT_PIGLINS, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, new MemoryModuleType[]{MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.PATH, MemoryModuleType.ANGRY_AT, MemoryModuleType.UNIVERSAL_ANGER, MemoryModuleType.AVOID_TARGET, MemoryModuleType.ADMIRING_ITEM, MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM, MemoryModuleType.ADMIRING_DISABLED, MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, MemoryModuleType.CELEBRATE_LOCATION, MemoryModuleType.DANCING, MemoryModuleType.HUNTED_RECENTLY, MemoryModuleType.NEAREST_VISIBLE_BABY_HOGLIN, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, MemoryModuleType.RIDE_TARGET, MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN, MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD, MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM, MemoryModuleType.ATE_RECENTLY, MemoryModuleType.NEAREST_REPELLENT});
    // CraftBukkit start - Custom bartering and interest list
    public Set<Item> allowedBarterItems = new HashSet<>();
    public Set<Item> interestItems = new HashSet<>();
    // CraftBukkit end

    public EntityPiglin(EntityTypes<? extends EntityPiglinAbstract> entitytypes, World world) {
        super(entitytypes, world);
        this.xpReward = 5;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("IsBaby", this.isBaby());
        valueoutput.putBoolean("CannotHunt", this.cannotHunt);
        this.writeInventoryToTag(valueoutput);
        // CraftBukkit start
        ValueOutput.a<String> barterList = valueoutput.list("Bukkit.BarterList", Codec.STRING);
        allowedBarterItems.stream().map(BuiltInRegistries.ITEM::getKey).map(MinecraftKey::toString).forEach(barterList::add);
        ValueOutput.a<String> interestList = valueoutput.list("Bukkit.InterestList", Codec.STRING);
        interestItems.stream().map(BuiltInRegistries.ITEM::getKey).map(MinecraftKey::toString).forEach(interestList::add);
        // CraftBukkit end
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setBaby(valueinput.getBooleanOr("IsBaby", false));
        this.setCannotHunt(valueinput.getBooleanOr("CannotHunt", false));
        this.readInventoryFromTag(valueinput);
        // CraftBukkit start
        valueinput.list("Bukkit.BarterList", Codec.STRING).ifPresent((list -> {
            this.allowedBarterItems = list.stream().map(MinecraftKey::tryParse).map(BuiltInRegistries.ITEM::getValue).collect(Collectors.toCollection(HashSet::new));
        }));
        valueinput.list("Bukkit.InterestList", Codec.STRING).ifPresent((list -> {
            this.interestItems = list.stream().map(MinecraftKey::tryParse).map(BuiltInRegistries.ITEM::getValue).collect(Collectors.toCollection(HashSet::new));
        }));
        // CraftBukkit end
    }

    @VisibleForDebug
    @Override
    public InventorySubcontainer getInventory() {
        return this.inventory;
    }

    @Override
    protected void dropCustomDeathLoot(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        super.dropCustomDeathLoot(worldserver, damagesource, flag);
        Entity entity = damagesource.getEntity();

        if (entity instanceof EntityCreeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                ItemStack itemstack = new ItemStack(Items.PIGLIN_HEAD);

                entitycreeper.increaseDroppedSkulls();
                this.spawnAtLocation(worldserver, itemstack);
            }
        }

        this.inventory.removeAllItems().forEach((itemstack1) -> {
            this.spawnAtLocation(worldserver, itemstack1);
        });
    }

    protected ItemStack addToInventory(ItemStack itemstack) {
        return this.inventory.addItem(itemstack);
    }

    protected boolean canAddToInventory(ItemStack itemstack) {
        return this.inventory.canAddItem(itemstack);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityPiglin.DATA_BABY_ID, false);
        datawatcher_a.define(EntityPiglin.DATA_IS_CHARGING_CROSSBOW, false);
        datawatcher_a.define(EntityPiglin.DATA_IS_DANCING, false);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (EntityPiglin.DATA_BABY_ID.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MAX_HEALTH, 16.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.35F).add(GenericAttributes.ATTACK_DAMAGE, 5.0D);
    }

    public static boolean checkPiglinSpawnRules(EntityTypes<EntityPiglin> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return !generatoraccess.getBlockState(blockposition.below()).is(Blocks.NETHER_WART_BLOCK);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();

        if (entityspawnreason != EntitySpawnReason.STRUCTURE) {
            if (randomsource.nextFloat() < 0.2F) {
                this.setBaby(true);
            } else if (this.isAdult()) {
                this.setItemSlot(EnumItemSlot.MAINHAND, this.createSpawnWeapon());
            }
        }

        PiglinAI.initMemories(this, worldaccess.getRandom());
        this.populateDefaultEquipmentSlots(randomsource, difficultydamagescaler);
        this.populateDefaultEquipmentEnchantments(worldaccess, randomsource, difficultydamagescaler);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isPersistenceRequired();
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        if (this.isAdult()) {
            this.maybeWearArmor(EnumItemSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET), randomsource);
            this.maybeWearArmor(EnumItemSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE), randomsource);
            this.maybeWearArmor(EnumItemSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS), randomsource);
            this.maybeWearArmor(EnumItemSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS), randomsource);
        }

    }

    private void maybeWearArmor(EnumItemSlot enumitemslot, ItemStack itemstack, RandomSource randomsource) {
        if (randomsource.nextFloat() < 0.1F) {
            this.setItemSlot(enumitemslot, itemstack);
        }

    }

    @Override
    protected BehaviorController.b<EntityPiglin> brainProvider() {
        return BehaviorController.<EntityPiglin>provider(EntityPiglin.MEMORY_TYPES, EntityPiglin.SENSOR_TYPES);
    }

    @Override
    protected BehaviorController<?> makeBrain(Dynamic<?> dynamic) {
        return PiglinAI.makeBrain(this, this.brainProvider().makeBrain(dynamic));
    }

    @Override
    public BehaviorController<EntityPiglin> getBrain() {
        return (BehaviorController<EntityPiglin>) super.getBrain(); // CraftBukkit - Decompile error
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        EnumInteractionResult enuminteractionresult = super.mobInteract(entityhuman, enumhand);

        if (enuminteractionresult.consumesAction()) {
            return enuminteractionresult;
        } else {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                return PiglinAI.mobInteract(worldserver, this, entityhuman, enumhand);
            } else {
                boolean flag = PiglinAI.canAdmire(this, entityhuman.getItemInHand(enumhand)) && this.getArmPose() != EntityPiglinArmPose.ADMIRING_ITEM;

                return (EnumInteractionResult) (flag ? EnumInteractionResult.SUCCESS : EnumInteractionResult.PASS);
            }
        }
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityPiglin.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    @Override
    public void setBaby(boolean flag) {
        this.getEntityData().set(EntityPiglin.DATA_BABY_ID, flag);
        if (!this.level().isClientSide) {
            AttributeModifiable attributemodifiable = this.getAttribute(GenericAttributes.MOVEMENT_SPEED);

            attributemodifiable.removeModifier(EntityPiglin.SPEED_MODIFIER_BABY.id());
            if (flag) {
                attributemodifiable.addTransientModifier(EntityPiglin.SPEED_MODIFIER_BABY);
            }
        }

    }

    @Override
    public boolean isBaby() {
        return (Boolean) this.getEntityData().get(EntityPiglin.DATA_BABY_ID);
    }

    private void setCannotHunt(boolean flag) {
        this.cannotHunt = flag;
    }

    @Override
    protected boolean canHunt() {
        return !this.cannotHunt;
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("piglinBrain");
        this.getBrain().tick(worldserver, this);
        gameprofilerfiller.pop();
        PiglinAI.updateActivity(this);
        super.customServerAiStep(worldserver);
    }

    @Override
    protected int getBaseExperienceReward(WorldServer worldserver) {
        return this.xpReward;
    }

    @Override
    protected void finishConversion(WorldServer worldserver) {
        PiglinAI.cancelAdmiring(worldserver, this);
        this.inventory.removeAllItems().forEach((itemstack) -> {
            this.spawnAtLocation(worldserver, itemstack);
        });
        super.finishConversion(worldserver);
    }

    private ItemStack createSpawnWeapon() {
        return (double) this.random.nextFloat() < 0.5D ? new ItemStack(Items.CROSSBOW) : new ItemStack(Items.GOLDEN_SWORD);
    }

    @Nullable
    @Override
    public TagKey<Item> getPreferredWeaponType() {
        return this.isBaby() ? null : TagsItem.PIGLIN_PREFERRED_WEAPONS;
    }

    private boolean isChargingCrossbow() {
        return (Boolean) this.entityData.get(EntityPiglin.DATA_IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean flag) {
        this.entityData.set(EntityPiglin.DATA_IS_CHARGING_CROSSBOW, flag);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public EntityPiglinArmPose getArmPose() {
        return this.isDancing() ? EntityPiglinArmPose.DANCING : (PiglinAI.isLovedItem(this.getOffhandItem()) ? EntityPiglinArmPose.ADMIRING_ITEM : (this.isAggressive() && this.isHoldingMeleeWeapon() ? EntityPiglinArmPose.ATTACKING_WITH_MELEE_WEAPON : (this.isChargingCrossbow() ? EntityPiglinArmPose.CROSSBOW_CHARGE : (this.isHolding(Items.CROSSBOW) && ItemCrossbow.isCharged(this.getWeaponItem()) ? EntityPiglinArmPose.CROSSBOW_HOLD : EntityPiglinArmPose.DEFAULT))));
    }

    public boolean isDancing() {
        return (Boolean) this.entityData.get(EntityPiglin.DATA_IS_DANCING);
    }

    public void setDancing(boolean flag) {
        this.entityData.set(EntityPiglin.DATA_IS_DANCING, flag);
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        boolean flag = super.hurtServer(worldserver, damagesource, f);

        if (flag) {
            Entity entity = damagesource.getEntity();

            if (entity instanceof EntityLiving) {
                EntityLiving entityliving = (EntityLiving) entity;

                PiglinAI.wasHurtBy(worldserver, this, entityliving);
            }
        }

        return flag;
    }

    @Override
    public void performRangedAttack(EntityLiving entityliving, float f) {
        this.performCrossbowAttack(this, 1.6F);
    }

    @Override
    public boolean canFireProjectileWeapon(ItemProjectileWeapon itemprojectileweapon) {
        return itemprojectileweapon == Items.CROSSBOW;
    }

    protected void holdInMainHand(ItemStack itemstack) {
        this.setItemSlotAndDropWhenKilled(EnumItemSlot.MAINHAND, itemstack);
    }

    protected void holdInOffHand(ItemStack itemstack) {
        if (itemstack.is(PiglinAI.BARTERING_ITEM) || allowedBarterItems.contains(itemstack.getItem())) { // CraftBukkit - Changes to accept custom payment items
            this.setItemSlot(EnumItemSlot.OFFHAND, itemstack);
            this.setGuaranteedDrop(EnumItemSlot.OFFHAND);
        } else {
            this.setItemSlotAndDropWhenKilled(EnumItemSlot.OFFHAND, itemstack);
        }

    }

    @Override
    public boolean wantsToPickUp(WorldServer worldserver, ItemStack itemstack) {
        return worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && this.canPickUpLoot() && PiglinAI.wantsToPickup(this, itemstack);
    }

    protected boolean canReplaceCurrentItem(ItemStack itemstack) {
        EnumItemSlot enumitemslot = this.getEquipmentSlotForItem(itemstack);
        ItemStack itemstack1 = this.getItemBySlot(enumitemslot);

        return this.canReplaceCurrentItem(itemstack, itemstack1, enumitemslot);
    }

    @Override
    protected boolean canReplaceCurrentItem(ItemStack itemstack, ItemStack itemstack1, EnumItemSlot enumitemslot) {
        if (EnchantmentManager.has(itemstack1, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        } else {
            TagKey<Item> tagkey = this.getPreferredWeaponType();
            boolean flag = PiglinAI.isLovedItem(itemstack, this) || tagkey != null && itemstack.is(tagkey); // CraftBukkit
            boolean flag1 = PiglinAI.isLovedItem(itemstack1, this) || tagkey != null && itemstack1.is(tagkey); // CraftBukkit

            return flag && !flag1 ? true : (!flag && flag1 ? false : super.canReplaceCurrentItem(itemstack, itemstack1, enumitemslot));
        }
    }

    @Override
    protected void pickUpItem(WorldServer worldserver, EntityItem entityitem) {
        this.onItemPickup(entityitem);
        PiglinAI.pickUpItem(worldserver, this, entityitem);
    }

    @Override
    public boolean startRiding(Entity entity, boolean flag) {
        if (this.isBaby() && entity.getType() == EntityTypes.HOGLIN) {
            entity = this.getTopPassenger(entity, 3);
        }

        return super.startRiding(entity, flag);
    }

    private Entity getTopPassenger(Entity entity, int i) {
        List<Entity> list = entity.getPassengers();

        return i != 1 && !list.isEmpty() ? this.getTopPassenger((Entity) list.getFirst(), i - 1) : entity;
    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return this.level().isClientSide ? null : (SoundEffect) PiglinAI.getSoundForCurrentActivity(this).orElse(null); // CraftBukkit - Decompile error
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.PIGLIN_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.PIGLIN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.PIGLIN_STEP, 0.15F, 1.0F);
    }

    @Override
    protected void playConvertedSound() {
        this.makeSound(SoundEffects.PIGLIN_CONVERTED_TO_ZOMBIFIED);
    }
}
