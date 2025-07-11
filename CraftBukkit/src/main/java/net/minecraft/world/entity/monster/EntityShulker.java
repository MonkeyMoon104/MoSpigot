package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.MathHelper;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerLook;
import net.minecraft.world.entity.ai.control.EntityAIBodyControl;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.animal.EntityGolem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.projectile.EntityShulkerBullet;
import net.minecraft.world.item.EnumColor;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import org.joml.Vector3f;

// CraftBukkit start
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EntityShulker extends EntityGolem implements IMonster {

    private static final MinecraftKey COVERED_ARMOR_MODIFIER_ID = MinecraftKey.withDefaultNamespace("covered");
    private static final AttributeModifier COVERED_ARMOR_MODIFIER = new AttributeModifier(EntityShulker.COVERED_ARMOR_MODIFIER_ID, 20.0D, AttributeModifier.Operation.ADD_VALUE);
    protected static final DataWatcherObject<EnumDirection> DATA_ATTACH_FACE_ID = DataWatcher.<EnumDirection>defineId(EntityShulker.class, DataWatcherRegistry.DIRECTION);
    protected static final DataWatcherObject<Byte> DATA_PEEK_ID = DataWatcher.<Byte>defineId(EntityShulker.class, DataWatcherRegistry.BYTE);
    public static final DataWatcherObject<Byte> DATA_COLOR_ID = DataWatcher.<Byte>defineId(EntityShulker.class, DataWatcherRegistry.BYTE);
    private static final int TELEPORT_STEPS = 6;
    private static final byte NO_COLOR = 16;
    private static final byte DEFAULT_COLOR = 16;
    private static final int MAX_TELEPORT_DISTANCE = 8;
    private static final int OTHER_SHULKER_SCAN_RADIUS = 8;
    private static final int OTHER_SHULKER_LIMIT = 5;
    private static final float PEEK_PER_TICK = 0.05F;
    private static final byte DEFAULT_PEEK = 0;
    private static final EnumDirection DEFAULT_ATTACH_FACE = EnumDirection.DOWN;
    static final Vector3f FORWARD = (Vector3f) SystemUtils.make(() -> {
        BaseBlockPosition baseblockposition = EnumDirection.SOUTH.getUnitVec3i();

        return new Vector3f((float) baseblockposition.getX(), (float) baseblockposition.getY(), (float) baseblockposition.getZ());
    });
    private static final float MAX_SCALE = 3.0F;
    private float currentPeekAmountO;
    private float currentPeekAmount;
    @Nullable
    private BlockPosition clientOldAttachPosition;
    private int clientSideTeleportInterpolation;
    private static final float MAX_LID_OPEN = 1.0F;

    public EntityShulker(EntityTypes<? extends EntityShulker> entitytypes, World world) {
        super(entitytypes, world);
        this.xpReward = 5;
        this.lookControl = new EntityShulker.d(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F, 0.02F, true));
        this.goalSelector.addGoal(4, new EntityShulker.a());
        this.goalSelector.addGoal(7, new EntityShulker.f());
        this.goalSelector.addGoal(8, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, (new PathfinderGoalHurtByTarget(this, new Class[]{this.getClass()})).setAlertOthers());
        this.targetSelector.addGoal(2, new EntityShulker.e(this));
        this.targetSelector.addGoal(3, new EntityShulker.c(this));
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.HOSTILE;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.SHULKER_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        if (!this.isClosed()) {
            super.playAmbientSound();
        }

    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.SHULKER_DEATH;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return this.isClosed() ? SoundEffects.SHULKER_HURT_CLOSED : SoundEffects.SHULKER_HURT;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityShulker.DATA_ATTACH_FACE_ID, EntityShulker.DEFAULT_ATTACH_FACE);
        datawatcher_a.define(EntityShulker.DATA_PEEK_ID, (byte) 0);
        datawatcher_a.define(EntityShulker.DATA_COLOR_ID, (byte) 16);
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityInsentient.createMobAttributes().add(GenericAttributes.MAX_HEALTH, 30.0D);
    }

    @Override
    protected EntityAIBodyControl createBodyControl() {
        return new EntityShulker.b(this);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setAttachFace((EnumDirection) valueinput.read("AttachFace", EnumDirection.LEGACY_ID_CODEC).orElse(EntityShulker.DEFAULT_ATTACH_FACE));
        this.entityData.set(EntityShulker.DATA_PEEK_ID, valueinput.getByteOr("Peek", (byte) 0));
        this.entityData.set(EntityShulker.DATA_COLOR_ID, valueinput.getByteOr("Color", (byte) 16));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("AttachFace", EnumDirection.LEGACY_ID_CODEC, this.getAttachFace());
        valueoutput.putByte("Peek", (Byte) this.entityData.get(EntityShulker.DATA_PEEK_ID));
        valueoutput.putByte("Color", (Byte) this.entityData.get(EntityShulker.DATA_COLOR_ID));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !this.isPassenger() && !this.canStayAt(this.blockPosition(), this.getAttachFace())) {
            this.findNewAttachment();
        }

        if (this.updatePeekAmount()) {
            this.onPeekAmountChange();
        }

        if (this.level().isClientSide) {
            if (this.clientSideTeleportInterpolation > 0) {
                --this.clientSideTeleportInterpolation;
            } else {
                this.clientOldAttachPosition = null;
            }
        }

    }

    private void findNewAttachment() {
        EnumDirection enumdirection = this.findAttachableSurface(this.blockPosition());

        if (enumdirection != null) {
            this.setAttachFace(enumdirection);
        } else {
            this.teleportSomewhere();
        }

    }

    @Override
    protected AxisAlignedBB makeBoundingBox(Vec3D vec3d) {
        float f = getPhysicalPeek(this.currentPeekAmount);
        EnumDirection enumdirection = this.getAttachFace().getOpposite();

        return getProgressAabb(this.getScale(), enumdirection, f, vec3d);
    }

    private static float getPhysicalPeek(float f) {
        return 0.5F - MathHelper.sin((0.5F + f) * (float) Math.PI) * 0.5F;
    }

    private boolean updatePeekAmount() {
        this.currentPeekAmountO = this.currentPeekAmount;
        float f = (float) this.getRawPeekAmount() * 0.01F;

        if (this.currentPeekAmount == f) {
            return false;
        } else {
            if (this.currentPeekAmount > f) {
                this.currentPeekAmount = MathHelper.clamp(this.currentPeekAmount - 0.05F, f, 1.0F);
            } else {
                this.currentPeekAmount = MathHelper.clamp(this.currentPeekAmount + 0.05F, 0.0F, f);
            }

            return true;
        }
    }

    private void onPeekAmountChange() {
        this.reapplyPosition();
        float f = getPhysicalPeek(this.currentPeekAmount);
        float f1 = getPhysicalPeek(this.currentPeekAmountO);
        EnumDirection enumdirection = this.getAttachFace().getOpposite();
        float f2 = (f - f1) * this.getScale();

        if (f2 > 0.0F) {
            for (Entity entity : this.level().getEntities(this, getProgressDeltaAabb(this.getScale(), enumdirection, f1, f, this.position()), IEntitySelector.NO_SPECTATORS.and((entity1) -> {
                return !entity1.isPassengerOfSameVehicle(this);
            }))) {
                if (!(entity instanceof EntityShulker) && !entity.noPhysics) {
                    entity.move(EnumMoveType.SHULKER, new Vec3D((double) (f2 * (float) enumdirection.getStepX()), (double) (f2 * (float) enumdirection.getStepY()), (double) (f2 * (float) enumdirection.getStepZ())));
                }
            }

        }
    }

    public static AxisAlignedBB getProgressAabb(float f, EnumDirection enumdirection, float f1, Vec3D vec3d) {
        return getProgressDeltaAabb(f, enumdirection, -1.0F, f1, vec3d);
    }

    public static AxisAlignedBB getProgressDeltaAabb(float f, EnumDirection enumdirection, float f1, float f2, Vec3D vec3d) {
        AxisAlignedBB axisalignedbb = new AxisAlignedBB((double) (-f) * 0.5D, 0.0D, (double) (-f) * 0.5D, (double) f * 0.5D, (double) f, (double) f * 0.5D);
        double d0 = (double) Math.max(f1, f2);
        double d1 = (double) Math.min(f1, f2);
        AxisAlignedBB axisalignedbb1 = axisalignedbb.expandTowards((double) enumdirection.getStepX() * d0 * (double) f, (double) enumdirection.getStepY() * d0 * (double) f, (double) enumdirection.getStepZ() * d0 * (double) f).contract((double) (-enumdirection.getStepX()) * (1.0D + d1) * (double) f, (double) (-enumdirection.getStepY()) * (1.0D + d1) * (double) f, (double) (-enumdirection.getStepZ()) * (1.0D + d1) * (double) f);

        return axisalignedbb1.move(vec3d.x, vec3d.y, vec3d.z);
    }

    @Override
    public boolean startRiding(Entity entity, boolean flag) {
        if (this.level().isClientSide()) {
            this.clientOldAttachPosition = null;
            this.clientSideTeleportInterpolation = 0;
        }

        this.setAttachFace(EnumDirection.DOWN);
        return super.startRiding(entity, flag);
    }

    @Override
    public void stopRiding() {
        super.stopRiding();
        if (this.level().isClientSide) {
            this.clientOldAttachPosition = this.blockPosition();
        }

        this.yBodyRotO = 0.0F;
        this.yBodyRot = 0.0F;
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        this.setYRot(0.0F);
        this.yHeadRot = this.getYRot();
        this.setOldPosAndRot();
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    public void move(EnumMoveType enummovetype, Vec3D vec3d) {
        if (enummovetype == EnumMoveType.SHULKER_BOX) {
            this.teleportSomewhere();
        } else {
            super.move(enummovetype, vec3d);
        }

    }

    @Override
    public Vec3D getDeltaMovement() {
        return Vec3D.ZERO;
    }

    @Override
    public void setDeltaMovement(Vec3D vec3d) {}

    @Override
    public void setPos(double d0, double d1, double d2) {
        BlockPosition blockposition = this.blockPosition();

        if (this.isPassenger()) {
            super.setPos(d0, d1, d2);
        } else {
            super.setPos((double) MathHelper.floor(d0) + 0.5D, (double) MathHelper.floor(d1 + 0.5D), (double) MathHelper.floor(d2) + 0.5D);
        }

        if (this.tickCount != 0) {
            BlockPosition blockposition1 = this.blockPosition();

            if (!blockposition1.equals(blockposition)) {
                this.entityData.set(EntityShulker.DATA_PEEK_ID, (byte) 0);
                this.hasImpulse = true;
                if (this.level().isClientSide && !this.isPassenger() && !blockposition1.equals(this.clientOldAttachPosition)) {
                    this.clientOldAttachPosition = blockposition;
                    this.clientSideTeleportInterpolation = 6;
                    this.xOld = this.getX();
                    this.yOld = this.getY();
                    this.zOld = this.getZ();
                }
            }

        }
    }

    @Nullable
    protected EnumDirection findAttachableSurface(BlockPosition blockposition) {
        for (EnumDirection enumdirection : EnumDirection.values()) {
            if (this.canStayAt(blockposition, enumdirection)) {
                return enumdirection;
            }
        }

        return null;
    }

    boolean canStayAt(BlockPosition blockposition, EnumDirection enumdirection) {
        if (this.isPositionBlocked(blockposition)) {
            return false;
        } else {
            EnumDirection enumdirection1 = enumdirection.getOpposite();

            if (!this.level().loadedAndEntityCanStandOnFace(blockposition.relative(enumdirection), this, enumdirection1)) {
                return false;
            } else {
                AxisAlignedBB axisalignedbb = getProgressAabb(this.getScale(), enumdirection1, 1.0F, blockposition.getBottomCenter()).deflate(1.0E-6D);

                return this.level().noCollision(this, axisalignedbb);
            }
        }
    }

    private boolean isPositionBlocked(BlockPosition blockposition) {
        IBlockData iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.isAir()) {
            return false;
        } else {
            boolean flag = iblockdata.is(Blocks.MOVING_PISTON) && blockposition.equals(this.blockPosition());

            return !flag;
        }
    }

    protected boolean teleportSomewhere() {
        if (!this.isNoAi() && this.isAlive()) {
            BlockPosition blockposition = this.blockPosition();

            for (int i = 0; i < 5; ++i) {
                BlockPosition blockposition1 = blockposition.offset(MathHelper.randomBetweenInclusive(this.random, -8, 8), MathHelper.randomBetweenInclusive(this.random, -8, 8), MathHelper.randomBetweenInclusive(this.random, -8, 8));

                if (blockposition1.getY() > this.level().getMinY() && this.level().isEmptyBlock(blockposition1) && this.level().getWorldBorder().isWithinBounds(blockposition1) && this.level().noCollision(this, (new AxisAlignedBB(blockposition1)).deflate(1.0E-6D))) {
                    EnumDirection enumdirection = this.findAttachableSurface(blockposition1);

                    if (enumdirection != null) {
                        // CraftBukkit start
                        EntityTeleportEvent teleportEvent = CraftEventFactory.callEntityTeleportEvent(this, blockposition1.getX(), blockposition1.getY(), blockposition1.getZ());
                        if (teleportEvent.isCancelled()) {
                            return false;
                        } else {
                            blockposition1 = CraftLocation.toBlockPosition(teleportEvent.getTo());
                        }
                        // CraftBukkit end
                        this.unRide();
                        this.setAttachFace(enumdirection);
                        this.playSound(SoundEffects.SHULKER_TELEPORT, 1.0F, 1.0F);
                        this.setPos((double) blockposition1.getX() + 0.5D, (double) blockposition1.getY(), (double) blockposition1.getZ() + 0.5D);
                        this.level().gameEvent(GameEvent.TELEPORT, blockposition, GameEvent.a.of((Entity) this));
                        this.entityData.set(EntityShulker.DATA_PEEK_ID, (byte) 0);
                        this.setTarget((EntityLiving) null);
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return null;
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isClosed()) {
            Entity entity = damagesource.getDirectEntity();

            if (entity instanceof EntityArrow) {
                return false;
            }
        }

        if (!super.hurtServer(worldserver, damagesource, f)) {
            return false;
        } else {
            if ((double) this.getHealth() < (double) this.getMaxHealth() * 0.5D && this.random.nextInt(4) == 0) {
                this.teleportSomewhere();
            } else if (damagesource.is(DamageTypeTags.IS_PROJECTILE)) {
                Entity entity1 = damagesource.getDirectEntity();

                if (entity1 != null && entity1.getType() == EntityTypes.SHULKER_BULLET) {
                    this.hitByShulkerBullet();
                }
            }

            return true;
        }
    }

    private boolean isClosed() {
        return this.getRawPeekAmount() == 0;
    }

    private void hitByShulkerBullet() {
        Vec3D vec3d = this.position();
        AxisAlignedBB axisalignedbb = this.getBoundingBox();

        if (!this.isClosed() && this.teleportSomewhere()) {
            int i = this.level().getEntities((EntityTypeTest) EntityTypes.SHULKER, axisalignedbb.inflate(8.0D), Entity::isAlive).size();
            float f = (float) (i - 1) / 5.0F;

            if (this.level().random.nextFloat() >= f) {
                EntityShulker entityshulker = EntityTypes.SHULKER.create(this.level(), EntitySpawnReason.BREEDING);

                if (entityshulker != null) {
                    entityshulker.setVariant(this.getVariant());
                    entityshulker.snapTo(vec3d);
                    this.level().addFreshEntity(entityshulker, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - the mysteries of life
                }

            }
        }
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity) {
        return this.isAlive();
    }

    public EnumDirection getAttachFace() {
        return (EnumDirection) this.entityData.get(EntityShulker.DATA_ATTACH_FACE_ID);
    }

    public void setAttachFace(EnumDirection enumdirection) {
        this.entityData.set(EntityShulker.DATA_ATTACH_FACE_ID, enumdirection);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityShulker.DATA_ATTACH_FACE_ID.equals(datawatcherobject)) {
            this.setBoundingBox(this.makeBoundingBox());
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    public int getRawPeekAmount() {
        return (Byte) this.entityData.get(EntityShulker.DATA_PEEK_ID);
    }

    public void setRawPeekAmount(int i) {
        if (!this.level().isClientSide) {
            this.getAttribute(GenericAttributes.ARMOR).removeModifier(EntityShulker.COVERED_ARMOR_MODIFIER_ID);
            if (i == 0) {
                this.getAttribute(GenericAttributes.ARMOR).addPermanentModifier(EntityShulker.COVERED_ARMOR_MODIFIER);
                this.playSound(SoundEffects.SHULKER_CLOSE, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_CLOSE);
            } else {
                this.playSound(SoundEffects.SHULKER_OPEN, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_OPEN);
            }
        }

        this.entityData.set(EntityShulker.DATA_PEEK_ID, (byte) i);
    }

    public float getClientPeekAmount(float f) {
        return MathHelper.lerp(f, this.currentPeekAmountO, this.currentPeekAmount);
    }

    @Override
    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        super.recreateFromPacket(packetplayoutspawnentity);
        this.yBodyRot = 0.0F;
        this.yBodyRotO = 0.0F;
    }

    @Override
    public int getMaxHeadXRot() {
        return 180;
    }

    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    public void push(Entity entity) {}

    @Nullable
    public Vec3D getRenderPosition(float f) {
        if (this.clientOldAttachPosition != null && this.clientSideTeleportInterpolation > 0) {
            double d0 = (double) ((float) this.clientSideTeleportInterpolation - f) / 6.0D;

            d0 *= d0;
            d0 *= (double) this.getScale();
            BlockPosition blockposition = this.blockPosition();
            double d1 = (double) (blockposition.getX() - this.clientOldAttachPosition.getX()) * d0;
            double d2 = (double) (blockposition.getY() - this.clientOldAttachPosition.getY()) * d0;
            double d3 = (double) (blockposition.getZ() - this.clientOldAttachPosition.getZ()) * d0;

            return new Vec3D(-d1, -d2, -d3);
        } else {
            return null;
        }
    }

    @Override
    protected float sanitizeScale(float f) {
        return Math.min(f, 3.0F);
    }

    private void setVariant(Optional<EnumColor> optional) {
        this.entityData.set(EntityShulker.DATA_COLOR_ID, (Byte) optional.map((enumcolor) -> {
            return (byte) enumcolor.getId();
        }).orElse((byte) 16));
    }

    public Optional<EnumColor> getVariant() {
        return Optional.ofNullable(this.getColor());
    }

    @Nullable
    public EnumColor getColor() {
        byte b0 = (Byte) this.entityData.get(EntityShulker.DATA_COLOR_ID);

        return b0 != 16 && b0 <= 15 ? EnumColor.byId(b0) : null;
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.SHULKER_COLOR ? castComponentValue(datacomponenttype, this.getColor()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.SHULKER_COLOR);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.SHULKER_COLOR) {
            this.setVariant(Optional.of((EnumColor) castComponentValue(DataComponents.SHULKER_COLOR, t0)));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    private class d extends ControllerLook {

        public d(final EntityInsentient entityinsentient) {
            super(entityinsentient);
        }

        @Override
        protected void clampHeadRotationToBody() {}

        @Override
        protected Optional<Float> getYRotD() {
            EnumDirection enumdirection = EntityShulker.this.getAttachFace().getOpposite();
            Vector3f vector3f = enumdirection.getRotation().transform(new Vector3f(EntityShulker.FORWARD));
            BaseBlockPosition baseblockposition = enumdirection.getUnitVec3i();
            Vector3f vector3f1 = new Vector3f((float) baseblockposition.getX(), (float) baseblockposition.getY(), (float) baseblockposition.getZ());

            vector3f1.cross(vector3f);
            double d0 = this.wantedX - this.mob.getX();
            double d1 = this.wantedY - this.mob.getEyeY();
            double d2 = this.wantedZ - this.mob.getZ();
            Vector3f vector3f2 = new Vector3f((float) d0, (float) d1, (float) d2);
            float f = vector3f1.dot(vector3f2);
            float f1 = vector3f.dot(vector3f2);

            return Math.abs(f) <= 1.0E-5F && Math.abs(f1) <= 1.0E-5F ? Optional.empty() : Optional.of((float) (MathHelper.atan2((double) (-f), (double) f1) * (double) (180F / (float) Math.PI)));
        }

        @Override
        protected Optional<Float> getXRotD() {
            return Optional.of(0.0F);
        }
    }

    private static class b extends EntityAIBodyControl {

        public b(EntityInsentient entityinsentient) {
            super(entityinsentient);
        }

        @Override
        public void clientTick() {}
    }

    private class f extends PathfinderGoal {

        private int peekTime;

        f() {}

        @Override
        public boolean canUse() {
            return EntityShulker.this.getTarget() == null && EntityShulker.this.random.nextInt(reducedTickDelay(40)) == 0 && EntityShulker.this.canStayAt(EntityShulker.this.blockPosition(), EntityShulker.this.getAttachFace());
        }

        @Override
        public boolean canContinueToUse() {
            return EntityShulker.this.getTarget() == null && this.peekTime > 0;
        }

        @Override
        public void start() {
            this.peekTime = this.adjustedTickDelay(20 * (1 + EntityShulker.this.random.nextInt(3)));
            EntityShulker.this.setRawPeekAmount(30);
        }

        @Override
        public void stop() {
            if (EntityShulker.this.getTarget() == null) {
                EntityShulker.this.setRawPeekAmount(0);
            }

        }

        @Override
        public void tick() {
            --this.peekTime;
        }
    }

    private class a extends PathfinderGoal {

        private int attackTime;

        public a() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            EntityLiving entityliving = EntityShulker.this.getTarget();

            return entityliving != null && entityliving.isAlive() ? EntityShulker.this.level().getDifficulty() != EnumDifficulty.PEACEFUL : false;
        }

        @Override
        public void start() {
            this.attackTime = 20;
            EntityShulker.this.setRawPeekAmount(100);
        }

        @Override
        public void stop() {
            EntityShulker.this.setRawPeekAmount(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (EntityShulker.this.level().getDifficulty() != EnumDifficulty.PEACEFUL) {
                --this.attackTime;
                EntityLiving entityliving = EntityShulker.this.getTarget();

                if (entityliving != null) {
                    EntityShulker.this.getLookControl().setLookAt(entityliving, 180.0F, 180.0F);
                    double d0 = EntityShulker.this.distanceToSqr((Entity) entityliving);

                    if (d0 < 400.0D) {
                        if (this.attackTime <= 0) {
                            this.attackTime = 20 + EntityShulker.this.random.nextInt(10) * 20 / 2;
                            EntityShulker.this.level().addFreshEntity(new EntityShulkerBullet(EntityShulker.this.level(), EntityShulker.this, entityliving, EntityShulker.this.getAttachFace().getAxis()));
                            EntityShulker.this.playSound(SoundEffects.SHULKER_SHOOT, 2.0F, (EntityShulker.this.random.nextFloat() - EntityShulker.this.random.nextFloat()) * 0.2F + 1.0F);
                        }
                    } else {
                        EntityShulker.this.setTarget((EntityLiving) null);
                    }

                    super.tick();
                }
            }
        }
    }

    private class e extends PathfinderGoalNearestAttackableTarget<EntityHuman> {

        public e(final EntityShulker entityshulker) {
            super(entityshulker, EntityHuman.class, true);
        }

        @Override
        public boolean canUse() {
            return EntityShulker.this.level().getDifficulty() == EnumDifficulty.PEACEFUL ? false : super.canUse();
        }

        @Override
        protected AxisAlignedBB getTargetSearchArea(double d0) {
            EnumDirection enumdirection = ((EntityShulker) this.mob).getAttachFace();

            return enumdirection.getAxis() == EnumDirection.EnumAxis.X ? this.mob.getBoundingBox().inflate(4.0D, d0, d0) : (enumdirection.getAxis() == EnumDirection.EnumAxis.Z ? this.mob.getBoundingBox().inflate(d0, d0, 4.0D) : this.mob.getBoundingBox().inflate(d0, 4.0D, d0));
        }
    }

    private static class c extends PathfinderGoalNearestAttackableTarget<EntityLiving> {

        public c(EntityShulker entityshulker) {
            super(entityshulker, EntityLiving.class, 10, true, false, (entityliving, worldserver) -> {
                return entityliving instanceof IMonster;
            });
        }

        @Override
        public boolean canUse() {
            return this.mob.getTeam() == null ? false : super.canUse();
        }

        @Override
        protected AxisAlignedBB getTargetSearchArea(double d0) {
            EnumDirection enumdirection = ((EntityShulker) this.mob).getAttachFace();

            return enumdirection.getAxis() == EnumDirection.EnumAxis.X ? this.mob.getBoundingBox().inflate(4.0D, d0, d0) : (enumdirection.getAxis() == EnumDirection.EnumAxis.Z ? this.mob.getBoundingBox().inflate(d0, d0, 4.0D) : this.mob.getBoundingBox().inflate(d0, 4.0D, d0));
        }
    }
}
