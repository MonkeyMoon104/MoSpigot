package org.bukkit.craftbukkit.block;

import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.level.block.entity.TileEntityConduit;
import net.minecraft.world.phys.AxisAlignedBB;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Conduit;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

public class CraftConduit extends CraftBlockEntityState<TileEntityConduit> implements Conduit {

    public CraftConduit(World world, TileEntityConduit tileEntity) {
        super(world, tileEntity);
    }

    protected CraftConduit(CraftConduit state, Location location) {
        super(state, location);
    }

    @Override
    public CraftConduit copy() {
        return new CraftConduit(this, null);
    }

    @Override
    public CraftConduit copy(Location location) {
        return new CraftConduit(this, location);
    }

    @Override
    public boolean isActive() {
        ensureNoWorldGeneration();
        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        return conduit != null && conduit.isActive();
    }

    @Override
    public boolean isHunting() {
        ensureNoWorldGeneration();
        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        return conduit != null && conduit.isHunting();
    }

    @Override
    public Collection<Block> getFrameBlocks() {
        ensureNoWorldGeneration();
        Collection<Block> blocks = new ArrayList<>();

        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        if (conduit != null) {
            for (BlockPosition position : conduit.effectBlocks) {
                blocks.add(CraftBlock.at(getWorldHandle(), position));
            }
        }

        return blocks;
    }

    @Override
    public int getFrameBlockCount() {
        ensureNoWorldGeneration();
        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        return (conduit != null) ? conduit.effectBlocks.size() : 0;
    }

    @Override
    public int getRange() {
        ensureNoWorldGeneration();
        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        return (conduit != null) ? TileEntityConduit.getRange(conduit.effectBlocks) : 0;
    }

    @Override
    public boolean setTarget(LivingEntity target) {
        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        if (conduit == null) {
            return false;
        }

        EntityReference<EntityLiving> currentTarget = conduit.destroyTarget;

        if (target == null) {
            if (currentTarget == null) {
                return false;
            }

            conduit.destroyTarget = null;
        } else {
            EntityLiving newTarget = ((CraftLivingEntity) target).getHandle();
            if (currentTarget != null && currentTarget.matches(newTarget)) {
                return false;
            }

            conduit.destroyTarget = new EntityReference<>(newTarget);
        }

        TileEntityConduit.updateAndAttackTarget((WorldServer) conduit.getLevel(), getPosition(), data, conduit, conduit.effectBlocks.size() >= 42, false);
        return true;
    }

    @Override
    public LivingEntity getTarget() {
        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        if (conduit == null) {
            return null;
        }

        EntityLiving nmsEntity = EntityReference.get(conduit.destroyTarget, conduit.getLevel(), EntityLiving.class);
        return (nmsEntity != null) ? (LivingEntity) nmsEntity.getBukkitEntity() : null;
    }

    @Override
    public boolean hasTarget() {
        TileEntityConduit conduit = (TileEntityConduit) getTileEntityFromWorld();
        EntityLiving destroyTarget = (conduit != null) ? EntityReference.get(conduit.destroyTarget, conduit.getLevel(), EntityLiving.class) : null;
        return destroyTarget != null && destroyTarget.isAlive();
    }

    @Override
    public BoundingBox getHuntingArea() {
        AxisAlignedBB bounds = TileEntityConduit.getDestroyRangeAABB(getPosition());
        return new BoundingBox(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
    }
}
