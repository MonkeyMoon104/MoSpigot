package net.minecraft.world.entity.boss.wither;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.BossBattleServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.BossBattle;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerMoveFlying;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalArrowAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomFly;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.ai.navigation.NavigationFlying;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.monster.IRangedEntity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.projectile.EntityWitherSkull;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.IMaterial;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import net.minecraft.network.protocol.game.PacketPlayOutWorldEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EntityWither extends EntityMonster implements IRangedEntity {

    private static final DataWatcherObject<Integer> DATA_TARGET_A = DataWatcher.<Integer>defineId(EntityWither.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Integer> DATA_TARGET_B = DataWatcher.<Integer>defineId(EntityWither.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Integer> DATA_TARGET_C = DataWatcher.<Integer>defineId(EntityWither.class, DataWatcherRegistry.INT);
    private static final List<DataWatcherObject<Integer>> DATA_TARGETS = ImmutableList.of(EntityWither.DATA_TARGET_A, EntityWither.DATA_TARGET_B, EntityWither.DATA_TARGET_C);
    private static final DataWatcherObject<Integer> DATA_ID_INV = DataWatcher.<Integer>defineId(EntityWither.class, DataWatcherRegistry.INT);
    private static final int INVULNERABLE_TICKS = 220;
    private static final int DEFAULT_INVULNERABLE_TICKS = 0;
    private final float[] xRotHeads = new float[2];
    private final float[] yRotHeads = new float[2];
    private final float[] xRotOHeads = new float[2];
    private final float[] yRotOHeads = new float[2];
    private final int[] nextHeadUpdate = new int[2];
    private final int[] idleHeadUpdates = new int[2];
    private int destroyBlocksTick;
    public final BossBattleServer bossEvent;
    private static final PathfinderTargetCondition.a LIVING_ENTITY_SELECTOR = (entityliving, worldserver) -> {
        return !entityliving.getType().is(TagsEntity.WITHER_FRIENDS) && entityliving.attackable();
    };
    private static final PathfinderTargetCondition TARGETING_CONDITIONS = PathfinderTargetCondition.forCombat().range(20.0D).selector(EntityWither.LIVING_ENTITY_SELECTOR);

    public EntityWither(EntityTypes<? extends EntityWither> entitytypes, World world) {
        super(entitytypes, world);
        this.bossEvent = (BossBattleServer) (new BossBattleServer(this.getDisplayName(), BossBattle.BarColor.PURPLE, BossBattle.BarStyle.PROGRESS)).setDarkenScreen(true);
        this.moveControl = new ControllerMoveFlying(this, 10, false);
        this.setHealth(this.getMaxHealth());
        this.xpReward = 50;
    }

    @Override
    protected NavigationAbstract createNavigation(World world) {
        NavigationFlying navigationflying = new NavigationFlying(this, world);

        navigationflying.setCanOpenDoors(false);
        navigationflying.setCanFloat(true);
        return navigationflying;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EntityWither.a());
        this.goalSelector.addGoal(2, new PathfinderGoalArrowAttack(this, 1.0D, 40, 20.0F));
        this.goalSelector.addGoal(5, new PathfinderGoalRandomFly(this, 1.0D));
        this.goalSelector.addGoal(6, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F));
        this.goalSelector.addGoal(7, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, new PathfinderGoalHurtByTarget(this, new Class[0]));
        this.targetSelector.addGoal(2, new PathfinderGoalNearestAttackableTarget(this, EntityLiving.class, 0, false, false, EntityWither.LIVING_ENTITY_SELECTOR));
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityWither.DATA_TARGET_A, 0);
        datawatcher_a.define(EntityWither.DATA_TARGET_B, 0);
        datawatcher_a.define(EntityWither.DATA_TARGET_C, 0);
        datawatcher_a.define(EntityWither.DATA_ID_INV, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("Invul", this.getInvulnerableTicks());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setInvulnerableTicks(valueinput.getIntOr("Invul", 0));
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }

    }

    @Override
    public void setCustomName(@Nullable IChatBaseComponent ichatbasecomponent) {
        super.setCustomName(ichatbasecomponent);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.WITHER_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.WITHER_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.WITHER_DEATH;
    }

    @Override
    public void aiStep() {
        Vec3D vec3d = this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D);

        if (!this.level().isClientSide && this.getAlternativeTarget(0) > 0) {
            Entity entity = this.level().getEntity(this.getAlternativeTarget(0));

            if (entity != null) {
                double d0 = vec3d.y;

                if (this.getY() < entity.getY() || !this.isPowered() && this.getY() < entity.getY() + 5.0D) {
                    d0 = Math.max(0.0D, d0);
                    d0 += 0.3D - d0 * (double) 0.6F;
                }

                vec3d = new Vec3D(vec3d.x, d0, vec3d.z);
                Vec3D vec3d1 = new Vec3D(entity.getX() - this.getX(), 0.0D, entity.getZ() - this.getZ());

                if (vec3d1.horizontalDistanceSqr() > 9.0D) {
                    Vec3D vec3d2 = vec3d1.normalize();

                    vec3d = vec3d.add(vec3d2.x * 0.3D - vec3d.x * 0.6D, 0.0D, vec3d2.z * 0.3D - vec3d.z * 0.6D);
                }
            }
        }

        this.setDeltaMovement(vec3d);
        if (vec3d.horizontalDistanceSqr() > 0.05D) {
            this.setYRot((float) MathHelper.atan2(vec3d.z, vec3d.x) * (180F / (float) Math.PI) - 90.0F);
        }

        super.aiStep();

        for (int i = 0; i < 2; ++i) {
            this.yRotOHeads[i] = this.yRotHeads[i];
            this.xRotOHeads[i] = this.xRotHeads[i];
        }

        for (int j = 0; j < 2; ++j) {
            int k = this.getAlternativeTarget(j + 1);
            Entity entity1 = null;

            if (k > 0) {
                entity1 = this.level().getEntity(k);
            }

            if (entity1 != null) {
                double d1 = this.getHeadX(j + 1);
                double d2 = this.getHeadY(j + 1);
                double d3 = this.getHeadZ(j + 1);
                double d4 = entity1.getX() - d1;
                double d5 = entity1.getEyeY() - d2;
                double d6 = entity1.getZ() - d3;
                double d7 = Math.sqrt(d4 * d4 + d6 * d6);
                float f = (float) (MathHelper.atan2(d6, d4) * (double) (180F / (float) Math.PI)) - 90.0F;
                float f1 = (float) (-(MathHelper.atan2(d5, d7) * (double) (180F / (float) Math.PI)));

                this.xRotHeads[j] = this.rotlerp(this.xRotHeads[j], f1, 40.0F);
                this.yRotHeads[j] = this.rotlerp(this.yRotHeads[j], f, 10.0F);
            } else {
                this.yRotHeads[j] = this.rotlerp(this.yRotHeads[j], this.yBodyRot, 10.0F);
            }
        }

        boolean flag = this.isPowered();

        for (int l = 0; l < 3; ++l) {
            double d8 = this.getHeadX(l);
            double d9 = this.getHeadY(l);
            double d10 = this.getHeadZ(l);
            float f2 = 0.3F * this.getScale();

            this.level().addParticle(Particles.SMOKE, d8 + this.random.nextGaussian() * (double) f2, d9 + this.random.nextGaussian() * (double) f2, d10 + this.random.nextGaussian() * (double) f2, 0.0D, 0.0D, 0.0D);
            if (flag && this.level().random.nextInt(4) == 0) {
                this.level().addParticle(ColorParticleOption.create(Particles.ENTITY_EFFECT, 0.7F, 0.7F, 0.5F), d8 + this.random.nextGaussian() * (double) f2, d9 + this.random.nextGaussian() * (double) f2, d10 + this.random.nextGaussian() * (double) f2, 0.0D, 0.0D, 0.0D);
            }
        }

        if (this.getInvulnerableTicks() > 0) {
            float f3 = 3.3F * this.getScale();

            for (int i1 = 0; i1 < 3; ++i1) {
                this.level().addParticle(ColorParticleOption.create(Particles.ENTITY_EFFECT, 0.7F, 0.7F, 0.9F), this.getX() + this.random.nextGaussian(), this.getY() + (double) (this.random.nextFloat() * f3), this.getZ() + this.random.nextGaussian(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        if (this.getInvulnerableTicks() > 0) {
            int i = this.getInvulnerableTicks() - 1;

            this.bossEvent.setProgress(1.0F - (float) i / 220.0F);
            if (i <= 0) {
                // CraftBukkit start
                // worldserver.explode(this, this.getX(), this.getEyeY(), this.getZ(), 7.0F, false, World.a.MOB);
                ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), 7.0F, false);
                worldserver.getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    worldserver.explode(this, this.getX(), this.getEyeY(), this.getZ(), event.getRadius(), event.getFire(), World.a.MOB);
                }
                // CraftBukkit end

                if (!this.isSilent()) {
                    // CraftBukkit start - Use relative location for far away sounds
                    // worldserver.globalLevelEvent(1023, new BlockPosition(this), 0);
                    int viewDistance = worldserver.getCraftServer().getViewDistance() * 16;
                    for (EntityPlayer player : (List<EntityPlayer>) MinecraftServer.getServer().getPlayerList().players) {
                        double deltaX = this.getX() - player.getX();
                        double deltaZ = this.getZ() - player.getZ();
                        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                        if (distanceSquared > viewDistance * viewDistance) {
                            double deltaLength = Math.sqrt(distanceSquared);
                            double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                            double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                            player.connection.send(new PacketPlayOutWorldEvent(1023, new BlockPosition((int) relativeX, (int) this.getY(), (int) relativeZ), 0, true));
                        } else {
                            player.connection.send(new PacketPlayOutWorldEvent(1023, this.blockPosition(), 0, true));
                        }
                    }
                    // CraftBukkit end
                }
            }

            this.setInvulnerableTicks(i);
            if (this.tickCount % 10 == 0) {
                this.heal(10.0F, EntityRegainHealthEvent.RegainReason.WITHER_SPAWN); // CraftBukkit
            }

        } else {
            super.customServerAiStep(worldserver);

            for (int j = 1; j < 3; ++j) {
                if (this.tickCount >= this.nextHeadUpdate[j - 1]) {
                    this.nextHeadUpdate[j - 1] = this.tickCount + 10 + this.random.nextInt(10);
                    if (worldserver.getDifficulty() == EnumDifficulty.NORMAL || worldserver.getDifficulty() == EnumDifficulty.HARD) {
                        int k = j - 1;
                        int l = this.idleHeadUpdates[j - 1];

                        this.idleHeadUpdates[k] = this.idleHeadUpdates[j - 1] + 1;
                        if (l > 15) {
                            float f = 10.0F;
                            float f1 = 5.0F;
                            double d0 = MathHelper.nextDouble(this.random, this.getX() - 10.0D, this.getX() + 10.0D);
                            double d1 = MathHelper.nextDouble(this.random, this.getY() - 5.0D, this.getY() + 5.0D);
                            double d2 = MathHelper.nextDouble(this.random, this.getZ() - 10.0D, this.getZ() + 10.0D);

                            this.performRangedAttack(j + 1, d0, d1, d2, true);
                            this.idleHeadUpdates[j - 1] = 0;
                        }
                    }

                    int i1 = this.getAlternativeTarget(j);

                    if (i1 > 0) {
                        EntityLiving entityliving = (EntityLiving) worldserver.getEntity(i1);

                        if (entityliving != null && this.canAttack(entityliving) && this.distanceToSqr((Entity) entityliving) <= 900.0D && this.hasLineOfSight(entityliving)) {
                            this.performRangedAttack(j + 1, entityliving);
                            this.nextHeadUpdate[j - 1] = this.tickCount + 40 + this.random.nextInt(20);
                            this.idleHeadUpdates[j - 1] = 0;
                        } else {
                            this.setAlternativeTarget(j, 0);
                        }
                    } else {
                        List<EntityLiving> list = worldserver.<EntityLiving>getNearbyEntities(EntityLiving.class, EntityWither.TARGETING_CONDITIONS, this, this.getBoundingBox().inflate(20.0D, 8.0D, 20.0D));

                        if (!list.isEmpty()) {
                            EntityLiving entityliving1 = (EntityLiving) list.get(this.random.nextInt(list.size()));

                            if (CraftEventFactory.callEntityTargetLivingEvent(this, entityliving1, EntityTargetEvent.TargetReason.CLOSEST_ENTITY).isCancelled()) continue; // CraftBukkit
                            this.setAlternativeTarget(j, entityliving1.getId());
                        }
                    }
                }
            }

            if (this.getTarget() != null) {
                this.setAlternativeTarget(0, this.getTarget().getId());
            } else {
                this.setAlternativeTarget(0, 0);
            }

            if (this.destroyBlocksTick > 0) {
                --this.destroyBlocksTick;
                if (this.destroyBlocksTick == 0 && worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    boolean flag = false;
                    int j1 = MathHelper.floor(this.getBbWidth() / 2.0F + 1.0F);
                    int k1 = MathHelper.floor(this.getBbHeight());

                    for (BlockPosition blockposition : BlockPosition.betweenClosed(this.getBlockX() - j1, this.getBlockY(), this.getBlockZ() - j1, this.getBlockX() + j1, this.getBlockY() + k1, this.getBlockZ() + j1)) {
                        IBlockData iblockdata = worldserver.getBlockState(blockposition);

                        if (canDestroy(iblockdata)) {
                            // CraftBukkit start
                            if (!CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, Blocks.AIR.defaultBlockState())) {
                                continue;
                            }
                            // CraftBukkit end
                            flag = worldserver.destroyBlock(blockposition, true, this) || flag;
                        }
                    }

                    if (flag) {
                        worldserver.levelEvent((Entity) null, 1022, this.blockPosition(), 0);
                    }
                }
            }

            if (this.tickCount % 20 == 0) {
                this.heal(1.0F, EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit
            }

            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
    }

    public static boolean canDestroy(IBlockData iblockdata) {
        return !iblockdata.isAir() && !iblockdata.is(TagsBlock.WITHER_IMMUNE);
    }

    public void makeInvulnerable() {
        this.setInvulnerableTicks(220);
        this.bossEvent.setProgress(0.0F);
        this.setHealth(this.getMaxHealth() / 3.0F);
    }

    @Override
    public void makeStuckInBlock(IBlockData iblockdata, Vec3D vec3d) {}

    @Override
    public void startSeenByPlayer(EntityPlayer entityplayer) {
        super.startSeenByPlayer(entityplayer);
        this.bossEvent.addPlayer(entityplayer);
    }

    @Override
    public void stopSeenByPlayer(EntityPlayer entityplayer) {
        super.stopSeenByPlayer(entityplayer);
        this.bossEvent.removePlayer(entityplayer);
    }

    private double getHeadX(int i) {
        if (i <= 0) {
            return this.getX();
        } else {
            float f = (this.yBodyRot + (float) (180 * (i - 1))) * ((float) Math.PI / 180F);
            float f1 = MathHelper.cos(f);

            return this.getX() + (double) f1 * 1.3D * (double) this.getScale();
        }
    }

    private double getHeadY(int i) {
        float f = i <= 0 ? 3.0F : 2.2F;

        return this.getY() + (double) (f * this.getScale());
    }

    private double getHeadZ(int i) {
        if (i <= 0) {
            return this.getZ();
        } else {
            float f = (this.yBodyRot + (float) (180 * (i - 1))) * ((float) Math.PI / 180F);
            float f1 = MathHelper.sin(f);

            return this.getZ() + (double) f1 * 1.3D * (double) this.getScale();
        }
    }

    private float rotlerp(float f, float f1, float f2) {
        float f3 = MathHelper.wrapDegrees(f1 - f);

        if (f3 > f2) {
            f3 = f2;
        }

        if (f3 < -f2) {
            f3 = -f2;
        }

        return f + f3;
    }

    private void performRangedAttack(int i, EntityLiving entityliving) {
        this.performRangedAttack(i, entityliving.getX(), entityliving.getY() + (double) entityliving.getEyeHeight() * 0.5D, entityliving.getZ(), i == 0 && this.random.nextFloat() < 0.001F);
    }

    private void performRangedAttack(int i, double d0, double d1, double d2, boolean flag) {
        if (!this.isSilent()) {
            this.level().levelEvent((Entity) null, 1024, this.blockPosition(), 0);
        }

        double d3 = this.getHeadX(i);
        double d4 = this.getHeadY(i);
        double d5 = this.getHeadZ(i);
        double d6 = d0 - d3;
        double d7 = d1 - d4;
        double d8 = d2 - d5;
        Vec3D vec3d = new Vec3D(d6, d7, d8);
        EntityWitherSkull entitywitherskull = new EntityWitherSkull(this.level(), this, vec3d.normalize());

        entitywitherskull.setOwner(this);
        if (flag) {
            entitywitherskull.setDangerous(true);
        }

        entitywitherskull.setPos(d3, d4, d5);
        this.level().addFreshEntity(entitywitherskull);
    }

    @Override
    public void performRangedAttack(EntityLiving entityliving, float f) {
        this.performRangedAttack(0, entityliving);
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else if (!damagesource.is(DamageTypeTags.WITHER_IMMUNE_TO) && !(damagesource.getEntity() instanceof EntityWither)) {
            if (this.getInvulnerableTicks() > 0 && !damagesource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            } else {
                if (this.isPowered()) {
                    Entity entity = damagesource.getDirectEntity();

                    if (entity instanceof EntityArrow || entity instanceof WindCharge) {
                        return false;
                    }
                }

                Entity entity1 = damagesource.getEntity();

                if (entity1 != null && entity1.getType().is(TagsEntity.WITHER_FRIENDS)) {
                    return false;
                } else {
                    if (this.destroyBlocksTick <= 0) {
                        this.destroyBlocksTick = 20;
                    }

                    for (int i = 0; i < this.idleHeadUpdates.length; ++i) {
                        this.idleHeadUpdates[i] += 3;
                    }

                    return super.hurtServer(worldserver, damagesource, f);
                }
            }
        } else {
            return false;
        }
    }

    @Override
    protected void dropCustomDeathLoot(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        super.dropCustomDeathLoot(worldserver, damagesource, flag);
        EntityItem entityitem = this.spawnAtLocation(worldserver, (IMaterial) Items.NETHER_STAR);

        if (entityitem != null) {
            entityitem.setExtendedLifetime();
        }

    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == EnumDifficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    public boolean addEffect(MobEffect mobeffect, @Nullable Entity entity) {
        return false;
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityMonster.createMonsterAttributes().add(GenericAttributes.MAX_HEALTH, 300.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.6F).add(GenericAttributes.FLYING_SPEED, (double) 0.6F).add(GenericAttributes.FOLLOW_RANGE, 40.0D).add(GenericAttributes.ARMOR, 4.0D);
    }

    public float[] getHeadYRots() {
        return this.yRotHeads;
    }

    public float[] getHeadXRots() {
        return this.xRotHeads;
    }

    public int getInvulnerableTicks() {
        return (Integer) this.entityData.get(EntityWither.DATA_ID_INV);
    }

    public void setInvulnerableTicks(int i) {
        this.entityData.set(EntityWither.DATA_ID_INV, i);
    }

    public int getAlternativeTarget(int i) {
        return (Integer) this.entityData.get((DataWatcherObject) EntityWither.DATA_TARGETS.get(i));
    }

    public void setAlternativeTarget(int i, int j) {
        this.entityData.set((DataWatcherObject) EntityWither.DATA_TARGETS.get(i), j);
    }

    public boolean isPowered() {
        return this.getHealth() <= this.getMaxHealth() / 2.0F;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean flag) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffect mobeffect) {
        return mobeffect.is(MobEffects.WITHER) ? false : super.canBeAffected(mobeffect);
    }

    private class a extends PathfinderGoal {

        public a() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.JUMP, PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            return EntityWither.this.getInvulnerableTicks() > 0;
        }
    }
}
