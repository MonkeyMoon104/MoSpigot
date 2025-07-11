package net.minecraft.world.entity.decoration;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLightning;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.block.state.IBlockData;
import org.bukkit.entity.Hanging;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
// CraftBukkit end

public abstract class BlockAttachedEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private int checkInterval;
    protected BlockPosition pos;

    protected BlockAttachedEntity(EntityTypes<? extends BlockAttachedEntity> entitytypes, World world) {
        super(entitytypes, world);
    }

    protected BlockAttachedEntity(EntityTypes<? extends BlockAttachedEntity> entitytypes, World world, BlockPosition blockposition) {
        this(entitytypes, world);
        this.pos = blockposition;
    }

    protected abstract void recalculateBoundingBox();

    @Override
    public void tick() {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            this.checkBelowWorld();
            if (this.checkInterval++ == this.level().spigotConfig.hangingTickFrequency) { // Spigot
                this.checkInterval = 0;
                if (!this.isRemoved() && !this.survives()) {
                    // CraftBukkit start - fire break events
                    IBlockData material = this.level().getBlockState(this.blockPosition());
                    HangingBreakEvent.RemoveCause cause;

                    if (!material.isAir()) {
                        // TODO: This feels insufficient to catch 100% of suffocation cases
                        cause = HangingBreakEvent.RemoveCause.OBSTRUCTION;
                    } else {
                        cause = HangingBreakEvent.RemoveCause.PHYSICS;
                    }

                    HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), cause);
                    this.level().getCraftServer().getPluginManager().callEvent(event);

                    if (this.isRemoved() || event.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                    this.dropItem(worldserver, (Entity) null);
                }
            }
        }

    }

    public abstract boolean survives();

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        if (entity instanceof EntityHuman entityhuman) {
            return !this.level().mayInteract(entityhuman, this.pos) ? true : this.hurtOrSimulate(this.damageSources().playerAttack(entityhuman), 0.0F);
        } else {
            return false;
        }
    }

    @Override
    public boolean hurtClient(DamageSource damagesource) {
        return !this.isInvulnerableToBase(damagesource);
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableToBase(damagesource)) {
            return false;
        } else if (!worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && damagesource.getEntity() instanceof EntityInsentient) {
            return false;
        } else {
            if (!this.isRemoved()) {
                // CraftBukkit start - fire break events
                Entity damager = (damagesource.isDirect()) ? damagesource.getDirectEntity() : damagesource.getEntity();
                HangingBreakEvent event;
                if (damager != null) {
                    event = new HangingBreakByEntityEvent((Hanging) this.getBukkitEntity(), damager.getBukkitEntity(), damagesource.is(DamageTypeTags.IS_EXPLOSION) ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.ENTITY);
                } else {
                    event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), damagesource.is(DamageTypeTags.IS_EXPLOSION) ? HangingBreakEvent.RemoveCause.EXPLOSION : HangingBreakEvent.RemoveCause.DEFAULT);
                }

                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (this.isRemoved() || event.isCancelled()) {
                    return true;
                }
                // CraftBukkit end

                this.kill(worldserver);
                this.markHurt();
                this.dropItem(worldserver, damagesource.getEntity());
            }

            return true;
        }
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        Entity entity = explosion.getDirectSourceEntity();

        return entity != null && entity.isInWater() ? true : (explosion.shouldAffectBlocklikeEntities() ? super.ignoreExplosion(explosion) : true);
    }

    @Override
    public void move(EnumMoveType enummovetype, Vec3D vec3d) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (!this.isRemoved() && vec3d.lengthSqr() > 0.0D) {
                // CraftBukkit start - fire break events
                // TODO - Does this need its own cause? Seems to only be triggered by pistons
                HangingBreakEvent event = new HangingBreakEvent((Hanging) this.getBukkitEntity(), HangingBreakEvent.RemoveCause.PHYSICS);
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (this.isRemoved() || event.isCancelled()) {
                    return;
                }
                // CraftBukkit end

                this.kill(worldserver);
                this.dropItem(worldserver, (Entity) null);
            }
        }

    }

    @Override
    public void push(double d0, double d1, double d2) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (false && !this.isRemoved() && d0 * d0 + d1 * d1 + d2 * d2 > 0.0D) { // CraftBukkit - not needed
                this.kill(worldserver);
                this.dropItem(worldserver, (Entity) null);
            }
        }

    }

    // CraftBukkit start - selectively save tile position
    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput, boolean includeAll) {
        if (includeAll) {
            addAdditionalSaveData(valueoutput);
        }
    }
    // CraftBukkit end

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        valueoutput.store("block_pos", BlockPosition.CODEC, this.getPos());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        BlockPosition blockposition = (BlockPosition) valueinput.read("block_pos", BlockPosition.CODEC).orElse(null); // CraftBukkit - decompile error

        if (blockposition != null && blockposition.closerThan(this.blockPosition(), 16.0D)) {
            this.pos = blockposition;
        } else {
            BlockAttachedEntity.LOGGER.error("Block-attached entity at invalid position: {}", blockposition);
        }
    }

    public abstract void dropItem(WorldServer worldserver, @Nullable Entity entity);

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    @Override
    public void setPos(double d0, double d1, double d2) {
        this.pos = BlockPosition.containing(d0, d1, d2);
        this.recalculateBoundingBox();
        this.hasImpulse = true;
    }

    public BlockPosition getPos() {
        return this.pos;
    }

    @Override
    public void thunderHit(WorldServer worldserver, EntityLightning entitylightning) {}

    @Override
    public void refreshDimensions() {}
}
