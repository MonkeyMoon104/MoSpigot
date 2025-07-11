package net.minecraft.world.entity.monster;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.MathHelper;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerLook;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.control.EntityAIBodyControl;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.animal.EntityCat;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

public class EntityPhantom extends EntityInsentient implements IMonster {

    public static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    public static final int TICKS_PER_FLAP = MathHelper.ceil(24.166098F);
    private static final DataWatcherObject<Integer> ID_SIZE = DataWatcher.<Integer>defineId(EntityPhantom.class, DataWatcherRegistry.INT);
    Vec3D moveTargetPoint;
    @Nullable
    BlockPosition anchorPoint;
    EntityPhantom.AttackPhase attackPhase;

    public EntityPhantom(EntityTypes<? extends EntityPhantom> entitytypes, World world) {
        super(entitytypes, world);
        this.moveTargetPoint = Vec3D.ZERO;
        this.attackPhase = EntityPhantom.AttackPhase.CIRCLE;
        this.xpReward = 5;
        this.moveControl = new EntityPhantom.g(this);
        this.lookControl = new EntityPhantom.f(this);
    }

    @Override
    public boolean isFlapping() {
        return (this.getUniqueFlapTickOffset() + this.tickCount) % EntityPhantom.TICKS_PER_FLAP == 0;
    }

