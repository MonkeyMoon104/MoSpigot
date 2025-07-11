package net.minecraft.world.level.block.entity.trialspawner;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectList;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MobSpawnerData;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameterSets;

public class TrialSpawnerData {

    private static final String TAG_SPAWN_DATA = "spawn_data";
    private static final String TAG_NEXT_MOB_SPAWNS_AT = "next_mob_spawns_at";
    private static final int DELAY_BETWEEN_PLAYER_SCANS = 20;
    private static final int TRIAL_OMEN_PER_BAD_OMEN_LEVEL = 18000;
    public final Set<UUID> detectedPlayers = new HashSet();
    public final Set<UUID> currentMobs = new HashSet();
    long cooldownEndsAt;
    long nextMobSpawnsAt;
    int totalMobsSpawned;
    public Optional<MobSpawnerData> nextSpawnData = Optional.empty();
    Optional<ResourceKey<LootTable>> ejectingLootTable = Optional.empty();
    @Nullable
    private Entity displayEntity;
    @Nullable
    private WeightedList<ItemStack> dispensing;
    double spin;
    double oSpin;

    public TrialSpawnerData() {}

    public TrialSpawnerData.a pack() {
        return new TrialSpawnerData.a(Set.copyOf(this.detectedPlayers), Set.copyOf(this.currentMobs), this.cooldownEndsAt, this.nextMobSpawnsAt, this.totalMobsSpawned, this.nextSpawnData, this.ejectingLootTable);
    }

    public void apply(TrialSpawnerData.a trialspawnerdata_a) {
        this.detectedPlayers.clear();
        this.detectedPlayers.addAll(trialspawnerdata_a.detectedPlayers);
        this.currentMobs.clear();
        this.currentMobs.addAll(trialspawnerdata_a.currentMobs);
        this.cooldownEndsAt = trialspawnerdata_a.cooldownEndsAt;
        this.nextMobSpawnsAt = trialspawnerdata_a.nextMobSpawnsAt;
        this.totalMobsSpawned = trialspawnerdata_a.totalMobsSpawned;
        this.nextSpawnData = trialspawnerdata_a.nextSpawnData;
        this.ejectingLootTable = trialspawnerdata_a.ejectingLootTable;
    }

    public void reset() {
        this.currentMobs.clear();
        this.nextSpawnData = Optional.empty();
        this.resetStatistics();
    }

    public void resetStatistics() {
        this.detectedPlayers.clear();
        this.totalMobsSpawned = 0;
        this.nextMobSpawnsAt = 0L;
        this.cooldownEndsAt = 0L;
    }

    public boolean hasMobToSpawn(TrialSpawner trialspawner, RandomSource randomsource) {
        boolean flag = this.getOrCreateNextSpawnData(trialspawner, randomsource).getEntityToSpawn().getString("id").isPresent();

        return flag || !trialspawner.activeConfig().spawnPotentialsDefinition().isEmpty();
    }

    public boolean hasFinishedSpawningAllMobs(TrialSpawnerConfig trialspawnerconfig, int i) {
        return this.totalMobsSpawned >= trialspawnerconfig.calculateTargetTotalMobs(i);
    }

    public boolean haveAllCurrentMobsDied() {
        return this.currentMobs.isEmpty();
    }

    public boolean isReadyToSpawnNextMob(WorldServer worldserver, TrialSpawnerConfig trialspawnerconfig, int i) {
        return worldserver.getGameTime() >= this.nextMobSpawnsAt && this.currentMobs.size() < trialspawnerconfig.calculateTargetSimultaneousMobs(i);
    }

    public int countAdditionalPlayers(BlockPosition blockposition) {
        if (this.detectedPlayers.isEmpty()) {
            SystemUtils.logAndPauseIfInIde("Trial Spawner at " + String.valueOf(blockposition) + " has no detected players");
        }

        return Math.max(0, this.detectedPlayers.size() - 1);
    }

