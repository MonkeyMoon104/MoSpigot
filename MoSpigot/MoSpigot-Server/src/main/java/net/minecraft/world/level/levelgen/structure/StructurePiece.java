package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.GeneratorAccessSeed;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockDispenser;
import net.minecraft.world.level.block.BlockFacingHorizontal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnumBlockMirror;
import net.minecraft.world.level.block.EnumBlockRotation;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityChest;
import net.minecraft.world.level.block.entity.TileEntityDispenser;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.WorldGenFeatureStructurePieceType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootTable;

public abstract class StructurePiece {

    protected static final IBlockData CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected StructureBoundingBox boundingBox;
    @Nullable
    private EnumDirection orientation;
    private EnumBlockMirror mirror;
    private EnumBlockRotation rotation;
    protected int genDepth;
    private final WorldGenFeatureStructurePieceType type;
    public static final Set<Block> SHAPE_CHECK_BLOCKS = ImmutableSet.<Block>builder().add(Blocks.NETHER_BRICK_FENCE).add(Blocks.TORCH).add(Blocks.WALL_TORCH).add(Blocks.OAK_FENCE).add(Blocks.SPRUCE_FENCE).add(Blocks.DARK_OAK_FENCE).add(Blocks.PALE_OAK_FENCE).add(Blocks.ACACIA_FENCE).add(Blocks.BIRCH_FENCE).add(Blocks.JUNGLE_FENCE).add(Blocks.LADDER).add(Blocks.IRON_BARS).build(); // CraftBukkit - decompile error / PAIL private -> public

    protected StructurePiece(WorldGenFeatureStructurePieceType worldgenfeaturestructurepiecetype, int i, StructureBoundingBox structureboundingbox) {
        this.type = worldgenfeaturestructurepiecetype;
        this.genDepth = i;
        this.boundingBox = structureboundingbox;
    }

    public StructurePiece(WorldGenFeatureStructurePieceType worldgenfeaturestructurepiecetype, NBTTagCompound nbttagcompound) {
        this(worldgenfeaturestructurepiecetype, nbttagcompound.getIntOr("GD", 0), (StructureBoundingBox) nbttagcompound.read("BB", StructureBoundingBox.CODEC).orElseThrow());
        int i = nbttagcompound.getIntOr("O", 0);

        this.setOrientation(i == -1 ? null : EnumDirection.from2DDataValue(i));
    }

    protected static StructureBoundingBox makeBoundingBox(int i, int j, int k, EnumDirection enumdirection, int l, int i1, int j1) {
        return enumdirection.getAxis() == EnumDirection.EnumAxis.Z ? new StructureBoundingBox(i, j, k, i + l - 1, j + i1 - 1, k + j1 - 1) : new StructureBoundingBox(i, j, k, i + j1 - 1, j + i1 - 1, k + l - 1);
    }

    protected static EnumDirection getRandomHorizontalDirection(RandomSource randomsource) {
        return EnumDirection.EnumDirectionLimit.HORIZONTAL.getRandomDirection(randomsource);
    }

    public final NBTTagCompound createTag(StructurePieceSerializationContext structurepieceserializationcontext) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        nbttagcompound.putString("id", BuiltInRegistries.STRUCTURE_PIECE.getKey(this.getType()).toString());
        nbttagcompound.store("BB", StructureBoundingBox.CODEC, this.boundingBox);
        EnumDirection enumdirection = this.getOrientation();

