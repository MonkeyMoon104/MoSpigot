package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.ParticleParamItem;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
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
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowParent;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalTempt;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
// CraftBukkit end

public class EntityPanda extends EntityAnimal {

    private static final DataWatcherObject<Integer> UNHAPPY_COUNTER = DataWatcher.<Integer>defineId(EntityPanda.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Integer> SNEEZE_COUNTER = DataWatcher.<Integer>defineId(EntityPanda.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Integer> EAT_COUNTER = DataWatcher.<Integer>defineId(EntityPanda.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Byte> MAIN_GENE_ID = DataWatcher.<Byte>defineId(EntityPanda.class, DataWatcherRegistry.BYTE);
    private static final DataWatcherObject<Byte> HIDDEN_GENE_ID = DataWatcher.<Byte>defineId(EntityPanda.class, DataWatcherRegistry.BYTE);
    private static final DataWatcherObject<Byte> DATA_ID_FLAGS = DataWatcher.<Byte>defineId(EntityPanda.class, DataWatcherRegistry.BYTE);
    static final PathfinderTargetCondition BREED_TARGETING = PathfinderTargetCondition.forNonCombat().range(8.0D);
    private static final EntitySize BABY_DIMENSIONS = EntityTypes.PANDA.getDimensions().scale(0.5F).withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, 0.40625F, 0.0F));
    private static final int FLAG_SNEEZE = 2;
    private static final int FLAG_ROLL = 4;
    private static final int FLAG_SIT = 8;
    private static final int FLAG_ON_BACK = 16;
    private static final int EAT_TICK_INTERVAL = 5;
    public static final int TOTAL_ROLL_STEPS = 32;
    private static final int TOTAL_UNHAPPY_TIME = 32;
    boolean gotBamboo;
    boolean didBite;
    public int rollCounter;
    private Vec3D rollDelta;
    private float sitAmount;
    private float sitAmountO;
    private float onBackAmount;
    private float onBackAmountO;
    private float rollAmount;
    private float rollAmountO;
    EntityPanda.g lookAtPlayerGoal;

    public EntityPanda(EntityTypes<? extends EntityPanda> entitytypes, World world) {
        super(entitytypes, world);
        this.moveControl = new EntityPanda.h(this);
        if (!this.isBaby()) {
            this.setCanPickUpLoot(true);
        }

    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EnumItemSlot enumitemslot) {
        return enumitemslot == EnumItemSlot.MAINHAND && this.canPickUpLoot();
    }

    public int getUnhappyCounter() {
        return (Integer) this.entityData.get(EntityPanda.UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int i) {
        this.entityData.set(EntityPanda.UNHAPPY_COUNTER, i);
    }

    public boolean isSneezing() {
        return this.getFlag(2);
    }

    public boolean isSitting() {
        return this.getFlag(8);
    }

    public void sit(boolean flag) {
        this.setFlag(8, flag);
    }

    public boolean isOnBack() {
        return this.getFlag(16);
    }

    public void setOnBack(boolean flag) {
        this.setFlag(16, flag);
    }

    public boolean isEating() {
        return (Integer) this.entityData.get(EntityPanda.EAT_COUNTER) > 0;
    }

    public void eat(boolean flag) {
        this.entityData.set(EntityPanda.EAT_COUNTER, flag ? 1 : 0);
    }

    private int getEatCounter() {
        return (Integer) this.entityData.get(EntityPanda.EAT_COUNTER);
    }

    private void setEatCounter(int i) {
        this.entityData.set(EntityPanda.EAT_COUNTER, i);
    }

    public void sneeze(boolean flag) {
        this.setFlag(2, flag);
        if (!flag) {
            this.setSneezeCounter(0);
        }

    }

    public int getSneezeCounter() {
        return (Integer) this.entityData.get(EntityPanda.SNEEZE_COUNTER);
    }

    public void setSneezeCounter(int i) {
        this.entityData.set(EntityPanda.SNEEZE_COUNTER, i);
    }

    public EntityPanda.Gene getMainGene() {
        return EntityPanda.Gene.byId((Byte) this.entityData.get(EntityPanda.MAIN_GENE_ID));
    }

    public void setMainGene(EntityPanda.Gene entitypanda_gene) {
        if (entitypanda_gene.getId() > 6) {
            entitypanda_gene = EntityPanda.Gene.getRandom(this.random);
        }

        this.entityData.set(EntityPanda.MAIN_GENE_ID, (byte) entitypanda_gene.getId());
    }

    public EntityPanda.Gene getHiddenGene() {
        return EntityPanda.Gene.byId((Byte) this.entityData.get(EntityPanda.HIDDEN_GENE_ID));
    }

    public void setHiddenGene(EntityPanda.Gene entitypanda_gene) {
        if (entitypanda_gene.getId() > 6) {
            entitypanda_gene = EntityPanda.Gene.getRandom(this.random);
        }

        this.entityData.set(EntityPanda.HIDDEN_GENE_ID, (byte) entitypanda_gene.getId());
    }

    public boolean isRolling() {
        return this.getFlag(4);
    }

    public void roll(boolean flag) {
        this.setFlag(4, flag);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityPanda.UNHAPPY_COUNTER, 0);
        datawatcher_a.define(EntityPanda.SNEEZE_COUNTER, 0);
        datawatcher_a.define(EntityPanda.MAIN_GENE_ID, (byte) 0);
        datawatcher_a.define(EntityPanda.HIDDEN_GENE_ID, (byte) 0);
        datawatcher_a.define(EntityPanda.DATA_ID_FLAGS, (byte) 0);
        datawatcher_a.define(EntityPanda.EAT_COUNTER, 0);
    }

    private boolean getFlag(int i) {
        return ((Byte) this.entityData.get(EntityPanda.DATA_ID_FLAGS) & i) != 0;
    }

    private void setFlag(int i, boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntityPanda.DATA_ID_FLAGS);

        if (flag) {
            this.entityData.set(EntityPanda.DATA_ID_FLAGS, (byte) (b0 | i));
        } else {
            this.entityData.set(EntityPanda.DATA_ID_FLAGS, (byte) (b0 & ~i));
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("MainGene", EntityPanda.Gene.CODEC, this.getMainGene());
        valueoutput.store("HiddenGene", EntityPanda.Gene.CODEC, this.getHiddenGene());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setMainGene((EntityPanda.Gene) valueinput.read("MainGene", EntityPanda.Gene.CODEC).orElse(EntityPanda.Gene.NORMAL));
        this.setHiddenGene((EntityPanda.Gene) valueinput.read("HiddenGene", EntityPanda.Gene.CODEC).orElse(EntityPanda.Gene.NORMAL));
    }

    @Nullable
    @Override
    public EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityPanda entitypanda = EntityTypes.PANDA.create(worldserver, EntitySpawnReason.BREEDING);

        if (entitypanda != null) {
            if (entityageable instanceof EntityPanda) {
                EntityPanda entitypanda1 = (EntityPanda) entityageable;

                entitypanda.setGeneFromParents(this, entitypanda1);
            }

            entitypanda.setAttributes();
        }

        return entitypanda;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(2, new EntityPanda.i(this, 2.0D));
        this.goalSelector.addGoal(2, new EntityPanda.d(this, 1.0D));
        this.goalSelector.addGoal(3, new EntityPanda.b(this, (double) 1.2F, true));
        this.goalSelector.addGoal(4, new PathfinderGoalTempt(this, 1.0D, (itemstack) -> {
            return itemstack.is(TagsItem.PANDA_FOOD);
        }, false));
        this.goalSelector.addGoal(6, new EntityPanda.c(this, EntityHuman.class, 8.0F, 2.0D, 2.0D));
        this.goalSelector.addGoal(6, new EntityPanda.c(this, EntityMonster.class, 4.0F, 2.0D, 2.0D));
        this.goalSelector.addGoal(7, new EntityPanda.k());
        this.goalSelector.addGoal(8, new EntityPanda.f(this));
        this.goalSelector.addGoal(8, new EntityPanda.l(this));
        this.lookAtPlayerGoal = new EntityPanda.g(this, EntityHuman.class, 6.0F);
        this.goalSelector.addGoal(9, this.lookAtPlayerGoal);
        this.goalSelector.addGoal(10, new PathfinderGoalRandomLookaround(this));
        this.goalSelector.addGoal(12, new EntityPanda.j(this));
        this.goalSelector.addGoal(13, new PathfinderGoalFollowParent(this, 1.25D));
        this.goalSelector.addGoal(14, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.targetSelector.addGoal(1, (new EntityPanda.e(this, new Class[0])).setAlertOthers(new Class[0]));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MOVEMENT_SPEED, (double) 0.15F).add(GenericAttributes.ATTACK_DAMAGE, 6.0D);
    }

