package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.HappyGhast;

public class CraftHappyGhast extends CraftAnimals implements HappyGhast {

    public CraftHappyGhast(CraftServer server, net.minecraft.world.entity.animal.HappyGhast entity) {
        super(server, entity);
    }

    @Override
    public net.minecraft.world.entity.animal.HappyGhast getHandle() {
        return (net.minecraft.world.entity.animal.HappyGhast) entity;
    }

    @Override
    public String toString() {
        return "CraftHappyGhast";
    }
}
