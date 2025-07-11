package net.minecraft.world.entity.projectile;

import net.minecraft.server.level.WorldServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionEntity;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EntityLargeFireball extends EntityFireballFireball {

    private static final byte DEFAULT_EXPLOSION_POWER = 1;
    public int explosionPower = 1;

    public EntityLargeFireball(EntityTypes<? extends EntityLargeFireball> entitytypes, World world) {
        super(entitytypes, world);
        isIncendiary = (world instanceof WorldServer worldserver) && worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit
    }

    public EntityLargeFireball(World world, EntityLiving entityliving, Vec3D vec3d, int i) {
        super(EntityTypes.FIREBALL, entityliving, vec3d, world);
        this.explosionPower = i;
        isIncendiary = (world instanceof WorldServer worldserver) && worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); // CraftBukkit
    }

    @Override
    protected void onHit(MovingObjectPosition movingobjectposition) {
        super.onHit(movingobjectposition);
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            boolean flag = worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);

            // CraftBukkit start - fire ExplosionPrimeEvent
            ExplosionPrimeEvent event = new ExplosionPrimeEvent((org.bukkit.entity.Explosive) this.getBukkitEntity());
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                // give 'this' instead of (Entity) null so we know what causes the damage
                this.level().explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), World.a.MOB);
            }
            // CraftBukkit end
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    protected void onHitEntity(MovingObjectPositionEntity movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            Entity entity = movingobjectpositionentity.getEntity();
            Entity entity1 = this.getOwner();
            DamageSource damagesource = this.damageSources().fireball(this, entity1);

            entity.hurtServer(worldserver, damagesource, 6.0F);
            EnchantmentManager.doPostAttackEffects(worldserver, entity, damagesource);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putByte("ExplosionPower", (byte) this.explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        // CraftBukkit - set bukkitYield when setting explosionpower
        bukkitYield = this.explosionPower = valueinput.getByteOr("ExplosionPower", (byte) 1);
    }
}