        nbttagcompound.putInt("O", enumdirection == null ? -1 : enumdirection.get2DDataValue());
        nbttagcompound.putInt("GD", this.genDepth);
        this.addAdditionalSaveData(structurepieceserializationcontext, nbttagcompound);
        return nbttagcompound;
    }

    protected abstract void addAdditionalSaveData(StructurePieceSerializationContext structurepieceserializationcontext, NBTTagCompound nbttagcompound);

    public void addChildren(StructurePiece structurepiece, StructurePieceAccessor structurepieceaccessor, RandomSource randomsource) {}

    public abstract void postProcess(GeneratorAccessSeed generatoraccessseed, StructureManager structuremanager, ChunkGenerator chunkgenerator, RandomSource randomsource, StructureBoundingBox structureboundingbox, ChunkCoordIntPair chunkcoordintpair, BlockPosition blockposition);

    public StructureBoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public int getGenDepth() {
        return this.genDepth;
    }

    public void setGenDepth(int i) {
        this.genDepth = i;
    }

    public boolean isCloseToChunk(ChunkCoordIntPair chunkcoordintpair, int i) {
        int j = chunkcoordintpair.getMinBlockX();
        int k = chunkcoordintpair.getMinBlockZ();

        return this.boundingBox.intersects(j - i, k - i, j + 15 + i, k + 15 + i);
    }

    public BlockPosition getLocatorPosition() {
        return new BlockPosition(this.boundingBox.getCenter());
    }

    protected BlockPosition.MutableBlockPosition getWorldPos(int i, int j, int k) {
        return new BlockPosition.MutableBlockPosition(this.getWorldX(i, k), this.getWorldY(j), this.getWorldZ(i, k));
    }

    protected int getWorldX(int i, int j) {
        EnumDirection enumdirection = this.getOrientation();

        if (enumdirection == null) {
            return i;
        } else {
            switch (enumdirection) {
                case NORTH:
                case SOUTH:
                    return this.boundingBox.minX() + i;
                case WEST:
                    return this.boundingBox.maxX() - j;
                case EAST:
                    return this.boundingBox.minX() + j;
                default:
                    return i;
            }
        }
    }

    protected int getWorldY(int i) {
        return this.getOrientation() == null ? i : i + this.boundingBox.minY();
    }

    protected int getWorldZ(int i, int j) {
        EnumDirection enumdirection = this.getOrientation();

        if (enumdirection == null) {
            return j;
        } else {
            switch (enumdirection) {
                case NORTH:
                    return this.boundingBox.maxZ() - j;
                case SOUTH:
                    return this.boundingBox.minZ() + j;
                case WEST:
                case EAST:
                    return this.boundingBox.minZ() + i;
                default:
                    return j;
            }
        }
    }

    protected void placeBlock(GeneratorAccessSeed generatoraccessseed, IBlockData iblockdata, int i, int j, int k, StructureBoundingBox structureboundingbox) {
        BlockPosition blockposition = this.getWorldPos(i, j, k);

        if (structureboundingbox.isInside(blockposition)) {
            if (this.canBeReplaced(generatoraccessseed, i, j, k, structureboundingbox)) {
                if (this.mirror != EnumBlockMirror.NONE) {
                    iblockdata = iblockdata.mirror(this.mirror);
                }

                if (this.rotation != EnumBlockRotation.NONE) {
                    iblockdata = iblockdata.rotate(this.rotation);
                }

                generatoraccessseed.setBlock(blockposition, iblockdata, 2);
                // CraftBukkit start - fluid handling is already done if we have a transformer generator access
                if (generatoraccessseed instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess) {
                    return;
                }
                // CraftBukkit end
                Fluid fluid = generatoraccessseed.getFluidState(blockposition);

                if (!fluid.isEmpty()) {
                    generatoraccessseed.scheduleTick(blockposition, fluid.getType(), 0);
                }

                if (StructurePiece.SHAPE_CHECK_BLOCKS.contains(iblockdata.getBlock())) {
                    generatoraccessseed.getChunk(blockposition).markPosForPostprocessing(blockposition);
                }

            }
        }
    }

    // CraftBukkit start
    protected boolean placeCraftBlockEntity(WorldAccess worldAccess, BlockPosition position, org.bukkit.craftbukkit.block.CraftBlockEntityState<?> craftBlockEntityState, int i) {
        if (worldAccess instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
            return transformerAccess.setCraftBlock(position, craftBlockEntityState, i);
        }
        boolean result = worldAccess.setBlock(position, craftBlockEntityState.getHandle(), i);
        TileEntity tileEntity = worldAccess.getBlockEntity(position);
        if (tileEntity != null) {
            tileEntity.loadWithComponents(craftBlockEntityState.getSnapshotInput());
        }
        return result;
    }

    protected void placeCraftSpawner(WorldAccess worldAccess, BlockPosition position, org.bukkit.entity.EntityType entityType, int i) {
        // This method is used in structures that are generated by code and place spawners as they set the entity after the block was placed making it impossible for plugins to access that information
        org.bukkit.craftbukkit.block.CraftCreatureSpawner spawner = (org.bukkit.craftbukkit.block.CraftCreatureSpawner) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(worldAccess, position, Blocks.SPAWNER.defaultBlockState(), null);
        spawner.setSpawnedType(entityType);
        placeCraftBlockEntity(worldAccess, position, spawner, i);
    }

    protected void setCraftLootTable(WorldAccess worldAccess, BlockPosition position, RandomSource randomSource, ResourceKey<LootTable> loottableKey) {
        // This method is used in structures that use data markers to a loot table to loot containers as otherwise plugins won't have access to that information.
        net.minecraft.world.level.block.entity.TileEntity tileEntity = worldAccess.getBlockEntity(position);
        if (tileEntity instanceof net.minecraft.world.level.block.entity.TileEntityLootable tileEntityLootable) {
            tileEntityLootable.setLootTable(loottableKey, randomSource.nextLong());
            if (worldAccess instanceof org.bukkit.craftbukkit.util.TransformerGeneratorAccess transformerAccess) {
                transformerAccess.setCraftBlock(position, (org.bukkit.craftbukkit.block.CraftBlockState) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(worldAccess, position, tileEntity.getBlockState(), tileEntityLootable.saveWithFullMetadata(worldAccess.registryAccess())), 3);
            }
        }
    }
    // CraftBukkit end

    protected boolean canBeReplaced(IWorldReader iworldreader, int i, int j, int k, StructureBoundingBox structureboundingbox) {
        return true;
    }

    protected IBlockData getBlock(IBlockAccess iblockaccess, int i, int j, int k, StructureBoundingBox structureboundingbox) {
        BlockPosition blockposition = this.getWorldPos(i, j, k);

        return !structureboundingbox.isInside(blockposition) ? Blocks.AIR.defaultBlockState() : iblockaccess.getBlockState(blockposition);
    }

    protected boolean isInterior(IWorldReader iworldreader, int i, int j, int k, StructureBoundingBox structureboundingbox) {
        BlockPosition blockposition = this.getWorldPos(i, j + 1, k);

        return !structureboundingbox.isInside(blockposition) ? false : blockposition.getY() < iworldreader.getHeight(HeightMap.Type.OCEAN_FLOOR_WG, blockposition.getX(), blockposition.getZ());
    }

    protected void generateAirBox(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, int i, int j, int k, int l, int i1, int j1) {
        for (int k1 = j; k1 <= i1; ++k1) {
            for (int l1 = i; l1 <= l; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    this.placeBlock(generatoraccessseed, Blocks.AIR.defaultBlockState(), l1, k1, i2, structureboundingbox);
                }
            }
        }

    }

    protected void generateBox(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, int i, int j, int k, int l, int i1, int j1, IBlockData iblockdata, IBlockData iblockdata1, boolean flag) {
        for (int k1 = j; k1 <= i1; ++k1) {
            for (int l1 = i; l1 <= l; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    if (!flag || !this.getBlock(generatoraccessseed, l1, k1, i2, structureboundingbox).isAir()) {
                        if (k1 != j && k1 != i1 && l1 != i && l1 != l && i2 != k && i2 != j1) {
                            this.placeBlock(generatoraccessseed, iblockdata1, l1, k1, i2, structureboundingbox);
                        } else {
                            this.placeBlock(generatoraccessseed, iblockdata, l1, k1, i2, structureboundingbox);
                        }
                    }
                }
            }
        }

    }

    protected void generateBox(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, StructureBoundingBox structureboundingbox1, IBlockData iblockdata, IBlockData iblockdata1, boolean flag) {
        this.generateBox(generatoraccessseed, structureboundingbox, structureboundingbox1.minX(), structureboundingbox1.minY(), structureboundingbox1.minZ(), structureboundingbox1.maxX(), structureboundingbox1.maxY(), structureboundingbox1.maxZ(), iblockdata, iblockdata1, flag);
    }

    protected void generateBox(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, int i, int j, int k, int l, int i1, int j1, boolean flag, RandomSource randomsource, StructurePiece.StructurePieceBlockSelector structurepiece_structurepieceblockselector) {
        for (int k1 = j; k1 <= i1; ++k1) {
            for (int l1 = i; l1 <= l; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    if (!flag || !this.getBlock(generatoraccessseed, l1, k1, i2, structureboundingbox).isAir()) {
                        structurepiece_structurepieceblockselector.next(randomsource, l1, k1, i2, k1 == j || k1 == i1 || l1 == i || l1 == l || i2 == k || i2 == j1);
                        this.placeBlock(generatoraccessseed, structurepiece_structurepieceblockselector.getNext(), l1, k1, i2, structureboundingbox);
                    }
                }
            }
        }

    }

    protected void generateBox(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, StructureBoundingBox structureboundingbox1, boolean flag, RandomSource randomsource, StructurePiece.StructurePieceBlockSelector structurepiece_structurepieceblockselector) {
        this.generateBox(generatoraccessseed, structureboundingbox, structureboundingbox1.minX(), structureboundingbox1.minY(), structureboundingbox1.minZ(), structureboundingbox1.maxX(), structureboundingbox1.maxY(), structureboundingbox1.maxZ(), flag, randomsource, structurepiece_structurepieceblockselector);
    }

    protected void generateMaybeBox(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, RandomSource randomsource, float f, int i, int j, int k, int l, int i1, int j1, IBlockData iblockdata, IBlockData iblockdata1, boolean flag, boolean flag1) {
        for (int k1 = j; k1 <= i1; ++k1) {
            for (int l1 = i; l1 <= l; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    if (randomsource.nextFloat() <= f && (!flag || !this.getBlock(generatoraccessseed, l1, k1, i2, structureboundingbox).isAir()) && (!flag1 || this.isInterior(generatoraccessseed, l1, k1, i2, structureboundingbox))) {
                        if (k1 != j && k1 != i1 && l1 != i && l1 != l && i2 != k && i2 != j1) {
                            this.placeBlock(generatoraccessseed, iblockdata1, l1, k1, i2, structureboundingbox);
                        } else {
                            this.placeBlock(generatoraccessseed, iblockdata, l1, k1, i2, structureboundingbox);
                        }
                    }
                }
            }
        }

    }

    protected void maybeGenerateBlock(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, RandomSource randomsource, float f, int i, int j, int k, IBlockData iblockdata) {
        if (randomsource.nextFloat() < f) {
            this.placeBlock(generatoraccessseed, iblockdata, i, j, k, structureboundingbox);
        }

    }

    protected void generateUpperHalfSphere(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, int i, int j, int k, int l, int i1, int j1, IBlockData iblockdata, boolean flag) {
        float f = (float) (l - i + 1);
        float f1 = (float) (i1 - j + 1);
        float f2 = (float) (j1 - k + 1);
        float f3 = (float) i + f / 2.0F;
        float f4 = (float) k + f2 / 2.0F;

        for (int k1 = j; k1 <= i1; ++k1) {
            float f5 = (float) (k1 - j) / f1;

            for (int l1 = i; l1 <= l; ++l1) {
                float f6 = ((float) l1 - f3) / (f * 0.5F);

                for (int i2 = k; i2 <= j1; ++i2) {
                    float f7 = ((float) i2 - f4) / (f2 * 0.5F);

                    if (!flag || !this.getBlock(generatoraccessseed, l1, k1, i2, structureboundingbox).isAir()) {
                        float f8 = f6 * f6 + f5 * f5 + f7 * f7;

                        if (f8 <= 1.05F) {
                            this.placeBlock(generatoraccessseed, iblockdata, l1, k1, i2, structureboundingbox);
                        }
                    }
                }
            }
        }

    }

    protected void fillColumnDown(GeneratorAccessSeed generatoraccessseed, IBlockData iblockdata, int i, int j, int k, StructureBoundingBox structureboundingbox) {
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = this.getWorldPos(i, j, k);

        if (structureboundingbox.isInside(blockposition_mutableblockposition)) {
            while (this.isReplaceableByStructures(generatoraccessseed.getBlockState(blockposition_mutableblockposition)) && blockposition_mutableblockposition.getY() > generatoraccessseed.getMinY() + 1) {
                generatoraccessseed.setBlock(blockposition_mutableblockposition, iblockdata, 2);
                blockposition_mutableblockposition.move(EnumDirection.DOWN);
            }

        }
    }

    protected boolean isReplaceableByStructures(IBlockData iblockdata) {
        return iblockdata.isAir() || iblockdata.liquid() || iblockdata.is(Blocks.GLOW_LICHEN) || iblockdata.is(Blocks.SEAGRASS) || iblockdata.is(Blocks.TALL_SEAGRASS);
    }

    protected boolean createChest(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, RandomSource randomsource, int i, int j, int k, ResourceKey<LootTable> resourcekey) {
        return this.createChest(generatoraccessseed, structureboundingbox, randomsource, this.getWorldPos(i, j, k), resourcekey, (IBlockData) null);
    }

    public static IBlockData reorient(IBlockAccess iblockaccess, BlockPosition blockposition, IBlockData iblockdata) {
        EnumDirection enumdirection = null;

        for (EnumDirection enumdirection1 : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
            BlockPosition blockposition1 = blockposition.relative(enumdirection1);
            IBlockData iblockdata1 = iblockaccess.getBlockState(blockposition1);

            if (iblockdata1.is(Blocks.CHEST)) {
                return iblockdata;
            }

            if (iblockdata1.isSolidRender()) {
                if (enumdirection != null) {
                    enumdirection = null;
                    break;
                }

                enumdirection = enumdirection1;
            }
        }

        if (enumdirection != null) {
            return (IBlockData) iblockdata.setValue(BlockFacingHorizontal.FACING, enumdirection.getOpposite());
        } else {
            EnumDirection enumdirection2 = (EnumDirection) iblockdata.getValue(BlockFacingHorizontal.FACING);
            BlockPosition blockposition2 = blockposition.relative(enumdirection2);

            if (iblockaccess.getBlockState(blockposition2).isSolidRender()) {
                enumdirection2 = enumdirection2.getOpposite();
                blockposition2 = blockposition.relative(enumdirection2);
            }

            if (iblockaccess.getBlockState(blockposition2).isSolidRender()) {
                enumdirection2 = enumdirection2.getClockWise();
                blockposition2 = blockposition.relative(enumdirection2);
            }

            if (iblockaccess.getBlockState(blockposition2).isSolidRender()) {
                enumdirection2 = enumdirection2.getOpposite();
                blockposition.relative(enumdirection2);
            }

            return (IBlockData) iblockdata.setValue(BlockFacingHorizontal.FACING, enumdirection2);
        }
    }

    protected boolean createChest(WorldAccess worldaccess, StructureBoundingBox structureboundingbox, RandomSource randomsource, BlockPosition blockposition, ResourceKey<LootTable> resourcekey, @Nullable IBlockData iblockdata) {
        if (structureboundingbox.isInside(blockposition) && !worldaccess.getBlockState(blockposition).is(Blocks.CHEST)) {
            if (iblockdata == null) {
                iblockdata = reorient(worldaccess, blockposition, Blocks.CHEST.defaultBlockState());
            }

            // CraftBukkit start
            /*
            worldaccess.setBlock(blockposition, iblockdata, 2);
            TileEntity tileentity = worldaccess.getBlockEntity(blockposition);

            if (tileentity instanceof TileEntityChest) {
                ((TileEntityChest) tileentity).setLootTable(resourcekey, randomsource.nextLong());
            }
            */
            org.bukkit.craftbukkit.block.CraftChest chestState = (org.bukkit.craftbukkit.block.CraftChest) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(worldaccess, blockposition, iblockdata, null);
            chestState.setLootTable(org.bukkit.craftbukkit.CraftLootTable.minecraftToBukkit(resourcekey));
            chestState.setSeed(randomsource.nextLong());
            placeCraftBlockEntity(worldaccess, blockposition, chestState, 2);
            // CraftBukkit end

            return true;
        } else {
            return false;
        }
    }

    protected boolean createDispenser(GeneratorAccessSeed generatoraccessseed, StructureBoundingBox structureboundingbox, RandomSource randomsource, int i, int j, int k, EnumDirection enumdirection, ResourceKey<LootTable> resourcekey) {
        BlockPosition blockposition = this.getWorldPos(i, j, k);

        if (structureboundingbox.isInside(blockposition) && !generatoraccessseed.getBlockState(blockposition).is(Blocks.DISPENSER)) {
            // CraftBukkit start
            /*
            this.placeBlock(generatoraccessseed, (IBlockData) Blocks.DISPENSER.defaultBlockState().setValue(BlockDispenser.FACING, enumdirection), i, j, k, structureboundingbox);
            TileEntity tileentity = generatoraccessseed.getBlockEntity(blockposition);

            if (tileentity instanceof TileEntityDispenser) {
                ((TileEntityDispenser) tileentity).setLootTable(resourcekey, randomsource.nextLong());
            }
            */
            if (!this.canBeReplaced(generatoraccessseed, i, j, k, structureboundingbox)) {
                return true;
            }
            IBlockData iblockdata = Blocks.DISPENSER.defaultBlockState().setValue(BlockDispenser.FACING, enumdirection);
            if (this.mirror != EnumBlockMirror.NONE) {
                iblockdata = iblockdata.mirror(this.mirror);
            }
            if (this.rotation != EnumBlockRotation.NONE) {
                iblockdata = iblockdata.rotate(this.rotation);
            }

            org.bukkit.craftbukkit.block.CraftDispenser dispenserState = (org.bukkit.craftbukkit.block.CraftDispenser) org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(generatoraccessseed, blockposition, iblockdata, null);
            dispenserState.setLootTable(org.bukkit.craftbukkit.CraftLootTable.minecraftToBukkit(resourcekey));
            dispenserState.setSeed(randomsource.nextLong());
            placeCraftBlockEntity(generatoraccessseed, blockposition, dispenserState, 2);
            // CraftBukkit end

            return true;
        } else {
            return false;
        }
    }

    public void move(int i, int j, int k) {
        this.boundingBox.move(i, j, k);
    }

    public static StructureBoundingBox createBoundingBox(Stream<StructurePiece> stream) {
        Stream<StructureBoundingBox> stream1 = stream.map(StructurePiece::getBoundingBox); // CraftBukkit - decompile error

        Objects.requireNonNull(stream1);
        return (StructureBoundingBox) StructureBoundingBox.encapsulatingBoxes(stream1::iterator).orElseThrow(() -> {
            return new IllegalStateException("Unable to calculate boundingbox without pieces");
        });
    }

    @Nullable
    public static StructurePiece findCollisionPiece(List<StructurePiece> list, StructureBoundingBox structureboundingbox) {
        for (StructurePiece structurepiece : list) {
            if (structurepiece.getBoundingBox().intersects(structureboundingbox)) {
                return structurepiece;
            }
        }

        return null;
    }

    @Nullable
    public EnumDirection getOrientation() {
        return this.orientation;
    }

    public void setOrientation(@Nullable EnumDirection enumdirection) {
        this.orientation = enumdirection;
        if (enumdirection == null) {
            this.rotation = EnumBlockRotation.NONE;
            this.mirror = EnumBlockMirror.NONE;
        } else {
            switch (enumdirection) {
                case SOUTH:
                    this.mirror = EnumBlockMirror.LEFT_RIGHT;
                    this.rotation = EnumBlockRotation.NONE;
                    break;
                case WEST:
                    this.mirror = EnumBlockMirror.LEFT_RIGHT;
                    this.rotation = EnumBlockRotation.CLOCKWISE_90;
                    break;
                case EAST:
                    this.mirror = EnumBlockMirror.NONE;
                    this.rotation = EnumBlockRotation.CLOCKWISE_90;
                    break;
                default:
                    this.mirror = EnumBlockMirror.NONE;
                    this.rotation = EnumBlockRotation.NONE;
            }
        }

    }

    public EnumBlockRotation getRotation() {
        return this.rotation;
    }

    public EnumBlockMirror getMirror() {
        return this.mirror;
    }

    public WorldGenFeatureStructurePieceType getType() {
        return this.type;
    }

    public abstract static class StructurePieceBlockSelector {

        protected IBlockData next;

        public StructurePieceBlockSelector() {
            this.next = Blocks.AIR.defaultBlockState();
        }

        public abstract void next(RandomSource randomsource, int i, int j, int k, boolean flag);

        public IBlockData getNext() {
            return this.next;
        }
    }
}