    @Override
    protected EntityAIBodyControl createBodyControl() {
        return new EntityPhantom.d(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new EntityPhantom.c());
        this.goalSelector.addGoal(2, new EntityPhantom.i());
        this.goalSelector.addGoal(3, new EntityPhantom.e());
        this.targetSelector.addGoal(1, new EntityPhantom.b());
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityPhantom.ID_SIZE, 0);
    }

    public void setPhantomSize(int i) {
        this.entityData.set(EntityPhantom.ID_SIZE, MathHelper.clamp(i, 0, 64));
    }

    private void updatePhantomSizeInfo() {
        this.refreshDimensions();
        this.getAttribute(GenericAttributes.ATTACK_DAMAGE).setBaseValue((double) (6 + this.getPhantomSize()));
    }

    public int getPhantomSize() {
        return (Integer) this.entityData.get(EntityPhantom.ID_SIZE);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityPhantom.ID_SIZE.equals(datawatcherobject)) {
            this.updatePhantomSizeInfo();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            float f = MathHelper.cos((float) (this.getUniqueFlapTickOffset() + this.tickCount) * 7.448451F * ((float) Math.PI / 180F) + (float) Math.PI);
            float f1 = MathHelper.cos((float) (this.getUniqueFlapTickOffset() + this.tickCount + 1) * 7.448451F * ((float) Math.PI / 180F) + (float) Math.PI);

            if (f > 0.0F && f1 <= 0.0F) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.PHANTOM_FLAP, this.getSoundSource(), 0.95F + this.random.nextFloat() * 0.05F, 0.95F + this.random.nextFloat() * 0.05F, false);
            }

            float f2 = this.getBbWidth() * 1.48F;
            float f3 = MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F)) * f2;
            float f4 = MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)) * f2;
            float f5 = (0.3F + f * 0.45F) * this.getBbHeight() * 2.5F;

            this.level().addParticle(Particles.MYCELIUM, this.getX() + (double) f3, this.getY() + (double) f5, this.getZ() + (double) f4, 0.0D, 0.0D, 0.0D);
            this.level().addParticle(Particles.MYCELIUM, this.getX() - (double) f3, this.getY() + (double) f5, this.getZ() - (double) f4, 0.0D, 0.0D, 0.0D);
        }

    }

    @Override
    public void aiStep() {
        if (this.isAlive() && this.isSunBurnTick()) {
            this.igniteForSeconds(8.0F);
        }

        super.aiStep();
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, IBlockData iblockdata, BlockPosition blockposition) {}

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(Vec3D vec3d) {
        this.travelFlying(vec3d, 0.2F);
    }

    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        this.anchorPoint = this.blockPosition().above(5);
        this.setPhantomSize(0);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.anchorPoint = (BlockPosition) valueinput.read("anchor_pos", BlockPosition.CODEC).orElse(null); // CraftBukkit - decompile error
        this.setPhantomSize(valueinput.getIntOr("size", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.storeNullable("anchor_pos", BlockPosition.CODEC, this.anchorPoint);
        valueoutput.putInt("size", this.getPhantomSize());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d0) {
        return true;
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.HOSTILE;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.PHANTOM_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.PHANTOM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    @Override
    public boolean canAttackType(EntityTypes<?> entitytypes) {
        return true;
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        int i = this.getPhantomSize();
        EntitySize entitysize = super.getDefaultDimensions(entitypose);

        return entitysize.scale(1.0F + 0.15F * (float) i);
    }

    boolean canAttack(WorldServer worldserver, EntityLiving entityliving, PathfinderTargetCondition pathfindertargetcondition) {
        return pathfindertargetcondition.test(worldserver, this, entityliving);
    }

    private static enum AttackPhase {

        CIRCLE, SWOOP;

        private AttackPhase() {}
    }

    private class g extends ControllerMove {

        private float speed = 0.1F;

        public g(final EntityInsentient entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void tick() {
            if (EntityPhantom.this.horizontalCollision) {
                EntityPhantom.this.setYRot(EntityPhantom.this.getYRot() + 180.0F);
                this.speed = 0.1F;
            }

            double d0 = EntityPhantom.this.moveTargetPoint.x - EntityPhantom.this.getX();
            double d1 = EntityPhantom.this.moveTargetPoint.y - EntityPhantom.this.getY();
            double d2 = EntityPhantom.this.moveTargetPoint.z - EntityPhantom.this.getZ();
            double d3 = Math.sqrt(d0 * d0 + d2 * d2);

            if (Math.abs(d3) > (double) 1.0E-5F) {
                double d4 = 1.0D - Math.abs(d1 * (double) 0.7F) / d3;

                d0 *= d4;
                d2 *= d4;
                d3 = Math.sqrt(d0 * d0 + d2 * d2);
                double d5 = Math.sqrt(d0 * d0 + d2 * d2 + d1 * d1);
                float f = EntityPhantom.this.getYRot();
                float f1 = (float) MathHelper.atan2(d2, d0);
                float f2 = MathHelper.wrapDegrees(EntityPhantom.this.getYRot() + 90.0F);
                float f3 = MathHelper.wrapDegrees(f1 * (180F / (float) Math.PI));

                EntityPhantom.this.setYRot(MathHelper.approachDegrees(f2, f3, 4.0F) - 90.0F);
                EntityPhantom.this.yBodyRot = EntityPhantom.this.getYRot();
                if (MathHelper.degreesDifferenceAbs(f, EntityPhantom.this.getYRot()) < 3.0F) {
                    this.speed = MathHelper.approach(this.speed, 1.8F, 0.005F * (1.8F / this.speed));
                } else {
                    this.speed = MathHelper.approach(this.speed, 0.2F, 0.025F);
                }

                float f4 = (float) (-(MathHelper.atan2(-d1, d3) * (double) (180F / (float) Math.PI)));

                EntityPhantom.this.setXRot(f4);
                float f5 = EntityPhantom.this.getYRot() + 90.0F;
                double d6 = (double) (this.speed * MathHelper.cos(f5 * ((float) Math.PI / 180F))) * Math.abs(d0 / d5);
                double d7 = (double) (this.speed * MathHelper.sin(f5 * ((float) Math.PI / 180F))) * Math.abs(d2 / d5);
                double d8 = (double) (this.speed * MathHelper.sin(f4 * ((float) Math.PI / 180F))) * Math.abs(d1 / d5);
                Vec3D vec3d = EntityPhantom.this.getDeltaMovement();

                EntityPhantom.this.setDeltaMovement(vec3d.add((new Vec3D(d6, d8, d7)).subtract(vec3d).scale(0.2D)));
            }

        }
    }

    private class d extends EntityAIBodyControl {

        public d(final EntityInsentient entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void clientTick() {
            EntityPhantom.this.yHeadRot = EntityPhantom.this.yBodyRot;
            EntityPhantom.this.yBodyRot = EntityPhantom.this.getYRot();
        }
    }

    private static class f extends ControllerLook {

        public f(EntityInsentient entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void tick() {}
    }

    private abstract class h extends PathfinderGoal {

        public h() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        protected boolean touchingTarget() {
            return EntityPhantom.this.moveTargetPoint.distanceToSqr(EntityPhantom.this.getX(), EntityPhantom.this.getY(), EntityPhantom.this.getZ()) < 4.0D;
        }
    }

    private class e extends EntityPhantom.h {

        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        e() {}

        @Override
        public boolean canUse() {
            return EntityPhantom.this.getTarget() == null || EntityPhantom.this.attackPhase == EntityPhantom.AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            this.distance = 5.0F + EntityPhantom.this.random.nextFloat() * 10.0F;
            this.height = -4.0F + EntityPhantom.this.random.nextFloat() * 9.0F;
            this.clockwise = EntityPhantom.this.random.nextBoolean() ? 1.0F : -1.0F;
            this.selectNext();
        }

        @Override
        public void tick() {
            if (EntityPhantom.this.random.nextInt(this.adjustedTickDelay(350)) == 0) {
                this.height = -4.0F + EntityPhantom.this.random.nextFloat() * 9.0F;
            }

            if (EntityPhantom.this.random.nextInt(this.adjustedTickDelay(250)) == 0) {
                ++this.distance;
                if (this.distance > 15.0F) {
                    this.distance = 5.0F;
                    this.clockwise = -this.clockwise;
                }
            }

            if (EntityPhantom.this.random.nextInt(this.adjustedTickDelay(450)) == 0) {
                this.angle = EntityPhantom.this.random.nextFloat() * 2.0F * (float) Math.PI;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            if (EntityPhantom.this.moveTargetPoint.y < EntityPhantom.this.getY() && !EntityPhantom.this.level().isEmptyBlock(EntityPhantom.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }

            if (EntityPhantom.this.moveTargetPoint.y > EntityPhantom.this.getY() && !EntityPhantom.this.level().isEmptyBlock(EntityPhantom.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }

        }

        private void selectNext() {
            if (EntityPhantom.this.anchorPoint == null) {
                EntityPhantom.this.anchorPoint = EntityPhantom.this.blockPosition();
            }

            this.angle += this.clockwise * 15.0F * ((float) Math.PI / 180F);
            EntityPhantom.this.moveTargetPoint = Vec3D.atLowerCornerOf(EntityPhantom.this.anchorPoint).add((double) (this.distance * MathHelper.cos(this.angle)), (double) (-4.0F + this.height), (double) (this.distance * MathHelper.sin(this.angle)));
        }
    }

    private class i extends EntityPhantom.h {

        private static final int CAT_SEARCH_TICK_DELAY = 20;
        private boolean isScaredOfCat;
        private int catSearchTick;

        i() {}

        @Override
        public boolean canUse() {
            return EntityPhantom.this.getTarget() != null && EntityPhantom.this.attackPhase == EntityPhantom.AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            EntityLiving entityliving = EntityPhantom.this.getTarget();

            if (entityliving == null) {
                return false;
            } else if (!entityliving.isAlive()) {
                return false;
            } else {
                if (entityliving instanceof EntityHuman) {
                    EntityHuman entityhuman = (EntityHuman) entityliving;

                    if (entityliving.isSpectator() || entityhuman.isCreative()) {
                        return false;
                    }
                }

                if (!this.canUse()) {
                    return false;
                } else {
                    if (EntityPhantom.this.tickCount > this.catSearchTick) {
                        this.catSearchTick = EntityPhantom.this.tickCount + 20;
                        List<EntityCat> list = EntityPhantom.this.level().<EntityCat>getEntitiesOfClass(EntityCat.class, EntityPhantom.this.getBoundingBox().inflate(16.0D), IEntitySelector.ENTITY_STILL_ALIVE);

                        for (EntityCat entitycat : list) {
                            entitycat.hiss();
                        }

                        this.isScaredOfCat = !list.isEmpty();
                    }

                    return !this.isScaredOfCat;
                }
            }
        }

        @Override
        public void start() {}

        @Override
        public void stop() {
            EntityPhantom.this.setTarget((EntityLiving) null);
            EntityPhantom.this.attackPhase = EntityPhantom.AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            EntityLiving entityliving = EntityPhantom.this.getTarget();

            if (entityliving != null) {
                EntityPhantom.this.moveTargetPoint = new Vec3D(entityliving.getX(), entityliving.getY(0.5D), entityliving.getZ());
                if (EntityPhantom.this.getBoundingBox().inflate((double) 0.2F).intersects(entityliving.getBoundingBox())) {
                    EntityPhantom.this.doHurtTarget(getServerLevel(EntityPhantom.this.level()), entityliving);
                    EntityPhantom.this.attackPhase = EntityPhantom.AttackPhase.CIRCLE;
                    if (!EntityPhantom.this.isSilent()) {
                        EntityPhantom.this.level().levelEvent(1039, EntityPhantom.this.blockPosition(), 0);
                    }
                } else if (EntityPhantom.this.horizontalCollision || EntityPhantom.this.hurtTime > 0) {
                    EntityPhantom.this.attackPhase = EntityPhantom.AttackPhase.CIRCLE;
                }

            }
        }
    }

    private class c extends PathfinderGoal {

        private int nextSweepTick;

        c() {}

        @Override
        public boolean canUse() {
            EntityLiving entityliving = EntityPhantom.this.getTarget();

            return entityliving != null ? EntityPhantom.this.canAttack(getServerLevel(EntityPhantom.this.level()), entityliving, PathfinderTargetCondition.DEFAULT) : false;
        }

        @Override
        public void start() {
            this.nextSweepTick = this.adjustedTickDelay(10);
            EntityPhantom.this.attackPhase = EntityPhantom.AttackPhase.CIRCLE;
            this.setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            if (EntityPhantom.this.anchorPoint != null) {
                EntityPhantom.this.anchorPoint = EntityPhantom.this.level().getHeightmapPos(HeightMap.Type.MOTION_BLOCKING, EntityPhantom.this.anchorPoint).above(10 + EntityPhantom.this.random.nextInt(20));
            }

        }

        @Override
        public void tick() {
            if (EntityPhantom.this.attackPhase == EntityPhantom.AttackPhase.CIRCLE) {
                --this.nextSweepTick;
                if (this.nextSweepTick <= 0) {
                    EntityPhantom.this.attackPhase = EntityPhantom.AttackPhase.SWOOP;
                    this.setAnchorAboveTarget();
                    this.nextSweepTick = this.adjustedTickDelay((8 + EntityPhantom.this.random.nextInt(4)) * 20);
                    EntityPhantom.this.playSound(SoundEffects.PHANTOM_SWOOP, 10.0F, 0.95F + EntityPhantom.this.random.nextFloat() * 0.1F);
                }
            }

        }

        private void setAnchorAboveTarget() {
            if (EntityPhantom.this.anchorPoint != null) {
                EntityPhantom.this.anchorPoint = EntityPhantom.this.getTarget().blockPosition().above(20 + EntityPhantom.this.random.nextInt(20));
                if (EntityPhantom.this.anchorPoint.getY() < EntityPhantom.this.level().getSeaLevel()) {
                    EntityPhantom.this.anchorPoint = new BlockPosition(EntityPhantom.this.anchorPoint.getX(), EntityPhantom.this.level().getSeaLevel() + 1, EntityPhantom.this.anchorPoint.getZ());
                }

            }
        }
    }

    private class b extends PathfinderGoal {

        private final PathfinderTargetCondition attackTargeting = PathfinderTargetCondition.forCombat().range(64.0D);
        private int nextScanTick = reducedTickDelay(20);

        b() {}

        @Override
        public boolean canUse() {
            if (this.nextScanTick > 0) {
                --this.nextScanTick;
                return false;
            } else {
                this.nextScanTick = reducedTickDelay(60);
                WorldServer worldserver = getServerLevel(EntityPhantom.this.level());
                List<EntityHuman> list = worldserver.getNearbyPlayers(this.attackTargeting, EntityPhantom.this, EntityPhantom.this.getBoundingBox().inflate(16.0D, 64.0D, 16.0D));

                if (!list.isEmpty()) {
                    list.sort(Comparator.comparing((Entity e) -> { return e.getY(); }).reversed()); // CraftBukkit - decompile error

                    for (EntityHuman entityhuman : list) {
                        if (EntityPhantom.this.canAttack(worldserver, entityhuman, PathfinderTargetCondition.DEFAULT)) {
                            EntityPhantom.this.setTarget(entityhuman, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit - reason
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            EntityLiving entityliving = EntityPhantom.this.getTarget();

            return entityliving != null ? EntityPhantom.this.canAttack(getServerLevel(EntityPhantom.this.level()), entityliving, PathfinderTargetCondition.DEFAULT) : false;
        }
    }
}
