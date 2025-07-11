package net.minecraft.world.entity.projectile;

import java.util.List;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAreaEffectCloud;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.World;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionEntity;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityDragonFireball extends EntityFireball {

    public static final float SPLASH_RANGE = 4.0F;

    public EntityDragonFireball(EntityTypes<? extends EntityDragonFireball> entitytypes, World world) {
        super(entitytypes, world);
    }

    public EntityDragonFireball(World world, EntityLiving entityliving, Vec3D vec3d) {
        super(EntityTypes.DRAGON_FIREBALL, entityliving, vec3d, world);
    }

    @Override
    protected void onHit(MovingObjectPosition movingobjectposition) {
        super.onHit(movingobjectposition);
        if (movingobjectposition.getType() != MovingObjectPosition.EnumMovingObjectType.ENTITY || !this.ownedBy(((MovingObjectPositionEntity) movingobjectposition).getEntity())) {
            if (!this.level().isClientSide) {
                List<EntityLiving> list = this.level().<EntityLiving>getEntitiesOfClass(EntityLiving.class, this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D));
                EntityAreaEffectCloud entityareaeffectcloud = new EntityAreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
                Entity entity = this.getOwner();

                if (entity instanceof EntityLiving) {
                    entityareaeffectcloud.setOwner((EntityLiving) entity);
                }

                entityareaeffectcloud.setCustomParticle(Particles.DRAGON_BREATH);
                entityareaeffectcloud.setRadius(3.0F);
                entityareaeffectcloud.setDuration(600);
                entityareaeffectcloud.setRadiusPerTick((7.0F - entityareaeffectcloud.getRadius()) / (float) entityareaeffectcloud.getDuration());
                entityareaeffectcloud.setPotionDurationScale(0.25F);
                entityareaeffectcloud.addEffect(new MobEffect(MobEffects.INSTANT_DAMAGE, 1, 1));
                if (!list.isEmpty()) {
                    for (EntityLiving entityliving : list) {
                        double d0 = this.distanceToSqr((Entity) entityliving);

                        if (d0 < 16.0D) {
                            entityareaeffectcloud.setPos(entityliving.getX(), entityliving.getY(), entityliving.getZ());
                            break;
                        }
                    }
                }

                this.level().levelEvent(2006, this.blockPosition(), this.isSilent() ? -1 : 1);
                this.level().addFreshEntity(entityareaeffectcloud);
                this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
            }

        }
    }

    @Override
    protected ParticleParam getTrailParticle() {
        return Particles.DRAGON_BREATH;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }
}
