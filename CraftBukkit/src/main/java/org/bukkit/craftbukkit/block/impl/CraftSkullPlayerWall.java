/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftSkullPlayerWall extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.WallSkull, org.bukkit.block.data.Directional, org.bukkit.block.data.Powerable {

    public CraftSkullPlayerWall() {
        super();
    }

    public CraftSkullPlayerWall(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.CraftDirectional

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.BlockFace> FACING = getEnum(net.minecraft.world.level.block.BlockSkullPlayerWall.class, "facing", org.bukkit.block.BlockFace.class);

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

    // org.bukkit.craftbukkit.block.data.CraftPowerable

    private static final net.minecraft.world.level.block.state.properties.BlockStateBoolean POWERED = getBoolean(net.minecraft.world.level.block.BlockSkullPlayerWall.class, "powered");

    @Override
    public boolean isPowered() {
        return get(POWERED);
    }

    @Override
    public void setPowered(boolean powered) {
        set(POWERED, powered);
    }
}
