/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftStructure extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.StructureBlock {

    public CraftStructure() {
        super();
    }

    public CraftStructure(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.type.CraftStructureBlock

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.data.type.StructureBlock.Mode> MODE = getEnum(net.minecraft.world.level.block.BlockStructure.class, "mode", org.bukkit.block.data.type.StructureBlock.Mode.class);

    @Override
    public org.bukkit.block.data.type.StructureBlock.Mode getMode() {
        return get(MODE);
    }

    @Override
    public void setMode(org.bukkit.block.data.type.StructureBlock.Mode mode) {
        set(MODE, mode);
    }
}
