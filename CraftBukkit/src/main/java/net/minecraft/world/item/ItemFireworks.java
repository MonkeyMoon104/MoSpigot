package net.minecraft.world.item;

import net.minecraft.core.EnumDirection;
import net.minecraft.core.IPosition;
import net.minecraft.core.dispenser.SourceBlock;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityFireworks;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.context.ItemActionContext;
import net.minecraft.world.level.World;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityUnleashEvent;
// CraftBukkit end

public class ItemFireworks extends Item implements ProjectileItem {

    public static final byte[] CRAFTABLE_DURATIONS = new byte[]{1, 2, 3};
    public static final double ROCKET_PLACEMENT_OFFSET = 0.15D;

    public ItemFireworks(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public EnumInteractionResult useOn(ItemActionContext itemactioncontext) {
        World world = itemactioncontext.getLevel();
        EntityHuman entityhuman = itemactioncontext.getPlayer();

        if (entityhuman != null && entityhuman.isFallFlying()) {
            return EnumInteractionResult.PASS;
        } else {
            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;
                ItemStack itemstack = itemactioncontext.getItemInHand();
                Vec3D vec3d = itemactioncontext.getClickLocation();
                EnumDirection enumdirection = itemactioncontext.getClickedFace();

                IProjectile.spawnProjectile(new EntityFireworks(world, itemactioncontext.getPlayer(), vec3d.x + (double) enumdirection.getStepX() * 0.15D, vec3d.y + (double) enumdirection.getStepY() * 0.15D, vec3d.z + (double) enumdirection.getStepZ() * 0.15D, itemstack), worldserver, itemstack);
                itemstack.shrink(1);
            }

            return EnumInteractionResult.SUCCESS;
        }
    }

    @Override
    public EnumInteractionResult use(World world, EntityHuman entityhuman, EnumHand enumhand) {
        if (entityhuman.isFallFlying()) {
            ItemStack itemstack = entityhuman.getItemInHand(enumhand);

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (entityhuman.dropAllLeashConnections((EntityHuman) null, EntityUnleashEvent.UnleashReason.FIREWORK)) { // CraftBukkit
                    world.playSound((Entity) null, (Entity) entityhuman, SoundEffects.LEAD_BREAK, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                }

                IProjectile.spawnProjectile(new EntityFireworks(world, itemstack, entityhuman), worldserver, itemstack);
                itemstack.consume(1, entityhuman);
                entityhuman.awardStat(StatisticList.ITEM_USED.get(this));
            }

            return EnumInteractionResult.SUCCESS;
        } else {
            return EnumInteractionResult.PASS;
        }
    }

    @Override
    public IProjectile asProjectile(World world, IPosition iposition, ItemStack itemstack, EnumDirection enumdirection) {
        return new EntityFireworks(world, itemstack.copyWithCount(1), iposition.x(), iposition.y(), iposition.z(), true);
    }

    @Override
    public ProjectileItem.a createDispenseConfig() {
        return ProjectileItem.a.builder().positionFunction(ItemFireworks::getEntityJustOutsideOfBlockPos).uncertainty(1.0F).power(0.5F).overrideDispenseEvent(1004).build();
    }

    private static Vec3D getEntityJustOutsideOfBlockPos(SourceBlock sourceblock, EnumDirection enumdirection) {
        return sourceblock.center().add((double) enumdirection.getStepX() * 0.5000099999997474D, (double) enumdirection.getStepY() * 0.5000099999997474D, (double) enumdirection.getStepZ() * 0.5000099999997474D);
    }
}
