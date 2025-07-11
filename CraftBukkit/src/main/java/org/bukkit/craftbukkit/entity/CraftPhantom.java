package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.monster.EntityPhantom;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Phantom;

public class CraftPhantom extends CraftMob implements Phantom, CraftEnemy {

    public CraftPhantom(CraftServer server, EntityPhantom entity) {
        super(server, entity);
    }

    @Override
    public EntityPhantom getHandle() {
        return (EntityPhantom) super.getHandle();
    }

    @Override
    public int getSize() {
        return getHandle().getPhantomSize();
    }

    @Override
    public void setSize(int sz) {
        getHandle().setPhantomSize(sz);
    }

    @Override
    public String toString() {
        return "CraftPhantom";
    }
}
