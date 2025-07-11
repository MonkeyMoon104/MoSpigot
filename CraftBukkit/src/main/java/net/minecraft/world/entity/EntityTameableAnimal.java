package net.minecraft.world.entity;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockLeaves;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfinderNormal;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.ScoreboardTeam;

// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTeleportEvent;
// CraftBukkit end

public abstract class EntityTameableAnimal extends EntityAnimal implements OwnableEntity {

    public static final int TELEPORT_WHEN_DISTANCE_IS_SQ = 144;
    private static final int MIN_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 2;
    private static final int MAX_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 3;
    private static final int MAX_VERTICAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 1;
    private static final boolean DEFAULT_ORDERED_TO_SIT = false;
    protected static final DataWatcherObject<Byte> DATA_FLAGS_ID = DataWatcher.<Byte>defineId(EntityTameableAnimal.class, DataWatcherRegistry.BYTE);
    protected static final DataWatcherObject<Optional<EntityReference<EntityLiving>>> DATA_OWNERUUID_ID = DataWatcher.<Optional<EntityReference<EntityLiving>>>defineId(EntityTameableAnimal.class, DataWatcherRegistry.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private boolean orderedToSit = false;

    protected EntityTameableAnimal(EntityTypes<? extends EntityTameableAnimal> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityTameableAnimal.DATA_FLAGS_ID, (byte) 0);
        datawatcher_a.define(EntityTameableAnimal.DATA_OWNERUUID_ID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        EntityReference<EntityLiving> entityreference = this.getOwnerReference();

        EntityReference.store(entityreference, valueoutput, "Owner");
        valueoutput.putBoolean("Sitting", this.orderedToSit);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        EntityReference<EntityLiving> entityreference = EntityReference.<EntityLiving>readWithOldOwnerConversion(valueinput, "Owner", this.level());

        if (entityreference != null) {
            try {
                this.entityData.set(EntityTameableAnimal.DATA_OWNERUUID_ID, Optional.of(entityreference));
                this.setTame(true, false);
            } catch (Throwable throwable) {
                this.setTame(false, true);
            }
        } else {
            this.entityData.set(EntityTameableAnimal.DATA_OWNERUUID_ID, Optional.empty());
            this.setTame(false, true);
        }

        this.orderedToSit = valueinput.getBooleanOr("Sitting", false);
        this.setInSittingPose(this.orderedToSit);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    protected void spawnTamingParticles(boolean flag) {
        ParticleParam particleparam = Particles.HEART;

        if (!flag) {
            particleparam = Particles.SMOKE;
        }

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

    public boolean isTame() {
        return ((Byte) this.entityData.get(EntityTameableAnimal.DATA_FLAGS_ID) & 4) != 0;
    }

    public void setTame(boolean flag, boolean flag1) {
        byte b0 = (Byte) this.entityData.get(EntityTameableAnimal.DATA_FLAGS_ID);

        if (flag) {
            this.entityData.set(EntityTameableAnimal.DATA_FLAGS_ID, (byte) (b0 | 4));
        } else {
            this.entityData.set(EntityTameableAnimal.DATA_FLAGS_ID, (byte) (b0 & -5));
        }

        if (flag1) {
            this.applyTamingSideEffects();
        }

    }

    protected void applyTamingSideEffects() {}

    public boolean isInSittingPose() {
        return ((Byte) this.entityData.get(EntityTameableAnimal.DATA_FLAGS_ID) & 1) != 0;
    }

    public void setInSittingPose(boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntityTameableAnimal.DATA_FLAGS_ID);

        if (flag) {
            this.entityData.set(EntityTameableAnimal.DATA_FLAGS_ID, (byte) (b0 | 1));
        } else {
            this.entityData.set(EntityTameableAnimal.DATA_FLAGS_ID, (byte) (b0 & -2));
        }

    }

    @Nullable
    @Override
    public EntityReference<EntityLiving> getOwnerReference() {
        return (EntityReference) ((Optional) this.entityData.get(EntityTameableAnimal.DATA_OWNERUUID_ID)).orElse((Object) null);
    }

    public void setOwner(@Nullable EntityLiving entityliving) {
        this.entityData.set(EntityTameableAnimal.DATA_OWNERUUID_ID, Optional.ofNullable(entityliving).map(EntityReference::new));
    }

    public void setOwnerReference(@Nullable EntityReference<EntityLiving> entityreference) {
        this.entityData.set(EntityTameableAnimal.DATA_OWNERUUID_ID, Optional.ofNullable(entityreference));
    }

    public void tame(EntityHuman entityhuman) {
        this.setTame(true, true);
        this.setOwner(entityhuman);
        if (entityhuman instanceof EntityPlayer entityplayer) {
            CriterionTriggers.TAME_ANIMAL.trigger(entityplayer, this);
        }

    }

    @Override
    public boolean canAttack(EntityLiving entityliving) {
        return this.isOwnedBy(entityliving) ? false : super.canAttack(entityliving);
    }

    public boolean isOwnedBy(EntityLiving entityliving) {
        return entityliving == this.getOwner();
    }

    public boolean wantsToAttack(EntityLiving entityliving, EntityLiving entityliving1) {
        return true;
    }

    @Nullable
    @Override
    public ScoreboardTeam getTeam() {
        ScoreboardTeam scoreboardteam = super.getTeam();

        if (scoreboardteam != null) {
            return scoreboardteam;
        } else {
            if (this.isTame()) {
                EntityLiving entityliving = this.getRootOwner();

                if (entityliving != null) {
                    return entityliving.getTeam();
                }
            }

            return null;
        }
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (this.isTame()) {
            EntityLiving entityliving = this.getRootOwner();

            if (entity == entityliving) {
                return true;
            }

            if (entityliving != null) {
                return entityliving.considersEntityAsAlly(entity);
            }
        }

        return super.considersEntityAsAlly(entity);
    }

    @Override
    public void die(DamageSource damagesource) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (worldserver.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
                EntityLiving entityliving = this.getOwner();

                if (entityliving instanceof EntityPlayer) {
                    EntityPlayer entityplayer = (EntityPlayer) entityliving;

                    entityplayer.sendSystemMessage(this.getCombatTracker().getDeathMessage());
                }
            }
        }

        super.die(damagesource);
    }

    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    public void setOrderedToSit(boolean flag) {
        this.orderedToSit = flag;
    }

