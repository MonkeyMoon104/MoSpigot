package net.minecraft.world.entity.monster.warden;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Dynamic;
import java.util.Collections;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleParamBlock;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.protocol.game.PacketListenerPlayOut;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityTrackerEntry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.BehaviorController;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.Navigation;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.EnumRenderType;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.pathfinder.PathPoint;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Pathfinder;
import net.minecraft.world.level.pathfinder.PathfinderNormal;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;
import org.jetbrains.annotations.Contract;

public class Warden extends EntityMonster implements VibrationSystem {

    private static final int VIBRATION_COOLDOWN_TICKS = 40;
    private static final int TIME_TO_USE_MELEE_UNTIL_SONIC_BOOM = 200;
    private static final int MAX_HEALTH = 500;
    private static final float MOVEMENT_SPEED_WHEN_FIGHTING = 0.3F;
    private static final float KNOCKBACK_RESISTANCE = 1.0F;
    private static final float ATTACK_KNOCKBACK = 1.5F;
    private static final int ATTACK_DAMAGE = 30;
    private static final int FOLLOW_RANGE = 24;
    private static final DataWatcherObject<Integer> CLIENT_ANGER_LEVEL = DataWatcher.<Integer>defineId(Warden.class, DataWatcherRegistry.INT);
    private static final int DARKNESS_DISPLAY_LIMIT = 200;
    private static final int DARKNESS_DURATION = 260;
    private static final int DARKNESS_RADIUS = 20;
    private static final int DARKNESS_INTERVAL = 120;
    private static final int ANGERMANAGEMENT_TICK_DELAY = 20;
    private static final int DEFAULT_ANGER = 35;
    private static final int PROJECTILE_ANGER = 10;
    private static final int ON_HURT_ANGER_BOOST = 20;
    private static final int RECENT_PROJECTILE_TICK_THRESHOLD = 100;
    private static final int TOUCH_COOLDOWN_TICKS = 20;
    private static final int DIGGING_PARTICLES_AMOUNT = 30;
    private static final float DIGGING_PARTICLES_DURATION = 4.5F;
    private static final float DIGGING_PARTICLES_OFFSET = 0.7F;
    private static final int PROJECTILE_ANGER_DISTANCE = 30;
    private int tendrilAnimation;
    private int tendrilAnimationO;
    private int heartAnimation;
    private int heartAnimationO;
    public AnimationState roarAnimationState = new AnimationState();
    public AnimationState sniffAnimationState = new AnimationState();
    public AnimationState emergeAnimationState = new AnimationState();
    public AnimationState diggingAnimationState = new AnimationState();
    public AnimationState attackAnimationState = new AnimationState();
    public AnimationState sonicBoomAnimationState = new AnimationState();
    private final DynamicGameEventListener<VibrationSystem.b> dynamicGameEventListener = new DynamicGameEventListener<VibrationSystem.b>(new VibrationSystem.b(this));
    private final VibrationSystem.d vibrationUser = new Warden.a();
    private VibrationSystem.a vibrationData = new VibrationSystem.a();
    AngerManagement angerManagement = new AngerManagement(this::canTargetEntity, Collections.emptyList());

    public Warden(EntityTypes<? extends EntityMonster> entitytypes, World world) {
        super(entitytypes, world);
        this.xpReward = 5;
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(PathType.UNPASSABLE_RAIL, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, 8.0F);
        this.setPathfindingMalus(PathType.POWDER_SNOW, 8.0F);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
    }

    @Override
    public Packet<PacketListenerPlayOut> getAddEntityPacket(EntityTrackerEntry entitytrackerentry) {
        return new PacketPlayOutSpawnEntity(this, entitytrackerentry, this.hasPose(EntityPose.EMERGING) ? 1 : 0);
    }

