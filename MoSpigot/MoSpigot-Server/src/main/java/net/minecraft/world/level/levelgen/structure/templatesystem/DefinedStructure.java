package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryBlockID;
import net.minecraft.data.worldgen.WorldGenFeaturePieces;
import net.minecraft.nbt.GameProfileSerializer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.decoration.EntityPainting;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.BlockAccessAir;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockJigsaw;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnumBlockMirror;
import net.minecraft.world.level.block.EnumBlockRotation;
import net.minecraft.world.level.block.IFluidContainer;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityJigsaw;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.levelgen.structure.StructureBoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.WorldGenFeatureDefinedStructurePoolTemplate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.VoxelShapeBitSet;
import net.minecraft.world.phys.shapes.VoxelShapeDiscrete;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.nbt.NBTBase;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
// CraftBukkit end

public class DefinedStructure {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String PALETTE_TAG = "palette";
    public static final String PALETTE_LIST_TAG = "palettes";
    public static final String ENTITIES_TAG = "entities";
    public static final String BLOCKS_TAG = "blocks";
    public static final String BLOCK_TAG_POS = "pos";
    public static final String BLOCK_TAG_STATE = "state";
    public static final String BLOCK_TAG_NBT = "nbt";
    public static final String ENTITY_TAG_POS = "pos";
    public static final String ENTITY_TAG_BLOCKPOS = "blockPos";
    public static final String ENTITY_TAG_NBT = "nbt";
    public static final String SIZE_TAG = "size";
    public final List<DefinedStructure.b> palettes = Lists.newArrayList();
    public final List<DefinedStructure.EntityInfo> entityInfoList = Lists.newArrayList();
    private BaseBlockPosition size;
    private String author;

    // CraftBukkit start - data containers
    private static final CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new CraftPersistentDataTypeRegistry();
    public CraftPersistentDataContainer persistentDataContainer = new CraftPersistentDataContainer(DATA_TYPE_REGISTRY);
    // CraftBukkit end

    public DefinedStructure() {
        this.size = BaseBlockPosition.ZERO;
        this.author = "?";
    }

    public BaseBlockPosition getSize() {
        return this.size;
    }

    public void setAuthor(String s) {
        this.author = s;
    }

    public String getAuthor() {
        return this.author;
    }

