package net.minecraft.world.entity.animal.horse;

import com.google.common.collect.UnmodifiableIterator;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.IInventory;
import net.minecraft.world.InventorySubcontainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.EnumMainHand;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.IJumpable;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeBase;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowParent;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTame;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.ai.goal.RandomStandGoal;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.vehicle.DismountUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundEffectType;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec2F;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.entity.EntityRegainHealthEvent;
// CraftBukkit end

public abstract class EntityHorseAbstract extends EntityAnimal implements HasCustomInventoryScreen, OwnableEntity, IJumpable {

    public static final int CHEST_SLOT_OFFSET = 499;
    public static final int INVENTORY_SLOT_OFFSET = 500;
    public static final double BREEDING_CROSS_FACTOR = 0.15D;
    private static final float MIN_MOVEMENT_SPEED = (float) generateSpeed(() -> {
        return 0.0D;
    });
    private static final float MAX_MOVEMENT_SPEED = (float) generateSpeed(() -> {
        return 1.0D;
    });
    private static final float MIN_JUMP_STRENGTH = (float) generateJumpStrength(() -> {
        return 0.0D;
    });
    private static final float MAX_JUMP_STRENGTH = (float) generateJumpStrength(() -> {
        return 1.0D;
    });
    private static final float MIN_HEALTH = generateMaxHealth((i) -> {
        return 0;
    });
    private static final float MAX_HEALTH = generateMaxHealth((i) -> {
        return i - 1;
    });
    private static final float BACKWARDS_MOVE_SPEED_FACTOR = 0.25F;
    private static final float SIDEWAYS_MOVE_SPEED_FACTOR = 0.5F;
    private static final PathfinderTargetCondition.a PARENT_HORSE_SELECTOR = (entityliving, worldserver) -> {
        boolean flag;

        if (entityliving instanceof EntityHorseAbstract entityhorseabstract) {
            if (entityhorseabstract.isBred()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    };
    private static final PathfinderTargetCondition MOMMY_TARGETING = PathfinderTargetCondition.forNonCombat().range(16.0D).ignoreLineOfSight().selector(EntityHorseAbstract.PARENT_HORSE_SELECTOR);
    private static final DataWatcherObject<Byte> DATA_ID_FLAGS = DataWatcher.<Byte>defineId(EntityHorseAbstract.class, DataWatcherRegistry.BYTE);
    private static final int FLAG_TAME = 2;
    private static final int FLAG_BRED = 8;
    private static final int FLAG_EATING = 16;
    private static final int FLAG_STANDING = 32;
    private static final int FLAG_OPEN_MOUTH = 64;
    public static final int INVENTORY_ROWS = 3;
    private static final int DEFAULT_TEMPER = 0;
    private static final boolean DEFAULT_EATING_HAYSTACK = false;
    private static final boolean DEFAULT_BRED = false;
    private static final boolean DEFAULT_TAME = false;
    private int eatingCounter;
    private int mouthCounter;
    private int standCounter;
    public int tailCounter;
    public int sprintCounter;
    public InventorySubcontainer inventory;
    protected int temper = 0;
    protected float playerJumpPendingScale;
    protected boolean allowStandSliding;
    private float eatAnim;
    private float eatAnimO;
    private float standAnim;
    private float standAnimO;
    private float mouthAnim;
    private float mouthAnimO;
    protected boolean canGallop = true;
    protected int gallopSoundCounter;
    @Nullable
    public EntityReference<EntityLiving> owner;
    public int maxDomestication = 100; // CraftBukkit - store max domestication value

    protected EntityHorseAbstract(EntityTypes<? extends EntityHorseAbstract> entitytypes, World world) {
        super(entitytypes, world);
        this.createInventory();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PathfinderGoalPanic(this, 1.2D));
        this.goalSelector.addGoal(1, new PathfinderGoalTame(this, 1.2D));
        this.goalSelector.addGoal(2, new PathfinderGoalBreed(this, 1.0D, EntityHorseAbstract.class));
        this.goalSelector.addGoal(4, new PathfinderGoalFollowParent(this, 1.0D));
        this.goalSelector.addGoal(6, new PathfinderGoalRandomStrollLand(this, 0.7D));
        this.goalSelector.addGoal(7, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 6.0F));
        this.goalSelector.addGoal(8, new PathfinderGoalRandomLookaround(this));
        if (this.canPerformRearing()) {
            this.goalSelector.addGoal(9, new RandomStandGoal(this));
        }

        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(0, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(3, new PathfinderGoalTempt(this, 1.25D, (itemstack) -> {
            return itemstack.is(TagsItem.HORSE_TEMPT_ITEMS);
        }, false));
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityHorseAbstract.DATA_ID_FLAGS, (byte) 0);
    }

