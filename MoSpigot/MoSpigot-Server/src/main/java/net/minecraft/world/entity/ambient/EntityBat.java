package net.minecraft.world.entity.ambient;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EntityBat extends EntityAmbient {

    public static final float FLAP_LENGTH_SECONDS = 0.5F;
    public static final float TICKS_PER_FLAP = 10.0F;
    private static final DataWatcherObject<Byte> DATA_ID_FLAGS = DataWatcher.<Byte>defineId(EntityBat.class, DataWatcherRegistry.BYTE);
    private static final int FLAG_RESTING = 1;
    private static final PathfinderTargetCondition BAT_RESTING_TARGETING = PathfinderTargetCondition.forNonCombat().range(4.0D);
    private static final byte DEFAULT_FLAGS = 0;
    public final AnimationState flyAnimationState = new AnimationState();
    public final AnimationState restAnimationState = new AnimationState();
    @Nullable
    private BlockPosition targetPosition;

    public EntityBat(EntityTypes<? extends EntityBat> entitytypes, World world) {
        super(entitytypes, world);
        if (!world.isClientSide) {
            this.setResting(true);
        }

    }

    @Override
    public boolean isFlapping() {
        return !this.isResting() && (float) this.tickCount % 10.0F == 0.0F;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityBat.DATA_ID_FLAGS, (byte) 0);
    }

    @Override
    protected float getSoundVolume() {
        return 0.1F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.95F;
    }

    @Nullable
    @Override
    public SoundEffect getAmbientSound() {
        return this.isResting() && this.random.nextInt(4) != 0 ? null : SoundEffects.BAT_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.BAT_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.BAT_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    public static AttributeProvider.Builder createAttributes() {
        return EntityInsentient.createMobAttributes().add(GenericAttributes.MAX_HEALTH, 6.0D);
    }

    public boolean isResting() {
        return ((Byte) this.entityData.get(EntityBat.DATA_ID_FLAGS) & 1) != 0;
    }

    public void setResting(boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntityBat.DATA_ID_FLAGS);

        if (flag) {
            this.entityData.set(EntityBat.DATA_ID_FLAGS, (byte) (b0 | 1));
        } else {
            this.entityData.set(EntityBat.DATA_ID_FLAGS, (byte) (b0 & -2));
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isResting()) {
            this.setDeltaMovement(Vec3D.ZERO);
            this.setPosRaw(this.getX(), (double) MathHelper.floor(this.getY()) + 1.0D - (double) this.getBbHeight(), this.getZ());
        } else {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D));
        }

        this.setupAnimationStates();
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        super.customServerAiStep(worldserver);
        BlockPosition blockposition = this.blockPosition();
        BlockPosition blockposition1 = blockposition.above();

        if (this.isResting()) {
            boolean flag = this.isSilent();

            if (worldserver.getBlockState(blockposition1).isRedstoneConductor(worldserver, blockposition)) {
                if (this.random.nextInt(200) == 0) {
                    this.yHeadRot = (float) this.random.nextInt(360);
                }

                if (worldserver.getNearestPlayer(EntityBat.BAT_RESTING_TARGETING, this) != null && CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                    this.setResting(false);
                    if (!flag) {
                        worldserver.levelEvent((Entity) null, 1025, blockposition, 0);
                    }
                }
            } else if (CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
                if (!flag) {
                    worldserver.levelEvent((Entity) null, 1025, blockposition, 0);
                }
            }
        } else {
            if (this.targetPosition != null && (!worldserver.isEmptyBlock(this.targetPosition) || this.targetPosition.getY() <= worldserver.getMinY())) {
                this.targetPosition = null;
            }

            if (this.targetPosition == null || this.random.nextInt(30) == 0 || this.targetPosition.closerToCenterThan(this.position(), 2.0D)) {
                this.targetPosition = BlockPosition.containing(this.getX() + (double) this.random.nextInt(7) - (double) this.random.nextInt(7), this.getY() + (double) this.random.nextInt(6) - 2.0D, this.getZ() + (double) this.random.nextInt(7) - (double) this.random.nextInt(7));
            }

            double d0 = (double) this.targetPosition.getX() + 0.5D - this.getX();
            double d1 = (double) this.targetPosition.getY() + 0.1D - this.getY();
            double d2 = (double) this.targetPosition.getZ() + 0.5D - this.getZ();
            Vec3D vec3d = this.getDeltaMovement();
            Vec3D vec3d1 = vec3d.add((Math.signum(d0) * 0.5D - vec3d.x) * (double) 0.1F, (Math.signum(d1) * (double) 0.7F - vec3d.y) * (double) 0.1F, (Math.signum(d2) * 0.5D - vec3d.z) * (double) 0.1F);

            this.setDeltaMovement(vec3d1);
            float f = (float) (MathHelper.atan2(vec3d1.z, vec3d1.x) * (double) (180F / (float) Math.PI)) - 90.0F;
            float f1 = MathHelper.wrapDegrees(f - this.getYRot());

            this.zza = 0.5F;
            this.setYRot(this.getYRot() + f1);
            if (this.random.nextInt(100) == 0 && worldserver.getBlockState(blockposition1).isRedstoneConductor(worldserver, blockposition1) && CraftEventFactory.handleBatToggleSleepEvent(this, false)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(true);
            }
        }

    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void checkFallDamage(double d0, boolean flag, IBlockData iblockdata, BlockPosition blockposition) {}

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else {
            if (this.isResting() && CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
            }

            return super.hurtServer(worldserver, damagesource, f);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.entityData.set(EntityBat.DATA_ID_FLAGS, valueinput.getByteOr("BatFlags", (byte) 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putByte("BatFlags", (Byte) this.entityData.get(EntityBat.DATA_ID_FLAGS));
    }

    public static boolean checkBatSpawnRules(EntityTypes<EntityBat> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        if (blockposition.getY() >= generatoraccess.getHeightmapPos(HeightMap.Type.WORLD_SURFACE, blockposition).getY()) {
            return false;
        } else {
            int i = generatoraccess.getMaxLocalRawBrightness(blockposition);
            int j = 4;

            if (isHalloween()) {
                j = 7;
            } else if (randomsource.nextBoolean()) {
                return false;
            }

            return i > randomsource.nextInt(j) ? false : (!generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.BATS_SPAWNABLE_ON) ? false : checkMobSpawnRules(entitytypes, generatoraccess, entityspawnreason, blockposition, randomsource));
        }
    }

    private static boolean isHalloween() {
        LocalDate localdate = LocalDate.now();
        int i = localdate.get(ChronoField.DAY_OF_MONTH);
        int j = localdate.get(ChronoField.MONTH_OF_YEAR);

        return j == 10 && i >= 20 || j == 11 && i <= 3;
    }

    private void setupAnimationStates() {
        if (this.isResting()) {
            this.flyAnimationState.stop();
            this.restAnimationState.startIfStopped(this.tickCount);
        } else {
            this.restAnimationState.stop();
            this.flyAnimationState.startIfStopped(this.tickCount);
        }

    }
}
