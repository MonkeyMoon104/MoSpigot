/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftTallSeagrass extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.Bisected {

    public CraftTallSeagrass() {
        super();
    }

    public CraftTallSeagrass(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.CraftBisected

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.data.Bisected.Half> HALF = getEnum(net.minecraft.world.level.block.TallSeagrassBlock.class, "half", org.bukkit.block.data.Bisected.Half.class);

    @Override
    public org.bukkit.block.data.Bisected.Half getHalf() {
        return get(HALF);
    }

    @Override
    public void setHalf(org.bukkit.block.data.Bisected.Half half) {
        set(HALF, half);
    }
}