    public EntityPanda.Gene getVariant() {
        return EntityPanda.Gene.getVariantFromGenes(this.getMainGene(), this.getHiddenGene());
    }

    public boolean isLazy() {
        return this.getVariant() == EntityPanda.Gene.LAZY;
    }

    public boolean isWorried() {
        return this.getVariant() == EntityPanda.Gene.WORRIED;
    }

    public boolean isPlayful() {
        return this.getVariant() == EntityPanda.Gene.PLAYFUL;
    }

    public boolean isBrown() {
        return this.getVariant() == EntityPanda.Gene.BROWN;
    }

    public boolean isWeak() {
        return this.getVariant() == EntityPanda.Gene.WEAK;
    }

    @Override
    public boolean isAggressive() {
        return this.getVariant() == EntityPanda.Gene.AGGRESSIVE;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        if (!this.isAggressive()) {
            this.didBite = true;
        }

        return super.doHurtTarget(worldserver, entity);
    }

    @Override
    public void playAttackSound() {
        this.playSound(SoundEffects.PANDA_BITE, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isWorried()) {
            if (this.level().isThundering() && !this.isInWater()) {
                this.sit(true);
                this.eat(false);
            } else if (!this.isEating()) {
                this.sit(false);
            }
        }

        EntityLiving entityliving = this.getTarget();

        if (entityliving == null) {
            this.gotBamboo = false;
            this.didBite = false;
        }

        if (this.getUnhappyCounter() > 0) {
            if (entityliving != null) {
                this.lookAt(entityliving, 90.0F, 90.0F);
            }

            if (this.getUnhappyCounter() == 29 || this.getUnhappyCounter() == 14) {
                this.playSound(SoundEffects.PANDA_CANT_BREED, 1.0F, 1.0F);
            }

            this.setUnhappyCounter(this.getUnhappyCounter() - 1);
        }

        if (this.isSneezing()) {
            this.setSneezeCounter(this.getSneezeCounter() + 1);
            if (this.getSneezeCounter() > 20) {
                this.sneeze(false);
                this.afterSneeze();
            } else if (this.getSneezeCounter() == 1) {
                this.playSound(SoundEffects.PANDA_PRE_SNEEZE, 1.0F, 1.0F);
            }
        }

        if (this.isRolling()) {
            this.handleRoll();
        } else {
            this.rollCounter = 0;
        }

        if (this.isSitting()) {
            this.setXRot(0.0F);
        }

        this.updateSitAmount();
        this.handleEating();
        this.updateOnBackAnimation();
        this.updateRollAmount();
    }

