/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftBed extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.Bed, org.bukkit.block.data.Directional {

    public CraftBed() {
        super();
    }

    public CraftBed(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.type.CraftBed

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.data.type.Bed.Part> PART = getEnum(net.minecraft.world.level.block.BlockBed.class, "part", org.bukkit.block.data.type.Bed.Part.class);
    private static final net.minecraft.world.level.block.state.properties.BlockStateBoolean OCCUPIED = getBoolean(net.minecraft.world.level.block.BlockBed.class, "occupied");

    @Override
    public org.bukkit.block.data.type.Bed.Part getPart() {
        return get(PART);
    }

    @Override
    public void setPart(org.bukkit.block.data.type.Bed.Part part) {
        set(PART, part);
    }

    @Override
    public boolean isOccupied() {
        return get(OCCUPIED);
    }

    // org.bukkit.craftbukkit.block.data.CraftDirectional

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.BlockFace> FACING = getEnum(net.minecraft.world.level.block.BlockBed.class, "facing", org.bukkit.block.BlockFace.class);

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
