/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftStonecutter extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.Directional {

    public CraftStonecutter() {
        super();
    }

    public CraftStonecutter(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.CraftDirectional

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.BlockFace> FACING = getEnum(net.minecraft.world.level.block.BlockStonecutter.class, "facing", org.bukkit.block.BlockFace.class);

    @Override
    public org.bukkit.block.BlockFace getFacing() {
        return get(FACING);
    }

    @Override
    public void setFacing(org.bukkit.block.BlockFace facing) {
        set(FACING, facing);
    }

    @Override
    public java.util.Set<org.bukkit.block.BlockFace> getFaces() {
        return getValues(FACING);
    }
}