    public boolean isScared() {
        return this.isWorried() && this.level().isThundering();
    }

    private void handleEating() {
        if (!this.isEating() && this.isSitting() && !this.isScared() && !this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty() && this.random.nextInt(80) == 1) {
            this.eat(true);
        } else if (this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty() || !this.isSitting()) {
            this.eat(false);
        }

        if (this.isEating()) {
            this.addEatingParticles();
            if (!this.level().isClientSide && this.getEatCounter() > 80 && this.random.nextInt(20) == 1) {
                if (this.getEatCounter() > 100 && this.getItemBySlot(EnumItemSlot.MAINHAND).is(TagsItem.PANDA_EATS_FROM_GROUND)) {
                    if (!this.level().isClientSide) {
                        this.setItemSlot(EnumItemSlot.MAINHAND, ItemStack.EMPTY);
                        this.gameEvent(GameEvent.EAT);
                    }

                    this.sit(false);
                }

                this.eat(false);
                return;
            }

            this.setEatCounter(this.getEatCounter() + 1);
        }

    }

    private void addEatingParticles() {
        if (this.getEatCounter() % 5 == 0) {
            this.playSound(SoundEffects.PANDA_EAT, 0.5F + 0.5F * (float) this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);

            for (int i = 0; i < 6; ++i) {
                Vec3D vec3d = new Vec3D(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, ((double) this.random.nextFloat() - 0.5D) * 0.1D);

                vec3d = vec3d.xRot(-this.getXRot() * ((float) Math.PI / 180F));
                vec3d = vec3d.yRot(-this.getYRot() * ((float) Math.PI / 180F));
                double d0 = (double) (-this.random.nextFloat()) * 0.6D - 0.3D;
                Vec3D vec3d1 = new Vec3D(((double) this.random.nextFloat() - 0.5D) * 0.8D, d0, 1.0D + ((double) this.random.nextFloat() - 0.5D) * 0.4D);

                vec3d1 = vec3d1.yRot(-this.yBodyRot * ((float) Math.PI / 180F));
                vec3d1 = vec3d1.add(this.getX(), this.getEyeY() + 1.0D, this.getZ());
                this.level().addParticle(new ParticleParamItem(Particles.ITEM, this.getItemBySlot(EnumItemSlot.MAINHAND)), vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05D, vec3d.z);
            }
        }

    }