    @Override
    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        super.recreateFromPacket(packetplayoutspawnentity);
        if (packetplayoutspawnentity.getData() == 1) {
            this.setPose(EntityPose.EMERGING);
        }

    }

    @Override
    public boolean checkSpawnObstruction(IWorldReader iworldreader) {
        return super.checkSpawnObstruction(iworldreader) && iworldreader.noCollision(this, this.getType().getDimensions().makeBoundingBox(this.position()));
    }

    @Override
    public float getWalkTargetValue(BlockPosition blockposition, IWorldReader iworldreader) {
        return 0.0F;
    }

    @Override
    public boolean isInvulnerableTo(WorldServer worldserver, DamageSource damagesource) {
        return this.isDiggingOrEmerging() && !damagesource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? true : super.isInvulnerableTo(worldserver, damagesource);
    }

    boolean isDiggingOrEmerging() {
        return this.hasPose(EntityPose.DIGGING) || this.hasPose(EntityPose.EMERGING);
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public float getSecondsToDisableBlocking() {
        return 5.0F;
    }

    @Override
    protected float nextStep() {
        return this.moveDist + 0.55F;
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MAX_HEALTH, 500.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.KNOCKBACK_RESISTANCE, 1.0D).add(GenericAttributes.ATTACK_KNOCKBACK, 1.5D).add(GenericAttributes.ATTACK_DAMAGE, 30.0D).add(GenericAttributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return !this.hasPose(EntityPose.ROARING) && !this.isDiggingOrEmerging() ? this.getAngerLevel().getAmbientSound() : null;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.WARDEN_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.WARDEN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.WARDEN_STEP, 10.0F, 1.0F);
    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        worldserver.broadcastEntityEvent(this, (byte) 4);
        this.playSound(SoundEffects.WARDEN_ATTACK_IMPACT, 10.0F, this.getVoicePitch());
        SonicBoom.setCooldown(this, 40);
        return super.doHurtTarget(worldserver, entity);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(Warden.CLIENT_ANGER_LEVEL, 0);
    }

    public int getClientAngerLevel() {
        return (Integer) this.entityData.get(Warden.CLIENT_ANGER_LEVEL);
    }

    private void syncClientAngerLevel() {
        this.entityData.set(Warden.CLIENT_ANGER_LEVEL, this.getActiveAnger());
    }

    @Override
    public void tick() {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            VibrationSystem.c.tick(worldserver, this.vibrationData, this.vibrationUser);
            if (this.isPersistenceRequired() || this.requiresCustomPersistence()) {
                WardenAi.setDigCooldown(this);
            }
        }

        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount % this.getHeartBeatDelay() == 0) {
                this.heartAnimation = 10;
                if (!this.isSilent()) {
                    this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.WARDEN_HEARTBEAT, this.getSoundSource(), 5.0F, this.getVoicePitch(), false);
                }
            }

            this.tendrilAnimationO = this.tendrilAnimation;
            if (this.tendrilAnimation > 0) {
                --this.tendrilAnimation;
            }

            this.heartAnimationO = this.heartAnimation;
            if (this.heartAnimation > 0) {
                --this.heartAnimation;
            }

            switch (this.getPose()) {
                case EMERGING:
                    this.clientDiggingParticles(this.emergeAnimationState);
                    break;
                case DIGGING:
                    this.clientDiggingParticles(this.diggingAnimationState);
            }
        }

    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("wardenBrain");
        this.getBrain().tick(worldserver, this);
        gameprofilerfiller.pop();
        super.customServerAiStep(worldserver);
        if ((this.tickCount + this.getId()) % 120 == 0) {
            applyDarknessAround(worldserver, this.position(), this, 20);
        }

        if (this.tickCount % 20 == 0) {
            this.angerManagement.tick(worldserver, this::canTargetEntity);
            this.syncClientAngerLevel();
        }

        WardenAi.updateActivity(this);
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 4) {
            this.roarAnimationState.stop();
            this.attackAnimationState.start(this.tickCount);
        } else if (b0 == 61) {
            this.tendrilAnimation = 10;
        } else if (b0 == 62) {
            this.sonicBoomAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(b0);
        }

    }

    private int getHeartBeatDelay() {
        float f = (float) this.getClientAngerLevel() / (float) AngerLevel.ANGRY.getMinimumAnger();

        return 40 - MathHelper.floor(MathHelper.clamp(f, 0.0F, 1.0F) * 30.0F);
    }

    public float getTendrilAnimation(float f) {
        return MathHelper.lerp(f, (float) this.tendrilAnimationO, (float) this.tendrilAnimation) / 10.0F;
    }

    public float getHeartAnimation(float f) {
        return MathHelper.lerp(f, (float) this.heartAnimationO, (float) this.heartAnimation) / 10.0F;
    }

    private void clientDiggingParticles(AnimationState animationstate) {
        if ((float) animationstate.getTimeInMillis((float) this.tickCount) < 4500.0F) {
            RandomSource randomsource = this.getRandom();
            IBlockData iblockdata = this.getBlockStateOn();

            if (iblockdata.getRenderShape() != EnumRenderType.INVISIBLE) {
                for (int i = 0; i < 30; ++i) {
                    double d0 = this.getX() + (double) MathHelper.randomBetween(randomsource, -0.7F, 0.7F);
                    double d1 = this.getY();
                    double d2 = this.getZ() + (double) MathHelper.randomBetween(randomsource, -0.7F, 0.7F);

                    this.level().addParticle(new ParticleParamBlock(Particles.BLOCK, iblockdata), d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (Warden.DATA_POSE.equals(datawatcherobject)) {
            switch (this.getPose()) {
                case EMERGING:
                    this.emergeAnimationState.start(this.tickCount);
                    break;
                case DIGGING:
                    this.diggingAnimationState.start(this.tickCount);
                    break;
                case ROARING:
                    this.roarAnimationState.start(this.tickCount);
                    break;
                case SNIFFING:
                    this.sniffAnimationState.start(this.tickCount);
            }
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return this.isDiggingOrEmerging();
    }

    @Override
    protected BehaviorController<?> makeBrain(Dynamic<?> dynamic) {
        return WardenAi.makeBrain(this, dynamic);
    }

    @Override
    public BehaviorController<Warden> getBrain() {
        return (BehaviorController<Warden>) super.getBrain(); // CraftBukkit - decompile error
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        PacketDebug.sendEntityBrain(this);
    }

    @Override
    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, WorldServer> biconsumer) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            biconsumer.accept(this.dynamicGameEventListener, worldserver);
        }

    }

    @Contract("null->false")
    public boolean canTargetEntity(@Nullable Entity entity) {
        boolean flag;

        if (entity instanceof EntityLiving entityliving) {
            if (this.level() == entity.level() && IEntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity) && !this.isAlliedTo(entity) && entityliving.getType() != EntityTypes.ARMOR_STAND && entityliving.getType() != EntityTypes.WARDEN && !entityliving.isInvulnerable() && !entityliving.isDeadOrDying() && this.level().getWorldBorder().isWithinBounds(entityliving.getBoundingBox())) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    public static void applyDarknessAround(WorldServer worldserver, Vec3D vec3d, @Nullable Entity entity, int i) {
        MobEffect mobeffect = new MobEffect(MobEffects.DARKNESS, 260, 0, false, false);

        MobEffectUtil.addEffectToPlayersAround(worldserver, entity, vec3d, i, mobeffect, 200, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.WARDEN); // CraftBukkit - Add EntityPotionEffectEvent.Cause
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("anger", AngerManagement.codec(this::canTargetEntity), this.angerManagement);
        valueoutput.store("listener", VibrationSystem.a.CODEC, this.vibrationData);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.angerManagement = (AngerManagement) valueinput.read("anger", AngerManagement.codec(this::canTargetEntity)).orElseGet(() -> {
            return new AngerManagement(this::canTargetEntity, Collections.emptyList());
        });
        this.syncClientAngerLevel();
        this.vibrationData = (VibrationSystem.a) valueinput.read("listener", VibrationSystem.a.CODEC).orElseGet(VibrationSystem.a::new);
    }

    private void playListeningSound() {
        if (!this.hasPose(EntityPose.ROARING)) {
            this.playSound(this.getAngerLevel().getListeningSound(), 10.0F, this.getVoicePitch());
        }

    }

    public AngerLevel getAngerLevel() {
        return AngerLevel.byAnger(this.getActiveAnger());
    }

    private int getActiveAnger() {
        return this.angerManagement.getActiveAnger(this.getTarget());
    }

    public void clearAnger(Entity entity) {
        this.angerManagement.clearAnger(entity);
    }

    public void increaseAngerAt(@Nullable Entity entity) {
        this.increaseAngerAt(entity, 35, true);
    }

    @VisibleForTesting
    public void increaseAngerAt(@Nullable Entity entity, int i, boolean flag) {
        if (!this.isNoAi() && this.canTargetEntity(entity)) {
            WardenAi.setDigCooldown(this);
            boolean flag1 = !(this.getTarget() instanceof EntityHuman);
            int j = this.angerManagement.increaseAnger(entity, i);

            if (entity instanceof EntityHuman && flag1 && AngerLevel.byAnger(j).isAngry()) {
                this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }

            if (flag) {
                this.playListeningSound();
            }
        }

    }

    public Optional<EntityLiving> getEntityAngryAt() {
        return this.getAngerLevel().isAngry() ? this.angerManagement.getActiveEntity() : Optional.empty();
    }

    @Nullable
    @Override
    public EntityLiving getTarget() {
        return this.getTargetFromBrain();
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return false;
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        this.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
        if (entityspawnreason == EntitySpawnReason.TRIGGERED) {
            this.setPose(EntityPose.EMERGING);
            this.getBrain().setMemoryWithExpiry(MemoryModuleType.IS_EMERGING, Unit.INSTANCE, (long) WardenAi.EMERGE_DURATION);
            this.playSound(SoundEffects.WARDEN_AGITATED, 5.0F, 1.0F);
        }

        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        boolean flag = super.hurtServer(worldserver, damagesource, f);

        if (!this.isNoAi() && !this.isDiggingOrEmerging()) {
            Entity entity = damagesource.getEntity();

            this.increaseAngerAt(entity, AngerLevel.ANGRY.getMinimumAnger() + 20, false);
            if (this.brain.getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty() && entity instanceof EntityLiving) {
                EntityLiving entityliving = (EntityLiving) entity;

                if (damagesource.isDirect() || this.closerThan(entityliving, 5.0D)) {
                    this.setAttackTarget(entityliving);
                }
            }
        }

        return flag;
    }

    public void setAttackTarget(EntityLiving entityliving) {
        this.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
        this.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, entityliving);
        this.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        SonicBoom.setCooldown(this, 200);
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        EntitySize entitysize = super.getDefaultDimensions(entitypose);

        return this.isDiggingOrEmerging() ? EntitySize.fixed(entitysize.width(), 1.0F) : entitysize;
    }

    @Override
    public boolean isPushable() {
        return !this.isDiggingOrEmerging() && super.isPushable();
    }

    @Override
    protected void doPush(Entity entity) {
        if (!this.isNoAi() && !this.getBrain().hasMemoryValue(MemoryModuleType.TOUCH_COOLDOWN)) {
            this.getBrain().setMemoryWithExpiry(MemoryModuleType.TOUCH_COOLDOWN, Unit.INSTANCE, 20L);
            this.increaseAngerAt(entity);
            WardenAi.setDisturbanceLocation(this, entity.blockPosition());
        }

        super.doPush(entity);
    }

    @VisibleForTesting
    public AngerManagement getAngerManagement() {
        return this.angerManagement;
    }

    @Override
    protected NavigationAbstract createNavigation(World world) {
        return new Navigation(this, world) {
            @Override
            protected Pathfinder createPathFinder(int i) {
                this.nodeEvaluator = new PathfinderNormal();
                return new Pathfinder(this.nodeEvaluator, i) {
                    @Override
                    protected float distance(PathPoint pathpoint, PathPoint pathpoint1) {
                        return pathpoint.distanceToXZ(pathpoint1);
                    }
                };
            }
        };
    }

    @Override
    public VibrationSystem.a getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.d getVibrationUser() {
        return this.vibrationUser;
    }

    private class a implements VibrationSystem.d {

        private static final int GAME_EVENT_LISTENER_RANGE = 16;
        private final PositionSource positionSource = new EntityPositionSource(Warden.this, Warden.this.getEyeHeight());

        a() {}

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.WARDEN_CAN_LISTEN;
        }

        @Override
        public boolean canTriggerAvoidVibration() {
            return true;
        }

        @Override
        public boolean canReceiveVibration(WorldServer worldserver, BlockPosition blockposition, Holder<GameEvent> holder, GameEvent.a gameevent_a) {
            if (!Warden.this.isNoAi() && !Warden.this.isDeadOrDying() && !Warden.this.getBrain().hasMemoryValue(MemoryModuleType.VIBRATION_COOLDOWN) && !Warden.this.isDiggingOrEmerging() && worldserver.getWorldBorder().isWithinBounds(blockposition)) {
                Entity entity = gameevent_a.sourceEntity();
                boolean flag;

                if (entity instanceof EntityLiving) {
                    EntityLiving entityliving = (EntityLiving) entity;

                    if (!Warden.this.canTargetEntity(entityliving)) {
                        flag = false;
                        return flag;
                    }
                }

                flag = true;
                return flag;
            } else {
                return false;
            }
        }

        @Override
        public void onReceiveVibration(WorldServer worldserver, BlockPosition blockposition, Holder<GameEvent> holder, @Nullable Entity entity, @Nullable Entity entity1, float f) {
            if (!Warden.this.isDeadOrDying()) {
                Warden.this.brain.setMemoryWithExpiry(MemoryModuleType.VIBRATION_COOLDOWN, Unit.INSTANCE, 40L);
                worldserver.broadcastEntityEvent(Warden.this, (byte) 61);
                Warden.this.playSound(SoundEffects.WARDEN_TENDRIL_CLICKS, 5.0F, Warden.this.getVoicePitch());
                BlockPosition blockposition1 = blockposition;

                if (entity1 != null) {
                    if (Warden.this.closerThan(entity1, 30.0D)) {
                        if (Warden.this.getBrain().hasMemoryValue(MemoryModuleType.RECENT_PROJECTILE)) {
                            if (Warden.this.canTargetEntity(entity1)) {
                                blockposition1 = entity1.blockPosition();
                            }

                            Warden.this.increaseAngerAt(entity1);
                        } else {
                            Warden.this.increaseAngerAt(entity1, 10, true);
                        }
                    }

                    Warden.this.getBrain().setMemoryWithExpiry(MemoryModuleType.RECENT_PROJECTILE, Unit.INSTANCE, 100L);
                } else {
                    Warden.this.increaseAngerAt(entity);
                }

                if (!Warden.this.getAngerLevel().isAngry()) {
                    Optional<EntityLiving> optional = Warden.this.angerManagement.getActiveEntity();

                    if (entity1 != null || optional.isEmpty() || optional.get() == entity) {
                        WardenAi.setDisturbanceLocation(Warden.this, blockposition1);
                    }
                }

            }
        }
    }
}
