package net.minecraft.world.entity.boss.enderdragon.phases;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityAreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EntityEnderDragon;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class DragonControllerLandedFlame extends AbstractDragonControllerLanded {

    private static final int FLAME_DURATION = 200;
    private static final int SITTING_FLAME_ATTACKS_COUNT = 4;
    private static final int WARMUP_TIME = 10;
    private int flameTicks;
    private int flameCount;
    @Nullable
    private EntityAreaEffectCloud flame;

    public DragonControllerLandedFlame(EntityEnderDragon entityenderdragon) {
        super(entityenderdragon);
    }

    @Override
    public void doClientTick() {
        ++this.flameTicks;
        if (this.flameTicks % 2 == 0 && this.flameTicks < 10) {
            Vec3D vec3d = this.dragon.getHeadLookVector(1.0F).normalize();

            vec3d.yRot((-(float) Math.PI / 4F));
            double d0 = this.dragon.head.getX();
            double d1 = this.dragon.head.getY(0.5D);
            double d2 = this.dragon.head.getZ();

            for (int i = 0; i < 8; ++i) {
                double d3 = d0 + this.dragon.getRandom().nextGaussian() / 2.0D;
                double d4 = d1 + this.dragon.getRandom().nextGaussian() / 2.0D;
                double d5 = d2 + this.dragon.getRandom().nextGaussian() / 2.0D;

                for (int j = 0; j < 6; ++j) {
                    this.dragon.level().addParticle(Particles.DRAGON_BREATH, d3, d4, d5, -vec3d.x * (double) 0.08F * (double) j, -vec3d.y * (double) 0.6F, -vec3d.z * (double) 0.08F * (double) j);
                }

                vec3d.yRot(0.19634955F);
            }
        }

    }

    @Override
    public void doServerTick(WorldServer worldserver) {
        ++this.flameTicks;
        if (this.flameTicks >= 200) {
            if (this.flameCount >= 4) {
                this.dragon.getPhaseManager().setPhase(DragonControllerPhase.TAKEOFF);
            } else {
                this.dragon.getPhaseManager().setPhase(DragonControllerPhase.SITTING_SCANNING);
            }
        } else if (this.flameTicks == 10) {
            Vec3D vec3d = (new Vec3D(this.dragon.head.getX() - this.dragon.getX(), 0.0D, this.dragon.head.getZ() - this.dragon.getZ())).normalize();
            float f = 5.0F;
            double d0 = this.dragon.head.getX() + vec3d.x * 5.0D / 2.0D;
            double d1 = this.dragon.head.getZ() + vec3d.z * 5.0D / 2.0D;
            double d2 = this.dragon.head.getY(0.5D);
            double d3 = d2;
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition(d0, d2, d1);

            while (worldserver.isEmptyBlock(blockposition_mutableblockposition)) {
                --d3;
                if (d3 < 0.0D) {
                    d3 = d2;
                    break;
                }

                blockposition_mutableblockposition.set(d0, d3, d1);
            }

            d3 = (double) (MathHelper.floor(d3) + 1);
            this.flame = new EntityAreaEffectCloud(worldserver, d0, d3, d1);
            this.flame.setOwner(this.dragon);
            this.flame.setRadius(5.0F);
            this.flame.setDuration(200);
            this.flame.setCustomParticle(Particles.DRAGON_BREATH);
            this.flame.setPotionDurationScale(0.25F);
            this.flame.addEffect(new MobEffect(MobEffects.INSTANT_DAMAGE));
            worldserver.addFreshEntity(this.flame);
        }

    }

    @Override
    public void begin() {
        this.flameTicks = 0;
        ++this.flameCount;
    }

    @Override
    public void end() {
        if (this.flame != null) {
            this.flame.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            this.flame = null;
        }

    }

    @Override
    public DragonControllerPhase<DragonControllerLandedFlame> getPhase() {
        return DragonControllerPhase.SITTING_FLAMING;
    }

    public void resetFlameCount() {
        this.flameCount = 0;
    }
}