    public void tryToTeleportToOwner() {
        EntityLiving entityliving = this.getOwner();

        if (entityliving != null) {
            this.teleportToAroundBlockPos(entityliving.blockPosition());
        }

    }

    public boolean shouldTryTeleportToOwner() {
        EntityLiving entityliving = this.getOwner();

        return entityliving != null && this.distanceToSqr((Entity) this.getOwner()) >= 144.0D;
    }

    private void teleportToAroundBlockPos(BlockPosition blockposition) {
        for (int i = 0; i < 10; ++i) {
            int j = this.random.nextIntBetweenInclusive(-3, 3);
            int k = this.random.nextIntBetweenInclusive(-3, 3);

            if (Math.abs(j) >= 2 || Math.abs(k) >= 2) {
                int l = this.random.nextIntBetweenInclusive(-1, 1);

                if (this.maybeTeleportTo(blockposition.getX() + j, blockposition.getY() + l, blockposition.getZ() + k)) {
                    return;
                }
            }
        }

    }

    private boolean maybeTeleportTo(int i, int j, int k) {
        if (!this.canTeleportTo(new BlockPosition(i, j, k))) {
            return false;
        } else {
            // CraftBukkit start
            EntityTeleportEvent event = CraftEventFactory.callEntityTeleportEvent(this, (double) i + 0.5D, (double) j, (double) k + 0.5D);
            if (event.isCancelled()) {
                return false;
            }
            Location to = event.getTo();
            this.snapTo(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch());
            // CraftBukkit end
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPosition blockposition) {
        PathType pathtype = PathfinderNormal.getPathTypeStatic((EntityInsentient) this, blockposition);

        if (pathtype != PathType.WALKABLE) {
            return false;
        } else {
            IBlockData iblockdata = this.level().getBlockState(blockposition.below());

            if (!this.canFlyToOwner() && iblockdata.getBlock() instanceof BlockLeaves) {
                return false;
            } else {
                BlockPosition blockposition1 = blockposition.subtract(this.blockPosition());

                return this.level().noCollision(this, this.getBoundingBox().move(blockposition1));
            }
        }
    }

    public final boolean unableToMoveToOwner() {
        return this.isOrderedToSit() || this.isPassenger() || this.mayBeLeashed() || this.getOwner() != null && this.getOwner().isSpectator();
    }

    protected boolean canFlyToOwner() {
        return false;
    }

    public class a extends PathfinderGoalPanic {

        public a(final double d0, final TagKey tagkey) {
            super(EntityTameableAnimal.this, d0, tagkey);
        }

        public a(final double d0) {
            super(EntityTameableAnimal.this, d0);
        }

        @Override
        public void tick() {
            if (!EntityTameableAnimal.this.unableToMoveToOwner() && EntityTameableAnimal.this.shouldTryTeleportToOwner()) {
                EntityTameableAnimal.this.tryToTeleportToOwner();
            }

            super.tick();
        }
    }
}
