package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.RayTrace;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.MovingObjectPositionEntity;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityFireworks extends IProjectile implements ItemSupplier {

    public static final DataWatcherObject<ItemStack> DATA_ID_FIREWORKS_ITEM = DataWatcher.<ItemStack>defineId(EntityFireworks.class, DataWatcherRegistry.ITEM_STACK);
    private static final DataWatcherObject<OptionalInt> DATA_ATTACHED_TO_TARGET = DataWatcher.<OptionalInt>defineId(EntityFireworks.class, DataWatcherRegistry.OPTIONAL_UNSIGNED_INT);
    public static final DataWatcherObject<Boolean> DATA_SHOT_AT_ANGLE = DataWatcher.<Boolean>defineId(EntityFireworks.class, DataWatcherRegistry.BOOLEAN);
    private static final int DEFAULT_LIFE = 0;
    private static final int DEFAULT_LIFE_TIME = 0;
    private static final boolean DEFAULT_SHOT_AT_ANGLE = false;
    public int life;
    public int lifetime;
    @Nullable
    public EntityLiving attachedToEntity;

    public EntityFireworks(EntityTypes<? extends EntityFireworks> entitytypes, World world) {
        super(entitytypes, world);
        this.life = 0;
        this.lifetime = 0;
    }

    public EntityFireworks(World world, double d0, double d1, double d2, ItemStack itemstack) {
        super(EntityTypes.FIREWORK_ROCKET, world);
        this.life = 0;
        this.lifetime = 0;
        this.life = 0;
        this.setPos(d0, d1, d2);
        this.entityData.set(EntityFireworks.DATA_ID_FIREWORKS_ITEM, itemstack.copy());
        int i = 1;
        Fireworks fireworks = (Fireworks) itemstack.get(DataComponents.FIREWORKS);

        if (fireworks != null) {
            i += fireworks.flightDuration();
        }

        this.setDeltaMovement(this.random.triangle(0.0D, 0.002297D), 0.05D, this.random.triangle(0.0D, 0.002297D));
        this.lifetime = 10 * i + this.random.nextInt(6) + this.random.nextInt(7);
    }

    public EntityFireworks(World world, @Nullable Entity entity, double d0, double d1, double d2, ItemStack itemstack) {
        this(world, d0, d1, d2, itemstack);
        this.setOwner(entity);
    }

    public EntityFireworks(World world, ItemStack itemstack, EntityLiving entityliving) {
        this(world, entityliving, entityliving.getX(), entityliving.getY(), entityliving.getZ(), itemstack);
        this.entityData.set(EntityFireworks.DATA_ATTACHED_TO_TARGET, OptionalInt.of(entityliving.getId()));
        this.attachedToEntity = entityliving;
    }

    public EntityFireworks(World world, ItemStack itemstack, double d0, double d1, double d2, boolean flag) {
        this(world, d0, d1, d2, itemstack);
        this.entityData.set(EntityFireworks.DATA_SHOT_AT_ANGLE, flag);
    }

    public EntityFireworks(World world, ItemStack itemstack, Entity entity, double d0, double d1, double d2, boolean flag) {
        this(world, itemstack, d0, d1, d2, flag);
        this.setOwner(entity);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(EntityFireworks.DATA_ID_FIREWORKS_ITEM, getDefaultItem());
        datawatcher_a.define(EntityFireworks.DATA_ATTACHED_TO_TARGET, OptionalInt.empty());
        datawatcher_a.define(EntityFireworks.DATA_SHOT_AT_ANGLE, false);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d0) {
        return d0 < 4096.0D && !this.isAttachedToEntity();
    }

    @Override
    public boolean shouldRender(double d0, double d1, double d2) {
        return super.shouldRender(d0, d1, d2) && !this.isAttachedToEntity();
    }

    @Override
    public void tick() {
        super.tick();
        MovingObjectPosition movingobjectposition;

        if (this.isAttachedToEntity()) {
            if (this.attachedToEntity == null) {
                ((OptionalInt) this.entityData.get(EntityFireworks.DATA_ATTACHED_TO_TARGET)).ifPresent((i) -> {
                    Entity entity = this.level().getEntity(i);

                    if (entity instanceof EntityLiving) {
                        this.attachedToEntity = (EntityLiving) entity;
                    }

                });
            }

            if (this.attachedToEntity != null) {
                Vec3D vec3d;

                if (this.attachedToEntity.isFallFlying()) {
                    Vec3D vec3d1 = this.attachedToEntity.getLookAngle();
                    double d0 = 1.5D;
                    double d1 = 0.1D;
                    Vec3D vec3d2 = this.attachedToEntity.getDeltaMovement();

                    this.attachedToEntity.setDeltaMovement(vec3d2.add(vec3d1.x * 0.1D + (vec3d1.x * 1.5D - vec3d2.x) * 0.5D, vec3d1.y * 0.1D + (vec3d1.y * 1.5D - vec3d2.y) * 0.5D, vec3d1.z * 0.1D + (vec3d1.z * 1.5D - vec3d2.z) * 0.5D));
                    vec3d = this.attachedToEntity.getHandHoldingItemAngle(Items.FIREWORK_ROCKET);
                } else {
                    vec3d = Vec3D.ZERO;
                }

                this.setPos(this.attachedToEntity.getX() + vec3d.x, this.attachedToEntity.getY() + vec3d.y, this.attachedToEntity.getZ() + vec3d.z);
                this.setDeltaMovement(this.attachedToEntity.getDeltaMovement());
            }

            movingobjectposition = ProjectileHelper.getHitResultOnMoveVector(this, this::canHitEntity);
        } else {
            if (!this.isShotAtAngle()) {
                double d2 = this.horizontalCollision ? 1.0D : 1.15D;

                this.setDeltaMovement(this.getDeltaMovement().multiply(d2, 1.0D, d2).add(0.0D, 0.04D, 0.0D));
            }

            Vec3D vec3d3 = this.getDeltaMovement();

            movingobjectposition = ProjectileHelper.getHitResultOnMoveVector(this, this::canHitEntity);
            this.move(EnumMoveType.SELF, vec3d3);
            this.applyEffectsFromBlocks();
            this.setDeltaMovement(vec3d3);
        }

        if (!this.noPhysics && this.isAlive() && movingobjectposition.getType() != MovingObjectPosition.EnumMovingObjectType.MISS) {
            this.preHitTargetOrDeflectSelf(movingobjectposition); // CraftBukkit - projectile hit event
            this.hasImpulse = true;
        }

        this.updateRotation();
        if (this.life == 0 && !this.isSilent()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.FIREWORK_ROCKET_LAUNCH, SoundCategory.AMBIENT, 3.0F, 1.0F);
        }

        ++this.life;
        if (this.level().isClientSide && this.life % 2 < 2) {
            this.level().addParticle(Particles.FIREWORK, this.getX(), this.getY(), this.getZ(), this.random.nextGaussian() * 0.05D, -this.getDeltaMovement().y * 0.5D, this.random.nextGaussian() * 0.05D);
        }

        if (this.life > this.lifetime) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                // CraftBukkit start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                    this.explode(worldserver);
                }
                // CraftBukkit end
            }
        }

    }

    private void explode(WorldServer worldserver) {
        worldserver.broadcastEntityEvent(this, (byte) 17);
        this.gameEvent(GameEvent.EXPLODE, this.getOwner());
        this.dealExplosionDamage(worldserver);
        this.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    protected void onHitEntity(MovingObjectPositionEntity movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            // CraftBukkit start
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                this.explode(worldserver);
            }
            // CraftBukkit end
        }

    }

    @Override
    protected void onHitBlock(MovingObjectPositionBlock movingobjectpositionblock) {
        BlockPosition blockposition = new BlockPosition(movingobjectpositionblock.getBlockPos());

        this.level().getBlockState(blockposition).entityInside(this.level(), blockposition, this, InsideBlockEffectApplier.NOOP);
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.hasExplosion()) {
                // CraftBukkit start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callFireworkExplodeEvent(this).isCancelled()) {
                    this.explode(worldserver);
                }
                // CraftBukkit end
            }
        }

        super.onHitBlock(movingobjectpositionblock);
    }

    private boolean hasExplosion() {
        return !this.getExplosions().isEmpty();
    }

    private void dealExplosionDamage(WorldServer worldserver) {
        float f = 0.0F;
        List<FireworkExplosion> list = this.getExplosions();

        if (!list.isEmpty()) {
            f = 5.0F + (float) (list.size() * 2);
        }

        if (f > 0.0F) {
            if (this.attachedToEntity != null) {
                this.attachedToEntity.hurtServer(worldserver, this.damageSources().fireworks(this, this.getOwner()), 5.0F + (float) (list.size() * 2));
            }

            double d0 = 5.0D;
            Vec3D vec3d = this.position();

            for (EntityLiving entityliving : this.level().getEntitiesOfClass(EntityLiving.class, this.getBoundingBox().inflate(5.0D))) {
                if (entityliving != this.attachedToEntity && this.distanceToSqr((Entity) entityliving) <= 25.0D) {
                    boolean flag = false;

                    for (int i = 0; i < 2; ++i) {
                        Vec3D vec3d1 = new Vec3D(entityliving.getX(), entityliving.getY(0.5D * (double) i), entityliving.getZ());
                        MovingObjectPosition movingobjectposition = this.level().clip(new RayTrace(vec3d, vec3d1, RayTrace.BlockCollisionOption.COLLIDER, RayTrace.FluidCollisionOption.NONE, this));

                        if (movingobjectposition.getType() == MovingObjectPosition.EnumMovingObjectType.MISS) {
                            flag = true;
                            break;
                        }
                    }

                    if (flag) {
                        float f1 = f * (float) Math.sqrt((5.0D - (double) this.distanceTo(entityliving)) / 5.0D);

                        entityliving.hurtServer(worldserver, this.damageSources().fireworks(this, this.getOwner()), f1);
                    }
                }
            }
        }

    }

    private boolean isAttachedToEntity() {
        return ((OptionalInt) this.entityData.get(EntityFireworks.DATA_ATTACHED_TO_TARGET)).isPresent();
    }

    public boolean isShotAtAngle() {
        return (Boolean) this.entityData.get(EntityFireworks.DATA_SHOT_AT_ANGLE);
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 17 && this.level().isClientSide) {
            Vec3D vec3d = this.getDeltaMovement();

            this.level().createFireworks(this.getX(), this.getY(), this.getZ(), vec3d.x, vec3d.y, vec3d.z, this.getExplosions());
        }

        super.handleEntityEvent(b0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("Life", this.life);
        valueoutput.putInt("LifeTime", this.lifetime);
        valueoutput.store("FireworksItem", ItemStack.CODEC, this.getItem());
        valueoutput.putBoolean("ShotAtAngle", (Boolean) this.entityData.get(EntityFireworks.DATA_SHOT_AT_ANGLE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.life = valueinput.getIntOr("Life", 0);
        this.lifetime = valueinput.getIntOr("LifeTime", 0);
        this.entityData.set(EntityFireworks.DATA_ID_FIREWORKS_ITEM, (ItemStack) valueinput.read("FireworksItem", ItemStack.CODEC).orElse(getDefaultItem()));
        this.entityData.set(EntityFireworks.DATA_SHOT_AT_ANGLE, valueinput.getBooleanOr("ShotAtAngle", false));
    }

    private List<FireworkExplosion> getExplosions() {
        ItemStack itemstack = (ItemStack) this.entityData.get(EntityFireworks.DATA_ID_FIREWORKS_ITEM);
        Fireworks fireworks = (Fireworks) itemstack.get(DataComponents.FIREWORKS);

        return fireworks != null ? fireworks.explosions() : List.of();
    }

    @Override
    public ItemStack getItem() {
        return (ItemStack) this.entityData.get(EntityFireworks.DATA_ID_FIREWORKS_ITEM);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    private static ItemStack getDefaultItem() {
        return new ItemStack(Items.FIREWORK_ROCKET);
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(EntityLiving entityliving, DamageSource damagesource) {
        double d0 = entityliving.position().x - this.position().x;
        double d1 = entityliving.position().z - this.position().z;

        return DoubleDoubleImmutablePair.of(d0, d1);
    }
}