    protected boolean getFlag(int i) {
        return ((Byte) this.entityData.get(EntityHorseAbstract.DATA_ID_FLAGS) & i) != 0;
    }

    protected void setFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntityHorseAbstract.DATA_ID_FLAGS);

        if (flag) {
            this.entityData.set(EntityHorseAbstract.DATA_ID_FLAGS, (byte) (b0 | i));
        } else {
            this.entityData.set(EntityHorseAbstract.DATA_ID_FLAGS, (byte) (b0 & ~i));
        }

    }

    public boolean isTamed() {
        return this.getFlag(2);
    }

    @Nullable
    @Override
    public EntityReference<EntityLiving> getOwnerReference() {
        return this.owner;
    }

    public void setOwner(@Nullable EntityLiving entityliving) {
        this.owner = entityliving != null ? new EntityReference(entityliving) : null;
    }

    public void setTamed(boolean flag) {
        this.setFlag(2, flag);
    }

    @Override
    public void onElasticLeashPull() {
        super.onElasticLeashPull();
        if (this.isEating()) {
            this.setEating(false);
        }

    }

    @Override
    public boolean supportQuadLeash() {
        return true;
    }

    @Override
    public Vec3D[] getQuadLeashOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.04D, 0.52D, 0.23D, 0.87D);
    }

    public boolean isEating() {
        return this.getFlag(16);
    }

    public boolean isStanding() {
        return this.getFlag(32);
    }

    public boolean isBred() {
        return this.getFlag(8);
    }

    public void setBred(boolean flag) {
        this.setFlag(8, flag);
    }

    @Override
    public boolean canUseSlot(EnumItemSlot enumitemslot) {
        return enumitemslot != EnumItemSlot.SADDLE ? super.canUseSlot(enumitemslot) : this.isAlive() && !this.isBaby() && this.isTamed();
    }

    public void equipBodyArmor(EntityHuman entityhuman, ItemStack itemstack) {
        if (this.isEquippableInSlot(itemstack, EnumItemSlot.BODY)) {
            this.setBodyArmorItem(itemstack.consumeAndReturn(1, entityhuman));
        }

    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EnumItemSlot enumitemslot) {
        return (enumitemslot == EnumItemSlot.BODY || enumitemslot == EnumItemSlot.SADDLE) && this.isTamed() || super.canDispenserEquipIntoSlot(enumitemslot);
    }

    public int getTemper() {
        return this.temper;
    }

    public void setTemper(int i) {
        this.temper = i;
    }

    public int modifyTemper(int i) {
        int j = MathHelper.clamp(this.getTemper() + i, 0, this.getMaxTemper());

        this.setTemper(j);
        return j;
    }

    @Override
    public boolean isPushable() {
        return !this.isVehicle();
    }

    private void eating() {
        this.openMouth();
        if (!this.isSilent()) {
            SoundEffect soundeffect = this.getEatingSound();

            if (soundeffect != null) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), soundeffect, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

    }

    @Override
    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        if (d0 > 1.0D) {
            this.playSound(SoundEffects.HORSE_LAND, 0.4F, 1.0F);
        }

        int i = this.calculateFallDamage(d0, f);

        if (i <= 0) {
            return false;
        } else {
            this.hurt(damagesource, (float) i);
            this.propagateFallToPassengers(d0, f, damagesource);
            this.playBlockFallSound();
            return true;
        }
    }

    public final int getInventorySize() {
        return getInventorySize(this.getInventoryColumns());
    }

    public static int getInventorySize(int i) {
        return i * 3;
    }

    public void createInventory() {
        InventorySubcontainer inventorysubcontainer = this.inventory;

        this.inventory = new InventorySubcontainer(this.getInventorySize(), (AbstractHorse) this.getBukkitEntity()); // CraftBukkit
        if (inventorysubcontainer != null) {
            int i = Math.min(inventorysubcontainer.getContainerSize(), this.inventory.getContainerSize());

            for (int j = 0; j < i; ++j) {
                ItemStack itemstack = inventorysubcontainer.getItem(j);

                if (!itemstack.isEmpty()) {
                    this.inventory.setItem(j, itemstack.copy());
                }
            }
        }

    }

    @Override
    protected Holder<SoundEffect> getEquipSound(EnumItemSlot enumitemslot, ItemStack itemstack, Equippable equippable) {
        return (Holder<SoundEffect>) (enumitemslot == EnumItemSlot.SADDLE ? SoundEffects.HORSE_SADDLE : super.getEquipSound(enumitemslot, itemstack, equippable));
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        boolean flag = super.hurtServer(worldserver, damagesource, f);

        if (flag && this.random.nextInt(3) == 0) {
            this.standIfPossible();
        }

        return flag;
    }

    protected boolean canPerformRearing() {
        return true;
    }

    @Nullable
    protected SoundEffect getEatingSound() {
        return null;
    }

    @Nullable
    protected SoundEffect getAngrySound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        if (!iblockdata.liquid()) {
            IBlockData iblockdata1 = this.level().getBlockState(blockposition.above());
            SoundEffectType soundeffecttype = iblockdata.getSoundType();

            if (iblockdata1.is(Blocks.SNOW)) {
                soundeffecttype = iblockdata1.getSoundType();
            }

            if (this.isVehicle() && this.canGallop) {
                ++this.gallopSoundCounter;
                if (this.gallopSoundCounter > 5 && this.gallopSoundCounter % 3 == 0) {
                    this.playGallopSound(soundeffecttype);
                } else if (this.gallopSoundCounter <= 5) {
                    this.playSound(SoundEffects.HORSE_STEP_WOOD, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
                }
            } else if (this.isWoodSoundType(soundeffecttype)) {
                this.playSound(SoundEffects.HORSE_STEP_WOOD, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
            } else {
                this.playSound(SoundEffects.HORSE_STEP, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
            }

        }
    }

    private boolean isWoodSoundType(SoundEffectType soundeffecttype) {
        return soundeffecttype == SoundEffectType.WOOD || soundeffecttype == SoundEffectType.NETHER_WOOD || soundeffecttype == SoundEffectType.STEM || soundeffecttype == SoundEffectType.CHERRY_WOOD || soundeffecttype == SoundEffectType.BAMBOO_WOOD;
    }

    protected void playGallopSound(SoundEffectType soundeffecttype) {
        this.playSound(SoundEffects.HORSE_GALLOP, soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
    }

    public static AttributeProvider.Builder createBaseHorseAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.JUMP_STRENGTH, 0.7D).add(GenericAttributes.MAX_HEALTH, 53.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.225F).add(GenericAttributes.STEP_HEIGHT, 1.0D).add(GenericAttributes.SAFE_FALL_DISTANCE, 6.0D).add(GenericAttributes.FALL_DAMAGE_MULTIPLIER, 0.5D);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 6;
    }

    public int getMaxTemper() {
        return this.maxDomestication; // CraftBukkit - return stored max domestication instead of 100
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    public void openCustomInventoryScreen(EntityHuman entityhuman) {
        if (!this.level().isClientSide && (!this.isVehicle() || this.hasPassenger(entityhuman)) && this.isTamed()) {
            entityhuman.openHorseInventory(this, this.inventory);
        }

    }

    public EnumInteractionResult fedFood(EntityHuman entityhuman, ItemStack itemstack) {
        boolean flag = this.handleEating(entityhuman, itemstack);

        if (flag) {
            itemstack.consume(1, entityhuman);
        }

        return (EnumInteractionResult) (!flag && !this.level().isClientSide ? EnumInteractionResult.PASS : EnumInteractionResult.SUCCESS_SERVER);
    }

    protected boolean handleEating(EntityHuman entityhuman, ItemStack itemstack) {
        boolean flag = false;
        float f = 0.0F;
        int i = 0;
        int j = 0;

        if (itemstack.is(Items.WHEAT)) {
            f = 2.0F;
            i = 20;
            j = 3;
        } else if (itemstack.is(Items.SUGAR)) {
            f = 1.0F;
            i = 30;
            j = 3;
        } else if (itemstack.is(Blocks.HAY_BLOCK.asItem())) {
            f = 20.0F;
            i = 180;
        } else if (itemstack.is(Items.APPLE)) {
            f = 3.0F;
            i = 60;
            j = 3;
        } else if (itemstack.is(Items.CARROT)) {
            f = 3.0F;
            i = 60;
            j = 3;
        } else if (itemstack.is(Items.GOLDEN_CARROT)) {
            f = 4.0F;
            i = 60;
            j = 5;
            if (!this.level().isClientSide && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(entityhuman);
            }
        } else if (itemstack.is(Items.GOLDEN_APPLE) || itemstack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            f = 10.0F;
            i = 240;
            j = 10;
            if (!this.level().isClientSide && this.isTamed() && this.getAge() == 0 && !this.isInLove()) {
                flag = true;
                this.setInLove(entityhuman);
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f, EntityRegainHealthEvent.RegainReason.EATING); // CraftBukkit
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

        if (flag) {
            this.eating();
            this.gameEvent(GameEvent.EAT);
        }

        return flag;
    }

    protected void doPlayerRide(EntityHuman entityhuman) {
        this.setEating(false);
        this.clearStanding();
        if (!this.level().isClientSide) {
            entityhuman.setYRot(this.getYRot());
            entityhuman.setXRot(this.getXRot());
            entityhuman.startRiding(this);
        }

    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() && this.isVehicle() && this.isSaddled() || this.isEating() || this.isStanding();
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.HORSE_FOOD);
    }

    private void moveTail() {
        this.tailCounter = 1;
    }

    @Override
    protected void dropEquipment(WorldServer worldserver) {
        super.dropEquipment(worldserver);
        if (this.inventory != null) {
            for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.inventory.getItem(i);

                if (!itemstack.isEmpty() && !EnchantmentManager.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    this.spawnAtLocation(worldserver, itemstack);
                }
            }

        }
    }

    @Override
    public void aiStep() {
        if (this.random.nextInt(200) == 0) {
            this.moveTail();
        }

        super.aiStep();
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.isAlive()) {
                if (this.random.nextInt(900) == 0 && this.deathTime == 0) {
                    this.heal(1.0F, EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit
                }

                if (this.canEatGrass()) {
                    if (!this.isEating() && !this.isVehicle() && this.random.nextInt(300) == 0 && worldserver.getBlockState(this.blockPosition().below()).is(Blocks.GRASS_BLOCK)) {
                        this.setEating(true);
                    }

                    if (this.isEating() && ++this.eatingCounter > 50) {
                        this.eatingCounter = 0;
                        this.setEating(false);
                    }
                }

                this.followMommy(worldserver);
                return;
            }
        }

    }

    protected void followMommy(WorldServer worldserver) {
        if (this.isBred() && this.isBaby() && !this.isEating()) {
            EntityLiving entityliving = worldserver.getNearestEntity(EntityHorseAbstract.class, EntityHorseAbstract.MOMMY_TARGETING, this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().inflate(16.0D));

            if (entityliving != null && this.distanceToSqr((Entity) entityliving) > 4.0D) {
                this.navigation.createPath(entityliving, 0);
            }
        }

    }

    public boolean canEatGrass() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mouthCounter > 0 && ++this.mouthCounter > 30) {
            this.mouthCounter = 0;
            this.setFlag(64, false);
        }

        if (this.standCounter > 0 && --this.standCounter <= 0) {
            this.clearStanding();
        }

        if (this.tailCounter > 0 && ++this.tailCounter > 8) {
            this.tailCounter = 0;
        }

        if (this.sprintCounter > 0) {
            ++this.sprintCounter;
            if (this.sprintCounter > 300) {
                this.sprintCounter = 0;
            }
        }

        this.eatAnimO = this.eatAnim;
        if (this.isEating()) {
            this.eatAnim += (1.0F - this.eatAnim) * 0.4F + 0.05F;
            if (this.eatAnim > 1.0F) {
                this.eatAnim = 1.0F;
            }
        } else {
            this.eatAnim += (0.0F - this.eatAnim) * 0.4F - 0.05F;
            if (this.eatAnim < 0.0F) {
                this.eatAnim = 0.0F;
            }
        }

        this.standAnimO = this.standAnim;
        if (this.isStanding()) {
            this.eatAnim = 0.0F;
            this.eatAnimO = this.eatAnim;
            this.standAnim += (1.0F - this.standAnim) * 0.4F + 0.05F;
            if (this.standAnim > 1.0F) {
                this.standAnim = 1.0F;
            }
        } else {
            this.allowStandSliding = false;
            this.standAnim += (0.8F * this.standAnim * this.standAnim * this.standAnim - this.standAnim) * 0.6F - 0.05F;
            if (this.standAnim < 0.0F) {
                this.standAnim = 0.0F;
            }
        }

        this.mouthAnimO = this.mouthAnim;
        if (this.getFlag(64)) {
            this.mouthAnim += (1.0F - this.mouthAnim) * 0.7F + 0.05F;
            if (this.mouthAnim > 1.0F) {
                this.mouthAnim = 1.0F;
            }
        } else {
            this.mouthAnim += (0.0F - this.mouthAnim) * 0.7F - 0.05F;
            if (this.mouthAnim < 0.0F) {
                this.mouthAnim = 0.0F;
            }
        }

    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        if (!this.isVehicle() && !this.isBaby()) {
            if (this.isTamed() && entityhuman.isSecondaryUseActive()) {
                this.openCustomInventoryScreen(entityhuman);
                return EnumInteractionResult.SUCCESS;
            } else {
                ItemStack itemstack = entityhuman.getItemInHand(enumhand);

                if (!itemstack.isEmpty()) {
                    EnumInteractionResult enuminteractionresult = itemstack.interactLivingEntity(entityhuman, this, enumhand);

                    if (enuminteractionresult.consumesAction()) {
                        return enuminteractionresult;
                    }

                    if (this.isEquippableInSlot(itemstack, EnumItemSlot.BODY) && !this.isWearingBodyArmor()) {
                        this.equipBodyArmor(entityhuman, itemstack);
                        return EnumInteractionResult.SUCCESS;
                    }
                }

                this.doPlayerRide(entityhuman);
                return EnumInteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    private void openMouth() {
        if (!this.level().isClientSide) {
            this.mouthCounter = 1;
            this.setFlag(64, true);
        }

    }

    public void setEating(boolean flag) {
        this.setFlag(16, flag);
    }

    public void setStanding(int i) {
        this.setEating(false);
        this.setFlag(32, true);
        this.standCounter = i;
    }

    public void clearStanding() {
        this.setFlag(32, false);
        this.standCounter = 0;
    }

    @Nullable
    public SoundEffect getAmbientStandSound() {
        return this.getAmbientSound();
    }

    public void standIfPossible() {
        if (this.canPerformRearing() && (this.isEffectiveAi() || !this.level().isClientSide)) {
            this.setStanding(20);
        }

    }

    public void makeMad() {
        if (!this.isStanding() && !this.level().isClientSide) {
            this.standIfPossible();
            this.makeSound(this.getAngrySound());
        }

    }

    public boolean tameWithName(EntityHuman entityhuman) {
        this.setOwner(entityhuman);
        this.setTamed(true);
        if (entityhuman instanceof EntityPlayer) {
            CriterionTriggers.TAME_ANIMAL.trigger((EntityPlayer) entityhuman, this);
        }

        this.level().broadcastEntityEvent(this, (byte) 7);
        return true;
    }

    @Override
    protected void tickRidden(EntityHuman entityhuman, Vec3D vec3d) {
        super.tickRidden(entityhuman, vec3d);
        Vec2F vec2f = this.getRiddenRotation(entityhuman);

        this.setRot(vec2f.y, vec2f.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (this.isLocalInstanceAuthoritative()) {
            if (vec3d.z <= 0.0D) {
                this.gallopSoundCounter = 0;
            }

            if (this.onGround()) {
                if (this.playerJumpPendingScale > 0.0F && !this.isJumping()) {
                    this.executeRidersJump(this.playerJumpPendingScale, vec3d);
                }

                this.playerJumpPendingScale = 0.0F;
            }
        }

    }

    protected Vec2F getRiddenRotation(EntityLiving entityliving) {
        return new Vec2F(entityliving.getXRot() * 0.5F, entityliving.getYRot());
    }

    @Override
    protected Vec3D getRiddenInput(EntityHuman entityhuman, Vec3D vec3d) {
        if (this.onGround() && this.playerJumpPendingScale == 0.0F && this.isStanding() && !this.allowStandSliding) {
            return Vec3D.ZERO;
        } else {
            float f = entityhuman.xxa * 0.5F;
            float f1 = entityhuman.zza;

            if (f1 <= 0.0F) {
                f1 *= 0.25F;
            }

            return new Vec3D((double) f, 0.0D, (double) f1);
        }
    }

    @Override
    protected float getRiddenSpeed(EntityHuman entityhuman) {
        return (float) this.getAttributeValue(GenericAttributes.MOVEMENT_SPEED);
    }

    protected void executeRidersJump(float f, Vec3D vec3d) {
        double d0 = (double) this.getJumpPower(f);
        Vec3D vec3d1 = this.getDeltaMovement();

        this.setDeltaMovement(vec3d1.x, d0, vec3d1.z);
        this.hasImpulse = true;
        if (vec3d.z > 0.0D) {
            float f1 = MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F));
            float f2 = MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F));

            this.setDeltaMovement(this.getDeltaMovement().add((double) (-0.4F * f1 * f), 0.0D, (double) (0.4F * f2 * f)));
        }

    }

    protected void playJumpSound() {
        this.playSound(SoundEffects.HORSE_JUMP, 0.4F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("EatingHaystack", this.isEating());
        valueoutput.putBoolean("Bred", this.isBred());
        valueoutput.putInt("Temper", this.getTemper());
        valueoutput.putBoolean("Tame", this.isTamed());
        EntityReference.store(this.owner, valueoutput, "Owner");
        valueoutput.putInt("Bukkit.MaxDomestication", this.maxDomestication); // CraftBukkit
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setEating(valueinput.getBooleanOr("EatingHaystack", false));
        this.setBred(valueinput.getBooleanOr("Bred", false));
        this.setTemper(valueinput.getIntOr("Temper", 0));
        this.setTamed(valueinput.getBooleanOr("Tame", false));
        this.owner = EntityReference.<EntityLiving>readWithOldOwnerConversion(valueinput, "Owner", this.level());
        // CraftBukkit start
        this.maxDomestication = valueinput.getIntOr("Bukkit.MaxDomestication", this.maxDomestication);
        // CraftBukkit end
    }

    @Override
    public boolean canMate(EntityAnimal entityanimal) {
        return false;
    }

    protected boolean canParent() {
        return !this.isVehicle() && !this.isPassenger() && this.isTamed() && !this.isBaby() && this.getHealth() >= this.getMaxHealth() && this.isInLove();
    }

    @Nullable
    @Override
    public EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        return null;
    }

    protected void setOffspringAttributes(EntityAgeable entityageable, EntityHorseAbstract entityhorseabstract) {
        this.setOffspringAttribute(entityageable, entityhorseabstract, GenericAttributes.MAX_HEALTH, (double) EntityHorseAbstract.MIN_HEALTH, (double) EntityHorseAbstract.MAX_HEALTH);
        this.setOffspringAttribute(entityageable, entityhorseabstract, GenericAttributes.JUMP_STRENGTH, (double) EntityHorseAbstract.MIN_JUMP_STRENGTH, (double) EntityHorseAbstract.MAX_JUMP_STRENGTH);
        this.setOffspringAttribute(entityageable, entityhorseabstract, GenericAttributes.MOVEMENT_SPEED, (double) EntityHorseAbstract.MIN_MOVEMENT_SPEED, (double) EntityHorseAbstract.MAX_MOVEMENT_SPEED);
    }

    private void setOffspringAttribute(EntityAgeable entityageable, EntityHorseAbstract entityhorseabstract, Holder<AttributeBase> holder, double d0, double d1) {
        double d2 = createOffspringAttribute(this.getAttributeBaseValue(holder), entityageable.getAttributeBaseValue(holder), d0, d1, this.random);

        entityhorseabstract.getAttribute(holder).setBaseValue(d2);
    }

    static double createOffspringAttribute(double d0, double d1, double d2, double d3, RandomSource randomsource) {
        if (d3 <= d2) {
            throw new IllegalArgumentException("Incorrect range for an attribute");
        } else {
            d0 = MathHelper.clamp(d0, d2, d3);
            d1 = MathHelper.clamp(d1, d2, d3);
            double d4 = 0.15D * (d3 - d2);
            double d5 = Math.abs(d0 - d1) + d4 * 2.0D;
            double d6 = (d0 + d1) / 2.0D;
            double d7 = (randomsource.nextDouble() + randomsource.nextDouble() + randomsource.nextDouble()) / 3.0D - 0.5D;
            double d8 = d6 + d5 * d7;

            if (d8 > d3) {
                double d9 = d8 - d3;

                return d3 - d9;
            } else if (d8 < d2) {
                double d10 = d2 - d8;

                return d2 + d10;
            } else {
                return d8;
            }
        }
    }

    public float getEatAnim(float f) {
        return MathHelper.lerp(f, this.eatAnimO, this.eatAnim);
    }

    public float getStandAnim(float f) {
        return MathHelper.lerp(f, this.standAnimO, this.standAnim);
    }

    public float getMouthAnim(float f) {
        return MathHelper.lerp(f, this.mouthAnimO, this.mouthAnim);
    }

    @Override
    public void onPlayerJump(int i) {
        if (this.isSaddled()) {
            if (i < 0) {
                i = 0;
            } else {
                this.allowStandSliding = true;
                this.standIfPossible();
            }

            if (i >= 90) {
                this.playerJumpPendingScale = 1.0F;
            } else {
                this.playerJumpPendingScale = 0.4F + 0.4F * (float) i / 90.0F;
            }

        }
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void handleStartJump(int i) {
        // CraftBukkit start
        float power;
        if (i >= 90) {
            power = 1.0F;
        } else {
            power = 0.4F + 0.4F * (float) i / 90.0F;
        }
        if (!CraftEventFactory.callHorseJumpEvent(this, power)) {
            return;
        }
        // CraftBukkit end
        this.allowStandSliding = true;
        this.standIfPossible();
        this.playJumpSound();
    }

    @Override
    public void handleStopJump() {}

    protected void spawnTamingParticles(boolean flag) {
        ParticleParam particleparam = flag ? Particles.HEART : Particles.SMOKE;

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(particleparam, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 7) {
            this.spawnTamingParticles(true);
        } else if (b0 == 6) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(b0);
        }

    }

    @Override
    protected void positionRider(Entity entity, Entity.MoveFunction entity_movefunction) {
        super.positionRider(entity, entity_movefunction);
        if (entity instanceof EntityLiving) {
            ((EntityLiving) entity).yBodyRot = this.yBodyRot;
        }

    }

    protected static float generateMaxHealth(IntUnaryOperator intunaryoperator) {
        return 15.0F + (float) intunaryoperator.applyAsInt(8) + (float) intunaryoperator.applyAsInt(9);
    }

    protected static double generateJumpStrength(DoubleSupplier doublesupplier) {
        return (double) 0.4F + doublesupplier.getAsDouble() * 0.2D + doublesupplier.getAsDouble() * 0.2D + doublesupplier.getAsDouble() * 0.2D;
    }

    protected static double generateSpeed(DoubleSupplier doublesupplier) {
        return ((double) 0.45F + doublesupplier.getAsDouble() * 0.3D + doublesupplier.getAsDouble() * 0.3D + doublesupplier.getAsDouble() * 0.3D) * 0.25D;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public SlotAccess getSlot(int i) {
        int j = i - 500;

        return j >= 0 && j < this.inventory.getContainerSize() ? SlotAccess.forContainer(this.inventory, j) : super.getSlot(i);
    }

    @Nullable
    @Override
    public EntityLiving getControllingPassenger() {
        if (this.isSaddled()) {
            Entity entity = this.getFirstPassenger();

            if (entity instanceof EntityHuman) {
                EntityHuman entityhuman = (EntityHuman) entity;

                return entityhuman;
            }
        }

        return super.getControllingPassenger();
    }

    @Nullable
    private Vec3D getDismountLocationInDirection(Vec3D vec3d, EntityLiving entityliving) {
        double d0 = this.getX() + vec3d.x;
        double d1 = this.getBoundingBox().minY;
        double d2 = this.getZ() + vec3d.z;
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
        UnmodifiableIterator unmodifiableiterator = entityliving.getDismountPoses().iterator();

        while (unmodifiableiterator.hasNext()) {
            EntityPose entitypose = (EntityPose) unmodifiableiterator.next();

            blockposition_mutableblockposition.set(d0, d1, d2);
            double d3 = this.getBoundingBox().maxY + 0.75D;

            while (true) {
                double d4 = this.level().getBlockFloorHeight(blockposition_mutableblockposition);

                if ((double) blockposition_mutableblockposition.getY() + d4 > d3) {
                    break;
                }

                if (DismountUtil.isBlockFloorValid(d4)) {
                    AxisAlignedBB axisalignedbb = entityliving.getLocalBoundsForPose(entitypose);
                    Vec3D vec3d1 = new Vec3D(d0, (double) blockposition_mutableblockposition.getY() + d4, d2);

                    if (DismountUtil.canDismountTo(this.level(), entityliving, axisalignedbb.move(vec3d1))) {
                        entityliving.setPose(entitypose);
                        return vec3d1;
                    }
                }

                blockposition_mutableblockposition.move(EnumDirection.UP);
                if ((double) blockposition_mutableblockposition.getY() >= d3) {
                    break;
                }
            }
        }

        return null;
    }

    @Override
    public Vec3D getDismountLocationForPassenger(EntityLiving entityliving) {
        Vec3D vec3d = getCollisionHorizontalEscapeVector((double) this.getBbWidth(), (double) entityliving.getBbWidth(), this.getYRot() + (entityliving.getMainArm() == EnumMainHand.RIGHT ? 90.0F : -90.0F));
        Vec3D vec3d1 = this.getDismountLocationInDirection(vec3d, entityliving);

        if (vec3d1 != null) {
            return vec3d1;
        } else {
            Vec3D vec3d2 = getCollisionHorizontalEscapeVector((double) this.getBbWidth(), (double) entityliving.getBbWidth(), this.getYRot() + (entityliving.getMainArm() == EnumMainHand.LEFT ? 90.0F : -90.0F));
            Vec3D vec3d3 = this.getDismountLocationInDirection(vec3d2, entityliving);

            return vec3d3 != null ? vec3d3 : this.position();
        }
    }

    protected void randomizeAttributes(RandomSource randomsource) {}

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        if (groupdataentity == null) {
            groupdataentity = new EntityAgeable.a(0.2F);
        }

        this.randomizeAttributes(worldaccess.getRandom());
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    public boolean hasInventoryChanged(IInventory iinventory) {
        return this.inventory != iinventory;
    }

    public int getAmbientStandInterval() {
        return this.getAmbientSoundInterval();
    }

    @Override
    protected Vec3D getPassengerAttachmentPoint(Entity entity, EntitySize entitysize, float f) {
        return super.getPassengerAttachmentPoint(entity, entitysize, f).add((new Vec3D(0.0D, 0.15D * (double) this.standAnimO * (double) f, -0.7D * (double) this.standAnimO * (double) f)).yRot(-this.getYRot() * ((float) Math.PI / 180F)));
    }

    public int getInventoryColumns() {
        return 0;
    }
}
