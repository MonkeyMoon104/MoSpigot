package net.minecraft.world.entity.monster;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.Particles;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.EntityIronGolem;
import net.minecraft.world.entity.npc.EntityVillagerAbstract;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.raid.EntityRaider;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockLeaves;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EntityRavager extends EntityRaider {

    private static final Predicate<Entity> ROAR_TARGET_WITH_GRIEFING = (entity) -> {
        return !(entity instanceof EntityRavager) && entity.isAlive();
    };
    private static final Predicate<Entity> ROAR_TARGET_WITHOUT_GRIEFING = (entity) -> {
        return EntityRavager.ROAR_TARGET_WITH_GRIEFING.test(entity) && !entity.getType().equals(EntityTypes.ARMOR_STAND);
    };
    private static final Predicate<EntityLiving> ROAR_TARGET_ON_CLIENT = (entityliving) -> {
        return !(entityliving instanceof EntityRavager) && entityliving.isAlive() && entityliving.isLocalInstanceAuthoritative();
    };
    private static final double BASE_MOVEMENT_SPEED = 0.3D;
    private static final double ATTACK_MOVEMENT_SPEED = 0.35D;
    private static final int STUNNED_COLOR = 8356754;
    private static final float STUNNED_COLOR_BLUE = 0.57254905F;
    private static final float STUNNED_COLOR_GREEN = 0.5137255F;
    private static final float STUNNED_COLOR_RED = 0.49803922F;
    public static final int ATTACK_DURATION = 10;
    public static final int STUN_DURATION = 40;
    private static final int DEFAULT_ATTACK_TICK = 0;
    private static final int DEFAULT_STUN_TICK = 0;
    private static final int DEFAULT_ROAR_TICK = 0;
    private int attackTick = 0;
    private int stunnedTick = 0;
    private int roarTick = 0;

    public EntityRavager(EntityTypes<? extends EntityRavager> entitytypes, World world) {
        super(entitytypes, world);
        this.xpReward = 20;
        this.setPathfindingMalus(PathType.LEAVES, 0.0F);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(4, new PathfinderGoalMeleeAttack(this, 1.0D, true));
        this.goalSelector.addGoal(5, new PathfinderGoalRandomStrollLand(this, 0.4D));
        this.goalSelector.addGoal(6, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 6.0F));
        this.goalSelector.addGoal(10, new PathfinderGoalLookAtPlayer(this, EntityInsentient.class, 8.0F));
        this.targetSelector.addGoal(2, (new PathfinderGoalHurtByTarget(this, new Class[]{EntityRaider.class})).setAlertOthers());
        this.targetSelector.addGoal(3, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, true));
        this.targetSelector.addGoal(4, new PathfinderGoalNearestAttackableTarget(this, EntityVillagerAbstract.class, true, (entityliving, worldserver) -> {
            return !entityliving.isBaby();
        }));
        this.targetSelector.addGoal(4, new PathfinderGoalNearestAttackableTarget(this, EntityIronGolem.class, true));
    }

    @Override
    protected void updateControlFlags() {
        boolean flag = !(this.getControllingPassenger() instanceof EntityInsentient) || this.getControllingPassenger().getType().is(TagsEntity.RAIDERS);
        boolean flag1 = !(this.getVehicle() instanceof AbstractBoat);

        this.goalSelector.setControlFlag(PathfinderGoal.Type.MOVE, flag);
        this.goalSelector.setControlFlag(PathfinderGoal.Type.JUMP, flag && flag1);
        this.goalSelector.setControlFlag(PathfinderGoal.Type.LOOK, flag);
        this.goalSelector.setControlFlag(PathfinderGoal.Type.TARGET, flag);
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MAX_HEALTH, 100.0D).add(GenericAttributes.MOVEMENT_SPEED, 0.3D).add(GenericAttributes.KNOCKBACK_RESISTANCE, 0.75D).add(GenericAttributes.ATTACK_DAMAGE, 12.0D).add(GenericAttributes.ATTACK_KNOCKBACK, 1.5D).add(GenericAttributes.FOLLOW_RANGE, 32.0D).add(GenericAttributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("AttackTick", this.attackTick);
        valueoutput.putInt("StunTick", this.stunnedTick);
        valueoutput.putInt("RoarTick", this.roarTick);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.attackTick = valueinput.getIntOr("AttackTick", 0);
        this.stunnedTick = valueinput.getIntOr("StunTick", 0);
        this.roarTick = valueinput.getIntOr("RoarTick", 0);
    }

    @Override
    public SoundEffect getCelebrateSound() {
        return SoundEffects.RAVAGER_CELEBRATE;
    }

    @Override
    public int getMaxHeadYRot() {
        return 45;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive()) {
            if (this.isImmobile()) {
                this.getAttribute(GenericAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            } else {
                double d0 = this.getTarget() != null ? 0.35D : 0.3D;
                double d1 = this.getAttribute(GenericAttributes.MOVEMENT_SPEED).getBaseValue();

                this.getAttribute(GenericAttributes.MOVEMENT_SPEED).setBaseValue(MathHelper.lerp(0.1D, d1, d0));
            }

            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (this.horizontalCollision && worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    boolean flag = false;
                    AxisAlignedBB axisalignedbb = this.getBoundingBox().inflate(0.2D);

                    for (BlockPosition blockposition : BlockPosition.betweenClosed(MathHelper.floor(axisalignedbb.minX), MathHelper.floor(axisalignedbb.minY), MathHelper.floor(axisalignedbb.minZ), MathHelper.floor(axisalignedbb.maxX), MathHelper.floor(axisalignedbb.maxY), MathHelper.floor(axisalignedbb.maxZ))) {
                        IBlockData iblockdata = worldserver.getBlockState(blockposition);
                        Block block = iblockdata.getBlock();

                        if (block instanceof BlockLeaves) {
                            // CraftBukkit start
                            if (!CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) {
                                continue;
                            }
                            // CraftBukkit end
                            flag = worldserver.destroyBlock(blockposition, true, this) || flag;
                        }
                    }

                    if (!flag && this.onGround()) {
                        this.jumpFromGround();
                    }
                }
            }

            if (this.roarTick > 0) {
                --this.roarTick;
                if (this.roarTick == 10) {
                    this.roar();
                }
            }

            if (this.attackTick > 0) {
                --this.attackTick;
            }

            if (this.stunnedTick > 0) {
                --this.stunnedTick;
                this.stunEffect();
                if (this.stunnedTick == 0) {
                    this.playSound(SoundEffects.RAVAGER_ROAR, 1.0F, 1.0F);
                    this.roarTick = 20;
                }
            }

        }
    }

    private void stunEffect() {
        if (this.random.nextInt(6) == 0) {
            double d0 = this.getX() - (double) this.getBbWidth() * Math.sin((double) (this.yBodyRot * ((float) Math.PI / 180F))) + (this.random.nextDouble() * 0.6D - 0.3D);
            double d1 = this.getY() + (double) this.getBbHeight() - 0.3D;
            double d2 = this.getZ() + (double) this.getBbWidth() * Math.cos((double) (this.yBodyRot * ((float) Math.PI / 180F))) + (this.random.nextDouble() * 0.6D - 0.3D);

            this.level().addParticle(ColorParticleOption.create(Particles.ENTITY_EFFECT, 0.49803922F, 0.5137255F, 0.57254905F), d0, d1, d2, 0.0D, 0.0D, 0.0D);
        }

    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.attackTick > 0 || this.stunnedTick > 0 || this.roarTick > 0;
    }

    @Override
    public boolean hasLineOfSight(Entity entity) {
        return this.stunnedTick <= 0 && this.roarTick <= 0 ? super.hasLineOfSight(entity) : false;
    }

    @Override
    protected void blockedByItem(EntityLiving entityliving) {
        if (this.roarTick == 0) {
            if (this.random.nextDouble() < 0.5D) {
                this.stunnedTick = 40;
                this.playSound(SoundEffects.RAVAGER_STUNNED, 1.0F, 1.0F);
                this.level().broadcastEntityEvent(this, (byte) 39);
                entityliving.push((Entity) this);
            } else {
                this.strongKnockback(entityliving);
            }

            entityliving.hurtMarked = true;
        }

    }

    private void roar() {
        if (this.isAlive()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;
                Predicate<Entity> predicate = worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? EntityRavager.ROAR_TARGET_WITH_GRIEFING : EntityRavager.ROAR_TARGET_WITHOUT_GRIEFING;

                for (EntityLiving entityliving : this.level().getEntitiesOfClass(EntityLiving.class, this.getBoundingBox().inflate(4.0D), predicate)) {
                    if (!(entityliving instanceof EntityIllagerAbstract)) {
                        entityliving.hurtServer(worldserver, this.damageSources().mobAttack(this), 6.0F);
                    }

                    if (!(entityliving instanceof EntityHuman)) {
                        this.strongKnockback(entityliving);
                    }
                }

                this.gameEvent(GameEvent.ENTITY_ACTION);
                worldserver.broadcastEntityEvent(this, (byte) 69);
            }
        }

    }

    private void applyRoarKnockbackClient() {
        for (EntityLiving entityliving : this.level().getEntitiesOfClass(EntityLiving.class, this.getBoundingBox().inflate(4.0D), EntityRavager.ROAR_TARGET_ON_CLIENT)) {
            this.strongKnockback(entityliving);
        }

    }

    private void strongKnockback(Entity entity) {
        double d0 = entity.getX() - this.getX();
        double d1 = entity.getZ() - this.getZ();
        double d2 = Math.max(d0 * d0 + d1 * d1, 0.001D);

        entity.push(d0 / d2 * 4.0D, 0.2D, d1 / d2 * 4.0D);
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 4) {
            this.attackTick = 10;
            this.playSound(SoundEffects.RAVAGER_ATTACK, 1.0F, 1.0F);
        } else if (b0 == 39) {
            this.stunnedTick = 40;
        } else if (b0 == 69) {
            this.addRoarParticleEffects();
            this.applyRoarKnockbackClient();
        }

        super.handleEntityEvent(b0);
    }

    private void addRoarParticleEffects() {
        Vec3D vec3d = this.getBoundingBox().getCenter();

        for (int i = 0; i < 40; ++i) {
            double d0 = this.random.nextGaussian() * 0.2D;
            double d1 = this.random.nextGaussian() * 0.2D;
            double d2 = this.random.nextGaussian() * 0.2D;

            this.level().addParticle(Particles.POOF, vec3d.x, vec3d.y, vec3d.z, d0, d1, d2);
        }

    }

    public int getAttackTick() {
        return this.attackTick;
    }

    public int getStunnedTick() {
        return this.stunnedTick;
    }

    public int getRoarTick() {
        return this.roarTick;
    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        this.attackTick = 10;
        worldserver.broadcastEntityEvent(this, (byte) 4);
        this.playSound(SoundEffects.RAVAGER_ATTACK, 1.0F, 1.0F);
        return super.doHurtTarget(worldserver, entity);
    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.RAVAGER_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.RAVAGER_STEP, 0.15F, 1.0F);
    }

    @Override
    public boolean checkSpawnObstruction(IWorldReader iworldreader) {
        return !iworldreader.containsAnyLiquid(this.getBoundingBox());
    }

    @Override
    public void applyRaidBuffs(WorldServer worldserver, int i, boolean flag) {}

    @Override
    public boolean canBeLeader() {
        return false;
    }

    @Override
    protected AxisAlignedBB getAttackBoundingBox() {
        AxisAlignedBB axisalignedbb = super.getAttackBoundingBox();

        return axisalignedbb.deflate(0.05D, 0.0D, 0.05D);
    }
}
