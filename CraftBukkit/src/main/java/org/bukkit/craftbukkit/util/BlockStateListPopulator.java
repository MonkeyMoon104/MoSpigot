package org.bukkit.craftbukkit.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.block.ITileEntity;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.IChunkProvider;
import net.minecraft.world.level.dimension.DimensionManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.WorldData;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.block.CraftBlockState;

public class BlockStateListPopulator extends DummyGeneratorAccess {
    private final GeneratorAccess world;
    private final Map<BlockPosition, IBlockData> dataMap = new HashMap<>();
    private final Map<BlockPosition, TileEntity> entityMap = new HashMap<>();
    private final LinkedHashMap<BlockPosition, CraftBlockState> list;

    public BlockStateListPopulator(GeneratorAccess world) {
        this(world, new LinkedHashMap<>());
    }

    private BlockStateListPopulator(GeneratorAccess world, LinkedHashMap<BlockPosition, CraftBlockState> list) {
        this.world = world;
        this.list = list;
    }

    @Override
    public IBlockData getBlockState(BlockPosition bp) {
        IBlockData blockData = dataMap.get(bp);
        return (blockData != null) ? blockData : world.getBlockState(bp);
    }

    @Override
    public Fluid getFluidState(BlockPosition bp) {
        IBlockData blockData = dataMap.get(bp);
        return (blockData != null) ? blockData.getFluidState() : world.getFluidState(bp);
    }

    @Override
    public TileEntity getBlockEntity(BlockPosition blockposition) {
        // The contains is important to check for null values
        if (entityMap.containsKey(blockposition)) {
            return entityMap.get(blockposition);
        }

        return world.getBlockEntity(blockposition);
    }

    @Override
    public boolean setBlock(BlockPosition position, IBlockData data, int flag) {
        position = position.immutable();
        // remove first to keep insertion order
        list.remove(position);

        dataMap.put(position, data);
        if (data.hasBlockEntity()) {
            entityMap.put(position, ((ITileEntity) data.getBlock()).newBlockEntity(position, data));
        } else {
            entityMap.put(position, null);
        }

        // use 'this' to ensure that the block state is the correct TileState
        CraftBlockState state = (CraftBlockState) CraftBlock.at(this, position).getState();
        state.setFlag(flag);
        // set world handle to ensure that updated calls are done to the world and not to this populator
        state.setWorldHandle(world);
        list.put(position, state);
        return true;
    }

    @Override
    public WorldServer getMinecraftWorld() {
        return world.getMinecraftWorld();
    }

    public void refreshTiles() {
        for (CraftBlockState state : list.values()) {
            if (state instanceof CraftBlockEntityState) {
                ((CraftBlockEntityState<?>) state).refreshSnapshot();
            }
        }
    }

    public void updateList() {
        for (BlockState state : list.values()) {
            state.update(true);
        }
    }

    public Set<BlockPosition> getBlocks() {
        return list.keySet();
    }

    public List<CraftBlockState> getList() {
        return new ArrayList<>(list.values());
    }

    public GeneratorAccess getWorld() {
        return world;
    }

    // For tree generation
    @Override
    public int getMinY() {
        return getWorld().getMinY();
    }

    @Override
    public int getHeight() {
        return getWorld().getHeight();
    }

    @Override
    public boolean isStateAtPosition(BlockPosition blockposition, Predicate<IBlockData> predicate) {
        return predicate.test(getBlockState(blockposition));
    }

    @Override
    public boolean isFluidAtPosition(BlockPosition bp, Predicate<Fluid> prdct) {
        return world.isFluidAtPosition(bp, prdct);
    }

    @Override
    public DimensionManager dimensionType() {
        return world.dimensionType();
    }

    @Override
    public IRegistryCustom registryAccess() {
        return world.registryAccess();
    }

    // Needed when a tree generates in water
    @Override
    public WorldData getLevelData() {
        return world.getLevelData();
    }

    @Override
    public long nextSubTickCount() {
        return world.nextSubTickCount();
    }

    // SPIGOT-7966: Needed for some tree generations
    @Override
    public RandomSource getRandom() {
        return world.getRandom();
    }

    // Needed for PaleMossDecorator
    @Override
    public IChunkProvider getChunkSource() {
        return world.getChunkSource();
    }
}
