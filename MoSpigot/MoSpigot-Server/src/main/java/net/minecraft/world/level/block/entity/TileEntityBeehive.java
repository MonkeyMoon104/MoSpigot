package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsEntity;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.EntityBee;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockBeehive;
import net.minecraft.world.level.block.BlockCampfire;
import net.minecraft.world.level.block.BlockFire;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class TileEntityBeehive extends TileEntity {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_FLOWER_POS = "flower_pos";
    private static final String BEES = "bees";
    static final List<String> IGNORED_BEE_TAGS = Arrays.asList("Air", "drop_chances", "equipment", "Brain", "CanPickUpLoot", "DeathTime", "fall_distance", "FallFlying", "Fire", "HurtByTimestamp", "HurtTime", "LeftHanded", "Motion", "NoGravity", "OnGround", "PortalCooldown", "Pos", "Rotation", "sleeping_pos", "CannotEnterHiveTicks", "TicksSincePollination", "CropsGrownSincePollination", "hive_pos", "Passengers", "leash", "UUID");
    public static final int MAX_OCCUPANTS = 3;
    private static final int MIN_TICKS_BEFORE_REENTERING_HIVE = 400;
    private static final int MIN_OCCUPATION_TICKS_NECTAR = 2400;
    public static final int MIN_OCCUPATION_TICKS_NECTARLESS = 600;
    private List<TileEntityBeehive.HiveBee> stored = Lists.newArrayList();
    @Nullable
    public BlockPosition savedFlowerPos;
    public int maxBees = 3; // CraftBukkit - allow setting max amount of bees a hive can hold

    public TileEntityBeehive(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.BEEHIVE, blockposition, iblockdata);
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby()) {
            this.emptyAllLivingFromHive((EntityHuman) null, this.level.getBlockState(this.getBlockPos()), TileEntityBeehive.ReleaseStatus.EMERGENCY);
        }

        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) {
            return false;
        } else {
            for (BlockPosition blockposition : BlockPosition.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1))) {
                if (this.level.getBlockState(blockposition).getBlock() instanceof BlockFire) {
                    return true;
                }
            }

            return false;
        }
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() == this.maxBees; // CraftBukkit
    }

    public void emptyAllLivingFromHive(@Nullable EntityHuman entityhuman, IBlockData iblockdata, TileEntityBeehive.ReleaseStatus tileentitybeehive_releasestatus) {
        List<Entity> list = this.releaseAllOccupants(iblockdata, tileentitybeehive_releasestatus);

        if (entityhuman != null) {
            for (Entity entity : list) {
                if (entity instanceof EntityBee) {
                    EntityBee entitybee = (EntityBee) entity;

                    if (entityhuman.position().distanceToSqr(entity.position()) <= 16.0D) {
                        if (!this.isSedated()) {
                            entitybee.setTarget(entityhuman, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit
                        } else {
                            entitybee.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }

    }

    private List<Entity> releaseAllOccupants(IBlockData iblockdata, TileEntityBeehive.ReleaseStatus tileentitybeehive_releasestatus) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return releaseBees(iblockdata, tileentitybeehive_releasestatus, false);
    }

    public List<Entity> releaseBees(IBlockData iblockdata, TileEntityBeehive.ReleaseStatus tileentitybeehive_releasestatus, boolean force) {
        List<Entity> list = Lists.newArrayList();

        this.stored.removeIf((tileentitybeehive_hivebee) -> {
            return releaseOccupant(this.level, this.worldPosition, iblockdata, tileentitybeehive_hivebee.toOccupant(), list, tileentitybeehive_releasestatus, this.savedFlowerPos, force);
            // CraftBukkit end
        });
        if (!list.isEmpty()) {
            super.setChanged();
        }

        return list;
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    public static int getHoneyLevel(IBlockData iblockdata) {
        return (Integer) iblockdata.getValue(BlockBeehive.HONEY_LEVEL);
    }

    @VisibleForDebug
    public boolean isSedated() {
        return BlockCampfire.isSmokeyPos(this.level, this.getBlockPos());
    }

    public void addOccupant(EntityBee entitybee) {
        if (this.stored.size() < this.maxBees) { // CraftBukkit
            // CraftBukkit start
            if (this.level != null) {
                org.bukkit.event.entity.EntityEnterBlockEvent event = new org.bukkit.event.entity.EntityEnterBlockEvent(entitybee.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(level, getBlockPos()));
                org.bukkit.Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    entitybee.setStayOutOfHiveCountdown(400);
                    return;
                }
            }
            // CraftBukkit end
            entitybee.stopRiding();
            entitybee.ejectPassengers();
            entitybee.dropLeash();
            this.storeBee(TileEntityBeehive.c.of(entitybee));
            if (this.level != null) {
                if (entitybee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                    this.savedFlowerPos = entitybee.getSavedFlowerPos();
                }

                BlockPosition blockposition = this.getBlockPos();

                this.level.playSound((Entity) null, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), SoundEffects.BEEHIVE_ENTER, SoundCategory.BLOCKS, 1.0F, 1.0F);
                this.level.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of(entitybee, this.getBlockState()));
            }

            entitybee.discard(EntityRemoveEvent.Cause.ENTER_BLOCK); // CraftBukkit - add Bukkit remove cause
            super.setChanged();
        }
    }

    public void storeBee(TileEntityBeehive.c tileentitybeehive_c) {
        this.stored.add(new TileEntityBeehive.HiveBee(tileentitybeehive_c));
    }

    private static boolean releaseOccupant(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityBeehive.c tileentitybeehive_c, @Nullable List<Entity> list, TileEntityBeehive.ReleaseStatus tileentitybeehive_releasestatus, @Nullable BlockPosition blockposition1) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return releaseOccupant(world, blockposition, iblockdata, tileentitybeehive_c, list, tileentitybeehive_releasestatus, blockposition1, false);
    }

    private static boolean releaseOccupant(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityBeehive.c tileentitybeehive_c, @Nullable List<Entity> list, TileEntityBeehive.ReleaseStatus tileentitybeehive_releasestatus, @Nullable BlockPosition blockposition1, boolean force) {
        if (!force && EntityBee.isNightOrRaining(world) && tileentitybeehive_releasestatus != TileEntityBeehive.ReleaseStatus.EMERGENCY) {
            // CraftBukkit end
            return false;
        } else {
            EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(BlockBeehive.FACING);
            BlockPosition blockposition2 = blockposition.relative(enumdirection);
            boolean flag = !world.getBlockState(blockposition2).getCollisionShape(world, blockposition2).isEmpty();

            if (flag && tileentitybeehive_releasestatus != TileEntityBeehive.ReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = tileentitybeehive_c.createEntity(world, blockposition);

                if (entity != null) {
                    // CraftBukkit start
                    if (entity instanceof EntityBee) {
                        float f = entity.getBbWidth();
                        double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                        double d1 = (double) blockposition.getX() + 0.5D + d0 * (double) enumdirection.getStepX();
                        double d2 = (double) blockposition.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                        double d3 = (double) blockposition.getZ() + 0.5D + d0 * (double) enumdirection.getStepZ();

                        entity.snapTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                    }
                    if (!world.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BEEHIVE)) return false; // CraftBukkit - SpawnReason, moved from below
                    // CraftBukkit end
                    if (entity instanceof EntityBee) {
                        EntityBee entitybee = (EntityBee) entity;

                        if (blockposition1 != null && !entitybee.hasSavedFlowerPos() && world.random.nextFloat() < 0.9F) {
                            entitybee.setSavedFlowerPos(blockposition1);
                        }

                        if (tileentitybeehive_releasestatus == TileEntityBeehive.ReleaseStatus.HONEY_DELIVERED) {
                            entitybee.dropOffNectar();
                            if (iblockdata.is(TagsBlock.BEEHIVES, (blockbase_blockdata) -> {
                                return blockbase_blockdata.hasProperty(BlockBeehive.HONEY_LEVEL);
                            })) {
                                int i = getHoneyLevel(iblockdata);

                                if (i < 5) {
                                    int j = world.random.nextInt(100) == 0 ? 2 : 1;

                                    if (i + j > 5) {
                                        --j;
                                    }

                                    world.setBlockAndUpdate(blockposition, (IBlockData) iblockdata.setValue(BlockBeehive.HONEY_LEVEL, i + j));
                                }
                            }
                        }

                        if (list != null) {
                            list.add(entitybee);
                        }

                        /* // CraftBukkit start
                        float f = entity.getBbWidth();
                        double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                        double d1 = (double) blockposition.getX() + 0.5D + d0 * (double) enumdirection.getStepX();
                        double d2 = (double) blockposition.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                        double d3 = (double) blockposition.getZ() + 0.5D + d0 * (double) enumdirection.getStepZ();

                        entity.snapTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                         */ // CraftBukkit end
                    }

                    world.playSound((Entity) null, blockposition, SoundEffects.BEEHIVE_EXIT, SoundCategory.BLOCKS, 1.0F, 1.0F);
                    world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.a.of(entity, world.getBlockState(blockposition)));
                    return true; // return this.world.addFreshEntity(entity); // CraftBukkit - moved up
                } else {
                    return false;
                }
            }
        }
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private static void tickOccupants(World world, BlockPosition blockposition, IBlockData iblockdata, List<TileEntityBeehive.HiveBee> list, @Nullable BlockPosition blockposition1) {
        boolean flag = false;
        Iterator<TileEntityBeehive.HiveBee> iterator = list.iterator();

        while (iterator.hasNext()) {
            TileEntityBeehive.HiveBee tileentitybeehive_hivebee = (TileEntityBeehive.HiveBee) iterator.next();

            if (tileentitybeehive_hivebee.tick()) {
                TileEntityBeehive.ReleaseStatus tileentitybeehive_releasestatus = tileentitybeehive_hivebee.hasNectar() ? TileEntityBeehive.ReleaseStatus.HONEY_DELIVERED : TileEntityBeehive.ReleaseStatus.BEE_RELEASED;

                if (releaseOccupant(world, blockposition, iblockdata, tileentitybeehive_hivebee.toOccupant(), (List) null, tileentitybeehive_releasestatus, blockposition1)) {
                    flag = true;
                    iterator.remove();
                    // CraftBukkit start
                } else {
                    tileentitybeehive_hivebee.ticksInHive = tileentitybeehive_hivebee.occupant.minTicksInHive / 2; // Not strictly Vanilla behaviour in cases where bees cannot spawn but still reasonable
                    // CraftBukkit end
                }
            }
        }

        if (flag) {
            setChanged(world, blockposition, iblockdata);
        }

    }

    public static void serverTick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityBeehive tileentitybeehive) {
        tickOccupants(world, blockposition, iblockdata, tileentitybeehive.stored, tileentitybeehive.savedFlowerPos);
        if (!tileentitybeehive.stored.isEmpty() && world.getRandom().nextDouble() < 0.005D) {
            double d0 = (double) blockposition.getX() + 0.5D;
            double d1 = (double) blockposition.getY();
            double d2 = (double) blockposition.getZ() + 0.5D;

            world.playSound((Entity) null, d0, d1, d2, SoundEffects.BEEHIVE_WORK, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        PacketDebug.sendHiveInfo(world, blockposition, iblockdata, tileentitybeehive);
    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.stored = Lists.newArrayList(); // CraftBukkit - SPIGOT-7790: create new copy (may be modified in physics event triggered by honey change)
        (valueinput.read("bees", TileEntityBeehive.c.LIST_CODEC).orElse(List.of())).forEach(this::storeBee); // CraftBukkit - decompile error
        this.savedFlowerPos = (BlockPosition) valueinput.read("flower_pos", BlockPosition.CODEC).orElse(null); // CraftBukkit - decompile error
        // CraftBukkit start
        this.maxBees = valueinput.getIntOr("Bukkit.MaxEntities", this.maxBees);
        // CraftBukkit end
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        valueoutput.store("bees", TileEntityBeehive.c.LIST_CODEC, this.getBees());
        valueoutput.storeNullable("flower_pos", BlockPosition.CODEC, this.savedFlowerPos);
        valueoutput.putInt("Bukkit.MaxEntities", this.maxBees); // CraftBukkit
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        super.applyImplicitComponents(datacomponentgetter);
        this.stored = Lists.newArrayList(); // CraftBukkit - SPIGOT-7790: create new copy (may be modified in physics event triggered by honey change)
        List<TileEntityBeehive.c> list = ((Bees) datacomponentgetter.getOrDefault(DataComponents.BEES, Bees.EMPTY)).bees();

        list.forEach(this::storeBee);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.a datacomponentmap_a) {
        super.collectImplicitComponents(datacomponentmap_a);
        datacomponentmap_a.set(DataComponents.BEES, new Bees(this.getBees()));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput valueoutput) {
        super.removeComponentsFromTag(valueoutput);
        valueoutput.discard("bees");
    }

    private List<TileEntityBeehive.c> getBees() {
        return this.stored.stream().map(TileEntityBeehive.HiveBee::toOccupant).toList();
    }

    public static enum ReleaseStatus {

        HONEY_DELIVERED, BEE_RELEASED, EMERGENCY;

        private ReleaseStatus() {}
    }

    private static class HiveBee {

        private final TileEntityBeehive.c occupant;
        private int ticksInHive;

        HiveBee(TileEntityBeehive.c tileentitybeehive_c) {
            this.occupant = tileentitybeehive_c;
            this.ticksInHive = tileentitybeehive_c.ticksInHive();
        }

        public boolean tick() {
            return this.ticksInHive++ > this.occupant.minTicksInHive;
        }

        public TileEntityBeehive.c toOccupant() {
            return new TileEntityBeehive.c(this.occupant.entityData, this.ticksInHive, this.occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return this.occupant.entityData.getUnsafe().getBooleanOr("HasNectar", false);
        }
    }

    public static record c(CustomData entityData, int ticksInHive, int minTicksInHive) {

        public static final Codec<TileEntityBeehive.c> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(CustomData.CODEC.optionalFieldOf("entity_data", CustomData.EMPTY).forGetter(TileEntityBeehive.c::entityData), Codec.INT.fieldOf("ticks_in_hive").forGetter(TileEntityBeehive.c::ticksInHive), Codec.INT.fieldOf("min_ticks_in_hive").forGetter(TileEntityBeehive.c::minTicksInHive)).apply(instance, TileEntityBeehive.c::new);
        });
        public static final Codec<List<TileEntityBeehive.c>> LIST_CODEC = TileEntityBeehive.c.CODEC.listOf();
        public static final StreamCodec<ByteBuf, TileEntityBeehive.c> STREAM_CODEC = StreamCodec.composite(CustomData.STREAM_CODEC, TileEntityBeehive.c::entityData, ByteBufCodecs.VAR_INT, TileEntityBeehive.c::ticksInHive, ByteBufCodecs.VAR_INT, TileEntityBeehive.c::minTicksInHive, TileEntityBeehive.c::new);

        public static TileEntityBeehive.c of(Entity entity) {
            TileEntityBeehive.c tileentitybeehive_c;

            try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(entity.problemPath(), TileEntityBeehive.LOGGER)) {
                TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_j, entity.registryAccess());

                entity.save(tagvalueoutput);
                List<String> list = TileEntityBeehive.IGNORED_BEE_TAGS; // CraftBukkit - decompile error

                Objects.requireNonNull(tagvalueoutput);
                list.forEach(tagvalueoutput::discard);
                NBTTagCompound nbttagcompound = tagvalueoutput.buildResult();
                boolean flag = nbttagcompound.getBooleanOr("HasNectar", false);

                tileentitybeehive_c = new TileEntityBeehive.c(CustomData.of(nbttagcompound), 0, flag ? 2400 : 600);
            }

            return tileentitybeehive_c;
        }

        public static TileEntityBeehive.c create(int i) {
            NBTTagCompound nbttagcompound = new NBTTagCompound();

            nbttagcompound.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.BEE).toString());
            return new TileEntityBeehive.c(CustomData.of(nbttagcompound), i, 600);
        }

        @Nullable
        public Entity createEntity(World world, BlockPosition blockposition) {
            NBTTagCompound nbttagcompound = this.entityData.copyTag();
            List<String> list = TileEntityBeehive.IGNORED_BEE_TAGS; // CraftBukkit - decompile error

            Objects.requireNonNull(nbttagcompound);
            list.forEach(nbttagcompound::remove);
            Entity entity = EntityTypes.loadEntityRecursive(nbttagcompound, world, EntitySpawnReason.LOAD, (entity1) -> {
                return entity1;
            });

            if (entity != null && entity.getType().is(TagsEntity.BEEHIVE_INHABITORS)) {
                entity.setNoGravity(true);
                if (entity instanceof EntityBee) {
                    EntityBee entitybee = (EntityBee) entity;

                    entitybee.setHivePos(blockposition);
                    setBeeReleaseData(this.ticksInHive, entitybee);
                }

                return entity;
            } else {
                return null;
            }
        }

        private static void setBeeReleaseData(int i, EntityBee entitybee) {
            int j = entitybee.getAge();

            if (j < 0) {
                entitybee.setAge(Math.min(0, j + i));
            } else if (j > 0) {
                entitybee.setAge(Math.max(0, j - i));
            }

            entitybee.setInLoveTime(Math.max(0, entitybee.getInLoveTime() - i));
        }
    }
}