    public void tryDetectPlayers(WorldServer worldserver, BlockPosition blockposition, TrialSpawner trialspawner) {
        boolean flag = (blockposition.asLong() + worldserver.getGameTime()) % 20L != 0L;

        if (!flag) {
            if (!trialspawner.getState().equals(TrialSpawnerState.COOLDOWN) || !trialspawner.isOminous()) {
                List<UUID> list = trialspawner.getPlayerDetector().detect(worldserver, trialspawner.getEntitySelector(), blockposition, (double) trialspawner.getRequiredPlayerRange(), true);
                boolean flag1;

                if (!trialspawner.isOminous() && !list.isEmpty()) {
                    Optional<Pair<EntityHuman, Holder<MobEffectList>>> optional = findPlayerWithOminousEffect(worldserver, list);

                    optional.ifPresent((pair) -> {
                        EntityHuman entityhuman = (EntityHuman) pair.getFirst();

                        if (pair.getSecond() == MobEffects.BAD_OMEN) {
                            transformBadOmenIntoTrialOmen(entityhuman);
                        }

                        worldserver.levelEvent(3020, BlockPosition.containing(entityhuman.getEyePosition()), 0);
                        trialspawner.applyOminous(worldserver, blockposition);
                    });
                    flag1 = optional.isPresent();
                } else {
                    flag1 = false;
                }

                if (!trialspawner.getState().equals(TrialSpawnerState.COOLDOWN) || flag1) {
                    boolean flag2 = trialspawner.getStateData().detectedPlayers.isEmpty();
                    List<UUID> list1 = flag2 ? list : trialspawner.getPlayerDetector().detect(worldserver, trialspawner.getEntitySelector(), blockposition, (double) trialspawner.getRequiredPlayerRange(), false);

                    if (this.detectedPlayers.addAll(list1)) {
                        this.nextMobSpawnsAt = Math.max(worldserver.getGameTime() + 40L, this.nextMobSpawnsAt);
                        if (!flag1) {
                            int i = trialspawner.isOminous() ? 3019 : 3013;

                            worldserver.levelEvent(i, blockposition, this.detectedPlayers.size());
                        }
                    }

                }
            }
        }
    }

    private static Optional<Pair<EntityHuman, Holder<MobEffectList>>> findPlayerWithOminousEffect(WorldServer worldserver, List<UUID> list) {
        EntityHuman entityhuman = null;

        for (UUID uuid : list) {
            EntityHuman entityhuman1 = worldserver.getPlayerByUUID(uuid);

            if (entityhuman1 != null) {
                Holder<MobEffectList> holder = MobEffects.TRIAL_OMEN;

                if (entityhuman1.hasEffect(holder)) {
                    return Optional.of(Pair.of(entityhuman1, holder));
                }

                if (entityhuman1.hasEffect(MobEffects.BAD_OMEN)) {
                    entityhuman = entityhuman1;
                }
            }
        }

        return Optional.ofNullable(entityhuman).map((entityhuman2) -> {
            return Pair.of(entityhuman2, MobEffects.BAD_OMEN);
        });
    }