    public void fillFromWorld(World world, BlockPosition blockposition, BaseBlockPosition baseblockposition, boolean flag, List<Block> list) {
        if (baseblockposition.getX() >= 1 && baseblockposition.getY() >= 1 && baseblockposition.getZ() >= 1) {
            BlockPosition blockposition1 = blockposition.offset(baseblockposition).offset(-1, -1, -1);
            List<DefinedStructure.BlockInfo> list1 = Lists.newArrayList();
            List<DefinedStructure.BlockInfo> list2 = Lists.newArrayList();
            List<DefinedStructure.BlockInfo> list3 = Lists.newArrayList();
            BlockPosition blockposition2 = new BlockPosition(Math.min(blockposition.getX(), blockposition1.getX()), Math.min(blockposition.getY(), blockposition1.getY()), Math.min(blockposition.getZ(), blockposition1.getZ()));
            BlockPosition blockposition3 = new BlockPosition(Math.max(blockposition.getX(), blockposition1.getX()), Math.max(blockposition.getY(), blockposition1.getY()), Math.max(blockposition.getZ(), blockposition1.getZ()));

            this.size = baseblockposition;

            try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(DefinedStructure.LOGGER)) {
                for (BlockPosition blockposition4 : BlockPosition.betweenClosed(blockposition2, blockposition3)) {
                    BlockPosition blockposition5 = blockposition4.subtract(blockposition2);
                    IBlockData iblockdata = world.getBlockState(blockposition4);
                    Stream<Block> stream = list.stream(); // CraftBukkit - decompile error

                    Objects.requireNonNull(iblockdata);
                    if (!stream.anyMatch(iblockdata::is)) {
                        TileEntity tileentity = world.getBlockEntity(blockposition4);
                        DefinedStructure.BlockInfo definedstructure_blockinfo;

                        if (tileentity != null) {
                            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_j, world.registryAccess());

                            tileentity.saveWithId(tagvalueoutput);
                            definedstructure_blockinfo = new DefinedStructure.BlockInfo(blockposition5, iblockdata, tagvalueoutput.buildResult());
                        } else {
                            definedstructure_blockinfo = new DefinedStructure.BlockInfo(blockposition5, iblockdata, (NBTTagCompound) null);
                        }

                        addToLists(definedstructure_blockinfo, list1, list2, list3);
                    }
                }

                List<DefinedStructure.BlockInfo> list4 = buildInfoList(list1, list2, list3);

                this.palettes.clear();
                this.palettes.add(new DefinedStructure.b(list4));
                if (flag) {
                    this.fillEntityList(world, blockposition2, blockposition3, problemreporter_j);
                } else {
                    this.entityInfoList.clear();
                }
            }

        }
    }

    private static void addToLists(DefinedStructure.BlockInfo definedstructure_blockinfo, List<DefinedStructure.BlockInfo> list, List<DefinedStructure.BlockInfo> list1, List<DefinedStructure.BlockInfo> list2) {
        if (definedstructure_blockinfo.nbt != null) {
            list1.add(definedstructure_blockinfo);
        } else if (!definedstructure_blockinfo.state.getBlock().hasDynamicShape() && definedstructure_blockinfo.state.isCollisionShapeFullBlock(BlockAccessAir.INSTANCE, BlockPosition.ZERO)) {
            list.add(definedstructure_blockinfo);
        } else {
            list2.add(definedstructure_blockinfo);
        }

    }

    private static List<DefinedStructure.BlockInfo> buildInfoList(List<DefinedStructure.BlockInfo> list, List<DefinedStructure.BlockInfo> list1, List<DefinedStructure.BlockInfo> list2) {
        Comparator<DefinedStructure.BlockInfo> comparator = Comparator.<DefinedStructure.BlockInfo>comparingInt((definedstructure_blockinfo) -> { // CraftBukkit - decompile error
            return definedstructure_blockinfo.pos.getY();
        }).thenComparingInt((definedstructure_blockinfo) -> {
            return definedstructure_blockinfo.pos.getX();
        }).thenComparingInt((definedstructure_blockinfo) -> {
            return definedstructure_blockinfo.pos.getZ();
        });

        list.sort(comparator);
        list2.sort(comparator);
        list1.sort(comparator);
        List<DefinedStructure.BlockInfo> list3 = Lists.newArrayList();

        list3.addAll(list);
        list3.addAll(list2);
        list3.addAll(list1);
        return list3;
    }

    private void fillEntityList(World world, BlockPosition blockposition, BlockPosition blockposition1, ProblemReporter problemreporter) {
        List<Entity> list = world.<Entity>getEntitiesOfClass(Entity.class, AxisAlignedBB.encapsulatingFullBlocks(blockposition, blockposition1), (entity) -> {
            return !(entity instanceof EntityHuman);
        });

        this.entityInfoList.clear();

        for (Entity entity : list) {
            Vec3D vec3d = new Vec3D(entity.getX() - (double) blockposition.getX(), entity.getY() - (double) blockposition.getY(), entity.getZ() - (double) blockposition.getZ());
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter.forChild(entity.problemPath()), entity.registryAccess());

            entity.save(tagvalueoutput);
            BlockPosition blockposition2;

            if (entity instanceof EntityPainting entitypainting) {
                blockposition2 = entitypainting.getPos().subtract(blockposition);
            } else {
                blockposition2 = BlockPosition.containing(vec3d);
            }

            this.entityInfoList.add(new DefinedStructure.EntityInfo(vec3d, blockposition2, tagvalueoutput.buildResult().copy()));
        }

    }

    public List<DefinedStructure.BlockInfo> filterBlocks(BlockPosition blockposition, DefinedStructureInfo definedstructureinfo, Block block) {
        return this.filterBlocks(blockposition, definedstructureinfo, block, true);
    }

    public List<DefinedStructure.a> getJigsaws(BlockPosition blockposition, EnumBlockRotation enumblockrotation) {
        if (this.palettes.isEmpty()) {
            return new ArrayList();
        } else {
            DefinedStructureInfo definedstructureinfo = (new DefinedStructureInfo()).setRotation(enumblockrotation);
            List<DefinedStructure.a> list = definedstructureinfo.getRandomPalette(this.palettes, blockposition).jigsaws();
            List<DefinedStructure.a> list1 = new ArrayList(list.size());

            for (DefinedStructure.a definedstructure_a : list) {
                DefinedStructure.BlockInfo definedstructure_blockinfo = definedstructure_a.info;

                list1.add(definedstructure_a.withInfo(new DefinedStructure.BlockInfo(calculateRelativePosition(definedstructureinfo, definedstructure_blockinfo.pos()).offset(blockposition), definedstructure_blockinfo.state.rotate(definedstructureinfo.getRotation()), definedstructure_blockinfo.nbt)));
            }

            return list1;
        }
    }

    public ObjectArrayList<DefinedStructure.BlockInfo> filterBlocks(BlockPosition blockposition, DefinedStructureInfo definedstructureinfo, Block block, boolean flag) {
        ObjectArrayList<DefinedStructure.BlockInfo> objectarraylist = new ObjectArrayList();
        StructureBoundingBox structureboundingbox = definedstructureinfo.getBoundingBox();

        if (this.palettes.isEmpty()) {
            return objectarraylist;
        } else {
            for (DefinedStructure.BlockInfo definedstructure_blockinfo : definedstructureinfo.getRandomPalette(this.palettes, blockposition).blocks(block)) {
                BlockPosition blockposition1 = flag ? calculateRelativePosition(definedstructureinfo, definedstructure_blockinfo.pos).offset(blockposition) : definedstructure_blockinfo.pos;

                if (structureboundingbox == null || structureboundingbox.isInside(blockposition1)) {
                    objectarraylist.add(new DefinedStructure.BlockInfo(blockposition1, definedstructure_blockinfo.state.rotate(definedstructureinfo.getRotation()), definedstructure_blockinfo.nbt));
                }
            }

            return objectarraylist;
        }
    }

    public BlockPosition calculateConnectedPosition(DefinedStructureInfo definedstructureinfo, BlockPosition blockposition, DefinedStructureInfo definedstructureinfo1, BlockPosition blockposition1) {
        BlockPosition blockposition2 = calculateRelativePosition(definedstructureinfo, blockposition);
        BlockPosition blockposition3 = calculateRelativePosition(definedstructureinfo1, blockposition1);

        return blockposition2.subtract(blockposition3);
    }

    public static BlockPosition calculateRelativePosition(DefinedStructureInfo definedstructureinfo, BlockPosition blockposition) {
        return transform(blockposition, definedstructureinfo.getMirror(), definedstructureinfo.getRotation(), definedstructureinfo.getRotationPivot());
    }

    public boolean placeInWorld(WorldAccess worldaccess, BlockPosition blockposition, BlockPosition blockposition1, DefinedStructureInfo definedstructureinfo, RandomSource randomsource, int i) {
        if (this.palettes.isEmpty()) {
            return false;
        } else {
            // CraftBukkit start
            // We only want the TransformerGeneratorAccess at certain locations because in here are many "block update" calls that shouldn't be transformed
            WorldAccess wrappedAccess = worldaccess;
            org.bukkit.craftbukkit.util.CraftStructureTransformer structureTransformer = null;
            if (wrappedAccess instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
                worldaccess = transformerAccess.getHandle();
                structureTransformer = transformerAccess.getStructureTransformer();
                // The structureTransformer is not needed if we can not transform blocks therefore we can save a little bit of performance doing this
                if (structureTransformer != null && !structureTransformer.canTransformBlocks()) {
                    structureTransformer = null;
                }
            }
            // CraftBukkit end
            List<DefinedStructure.BlockInfo> list = definedstructureinfo.getRandomPalette(this.palettes, blockposition).blocks();

            if ((!list.isEmpty() || !definedstructureinfo.isIgnoreEntities() && !this.entityInfoList.isEmpty()) && this.size.getX() >= 1 && this.size.getY() >= 1 && this.size.getZ() >= 1) {
                StructureBoundingBox structureboundingbox = definedstructureinfo.getBoundingBox();
                List<BlockPosition> list1 = Lists.newArrayListWithCapacity(definedstructureinfo.shouldApplyWaterlogging() ? list.size() : 0);
                List<BlockPosition> list2 = Lists.newArrayListWithCapacity(definedstructureinfo.shouldApplyWaterlogging() ? list.size() : 0);
                List<Pair<BlockPosition, NBTTagCompound>> list3 = Lists.newArrayListWithCapacity(list.size());
                int j = Integer.MAX_VALUE;
                int k = Integer.MAX_VALUE;
                int l = Integer.MAX_VALUE;
                int i1 = Integer.MIN_VALUE;
                int j1 = Integer.MIN_VALUE;
                int k1 = Integer.MIN_VALUE;
                List<DefinedStructure.BlockInfo> list4 = processBlockInfos(worldaccess, blockposition, blockposition1, definedstructureinfo, list);

                try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(DefinedStructure.LOGGER)) {
                    for (DefinedStructure.BlockInfo definedstructure_blockinfo : list4) {
                        BlockPosition blockposition2 = definedstructure_blockinfo.pos;

                        if (structureboundingbox == null || structureboundingbox.isInside(blockposition2)) {
                            Fluid fluid = definedstructureinfo.shouldApplyWaterlogging() ? worldaccess.getFluidState(blockposition2) : null;
                            IBlockData iblockdata = definedstructure_blockinfo.state.mirror(definedstructureinfo.getMirror()).rotate(definedstructureinfo.getRotation());

                            if (definedstructure_blockinfo.nbt != null) {
                                worldaccess.setBlock(blockposition2, Blocks.BARRIER.defaultBlockState(), 820);
                            }
                            // CraftBukkit start
                            if (structureTransformer != null) {
                                org.bukkit.craftbukkit.block.CraftBlockState craftBlockState = (org.bukkit.craftbukkit.block.CraftBlockState) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(worldaccess, blockposition2, iblockdata, null);
                                if (definedstructure_blockinfo.nbt != null && craftBlockState instanceof org.bukkit.craftbukkit.block.CraftBlockEntityState<?> entityState) {
                                    entityState.loadData(definedstructure_blockinfo.nbt);
                                    if (craftBlockState instanceof org.bukkit.craftbukkit.block.CraftLootable<?> craftLootable) {
                                        craftLootable.setSeed(randomsource.nextLong());
                                    }
                                }
                                craftBlockState = structureTransformer.transformCraftState(craftBlockState);
                                iblockdata = craftBlockState.getHandle();
                                definedstructure_blockinfo = new DefinedStructure.BlockInfo(blockposition2, iblockdata, (craftBlockState instanceof org.bukkit.craftbukkit.block.CraftBlockEntityState<?> craftBlockEntityState ? craftBlockEntityState.getSnapshotNBT() : null));
                            }
                            // CraftBukkit end

                            if (worldaccess.setBlock(blockposition2, iblockdata, i)) {
                                j = Math.min(j, blockposition2.getX());
                                k = Math.min(k, blockposition2.getY());
                                l = Math.min(l, blockposition2.getZ());
                                i1 = Math.max(i1, blockposition2.getX());
                                j1 = Math.max(j1, blockposition2.getY());
                                k1 = Math.max(k1, blockposition2.getZ());
                                list3.add(Pair.of(blockposition2, definedstructure_blockinfo.nbt));
                                if (definedstructure_blockinfo.nbt != null) {
                                    TileEntity tileentity = worldaccess.getBlockEntity(blockposition2);

                                    if (tileentity != null) {
                                        if (tileentity instanceof RandomizableContainer) {
                                            definedstructure_blockinfo.nbt.putLong("LootTableSeed", randomsource.nextLong());
                                        }

                                        tileentity.loadWithComponents(TagValueInput.create(problemreporter_j.forChild(tileentity.problemPath()), worldaccess.registryAccess(), definedstructure_blockinfo.nbt));
                                    }
                                }

                                if (fluid != null) {
                                    if (iblockdata.getFluidState().isSource()) {
                                        list2.add(blockposition2);
                                    } else if (iblockdata.getBlock() instanceof IFluidContainer) {
                                        ((IFluidContainer) iblockdata.getBlock()).placeLiquid(worldaccess, blockposition2, iblockdata, fluid);
                                        if (!fluid.isSource()) {
                                            list1.add(blockposition2);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    boolean flag = true;
                    EnumDirection[] aenumdirection = new EnumDirection[]{EnumDirection.UP, EnumDirection.NORTH, EnumDirection.EAST, EnumDirection.SOUTH, EnumDirection.WEST};

                    while (flag && !((List) list1).isEmpty()) {
                        flag = false;
                        Iterator<BlockPosition> iterator = list1.iterator();

                        while (iterator.hasNext()) {
                            BlockPosition blockposition3 = (BlockPosition) iterator.next();
                            Fluid fluid1 = worldaccess.getFluidState(blockposition3);

                            for (int l1 = 0; l1 < aenumdirection.length && !fluid1.isSource(); ++l1) {
                                BlockPosition blockposition4 = blockposition3.relative(aenumdirection[l1]);
                                Fluid fluid2 = worldaccess.getFluidState(blockposition4);

                                if (fluid2.isSource() && !list2.contains(blockposition4)) {
                                    fluid1 = fluid2;
                                }
                            }

                            if (fluid1.isSource()) {
                                IBlockData iblockdata1 = worldaccess.getBlockState(blockposition3);
                                Block block = iblockdata1.getBlock();

                                if (block instanceof IFluidContainer) {
                                    ((IFluidContainer) block).placeLiquid(worldaccess, blockposition3, iblockdata1, fluid1);
                                    flag = true;
                                    iterator.remove();
                                }
                            }
                        }
                    }

                    if (j <= i1) {
                        if (!definedstructureinfo.getKnownShape()) {
                            VoxelShapeDiscrete voxelshapediscrete = new VoxelShapeBitSet(i1 - j + 1, j1 - k + 1, k1 - l + 1);
                            int i2 = j;
                            int j2 = k;
                            int k2 = l;

                            for (Pair<BlockPosition, NBTTagCompound> pair : list3) {
                                BlockPosition blockposition5 = (BlockPosition) pair.getFirst();

                                voxelshapediscrete.fill(blockposition5.getX() - i2, blockposition5.getY() - j2, blockposition5.getZ() - k2);
                            }

                            updateShapeAtEdge(worldaccess, i, voxelshapediscrete, i2, j2, k2);
                        }

                        for (Pair<BlockPosition, NBTTagCompound> pair1 : list3) {
                            BlockPosition blockposition6 = (BlockPosition) pair1.getFirst();

                            if (!definedstructureinfo.getKnownShape()) {
                                IBlockData iblockdata2 = worldaccess.getBlockState(blockposition6);
                                IBlockData iblockdata3 = Block.updateFromNeighbourShapes(iblockdata2, worldaccess, blockposition6);

                                if (iblockdata2 != iblockdata3) {
                                    worldaccess.setBlock(blockposition6, iblockdata3, i & -2 | 16);
                                }

                                worldaccess.updateNeighborsAt(blockposition6, iblockdata3.getBlock());
                            }

                            if (pair1.getSecond() != null) {
                                TileEntity tileentity1 = worldaccess.getBlockEntity(blockposition6);

                                if (tileentity1 != null) {
                                    tileentity1.setChanged();
                                }
                            }
                        }
                    }

                    if (!definedstructureinfo.isIgnoreEntities()) {
                        this.placeEntities(wrappedAccess, blockposition, definedstructureinfo.getMirror(), definedstructureinfo.getRotation(), definedstructureinfo.getRotationPivot(), structureboundingbox, definedstructureinfo.shouldFinalizeEntities(), problemreporter_j); // CraftBukkit
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static void updateShapeAtEdge(GeneratorAccess generatoraccess, int i, VoxelShapeDiscrete voxelshapediscrete, BlockPosition blockposition) {
        updateShapeAtEdge(generatoraccess, i, voxelshapediscrete, blockposition.getX(), blockposition.getY(), blockposition.getZ());
    }

    public static void updateShapeAtEdge(GeneratorAccess generatoraccess, int i, VoxelShapeDiscrete voxelshapediscrete, int j, int k, int l) {
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition1 = new BlockPosition.MutableBlockPosition();

        voxelshapediscrete.forAllFaces((enumdirection, i1, j1, k1) -> {
            blockposition_mutableblockposition.set(j + i1, k + j1, l + k1);
            blockposition_mutableblockposition1.setWithOffset(blockposition_mutableblockposition, enumdirection);
            IBlockData iblockdata = generatoraccess.getBlockState(blockposition_mutableblockposition);
            IBlockData iblockdata1 = generatoraccess.getBlockState(blockposition_mutableblockposition1);
            IBlockData iblockdata2 = iblockdata.updateShape(generatoraccess, generatoraccess, blockposition_mutableblockposition, enumdirection, blockposition_mutableblockposition1, iblockdata1, generatoraccess.getRandom());

            if (iblockdata != iblockdata2) {
                generatoraccess.setBlock(blockposition_mutableblockposition, iblockdata2, i & -2);
            }

            IBlockData iblockdata3 = iblockdata1.updateShape(generatoraccess, generatoraccess, blockposition_mutableblockposition1, enumdirection.getOpposite(), blockposition_mutableblockposition, iblockdata2, generatoraccess.getRandom());

            if (iblockdata1 != iblockdata3) {
                generatoraccess.setBlock(blockposition_mutableblockposition1, iblockdata3, i & -2);
            }

        });
    }

    public static List<DefinedStructure.BlockInfo> processBlockInfos(WorldAccess worldaccess, BlockPosition blockposition, BlockPosition blockposition1, DefinedStructureInfo definedstructureinfo, List<DefinedStructure.BlockInfo> list) {
        List<DefinedStructure.BlockInfo> list1 = new ArrayList();
        List<DefinedStructure.BlockInfo> list2 = new ArrayList();

        for (DefinedStructure.BlockInfo definedstructure_blockinfo : list) {
            BlockPosition blockposition2 = calculateRelativePosition(definedstructureinfo, definedstructure_blockinfo.pos).offset(blockposition);
            DefinedStructure.BlockInfo definedstructure_blockinfo1 = new DefinedStructure.BlockInfo(blockposition2, definedstructure_blockinfo.state, definedstructure_blockinfo.nbt != null ? definedstructure_blockinfo.nbt.copy() : null);

            for (Iterator<DefinedStructureProcessor> iterator = definedstructureinfo.getProcessors().iterator(); definedstructure_blockinfo1 != null && iterator.hasNext(); definedstructure_blockinfo1 = ((DefinedStructureProcessor) iterator.next()).processBlock(worldaccess, blockposition, blockposition1, definedstructure_blockinfo, definedstructure_blockinfo1, definedstructureinfo)) {
                ;
            }

            if (definedstructure_blockinfo1 != null) {
                list2.add(definedstructure_blockinfo1);
                list1.add(definedstructure_blockinfo);
            }
        }

        for (DefinedStructureProcessor definedstructureprocessor : definedstructureinfo.getProcessors()) {
            list2 = definedstructureprocessor.finalizeProcessing(worldaccess, blockposition, blockposition1, list1, list2, definedstructureinfo);
        }

        return list2;
    }

    private void placeEntities(WorldAccess worldaccess, BlockPosition blockposition, EnumBlockMirror enumblockmirror, EnumBlockRotation enumblockrotation, BlockPosition blockposition1, @Nullable StructureBoundingBox structureboundingbox, boolean flag, ProblemReporter problemreporter) {
        for (DefinedStructure.EntityInfo definedstructure_entityinfo : this.entityInfoList) {
            BlockPosition blockposition2 = transform(definedstructure_entityinfo.blockPos, enumblockmirror, enumblockrotation, blockposition1).offset(blockposition);

            if (structureboundingbox == null || structureboundingbox.isInside(blockposition2)) {
                NBTTagCompound nbttagcompound = definedstructure_entityinfo.nbt.copy();
                Vec3D vec3d = transform(definedstructure_entityinfo.pos, enumblockmirror, enumblockrotation, blockposition1);
                Vec3D vec3d1 = vec3d.add((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                NBTTagList nbttaglist = new NBTTagList();

                nbttaglist.add(NBTTagDouble.valueOf(vec3d1.x));
                nbttaglist.add(NBTTagDouble.valueOf(vec3d1.y));
                nbttaglist.add(NBTTagDouble.valueOf(vec3d1.z));
                nbttagcompound.put("Pos", nbttaglist);
                nbttagcompound.remove("UUID");
                createEntityIgnoreException(problemreporter, worldaccess, nbttagcompound).ifPresent((entity) -> {
                    float f = entity.rotate(enumblockrotation);

                    f += entity.mirror(enumblockmirror) - entity.getYRot();
                    entity.snapTo(vec3d1.x, vec3d1.y, vec3d1.z, f, entity.getXRot());
                    if (flag && entity instanceof EntityInsentient) {
                        ((EntityInsentient) entity).finalizeSpawn(worldaccess, worldaccess.getCurrentDifficultyAt(BlockPosition.containing(vec3d1)), EntitySpawnReason.STRUCTURE, (GroupDataEntity) null);
                    }

                    worldaccess.addFreshEntityWithPassengers(entity);
                });
            }
        }

    }

    private static Optional<Entity> createEntityIgnoreException(ProblemReporter problemreporter, WorldAccess worldaccess, NBTTagCompound nbttagcompound) {
        // CraftBukkit start
        // try {
            return EntityTypes.create(TagValueInput.create(problemreporter, worldaccess.registryAccess(), nbttagcompound), worldaccess.getLevel(), EntitySpawnReason.STRUCTURE);
        // } catch (Exception exception) {
            // return Optional.empty();
        // }
        // CraftBukkit end
    }

    public BaseBlockPosition getSize(EnumBlockRotation enumblockrotation) {
        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new BaseBlockPosition(this.size.getZ(), this.size.getY(), this.size.getX());
            default:
                return this.size;
        }
    }

    public static BlockPosition transform(BlockPosition blockposition, EnumBlockMirror enumblockmirror, EnumBlockRotation enumblockrotation, BlockPosition blockposition1) {
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();
        boolean flag = true;

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                k = -k;
                break;
            case FRONT_BACK:
                i = -i;
                break;
            default:
                flag = false;
        }

        int l = blockposition1.getX();
        int i1 = blockposition1.getZ();

        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
                return new BlockPosition(l - i1 + k, j, l + i1 - i);
            case CLOCKWISE_90:
                return new BlockPosition(l + i1 - k, j, i1 - l + i);
            case CLOCKWISE_180:
                return new BlockPosition(l + l - i, j, i1 + i1 - k);
            default:
                return flag ? new BlockPosition(i, j, k) : blockposition;
        }
    }

    public static Vec3D transform(Vec3D vec3d, EnumBlockMirror enumblockmirror, EnumBlockRotation enumblockrotation, BlockPosition blockposition) {
        double d0 = vec3d.x;
        double d1 = vec3d.y;
        double d2 = vec3d.z;
        boolean flag = true;

        switch (enumblockmirror) {
            case LEFT_RIGHT:
                d2 = 1.0D - d2;
                break;
            case FRONT_BACK:
                d0 = 1.0D - d0;
                break;
            default:
                flag = false;
        }

        int i = blockposition.getX();
        int j = blockposition.getZ();

        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
                return new Vec3D((double) (i - j) + d2, d1, (double) (i + j + 1) - d0);
            case CLOCKWISE_90:
                return new Vec3D((double) (i + j + 1) - d2, d1, (double) (j - i) + d0);
            case CLOCKWISE_180:
                return new Vec3D((double) (i + i + 1) - d0, d1, (double) (j + j + 1) - d2);
            default:
                return flag ? new Vec3D(d0, d1, d2) : vec3d;
        }
    }

    public BlockPosition getZeroPositionWithTransform(BlockPosition blockposition, EnumBlockMirror enumblockmirror, EnumBlockRotation enumblockrotation) {
        return getZeroPositionWithTransform(blockposition, enumblockmirror, enumblockrotation, this.getSize().getX(), this.getSize().getZ());
    }

    public static BlockPosition getZeroPositionWithTransform(BlockPosition blockposition, EnumBlockMirror enumblockmirror, EnumBlockRotation enumblockrotation, int i, int j) {
        --i;
        --j;
        int k = enumblockmirror == EnumBlockMirror.FRONT_BACK ? i : 0;
        int l = enumblockmirror == EnumBlockMirror.LEFT_RIGHT ? j : 0;
        BlockPosition blockposition1 = blockposition;

        switch (enumblockrotation) {
            case COUNTERCLOCKWISE_90:
                blockposition1 = blockposition.offset(l, 0, i - k);
                break;
            case CLOCKWISE_90:
                blockposition1 = blockposition.offset(j - l, 0, k);
                break;
            case CLOCKWISE_180:
                blockposition1 = blockposition.offset(i - k, 0, j - l);
                break;
            case NONE:
                blockposition1 = blockposition.offset(k, 0, l);
        }

        return blockposition1;
    }

    public StructureBoundingBox getBoundingBox(DefinedStructureInfo definedstructureinfo, BlockPosition blockposition) {
        return this.getBoundingBox(blockposition, definedstructureinfo.getRotation(), definedstructureinfo.getRotationPivot(), definedstructureinfo.getMirror());
    }

    public StructureBoundingBox getBoundingBox(BlockPosition blockposition, EnumBlockRotation enumblockrotation, BlockPosition blockposition1, EnumBlockMirror enumblockmirror) {
        return getBoundingBox(blockposition, enumblockrotation, blockposition1, enumblockmirror, this.size);
    }

    @VisibleForTesting
    protected static StructureBoundingBox getBoundingBox(BlockPosition blockposition, EnumBlockRotation enumblockrotation, BlockPosition blockposition1, EnumBlockMirror enumblockmirror, BaseBlockPosition baseblockposition) {
        BaseBlockPosition baseblockposition1 = baseblockposition.offset(-1, -1, -1);
        BlockPosition blockposition2 = transform(BlockPosition.ZERO, enumblockmirror, enumblockrotation, blockposition1);
        BlockPosition blockposition3 = transform(BlockPosition.ZERO.offset(baseblockposition1), enumblockmirror, enumblockrotation, blockposition1);

        return StructureBoundingBox.fromCorners(blockposition2, blockposition3).move(blockposition);
    }

    public NBTTagCompound save(NBTTagCompound nbttagcompound) {
        if (this.palettes.isEmpty()) {
            nbttagcompound.put("blocks", new NBTTagList());
            nbttagcompound.put("palette", new NBTTagList());
        } else {
            List<DefinedStructure.c> list = Lists.newArrayList();
            DefinedStructure.c definedstructure_c = new DefinedStructure.c();

            list.add(definedstructure_c);

            for (int i = 1; i < this.palettes.size(); ++i) {
                list.add(new DefinedStructure.c());
            }

            NBTTagList nbttaglist = new NBTTagList();
            List<DefinedStructure.BlockInfo> list1 = ((DefinedStructure.b) this.palettes.get(0)).blocks();

            for (int j = 0; j < list1.size(); ++j) {
                DefinedStructure.BlockInfo definedstructure_blockinfo = (DefinedStructure.BlockInfo) list1.get(j);
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();

                nbttagcompound1.put("pos", this.newIntegerList(definedstructure_blockinfo.pos.getX(), definedstructure_blockinfo.pos.getY(), definedstructure_blockinfo.pos.getZ()));
                int k = definedstructure_c.idFor(definedstructure_blockinfo.state);

                nbttagcompound1.putInt("state", k);
                if (definedstructure_blockinfo.nbt != null) {
                    nbttagcompound1.put("nbt", definedstructure_blockinfo.nbt);
                }

                nbttaglist.add(nbttagcompound1);

                for (int l = 1; l < this.palettes.size(); ++l) {
                    DefinedStructure.c definedstructure_c1 = (DefinedStructure.c) list.get(l);

                    definedstructure_c1.addMapping(((DefinedStructure.BlockInfo) ((DefinedStructure.b) this.palettes.get(l)).blocks().get(j)).state, k);
                }
            }

            nbttagcompound.put("blocks", nbttaglist);
            if (list.size() == 1) {
                NBTTagList nbttaglist1 = new NBTTagList();

                for (IBlockData iblockdata : definedstructure_c) {
                    nbttaglist1.add(GameProfileSerializer.writeBlockState(iblockdata));
                }

                nbttagcompound.put("palette", nbttaglist1);
            } else {
                NBTTagList nbttaglist2 = new NBTTagList();

                for (DefinedStructure.c definedstructure_c2 : list) {
                    NBTTagList nbttaglist3 = new NBTTagList();

                    for (IBlockData iblockdata1 : definedstructure_c2) {
                        nbttaglist3.add(GameProfileSerializer.writeBlockState(iblockdata1));
                    }

                    nbttaglist2.add(nbttaglist3);
                }

                nbttagcompound.put("palettes", nbttaglist2);
            }
        }

        NBTTagList nbttaglist4 = new NBTTagList();

        for (DefinedStructure.EntityInfo definedstructure_entityinfo : this.entityInfoList) {
            NBTTagCompound nbttagcompound2 = new NBTTagCompound();

            nbttagcompound2.put("pos", this.newDoubleList(definedstructure_entityinfo.pos.x, definedstructure_entityinfo.pos.y, definedstructure_entityinfo.pos.z));
            nbttagcompound2.put("blockPos", this.newIntegerList(definedstructure_entityinfo.blockPos.getX(), definedstructure_entityinfo.blockPos.getY(), definedstructure_entityinfo.blockPos.getZ()));
            if (definedstructure_entityinfo.nbt != null) {
                nbttagcompound2.put("nbt", definedstructure_entityinfo.nbt);
            }

            nbttaglist4.add(nbttagcompound2);
        }

        nbttagcompound.put("entities", nbttaglist4);
        nbttagcompound.put("size", this.newIntegerList(this.size.getX(), this.size.getY(), this.size.getZ()));
        // CraftBukkit start - PDC
        if (!this.persistentDataContainer.isEmpty()) {
            nbttagcompound.put("BukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        return GameProfileSerializer.addCurrentDataVersion(nbttagcompound);
    }

    public void load(HolderGetter<Block> holdergetter, NBTTagCompound nbttagcompound) {
        this.palettes.clear();
        this.entityInfoList.clear();
        NBTTagList nbttaglist = nbttagcompound.getListOrEmpty("size");

        this.size = new BaseBlockPosition(nbttaglist.getIntOr(0, 0), nbttaglist.getIntOr(1, 0), nbttaglist.getIntOr(2, 0));
        NBTTagList nbttaglist1 = nbttagcompound.getListOrEmpty("blocks");
        Optional<NBTTagList> optional = nbttagcompound.getList("palettes");

        if (optional.isPresent()) {
            for (int i = 0; i < ((NBTTagList) optional.get()).size(); ++i) {
                this.loadPalette(holdergetter, ((NBTTagList) optional.get()).getListOrEmpty(i), nbttaglist1);
            }
        } else {
            this.loadPalette(holdergetter, nbttagcompound.getListOrEmpty("palette"), nbttaglist1);
        }

        nbttagcompound.getListOrEmpty("entities").compoundStream().forEach((nbttagcompound1) -> {
            NBTTagList nbttaglist2 = nbttagcompound1.getListOrEmpty("pos");
            Vec3D vec3d = new Vec3D(nbttaglist2.getDoubleOr(0, 0.0D), nbttaglist2.getDoubleOr(1, 0.0D), nbttaglist2.getDoubleOr(2, 0.0D));
            NBTTagList nbttaglist3 = nbttagcompound1.getListOrEmpty("blockPos");
            BlockPosition blockposition = new BlockPosition(nbttaglist3.getIntOr(0, 0), nbttaglist3.getIntOr(1, 0), nbttaglist3.getIntOr(2, 0));

            nbttagcompound1.getCompound("nbt").ifPresent((nbttagcompound2) -> {
                this.entityInfoList.add(new DefinedStructure.EntityInfo(vec3d, blockposition, nbttagcompound2));
            });
        });
        // CraftBukkit start - PDC
        NBTBase base = nbttagcompound.get("BukkitValues");
        if (base instanceof NBTTagCompound) {
            this.persistentDataContainer.putAll((NBTTagCompound) base);
        }
        // CraftBukkit end
    }

    private void loadPalette(HolderGetter<Block> holdergetter, NBTTagList nbttaglist, NBTTagList nbttaglist1) {
        DefinedStructure.c definedstructure_c = new DefinedStructure.c();

        for (int i = 0; i < nbttaglist.size(); ++i) {
            definedstructure_c.addMapping(GameProfileSerializer.readBlockState(holdergetter, nbttaglist.getCompoundOrEmpty(i)), i);
        }

        List<DefinedStructure.BlockInfo> list = Lists.newArrayList();
        List<DefinedStructure.BlockInfo> list1 = Lists.newArrayList();
        List<DefinedStructure.BlockInfo> list2 = Lists.newArrayList();

        nbttaglist1.compoundStream().forEach((nbttagcompound) -> {
            NBTTagList nbttaglist2 = nbttagcompound.getListOrEmpty("pos");
            BlockPosition blockposition = new BlockPosition(nbttaglist2.getIntOr(0, 0), nbttaglist2.getIntOr(1, 0), nbttaglist2.getIntOr(2, 0));
            IBlockData iblockdata = definedstructure_c.stateFor(nbttagcompound.getIntOr("state", 0));
            NBTTagCompound nbttagcompound1 = (NBTTagCompound) nbttagcompound.getCompound("nbt").orElse(null); // CraftBukkit - decompile error
            DefinedStructure.BlockInfo definedstructure_blockinfo = new DefinedStructure.BlockInfo(blockposition, iblockdata, nbttagcompound1);

            addToLists(definedstructure_blockinfo, list, list1, list2);
        });
        List<DefinedStructure.BlockInfo> list3 = buildInfoList(list, list1, list2);

        this.palettes.add(new DefinedStructure.b(list3));
    }

    private NBTTagList newIntegerList(int... aint) {
        NBTTagList nbttaglist = new NBTTagList();

        for (int i : aint) {
            nbttaglist.add(NBTTagInt.valueOf(i));
        }

        return nbttaglist;
    }

    private NBTTagList newDoubleList(double... adouble) {
        NBTTagList nbttaglist = new NBTTagList();

        for (double d0 : adouble) {
            nbttaglist.add(NBTTagDouble.valueOf(d0));
        }

        return nbttaglist;
    }

    public static TileEntityJigsaw.JointType getJointType(NBTTagCompound nbttagcompound, IBlockData iblockdata) {
        return (TileEntityJigsaw.JointType) nbttagcompound.read("joint", TileEntityJigsaw.JointType.CODEC).orElseGet(() -> {
            return getDefaultJointType(iblockdata);
        });
    }

    public static TileEntityJigsaw.JointType getDefaultJointType(IBlockData iblockdata) {
        return BlockJigsaw.getFrontFacing(iblockdata).getAxis().isHorizontal() ? TileEntityJigsaw.JointType.ALIGNED : TileEntityJigsaw.JointType.ROLLABLE;
    }

    private static class c implements Iterable<IBlockData> {

        public static final IBlockData DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
        private final RegistryBlockID<IBlockData> ids = new RegistryBlockID<IBlockData>(16);
        private int lastId;

        c() {}

        public int idFor(IBlockData iblockdata) {
            int i = this.ids.getId(iblockdata);

            if (i == -1) {
                i = this.lastId++;
                this.ids.addMapping(iblockdata, i);
            }

            return i;
        }

        @Nullable
        public IBlockData stateFor(int i) {
            IBlockData iblockdata = (IBlockData) this.ids.byId(i);

            return iblockdata == null ? DEFAULT_BLOCK_STATE : iblockdata; // CraftBukkit - decompile error
        }

        public Iterator<IBlockData> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(IBlockData iblockdata, int i) {
            this.ids.addMapping(iblockdata, i);
        }
    }

    public static record BlockInfo(BlockPosition pos, IBlockData state, @Nullable NBTTagCompound nbt) {

        public String toString() {
            return String.format(Locale.ROOT, "<StructureBlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
        }
    }

    public static record a(DefinedStructure.BlockInfo info, TileEntityJigsaw.JointType jointType, MinecraftKey name, ResourceKey<WorldGenFeatureDefinedStructurePoolTemplate> pool, MinecraftKey target, int placementPriority, int selectionPriority) {

        public static DefinedStructure.a of(DefinedStructure.BlockInfo definedstructure_blockinfo) {
            NBTTagCompound nbttagcompound = (NBTTagCompound) Objects.requireNonNull(definedstructure_blockinfo.nbt(), () -> {
                return String.valueOf(definedstructure_blockinfo) + " nbt was null";
            });

            return new DefinedStructure.a(definedstructure_blockinfo, DefinedStructure.getJointType(nbttagcompound, definedstructure_blockinfo.state()), (MinecraftKey) nbttagcompound.read("name", MinecraftKey.CODEC).orElse(TileEntityJigsaw.EMPTY_ID), (ResourceKey) nbttagcompound.read("pool", TileEntityJigsaw.POOL_CODEC).orElse(WorldGenFeaturePieces.EMPTY), (MinecraftKey) nbttagcompound.read("target", MinecraftKey.CODEC).orElse(TileEntityJigsaw.EMPTY_ID), nbttagcompound.getIntOr("placement_priority", 0), nbttagcompound.getIntOr("selection_priority", 0));
        }

        public String toString() {
            return String.format(Locale.ROOT, "<JigsawBlockInfo | %s | %s | name: %s | pool: %s | target: %s | placement: %d | selection: %d | %s>", this.info.pos, this.info.state, this.name, this.pool.location(), this.target, this.placementPriority, this.selectionPriority, this.info.nbt);
        }

        public DefinedStructure.a withInfo(DefinedStructure.BlockInfo definedstructure_blockinfo) {
            return new DefinedStructure.a(definedstructure_blockinfo, this.jointType, this.name, this.pool, this.target, this.placementPriority, this.selectionPriority);
        }
    }

    public static class EntityInfo {

        public final Vec3D pos;
        public final BlockPosition blockPos;
        public final NBTTagCompound nbt;

        public EntityInfo(Vec3D vec3d, BlockPosition blockposition, NBTTagCompound nbttagcompound) {
            this.pos = vec3d;
            this.blockPos = blockposition;
            this.nbt = nbttagcompound;
        }
    }

    public static final class b {

        private final List<DefinedStructure.BlockInfo> blocks;
        private final Map<Block, List<DefinedStructure.BlockInfo>> cache = Maps.newHashMap();
        @Nullable
        private List<DefinedStructure.a> cachedJigsaws;

        b(List<DefinedStructure.BlockInfo> list) {
            this.blocks = list;
        }

        public List<DefinedStructure.a> jigsaws() {
            if (this.cachedJigsaws == null) {
                this.cachedJigsaws = this.blocks(Blocks.JIGSAW).stream().map(DefinedStructure.a::of).toList();
            }

            return this.cachedJigsaws;
        }

        public List<DefinedStructure.BlockInfo> blocks() {
            return this.blocks;
        }

        public List<DefinedStructure.BlockInfo> blocks(Block block) {
            return (List) this.cache.computeIfAbsent(block, (block1) -> {
                return (List) this.blocks.stream().filter((definedstructure_blockinfo) -> {
                    return definedstructure_blockinfo.state.is(block1);
                }).collect(Collectors.toList());
            });
        }
    }
}
