/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftRedstoneComparator extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.Comparator, org.bukkit.block.data.Directional, org.bukkit.block.data.Powerable {

    public CraftRedstoneComparator() {
        super();
    }

    public CraftRedstoneComparator(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.type.CraftComparator

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.data.type.Comparator.Mode> MODE = getEnum(net.minecraft.world.level.block.BlockRedstoneComparator.class, "mode", org.bukkit.block.data.type.Comparator.Mode.class);

    @Override
    public org.bukkit.block.data.type.Comparator.Mode getMode() {
        return get(MODE);
    }

    @Override
    public void setMode(org.bukkit.block.data.type.Comparator.Mode mode) {
        set(MODE, mode);
    }

    // org.bukkit.craftbukkit.block.data.CraftDirectional

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.BlockFace> FACING = getEnum(net.minecraft.world.level.block.BlockRedstoneComparator.class, "facing", org.bukkit.block.BlockFace.class);

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

    private static final net.minecraft.world.level.block.state.properties.BlockStateBoolean POWERED = getBoolean(net.minecraft.world.level.block.BlockRedstoneComparator.class, "powered");

    @Override
    public boolean isPowered() {
        return get(POWERED);
    }

    @Override
    public void setPowered(boolean powered) {
        set(POWERED, powered);
    }
}
