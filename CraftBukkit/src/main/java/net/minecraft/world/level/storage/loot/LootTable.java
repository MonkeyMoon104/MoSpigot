package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.SystemUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.IInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionUser;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameterSets;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.world.LootGenerateEvent;
// CraftBukkit end

public class LootTable {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ResourceKey<LootTable>> KEY_CODEC = ResourceKey.codec(Registries.LOOT_TABLE);
    public static final ContextKeySet DEFAULT_PARAM_SET = LootContextParameterSets.ALL_PARAMS;
    public static final long RANDOMIZE_SEED = 0L;
    public static final Codec<LootTable> DIRECT_CODEC = Codec.lazyInitialized(() -> {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(LootContextParameterSets.CODEC.lenientOptionalFieldOf("type", LootTable.DEFAULT_PARAM_SET).forGetter((loottable) -> {
                return loottable.paramSet;
            }), MinecraftKey.CODEC.optionalFieldOf("random_sequence").forGetter((loottable) -> {
                return loottable.randomSequence;
            }), LootSelector.CODEC.listOf().optionalFieldOf("pools", List.of()).forGetter((loottable) -> {
                return loottable.pools;
            }), LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter((loottable) -> {
                return loottable.functions;
            })).apply(instance, LootTable::new);
        });
    });
    public static final Codec<Holder<LootTable>> CODEC = RegistryFileCodec.<LootTable>create(Registries.LOOT_TABLE, LootTable.DIRECT_CODEC); // CraftBukkit - decompile error
    public static final LootTable EMPTY = new LootTable(LootContextParameterSets.EMPTY, Optional.empty(), List.of(), List.of());
    private final ContextKeySet paramSet;
    private final Optional<MinecraftKey> randomSequence;
    private final List<LootSelector> pools;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootTableInfo, ItemStack> compositeFunction;
    public CraftLootTable craftLootTable; // CraftBukkit

    LootTable(ContextKeySet contextkeyset, Optional<MinecraftKey> optional, List<LootSelector> list, List<LootItemFunction> list1) {
        this.paramSet = contextkeyset;
        this.randomSequence = optional;
        this.pools = list;
        this.functions = list1;
        this.compositeFunction = LootItemFunctions.compose(list1);
    }

    public static Consumer<ItemStack> createStackSplitter(WorldServer worldserver, Consumer<ItemStack> consumer) {
        return (itemstack) -> {
            if (itemstack.isItemEnabled(worldserver.enabledFeatures())) {
                if (itemstack.getCount() < itemstack.getMaxStackSize()) {
                    consumer.accept(itemstack);
                } else {
                    int i = itemstack.getCount();

                    while (i > 0) {
                        ItemStack itemstack1 = itemstack.copyWithCount(Math.min(itemstack.getMaxStackSize(), i));

                        i -= itemstack1.getCount();
                        consumer.accept(itemstack1);
                    }
                }

            }
        };
    }

    public void getRandomItemsRaw(LootParams lootparams, Consumer<ItemStack> consumer) {
        this.getRandomItemsRaw((new LootTableInfo.Builder(lootparams)).create(this.randomSequence), consumer);
    }

    public void getRandomItemsRaw(LootTableInfo loottableinfo, Consumer<ItemStack> consumer) {
        LootTableInfo.c<?> loottableinfo_c = LootTableInfo.createVisitedEntry(this);

        if (loottableinfo.pushVisitedElement(loottableinfo_c)) {
            Consumer<ItemStack> consumer1 = LootItemFunction.decorate(this.compositeFunction, consumer, loottableinfo);

            for (LootSelector lootselector : this.pools) {
                lootselector.addRandomItems(consumer1, loottableinfo);
            }

            loottableinfo.popVisitedElement(loottableinfo_c);
        } else {
            LootTable.LOGGER.warn("Detected infinite loop in loot tables");
        }

    }

    public void getRandomItems(LootParams lootparams, long i, Consumer<ItemStack> consumer) {
        this.getRandomItemsRaw((new LootTableInfo.Builder(lootparams)).withOptionalRandomSeed(i).create(this.randomSequence), createStackSplitter(lootparams.getLevel(), consumer));
    }

    public void getRandomItems(LootParams lootparams, Consumer<ItemStack> consumer) {
        this.getRandomItemsRaw(lootparams, createStackSplitter(lootparams.getLevel(), consumer));
    }

    public void getRandomItems(LootTableInfo loottableinfo, Consumer<ItemStack> consumer) {
        this.getRandomItemsRaw(loottableinfo, createStackSplitter(loottableinfo.getLevel(), consumer));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams lootparams, RandomSource randomsource) {
        return this.getRandomItems((new LootTableInfo.Builder(lootparams)).withOptionalRandomSource(randomsource).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams lootparams, long i) {
        return this.getRandomItems((new LootTableInfo.Builder(lootparams)).withOptionalRandomSeed(i).create(this.randomSequence));
    }

    public ObjectArrayList<ItemStack> getRandomItems(LootParams lootparams) {
        return this.getRandomItems((new LootTableInfo.Builder(lootparams)).create(this.randomSequence));
    }

    private ObjectArrayList<ItemStack> getRandomItems(LootTableInfo loottableinfo) {
        ObjectArrayList<ItemStack> objectarraylist = new ObjectArrayList();

        Objects.requireNonNull(objectarraylist);
        this.getRandomItems(loottableinfo, objectarraylist::add);
        return objectarraylist;
    }

    public ContextKeySet getParamSet() {
        return this.paramSet;
    }

    public void validate(LootCollector lootcollector) {
        for (int i = 0; i < this.pools.size(); ++i) {
            ((LootSelector) this.pools.get(i)).validate(lootcollector.forChild(new ProblemReporter.d("pools", i)));
        }

        for (int j = 0; j < this.functions.size(); ++j) {
            ((LootItemFunction) this.functions.get(j)).validate(lootcollector.forChild(new ProblemReporter.d("functions", j)));
        }

    }

    public void fill(IInventory iinventory, LootParams lootparams, long i) {
        // CraftBukkit start
        this.fillInventory(iinventory, lootparams, i, false);
    }

    public void fillInventory(IInventory iinventory, LootParams lootparams, long i, boolean plugin) {
        // CraftBukkit end
        LootTableInfo loottableinfo = (new LootTableInfo.Builder(lootparams)).withOptionalRandomSeed(i).create(this.randomSequence);
        ObjectArrayList<ItemStack> objectarraylist = this.getRandomItems(loottableinfo);
        RandomSource randomsource = loottableinfo.getRandom();
        // CraftBukkit start
        LootGenerateEvent event = CraftEventFactory.callLootGenerateEvent(iinventory, this, loottableinfo, objectarraylist, plugin);
        if (event.isCancelled()) {
            return;
        }
        objectarraylist = event.getLoot().stream().map(CraftItemStack::asNMSCopy).collect(ObjectArrayList.toList());
        // CraftBukkit end
        List<Integer> list = this.getAvailableSlots(iinventory, randomsource);

        this.shuffleAndSplitItems(objectarraylist, list.size(), randomsource);
        ObjectListIterator objectlistiterator = objectarraylist.iterator();

        while (objectlistiterator.hasNext()) {
            ItemStack itemstack = (ItemStack) objectlistiterator.next();

            if (list.isEmpty()) {
                LootTable.LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (itemstack.isEmpty()) {
                iinventory.setItem((Integer) list.remove(list.size() - 1), ItemStack.EMPTY);
            } else {
                iinventory.setItem((Integer) list.remove(list.size() - 1), itemstack);
            }
        }

    }

    private void shuffleAndSplitItems(ObjectArrayList<ItemStack> objectarraylist, int i, RandomSource randomsource) {
        List<ItemStack> list = Lists.newArrayList();
        Iterator<ItemStack> iterator = objectarraylist.iterator();

        while (((Iterator) iterator).hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (itemstack.isEmpty()) {
                iterator.remove();
            } else if (itemstack.getCount() > 1) {
                list.add(itemstack);
                iterator.remove();
            }
        }

        while (i - objectarraylist.size() - ((List) list).size() > 0 && !((List) list).isEmpty()) {
            ItemStack itemstack1 = (ItemStack) list.remove(MathHelper.nextInt(randomsource, 0, list.size() - 1));
            int j = MathHelper.nextInt(randomsource, 1, itemstack1.getCount() / 2);
            ItemStack itemstack2 = itemstack1.split(j);

            if (itemstack1.getCount() > 1 && randomsource.nextBoolean()) {
                list.add(itemstack1);
            } else {
                objectarraylist.add(itemstack1);
            }

            if (itemstack2.getCount() > 1 && randomsource.nextBoolean()) {
                list.add(itemstack2);
            } else {
                objectarraylist.add(itemstack2);
            }
        }

        objectarraylist.addAll(list);
        SystemUtils.shuffle(objectarraylist, randomsource);
    }

    private List<Integer> getAvailableSlots(IInventory iinventory, RandomSource randomsource) {
        ObjectArrayList<Integer> objectarraylist = new ObjectArrayList();

        for (int i = 0; i < iinventory.getContainerSize(); ++i) {
            if (iinventory.getItem(i).isEmpty()) {
                objectarraylist.add(i);
            }
        }

        SystemUtils.shuffle(objectarraylist, randomsource);
        return objectarraylist;
    }

    public static LootTable.a lootTable() {
        return new LootTable.a();
    }

    public static class a implements LootItemFunctionUser<LootTable.a> {

        private final ImmutableList.Builder<LootSelector> pools = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private ContextKeySet paramSet;
        private Optional<MinecraftKey> randomSequence;

        public a() {
            this.paramSet = LootTable.DEFAULT_PARAM_SET;
            this.randomSequence = Optional.empty();
        }

        public LootTable.a withPool(LootSelector.a lootselector_a) {
            this.pools.add(lootselector_a.build());
            return this;
        }

        public LootTable.a setParamSet(ContextKeySet contextkeyset) {
            this.paramSet = contextkeyset;
            return this;
        }

        public LootTable.a setRandomSequence(MinecraftKey minecraftkey) {
            this.randomSequence = Optional.of(minecraftkey);
            return this;
        }

        @Override
        public LootTable.a apply(LootItemFunction.a lootitemfunction_a) {
            this.functions.add(lootitemfunction_a.build());
            return this;
        }

        @Override
        public LootTable.a unwrap() {
            return this;
        }

        public LootTable build() {
            return new LootTable(this.paramSet, this.randomSequence, this.pools.build(), this.functions.build());
        }
    }
}