    private void updateSitAmount() {
        this.sitAmountO = this.sitAmount;
        if (this.isSitting()) {
            this.sitAmount = Math.min(1.0F, this.sitAmount + 0.15F);
        } else {
            this.sitAmount = Math.max(0.0F, this.sitAmount - 0.19F);
        }

    }

    private void updateOnBackAnimation() {
        this.onBackAmountO = this.onBackAmount;
        if (this.isOnBack()) {
            this.onBackAmount = Math.min(1.0F, this.onBackAmount + 0.15F);
        } else {
            this.onBackAmount = Math.max(0.0F, this.onBackAmount - 0.19F);
        }

    }

    private void updateRollAmount() {
        this.rollAmountO = this.rollAmount;
        if (this.isRolling()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.15F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.19F);
        }

    }

    public float getSitAmount(float f) {
        return MathHelper.lerp(f, this.sitAmountO, this.sitAmount);
    }

    public float getLieOnBackAmount(float f) {
        return MathHelper.lerp(f, this.onBackAmountO, this.onBackAmount);
    }

    public float getRollAmount(float f) {
        return MathHelper.lerp(f, this.rollAmountO, this.rollAmount);
    }

    private void handleRoll() {
        ++this.rollCounter;
        if (this.rollCounter > 32) {
            this.roll(false);
        } else {
            if (!this.level().isClientSide) {
                Vec3D vec3d = this.getDeltaMovement();

                if (this.rollCounter == 1) {
                    float f = this.getYRot() * ((float) Math.PI / 180F);
                    float f1 = this.isBaby() ? 0.1F : 0.2F;

                    this.rollDelta = new Vec3D(vec3d.x + (double) (-MathHelper.sin(f) * f1), 0.0D, vec3d.z + (double) (MathHelper.cos(f) * f1));
                    this.setDeltaMovement(this.rollDelta.add(0.0D, 0.27D, 0.0D));
                } else if ((float) this.rollCounter != 7.0F && (float) this.rollCounter != 15.0F && (float) this.rollCounter != 23.0F) {
                    this.setDeltaMovement(this.rollDelta.x, vec3d.y, this.rollDelta.z);
                } else {
                    this.setDeltaMovement(0.0D, this.onGround() ? 0.27D : vec3d.y, 0.0D);
                }
            }

        }
    }

    private void afterSneeze() {
        Vec3D vec3d = this.getDeltaMovement();
        World world = this.level();

        world.addParticle(Particles.SNEEZE, this.getX() - (double) (this.getBbWidth() + 1.0F) * 0.5D * (double) MathHelper.sin(this.yBodyRot * ((float) Math.PI / 180F)), this.getEyeY() - (double) 0.1F, this.getZ() + (double) (this.getBbWidth() + 1.0F) * 0.5D * (double) MathHelper.cos(this.yBodyRot * ((float) Math.PI / 180F)), vec3d.x, 0.0D, vec3d.z);
        this.playSound(SoundEffects.PANDA_SNEEZE, 1.0F, 1.0F);

        for (EntityPanda entitypanda : world.getEntitiesOfClass(EntityPanda.class, this.getBoundingBox().inflate(10.0D))) {
            if (!entitypanda.isBaby() && entitypanda.onGround() && !entitypanda.isInWater() && entitypanda.canPerformAction()) {
                entitypanda.jumpFromGround();
            }
        }

        World world1 = this.level();

        if (world1 instanceof WorldServer worldserver) {
            if (worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                this.dropFromGiftLootTable(worldserver, LootTables.PANDA_SNEEZE, this::spawnAtLocation);
            }
        }

    }

    @Override
    protected void pickUpItem(WorldServer worldserver, EntityItem entityitem) {
        if (!CraftEventFactory.callEntityPickupItemEvent(this, entityitem, 0, !(this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty() && canPickUpAndEat(entityitem))).isCancelled()) { // CraftBukkit
            this.onItemPickup(entityitem);
            ItemStack itemstack = entityitem.getItem();

            this.setItemSlot(EnumItemSlot.MAINHAND, itemstack);
            this.setGuaranteedDrop(EnumItemSlot.MAINHAND);
            this.take(entityitem, itemstack.getCount());
            entityitem.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        this.sit(false);
        return super.hurtServer(worldserver, damagesource, f);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();

        this.setMainGene(EntityPanda.Gene.getRandom(randomsource));
        this.setHiddenGene(EntityPanda.Gene.getRandom(randomsource));
        this.setAttributes();
        if (groupdataentity == null) {
            groupdataentity = new EntityAgeable.a(0.2F);
        }

        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    public void setGeneFromParents(EntityPanda entitypanda, @Nullable EntityPanda entitypanda1) {
        if (entitypanda1 == null) {
            if (this.random.nextBoolean()) {
                this.setMainGene(entitypanda.getOneOfGenesRandomly());
                this.setHiddenGene(EntityPanda.Gene.getRandom(this.random));
            } else {
                this.setMainGene(EntityPanda.Gene.getRandom(this.random));
                this.setHiddenGene(entitypanda.getOneOfGenesRandomly());
            }
        } else if (this.random.nextBoolean()) {
            this.setMainGene(entitypanda.getOneOfGenesRandomly());
            this.setHiddenGene(entitypanda1.getOneOfGenesRandomly());
        } else {
            this.setMainGene(entitypanda1.getOneOfGenesRandomly());
            this.setHiddenGene(entitypanda.getOneOfGenesRandomly());
        }

        if (this.random.nextInt(32) == 0) {
            this.setMainGene(EntityPanda.Gene.getRandom(this.random));
        }

        if (this.random.nextInt(32) == 0) {
            this.setHiddenGene(EntityPanda.Gene.getRandom(this.random));
        }

    }

    private EntityPanda.Gene getOneOfGenesRandomly() {
        return this.random.nextBoolean() ? this.getMainGene() : this.getHiddenGene();
    }

    public void setAttributes() {
        if (this.isWeak()) {
            this.getAttribute(GenericAttributes.MAX_HEALTH).setBaseValue(10.0D);
        }

        if (this.isLazy()) {
            this.getAttribute(GenericAttributes.MOVEMENT_SPEED).setBaseValue((double) 0.07F);
        }

    }

    void tryToSit() {
        if (!this.isInWater()) {
            this.setZza(0.0F);
            this.getNavigation().stop();
            this.sit(true);
        }

    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (this.isScared()) {
            return EnumInteractionResult.PASS;
        } else if (this.isOnBack()) {
            this.setOnBack(false);
            return EnumInteractionResult.SUCCESS;
        } else if (this.isFood(itemstack)) {
            if (this.getTarget() != null) {
                this.gotBamboo = true;
            }

            if (this.isBaby()) {
                this.usePlayerItem(entityhuman, enumhand, itemstack);
                this.ageUp((int) ((float) (-this.getAge() / 20) * 0.1F), true);
            } else if (!this.level().isClientSide && this.getAge() == 0 && this.canFallInLove()) {
                this.usePlayerItem(entityhuman, enumhand, itemstack);
                this.setInLove(entityhuman);
            } else {
                World world = this.level();

                if (!(world instanceof WorldServer)) {
                    return EnumInteractionResult.PASS;
                }

                WorldServer worldserver = (WorldServer) world;

                if (this.isSitting() || this.isInWater()) {
                    return EnumInteractionResult.PASS;
                }

                this.tryToSit();
                this.eat(true);
                ItemStack itemstack1 = this.getItemBySlot(EnumItemSlot.MAINHAND);

                if (!itemstack1.isEmpty() && !entityhuman.hasInfiniteMaterials()) {
                    this.spawnAtLocation(worldserver, itemstack1);
                }

                this.setItemSlot(EnumItemSlot.MAINHAND, new ItemStack(itemstack.getItem(), 1));
                this.usePlayerItem(entityhuman, enumhand, itemstack);
            }

            return EnumInteractionResult.SUCCESS_SERVER;
        } else {
            return EnumInteractionResult.PASS;
        }
    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return this.isAggressive() ? SoundEffects.PANDA_AGGRESSIVE_AMBIENT : (this.isWorried() ? SoundEffects.PANDA_WORRIED_AMBIENT : SoundEffects.PANDA_AMBIENT);
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.PANDA_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.PANDA_FOOD);
    }

    @Nullable
    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.PANDA_DEATH;
    }

    @Nullable
    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.PANDA_HURT;
    }

    public boolean canPerformAction() {
        return !this.isOnBack() && !this.isScared() && !this.isEating() && !this.isRolling() && !this.isSitting();
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityPanda.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    private static boolean canPickUpAndEat(EntityItem entityitem) {
        return entityitem.getItem().is(TagsItem.PANDA_EATS_FROM_GROUND) && entityitem.isAlive() && !entityitem.hasPickUpDelay();
    }

    public static enum Gene implements INamable {

        NORMAL(0, "normal", false), LAZY(1, "lazy", false), WORRIED(2, "worried", false), PLAYFUL(3, "playful", false), BROWN(4, "brown", true), WEAK(5, "weak", true), AGGRESSIVE(6, "aggressive", false);

        public static final Codec<EntityPanda.Gene> CODEC = INamable.<EntityPanda.Gene>fromEnum(EntityPanda.Gene::values);
        private static final IntFunction<EntityPanda.Gene> BY_ID = ByIdMap.<EntityPanda.Gene>continuous(EntityPanda.Gene::getId, values(), ByIdMap.a.ZERO);
        private static final int MAX_GENE = 6;
        private final int id;
        private final String name;
        private final boolean isRecessive;

        private Gene(final int i, final String s, final boolean flag) {
            this.id = i;
            this.name = s;
            this.isRecessive = flag;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public boolean isRecessive() {
            return this.isRecessive;
        }

        static EntityPanda.Gene getVariantFromGenes(EntityPanda.Gene entitypanda_gene, EntityPanda.Gene entitypanda_gene1) {
            return entitypanda_gene.isRecessive() ? (entitypanda_gene == entitypanda_gene1 ? entitypanda_gene : EntityPanda.Gene.NORMAL) : entitypanda_gene;
        }

        public static EntityPanda.Gene byId(int i) {
            return (EntityPanda.Gene) EntityPanda.Gene.BY_ID.apply(i);
        }

        public static EntityPanda.Gene getRandom(RandomSource randomsource) {
            int i = randomsource.nextInt(16);

            return i == 0 ? EntityPanda.Gene.LAZY : (i == 1 ? EntityPanda.Gene.WORRIED : (i == 2 ? EntityPanda.Gene.PLAYFUL : (i == 4 ? EntityPanda.Gene.AGGRESSIVE : (i < 9 ? EntityPanda.Gene.WEAK : (i < 11 ? EntityPanda.Gene.BROWN : EntityPanda.Gene.NORMAL)))));
        }
    }

    private static class h extends ControllerMove {

        private final EntityPanda panda;

        public h(EntityPanda entitypanda) {
            super(entitypanda);
            this.panda = entitypanda;
        }

        @Override
        public void tick() {
            if (this.panda.canPerformAction()) {
                super.tick();
            }
        }
    }

    private static class b extends PathfinderGoalMeleeAttack {

        private final EntityPanda panda;

        public b(EntityPanda entitypanda, double d0, boolean flag) {
            super(entitypanda, d0, flag);
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.panda.canPerformAction() && super.canUse();
        }
    }

    private static class g extends PathfinderGoalLookAtPlayer {

        private final EntityPanda panda;

        public g(EntityPanda entitypanda, Class<? extends EntityLiving> oclass, float f) {
            super(entitypanda, oclass, f);
            this.panda = entitypanda;
        }

        public void setTarget(EntityLiving entityliving) {
            this.lookAt = entityliving;
        }

        @Override
        public boolean canContinueToUse() {
            return this.lookAt != null && super.canContinueToUse();
        }

        @Override
        public boolean canUse() {
            if (this.mob.getRandom().nextFloat() >= this.probability) {
                return false;
            } else {
                if (this.lookAt == null) {
                    WorldServer worldserver = getServerLevel((Entity) this.mob);

                    if (this.lookAtType == EntityHuman.class) {
                        this.lookAt = worldserver.getNearestPlayer(this.lookAtContext, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
                    } else {
                        this.lookAt = worldserver.getNearestEntity(this.mob.level().getEntitiesOfClass(this.lookAtType, this.mob.getBoundingBox().inflate((double) this.lookDistance, 3.0D, (double) this.lookDistance), (entityliving) -> {
                            return true;
                        }), this.lookAtContext, this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
                    }
                }

                return this.panda.canPerformAction() && this.lookAt != null;
            }
        }

        @Override
        public void tick() {
            if (this.lookAt != null) {
                super.tick();
            }

        }
    }

    private static class j extends PathfinderGoal {

        private final EntityPanda panda;

        public j(EntityPanda entitypanda) {
            this.panda = entitypanda;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK, PathfinderGoal.Type.JUMP));
        }

        @Override
        public boolean canUse() {
            if ((this.panda.isBaby() || this.panda.isPlayful()) && this.panda.onGround()) {
                if (!this.panda.canPerformAction()) {
                    return false;
                } else {
                    float f = this.panda.getYRot() * ((float) Math.PI / 180F);
                    float f1 = -MathHelper.sin(f);
                    float f2 = MathHelper.cos(f);
                    int i = (double) Math.abs(f1) > 0.5D ? MathHelper.sign((double) f1) : 0;
                    int j = (double) Math.abs(f2) > 0.5D ? MathHelper.sign((double) f2) : 0;

                    return this.panda.level().getBlockState(this.panda.blockPosition().offset(i, -1, j)).isAir() ? true : (this.panda.isPlayful() && this.panda.random.nextInt(reducedTickDelay(60)) == 1 ? true : this.panda.random.nextInt(reducedTickDelay(500)) == 1);
                }
            } else {
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.panda.roll(true);
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
    }

    private static class l extends PathfinderGoal {

        private final EntityPanda panda;

        public l(EntityPanda entitypanda) {
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.panda.isBaby() && this.panda.canPerformAction() ? (this.panda.isWeak() && this.panda.random.nextInt(reducedTickDelay(500)) == 1 ? true : this.panda.random.nextInt(reducedTickDelay(6000)) == 1) : false;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            this.panda.sneeze(true);
        }
    }

    private static class d extends PathfinderGoalBreed {

        private final EntityPanda panda;
        private int unhappyCooldown;

        public d(EntityPanda entitypanda, double d0) {
            super(entitypanda, d0);
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            if (super.canUse() && this.panda.getUnhappyCounter() == 0) {
                if (!this.canFindBamboo()) {
                    if (this.unhappyCooldown <= this.panda.tickCount) {
                        this.panda.setUnhappyCounter(32);
                        this.unhappyCooldown = this.panda.tickCount + 600;
                        if (this.panda.isEffectiveAi()) {
                            EntityHuman entityhuman = this.level.getNearestPlayer(EntityPanda.BREED_TARGETING, this.panda);

                            this.panda.lookAtPlayerGoal.setTarget(entityhuman);
                        }
                    }

                    return false;
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }

        private boolean canFindBamboo() {
            BlockPosition blockposition = this.panda.blockPosition();
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();

            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 8; ++j) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockposition_mutableblockposition.setWithOffset(blockposition, k, i, l);
                            if (this.level.getBlockState(blockposition_mutableblockposition).is(Blocks.BAMBOO)) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }
    }

    private static class c<T extends EntityLiving> extends PathfinderGoalAvoidTarget<T> {

        private final EntityPanda panda;

        public c(EntityPanda entitypanda, Class<T> oclass, float f, double d0, double d1) {
            // Predicate predicate = IEntitySelector.NO_SPECTATORS;

            // Objects.requireNonNull(predicate);
            super(entitypanda, oclass, f, d0, d1, IEntitySelector.NO_SPECTATORS::test);
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.panda.isWorried() && this.panda.canPerformAction() && super.canUse();
        }
    }

    private class k extends PathfinderGoal {

        private int cooldown;

        public k() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.cooldown <= EntityPanda.this.tickCount && !EntityPanda.this.isBaby() && !EntityPanda.this.isInWater() && EntityPanda.this.canPerformAction() && EntityPanda.this.getUnhappyCounter() <= 0 ? (!EntityPanda.this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty() ? true : !EntityPanda.this.level().getEntitiesOfClass(EntityItem.class, EntityPanda.this.getBoundingBox().inflate(6.0D, 6.0D, 6.0D), EntityPanda::canPickUpAndEat).isEmpty()) : false;
        }

        @Override
        public boolean canContinueToUse() {
            return !EntityPanda.this.isInWater() && (EntityPanda.this.isLazy() || EntityPanda.this.random.nextInt(reducedTickDelay(600)) != 1) ? EntityPanda.this.random.nextInt(reducedTickDelay(2000)) != 1 : false;
        }

        @Override
        public void tick() {
            if (!EntityPanda.this.isSitting() && !EntityPanda.this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty()) {
                EntityPanda.this.tryToSit();
            }

        }

        @Override
        public void start() {
            if (EntityPanda.this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty()) {
                List<EntityItem> list = EntityPanda.this.level().<EntityItem>getEntitiesOfClass(EntityItem.class, EntityPanda.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), EntityPanda::canPickUpAndEat);

                if (!list.isEmpty()) {
                    EntityPanda.this.getNavigation().moveTo((Entity) list.getFirst(), (double) 1.2F);
                }
            } else {
                EntityPanda.this.tryToSit();
            }

            this.cooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack itemstack = EntityPanda.this.getItemBySlot(EnumItemSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                EntityPanda.this.spawnAtLocation(getServerLevel(EntityPanda.this.level()), itemstack);
                EntityPanda.this.setItemSlot(EnumItemSlot.MAINHAND, ItemStack.EMPTY);
                int i = EntityPanda.this.isLazy() ? EntityPanda.this.random.nextInt(50) + 10 : EntityPanda.this.random.nextInt(150) + 10;

                this.cooldown = EntityPanda.this.tickCount + i * 20;
            }

            EntityPanda.this.sit(false);
        }
    }

    private static class f extends PathfinderGoal {

        private final EntityPanda panda;
        private int cooldown;

        public f(EntityPanda entitypanda) {
            this.panda = entitypanda;
        }

        @Override
        public boolean canUse() {
            return this.cooldown < this.panda.tickCount && this.panda.isLazy() && this.panda.canPerformAction() && this.panda.random.nextInt(reducedTickDelay(400)) == 1;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.panda.isInWater() && (this.panda.isLazy() || this.panda.random.nextInt(reducedTickDelay(600)) != 1) ? this.panda.random.nextInt(reducedTickDelay(2000)) != 1 : false;
        }

        @Override
        public void start() {
            this.panda.setOnBack(true);
            this.cooldown = 0;
        }

        @Override
        public void stop() {
            this.panda.setOnBack(false);
            this.cooldown = this.panda.tickCount + 200;
        }
    }

    private static class e extends PathfinderGoalHurtByTarget {

        private final EntityPanda panda;

        public e(EntityPanda entitypanda, Class<?>... aclass) {
            super(entitypanda, aclass);
            this.panda = entitypanda;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.panda.gotBamboo && !this.panda.didBite) {
                return super.canContinueToUse();
            } else {
                this.panda.setTarget((EntityLiving) null);
                return false;
            }
        }

        @Override
        protected void alertOther(EntityInsentient entityinsentient, EntityLiving entityliving) {
            if (entityinsentient instanceof EntityPanda && entityinsentient.isAggressive()) {
                entityinsentient.setTarget(entityliving, EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY, true); // CraftBukkit
            }

        }
    }

    private static class i extends PathfinderGoalPanic {

        private final EntityPanda panda;

        public i(EntityPanda entitypanda, double d0) {
            super(entitypanda, d0, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES);
            this.panda = entitypanda;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.panda.isSitting()) {
                this.panda.getNavigation().stop();
                return false;
            } else {
                return super.canContinueToUse();
            }
        }
    }
}
