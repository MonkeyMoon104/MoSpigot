/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftPortal extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.Orientable {

    public CraftPortal() {
        super();
    }

    public CraftPortal(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.CraftOrientable

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.Axis> AXIS = getEnum(net.minecraft.world.level.block.BlockPortal.class, "axis", org.bukkit.Axis.class);

    @Override
    public org.bukkit.Axis getAxis() {
        return get(AXIS);
    }

    @Override
    public void setAxis(org.bukkit.Axis axis) {
        set(AXIS, axis);
    }

    @Override
    public java.util.Set<org.bukkit.Axis> getAxes() {
        return getValues(AXIS);
    }
}