    public void resetAfterBecomingOminous(TrialSpawner trialspawner, WorldServer worldserver) {
        Stream<UUID> stream = this.currentMobs.stream(); // CraftBukkit - decompile error

        Objects.requireNonNull(worldserver);
        stream.map(worldserver::getEntity).forEach((entity) -> {
            if (entity != null) {
                worldserver.levelEvent(3012, entity.blockPosition(), TrialSpawner.a.NORMAL.encode());
                if (entity instanceof EntityInsentient) {
                    EntityInsentient entityinsentient = (EntityInsentient) entity;

                    entityinsentient.dropPreservedEquipment(worldserver);
                }

                entity.remove(Entity.RemovalReason.DISCARDED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - Add bukkit remove cause;
            }
        });
        if (!trialspawner.ominousConfig().spawnPotentialsDefinition().isEmpty()) {
            this.nextSpawnData = Optional.empty();
        }

        this.totalMobsSpawned = 0;
        this.currentMobs.clear();
        this.nextMobSpawnsAt = worldserver.getGameTime() + (long) trialspawner.ominousConfig().ticksBetweenSpawn();
        trialspawner.markUpdated();
        this.cooldownEndsAt = worldserver.getGameTime() + trialspawner.ominousConfig().ticksBetweenItemSpawners();
    }

    private static void transformBadOmenIntoTrialOmen(EntityHuman entityhuman) {
        MobEffect mobeffect = entityhuman.getEffect(MobEffects.BAD_OMEN);

        if (mobeffect != null) {
            int i = mobeffect.getAmplifier() + 1;
            int j = 18000 * i;

            entityhuman.removeEffect(MobEffects.BAD_OMEN);
            entityhuman.addEffect(new MobEffect(MobEffects.TRIAL_OMEN, j, 0));
        }
    }

    public boolean isReadyToOpenShutter(WorldServer worldserver, float f, int i) {
        long j = this.cooldownEndsAt - (long) i;

        return (float) worldserver.getGameTime() >= (float) j + f;
    }

    public boolean isReadyToEjectItems(WorldServer worldserver, float f, int i) {
        long j = this.cooldownEndsAt - (long) i;

        return (float) (worldserver.getGameTime() - j) % f == 0.0F;
    }

    public boolean isCooldownFinished(WorldServer worldserver) {
        return worldserver.getGameTime() >= this.cooldownEndsAt;
    }

    protected MobSpawnerData getOrCreateNextSpawnData(TrialSpawner trialspawner, RandomSource randomsource) {
        if (this.nextSpawnData.isPresent()) {
            return (MobSpawnerData) this.nextSpawnData.get();
        } else {
            WeightedList<MobSpawnerData> weightedlist = trialspawner.activeConfig().spawnPotentialsDefinition();
            Optional<MobSpawnerData> optional = weightedlist.isEmpty() ? this.nextSpawnData : weightedlist.getRandom(randomsource);

            this.nextSpawnData = Optional.of((MobSpawnerData) optional.orElseGet(MobSpawnerData::new));
            trialspawner.markUpdated();
            return (MobSpawnerData) this.nextSpawnData.get();
        }
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(TrialSpawner trialspawner, World world, TrialSpawnerState trialspawnerstate) {
        if (!trialspawnerstate.hasSpinningMob()) {
            return null;
        } else {
            if (this.displayEntity == null) {
                NBTTagCompound nbttagcompound = this.getOrCreateNextSpawnData(trialspawner, world.getRandom()).getEntityToSpawn();

                if (nbttagcompound.getString("id").isPresent()) {
                    this.displayEntity = EntityTypes.loadEntityRecursive(nbttagcompound, world, EntitySpawnReason.TRIAL_SPAWNER, Function.identity());
                }
            }

            return this.displayEntity;
        }
    }

    public NBTTagCompound getUpdateTag(TrialSpawnerState trialspawnerstate) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        if (trialspawnerstate == TrialSpawnerState.ACTIVE) {
            nbttagcompound.putLong("next_mob_spawns_at", this.nextMobSpawnsAt);
        }

        this.nextSpawnData.ifPresent((mobspawnerdata) -> {
            nbttagcompound.store("spawn_data", MobSpawnerData.CODEC, mobspawnerdata);
        });
        return nbttagcompound;
    }

    public double getSpin() {
        return this.spin;
    }

    public double getOSpin() {
        return this.oSpin;
    }

    WeightedList<ItemStack> getDispensingItems(WorldServer worldserver, TrialSpawnerConfig trialspawnerconfig, BlockPosition blockposition) {
        if (this.dispensing != null) {
            return this.dispensing;
        } else {
            LootTable loottable = worldserver.getServer().reloadableRegistries().getLootTable(trialspawnerconfig.itemsToDropWhenOminous());
            LootParams lootparams = (new LootParams.a(worldserver)).create(LootContextParameterSets.EMPTY);
            long i = lowResolutionPosition(worldserver, blockposition);
            ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams, i);

            if (objectarraylist.isEmpty()) {
                return WeightedList.<ItemStack>of();
            } else {
                WeightedList.a<ItemStack> weightedlist_a = WeightedList.<ItemStack>builder();
                ObjectListIterator objectlistiterator = objectarraylist.iterator();

                while (objectlistiterator.hasNext()) {
                    ItemStack itemstack = (ItemStack) objectlistiterator.next();

                    weightedlist_a.add(itemstack.copyWithCount(1), itemstack.getCount());
                }

                this.dispensing = weightedlist_a.build();
                return this.dispensing;
            }
        }
    }

    private static long lowResolutionPosition(WorldServer worldserver, BlockPosition blockposition) {
        BlockPosition blockposition1 = new BlockPosition(MathHelper.floor((float) blockposition.getX() / 30.0F), MathHelper.floor((float) blockposition.getY() / 20.0F), MathHelper.floor((float) blockposition.getZ() / 30.0F));

        return worldserver.getSeed() + blockposition1.asLong();
    }

    public static record a(Set<UUID> detectedPlayers, Set<UUID> currentMobs, long cooldownEndsAt, long nextMobSpawnsAt, int totalMobsSpawned, Optional<MobSpawnerData> nextSpawnData, Optional<ResourceKey<LootTable>> ejectingLootTable) {

        public static final MapCodec<TrialSpawnerData.a> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(UUIDUtil.CODEC_SET.lenientOptionalFieldOf("registered_players", Set.of()).forGetter(TrialSpawnerData.a::detectedPlayers), UUIDUtil.CODEC_SET.lenientOptionalFieldOf("current_mobs", Set.of()).forGetter(TrialSpawnerData.a::currentMobs), Codec.LONG.lenientOptionalFieldOf("cooldown_ends_at", 0L).forGetter(TrialSpawnerData.a::cooldownEndsAt), Codec.LONG.lenientOptionalFieldOf("next_mob_spawns_at", 0L).forGetter(TrialSpawnerData.a::nextMobSpawnsAt), Codec.intRange(0, Integer.MAX_VALUE).lenientOptionalFieldOf("total_mobs_spawned", 0).forGetter(TrialSpawnerData.a::totalMobsSpawned), MobSpawnerData.CODEC.lenientOptionalFieldOf("spawn_data").forGetter(TrialSpawnerData.a::nextSpawnData), LootTable.KEY_CODEC.lenientOptionalFieldOf("ejecting_loot_table").forGetter(TrialSpawnerData.a::ejectingLootTable)).apply(instance, TrialSpawnerData.a::new);
        });
    }
}
