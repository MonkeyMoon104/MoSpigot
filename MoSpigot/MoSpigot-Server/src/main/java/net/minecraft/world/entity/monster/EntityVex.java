package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityCreature;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalTarget;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.raid.EntityRaider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

public class EntityVex extends EntityMonster implements TraceableEntity {

    public static final float FLAP_DEGREES_PER_TICK = 45.836624F;
    public static final int TICKS_PER_FLAP = MathHelper.ceil(3.9269907F);
    protected static final DataWatcherObject<Byte> DATA_FLAGS_ID = DataWatcher.<Byte>defineId(EntityVex.class, DataWatcherRegistry.BYTE);
    private static final int FLAG_IS_CHARGING = 1;
    @Nullable
    private EntityReference<EntityInsentient> owner;
    @Nullable
    private BlockPosition boundOrigin;
    public boolean hasLimitedLife;
    public int limitedLifeTicks;

    public EntityVex(EntityTypes<? extends EntityVex> entitytypes, World world) {
        super(entitytypes, world);
        this.moveControl = new EntityVex.c(this);
        this.xpReward = 3;
    }

    @Override
    public boolean isFlapping() {
        return this.tickCount % EntityVex.TICKS_PER_FLAP == 0;
    }

    @Override
    protected boolean isAffectedByBlocks() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);
        if (this.hasLimitedLife && --this.limitedLifeTicks <= 0) {
            this.limitedLifeTicks = 20;
            this.hurt(this.damageSources().starve(), 1.0F);
        }

    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(4, new EntityVex.a());
        this.goalSelector.addGoal(8, new EntityVex.d());
        this.goalSelector.addGoal(9, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new PathfinderGoalLookAtPlayer(this, EntityInsentient.class, 8.0F));
        this.targetSelector.addGoal(1, (new PathfinderGoalHurtByTarget(this, new Class[]{EntityRaider.class})).setAlertOthers());
        this.targetSelector.addGoal(2, new EntityVex.b(this));
        this.targetSelector.addGoal(3, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, true));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MAX_HEALTH, 14.0D).add(GenericAttributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityVex.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.boundOrigin = (BlockPosition) valueinput.read("bound_pos", BlockPosition.CODEC).orElse(null); // CraftBukkit - decompile error
        valueinput.getInt("life_ticks").ifPresentOrElse(this::setLimitedLife, () -> {
            this.hasLimitedLife = false;
        });
        this.owner = EntityReference.<EntityInsentient>read(valueinput, "owner");
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof EntityVex entityvex) {
            this.owner = entityvex.owner;
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.storeNullable("bound_pos", BlockPosition.CODEC, this.boundOrigin);
        if (this.hasLimitedLife) {
            valueoutput.putInt("life_ticks", this.limitedLifeTicks);
        }

        EntityReference.store(this.owner, valueoutput, "owner");
    }

    @Nullable
    @Override
    public EntityInsentient getOwner() {
        return (EntityInsentient) EntityReference.get(this.owner, this.level(), EntityInsentient.class);
    }

    @Nullable
    public BlockPosition getBoundOrigin() {
        return this.boundOrigin;
    }

    public void setBoundOrigin(@Nullable BlockPosition blockposition) {
        this.boundOrigin = blockposition;
    }

    private boolean getVexFlag(int i) {
        int j = (Byte) this.entityData.get(EntityVex.DATA_FLAGS_ID);

        return (j & i) != 0;
    }

    private void setVexFlag(int i, boolean flag) {
        int j = (Byte) this.entityData.get(EntityVex.DATA_FLAGS_ID);

        if (flag) {
            j |= i;
        } else {
            j &= ~i;
        }

        this.entityData.set(EntityVex.DATA_FLAGS_ID, (byte) (j & 255));
    }

    public boolean isCharging() {
        return this.getVexFlag(1);
    }

    public void setIsCharging(boolean flag) {
        this.setVexFlag(1, flag);
    }

    public void setOwner(EntityInsentient entityinsentient) {
        this.owner = new EntityReference<EntityInsentient>(entityinsentient);
    }

    public void setLimitedLife(int i) {
        this.hasLimitedLife = true;
        this.limitedLifeTicks = i;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.VEX_AMBIENT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.VEX_DEATH;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.VEX_HURT;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();

        this.populateDefaultEquipmentSlots(randomsource, difficultydamagescaler);
        this.populateDefaultEquipmentEnchantments(worldaccess, randomsource, difficultydamagescaler);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        this.setItemSlot(EnumItemSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setDropChance(EnumItemSlot.MAINHAND, 0.0F);
    }

    private class c extends ControllerMove {

        public c(final EntityVex entityvex) {
            super(entityvex);
        }

        @Override
        public void tick() {
            if (this.operation == ControllerMove.Operation.MOVE_TO) {
                Vec3D vec3d = new Vec3D(this.wantedX - EntityVex.this.getX(), this.wantedY - EntityVex.this.getY(), this.wantedZ - EntityVex.this.getZ());
                double d0 = vec3d.length();

                if (d0 < EntityVex.this.getBoundingBox().getSize()) {
                    this.operation = ControllerMove.Operation.WAIT;
                    EntityVex.this.setDeltaMovement(EntityVex.this.getDeltaMovement().scale(0.5D));
                } else {
                    EntityVex.this.setDeltaMovement(EntityVex.this.getDeltaMovement().add(vec3d.scale(this.speedModifier * 0.05D / d0)));
                    if (EntityVex.this.getTarget() == null) {
                        Vec3D vec3d1 = EntityVex.this.getDeltaMovement();

                        EntityVex.this.setYRot(-((float) MathHelper.atan2(vec3d1.x, vec3d1.z)) * (180F / (float) Math.PI));
                        EntityVex.this.yBodyRot = EntityVex.this.getYRot();
                    } else {
                        double d1 = EntityVex.this.getTarget().getX() - EntityVex.this.getX();
                        double d2 = EntityVex.this.getTarget().getZ() - EntityVex.this.getZ();

                        EntityVex.this.setYRot(-((float) MathHelper.atan2(d1, d2)) * (180F / (float) Math.PI));
                        EntityVex.this.yBodyRot = EntityVex.this.getYRot();
                    }
                }

            }
        }
    }

    private class a extends PathfinderGoal {

        public a() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            EntityLiving entityliving = EntityVex.this.getTarget();

            return entityliving != null && entityliving.isAlive() && !EntityVex.this.getMoveControl().hasWanted() && EntityVex.this.random.nextInt(reducedTickDelay(7)) == 0 ? EntityVex.this.distanceToSqr((Entity) entityliving) > 4.0D : false;
        }

        @Override
        public boolean canContinueToUse() {
            return EntityVex.this.getMoveControl().hasWanted() && EntityVex.this.isCharging() && EntityVex.this.getTarget() != null && EntityVex.this.getTarget().isAlive();
        }

        @Override
        public void start() {
            EntityLiving entityliving = EntityVex.this.getTarget();

            if (entityliving != null) {
                Vec3D vec3d = entityliving.getEyePosition();

                EntityVex.this.moveControl.setWantedPosition(vec3d.x, vec3d.y, vec3d.z, 1.0D);
            }

            EntityVex.this.setIsCharging(true);
            EntityVex.this.playSound(SoundEffects.VEX_CHARGE, 1.0F, 1.0F);
        }

        @Override
        public void stop() {
            EntityVex.this.setIsCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            EntityLiving entityliving = EntityVex.this.getTarget();

            if (entityliving != null) {
                if (EntityVex.this.getBoundingBox().intersects(entityliving.getBoundingBox())) {
                    EntityVex.this.doHurtTarget(getServerLevel(EntityVex.this.level()), entityliving);
                    EntityVex.this.setIsCharging(false);
                } else {
                    double d0 = EntityVex.this.distanceToSqr((Entity) entityliving);

                    if (d0 < 9.0D) {
                        Vec3D vec3d = entityliving.getEyePosition();

                        EntityVex.this.moveControl.setWantedPosition(vec3d.x, vec3d.y, vec3d.z, 1.0D);
                    }
                }

            }
        }
    }

    private class d extends PathfinderGoal {

        public d() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            return !EntityVex.this.getMoveControl().hasWanted() && EntityVex.this.random.nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            BlockPosition blockposition = EntityVex.this.getBoundOrigin();

            if (blockposition == null) {
                blockposition = EntityVex.this.blockPosition();
            }

            for (int i = 0; i < 3; ++i) {
                BlockPosition blockposition1 = blockposition.offset(EntityVex.this.random.nextInt(15) - 7, EntityVex.this.random.nextInt(11) - 5, EntityVex.this.random.nextInt(15) - 7);

                if (EntityVex.this.level().isEmptyBlock(blockposition1)) {
                    EntityVex.this.moveControl.setWantedPosition((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.5D, (double) blockposition1.getZ() + 0.5D, 0.25D);
                    if (EntityVex.this.getTarget() == null) {
                        EntityVex.this.getLookControl().setLookAt((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY() + 0.5D, (double) blockposition1.getZ() + 0.5D, 180.0F, 20.0F);
                    }
                    break;
                }
            }

        }
    }

    private class b extends PathfinderGoalTarget {

        private final PathfinderTargetCondition copyOwnerTargeting = PathfinderTargetCondition.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

        public b(final EntityCreature entitycreature) {
            super(entitycreature, false);
        }

        @Override
        public boolean canUse() {
            EntityInsentient entityinsentient = EntityVex.this.getOwner();

            return entityinsentient != null && entityinsentient.getTarget() != null && this.canAttack(entityinsentient.getTarget(), this.copyOwnerTargeting);
        }

        @Override
        public void start() {
            EntityInsentient entityinsentient = EntityVex.this.getOwner();

            EntityVex.this.setTarget(entityinsentient != null ? entityinsentient.getTarget() : null, org.bukkit.event.entity.EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET, true); // CraftBukkit
            super.start();
        }
    }
}
