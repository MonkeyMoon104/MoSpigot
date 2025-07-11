package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityEvokerFangs extends Entity implements TraceableEntity {

    public static final int ATTACK_DURATION = 20;
    public static final int LIFE_OFFSET = 2;
    public static final int ATTACK_TRIGGER_TICKS = 14;
    private static final int DEFAULT_WARMUP_DELAY = 0;
    public int warmupDelayTicks;
    private boolean sentSpikeEvent;
    private int lifeTicks;
    private boolean clientSideAttackStarted;
    @Nullable
    private EntityReference<EntityLiving> owner;

    public EntityEvokerFangs(EntityTypes<? extends EntityEvokerFangs> entitytypes, World world) {
        super(entitytypes, world);
        this.warmupDelayTicks = 0;
        this.lifeTicks = 22;
    }

    public EntityEvokerFangs(World world, double d0, double d1, double d2, float f, int i, EntityLiving entityliving) {
        this(EntityTypes.EVOKER_FANGS, world);
        this.warmupDelayTicks = i;
        this.setOwner(entityliving);
        this.setYRot(f * (180F / (float) Math.PI));
        this.setPos(d0, d1, d2);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {}

    public void setOwner(@Nullable EntityLiving entityliving) {
        this.owner = entityliving != null ? new EntityReference(entityliving) : null;
    }

    @Nullable
    @Override
    public EntityLiving getOwner() {
        return (EntityLiving) EntityReference.get(this.owner, this.level(), EntityLiving.class);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        this.warmupDelayTicks = valueinput.getIntOr("Warmup", 0);
        this.owner = EntityReference.<EntityLiving>read(valueinput, "Owner");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        valueoutput.putInt("Warmup", this.warmupDelayTicks);
        EntityReference.store(this.owner, valueoutput, "Owner");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.clientSideAttackStarted) {
                --this.lifeTicks;
                if (this.lifeTicks == 14) {
                    for (int i = 0; i < 12; ++i) {
                        double d0 = this.getX() + (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.getBbWidth() * 0.5D;
                        double d1 = this.getY() + 0.05D + this.random.nextDouble();
                        double d2 = this.getZ() + (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.getBbWidth() * 0.5D;
                        double d3 = (this.random.nextDouble() * 2.0D - 1.0D) * 0.3D;
                        double d4 = 0.3D + this.random.nextDouble() * 0.3D;
                        double d5 = (this.random.nextDouble() * 2.0D - 1.0D) * 0.3D;

                        this.level().addParticle(Particles.CRIT, d0, d1 + 1.0D, d2, d3, d4, d5);
                    }
                }
            }
        } else if (--this.warmupDelayTicks < 0) {
            if (this.warmupDelayTicks == -8) {
                for (EntityLiving entityliving : this.level().getEntitiesOfClass(EntityLiving.class, this.getBoundingBox().inflate(0.2D, 0.0D, 0.2D))) {
                    this.dealDamageTo(entityliving);
                }
            }

            if (!this.sentSpikeEvent) {
                this.level().broadcastEntityEvent(this, (byte) 4);
                this.sentSpikeEvent = true;
            }

            if (--this.lifeTicks < 0) {
                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }
        }

    }

    private void dealDamageTo(EntityLiving entityliving) {
        EntityLiving entityliving1 = this.getOwner();

        if (entityliving.isAlive() && !entityliving.isInvulnerable() && entityliving != entityliving1) {
            if (entityliving1 == null) {
                entityliving.hurt(this.damageSources().magic().customEntityDamager(this), 6.0F); // CraftBukkit
            } else {
                if (entityliving1.isAlliedTo((Entity) entityliving)) {
                    return;
                }

                DamageSource damagesource = this.damageSources().indirectMagic(this, entityliving1);
                World world = this.level();

                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;

                    if (entityliving.hurtServer(worldserver, damagesource, 6.0F)) {
                        EnchantmentManager.doPostAttackEffects(worldserver, entityliving, damagesource);
                    }
                }
            }

        }
    }

    @Override
    public void handleEntityEvent(byte b0) {
        super.handleEntityEvent(b0);
        if (b0 == 4) {
            this.clientSideAttackStarted = true;
            if (!this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.EVOKER_FANGS_ATTACK, this.getSoundSource(), 1.0F, this.random.nextFloat() * 0.2F + 0.85F, false);
            }
        }

    }

    public float getAnimationProgress(float f) {
        if (!this.clientSideAttackStarted) {
            return 0.0F;
        } else {
            int i = this.lifeTicks - 2;

            return i <= 0 ? 1.0F : 1.0F - ((float) i - f) / 20.0F;
        }
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        return false;
    }
}
