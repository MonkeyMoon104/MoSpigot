package net.minecraft.world.entity.monster;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityCreature;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBowShoot;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFleeSun;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRestrictSun;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.EntityIronGolem;
import net.minecraft.world.entity.animal.EntityTurtle;
import net.minecraft.world.entity.animal.wolf.EntityWolf;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.entity.projectile.ProjectileHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemProjectileWeapon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;

public abstract class EntitySkeletonAbstract extends EntityMonster implements IRangedEntity {

    private static final int HARD_ATTACK_INTERVAL = 20;
    private static final int NORMAL_ATTACK_INTERVAL = 40;
    private final PathfinderGoalBowShoot<EntitySkeletonAbstract> bowGoal = new PathfinderGoalBowShoot<EntitySkeletonAbstract>(this, 1.0D, 20, 15.0F);
    private final PathfinderGoalMeleeAttack meleeGoal = new PathfinderGoalMeleeAttack(this, 1.2D, false) {
        @Override
        public void stop() {
            super.stop();
            EntitySkeletonAbstract.this.setAggressive(false);
        }

        @Override
        public void start() {
            super.start();
            EntitySkeletonAbstract.this.setAggressive(true);
        }
    };

    protected EntitySkeletonAbstract(EntityTypes<? extends EntitySkeletonAbstract> entitytypes, World world) {
        super(entitytypes, world);
        this.reassessWeaponGoal();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new PathfinderGoalRestrictSun(this));
        this.goalSelector.addGoal(3, new PathfinderGoalFleeSun(this, 1.0D));
        this.goalSelector.addGoal(3, new PathfinderGoalAvoidTarget(this, EntityWolf.class, 6.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(5, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.goalSelector.addGoal(6, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F));
        this.goalSelector.addGoal(6, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, new PathfinderGoalHurtByTarget(this, new Class[0]));
        this.targetSelector.addGoal(2, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, true));
        this.targetSelector.addGoal(3, new PathfinderGoalNearestAttackableTarget(this, EntityIronGolem.class, true));
        this.targetSelector.addGoal(3, new PathfinderGoalNearestAttackableTarget(this, EntityTurtle.class, 10, true, false, EntityTurtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }

    abstract SoundEffect getStepSound();

    @Override
    public void aiStep() {
        boolean flag = this.isSunBurnTick();

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

        super.aiStep();
    }

    @Override
    public void rideTick() {
        super.rideTick();
        Entity entity = this.getControlledVehicle();

        if (entity instanceof EntityCreature entitycreature) {
            this.yBodyRot = entitycreature.yBodyRot;
        }

    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        super.populateDefaultEquipmentSlots(randomsource, difficultydamagescaler);
        this.setItemSlot(EnumItemSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        groupdataentity = super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
        RandomSource randomsource = worldaccess.getRandom();

        this.populateDefaultEquipmentSlots(randomsource, difficultydamagescaler);
        this.populateDefaultEquipmentEnchantments(worldaccess, randomsource, difficultydamagescaler);
        this.reassessWeaponGoal();
        this.setCanPickUpLoot(randomsource.nextFloat() < 0.55F * difficultydamagescaler.getSpecialMultiplier());
        if (this.getItemBySlot(EnumItemSlot.HEAD).isEmpty()) {
            LocalDate localdate = LocalDate.now();
            int i = localdate.get(ChronoField.DAY_OF_MONTH);
            int j = localdate.get(ChronoField.MONTH_OF_YEAR);

            if (j == 10 && i == 31 && randomsource.nextFloat() < 0.25F) {
                this.setItemSlot(EnumItemSlot.HEAD, new ItemStack(randomsource.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
                this.setDropChance(EnumItemSlot.HEAD, 0.0F);
            }
        }

        return groupdataentity;
    }

    public void reassessWeaponGoal() {
        if (this.level() != null && !this.level().isClientSide) {
            this.goalSelector.removeGoal(this.meleeGoal);
            this.goalSelector.removeGoal(this.bowGoal);
            ItemStack itemstack = this.getItemInHand(ProjectileHelper.getWeaponHoldingHand(this, Items.BOW));

            if (itemstack.is(Items.BOW)) {
                int i = this.getHardAttackInterval();

                if (this.level().getDifficulty() != EnumDifficulty.HARD) {
                    i = this.getAttackInterval();
                }

                this.bowGoal.setMinAttackInterval(i);
                this.goalSelector.addGoal(4, this.bowGoal);
            } else {
                this.goalSelector.addGoal(4, this.meleeGoal);
            }

        }
    }

    protected int getHardAttackInterval() {
        return 20;
    }

    protected int getAttackInterval() {
        return 40;
    }

    @Override
    public void performRangedAttack(EntityLiving entityliving, float f) {
        ItemStack itemstack = this.getItemInHand(ProjectileHelper.getWeaponHoldingHand(this, Items.BOW));
        ItemStack itemstack1 = this.getProjectile(itemstack);
        EntityArrow entityarrow = this.getArrow(itemstack1, f, itemstack);
        double d0 = entityliving.getX() - this.getX();
        double d1 = entityliving.getY(0.3333333333333333D) - entityarrow.getY();
        double d2 = entityliving.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            // CraftBukkit start
            org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(this, this.getMainHandItem(), null, entityarrow, net.minecraft.world.EnumHand.MAIN_HAND, 0.8F, true);
            if (event.isCancelled()) {
                event.getProjectile().remove();
                return;
            }

            if (event.getProjectile() == entityarrow.getBukkitEntity()) {
                IProjectile.spawnProjectileUsingShoot(entityarrow, worldserver, itemstack1, d0, d1 + d3 * (double) 0.2F, d2, 1.6F, (float) (14 - worldserver.getDifficulty().getId() * 4));
            }
            // CraftBukkit end
        }

        this.playSound(SoundEffects.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    protected EntityArrow getArrow(ItemStack itemstack, float f, @Nullable ItemStack itemstack1) {
        return ProjectileHelper.getMobArrow(this, itemstack, f, itemstack1);
    }

    @Override
    public boolean canFireProjectileWeapon(ItemProjectileWeapon itemprojectileweapon) {
        return itemprojectileweapon == Items.BOW;
    }

    @Override
    public TagKey<Item> getPreferredWeaponType() {
        return TagsItem.SKELETON_PREFERRED_WEAPONS;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.reassessWeaponGoal();
    }

    @Override
    // CraftBukkit start
    public void onEquipItem(EnumItemSlot enumitemslot, ItemStack itemstack, ItemStack itemstack1, boolean silent) {
        super.onEquipItem(enumitemslot, itemstack, itemstack1, silent);
        // CraftBukkit end
        if (!this.level().isClientSide) {
            this.reassessWeaponGoal();
        }

    }

    public boolean isShaking() {
        return this.isFullyFrozen();
    }
}
