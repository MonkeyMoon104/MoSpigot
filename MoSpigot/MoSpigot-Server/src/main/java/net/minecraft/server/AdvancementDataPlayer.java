package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionInstance;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.PacketPlayOutAdvancements;
import net.minecraft.network.protocol.game.PacketPlayOutSelectAdvancementTab;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

public class AdvancementDataPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final PlayerList playerList;
    private final Path playerSavePath;
    private AdvancementTree tree;
    private final Map<AdvancementHolder, AdvancementProgress> progress = new LinkedHashMap();
    private final Set<AdvancementHolder> visible = new HashSet();
    private final Set<AdvancementHolder> progressChanged = new HashSet();
    private final Set<AdvancementNode> rootsToUpdate = new HashSet();
    private EntityPlayer player;
    @Nullable
    private AdvancementHolder lastSelectedTab;
    private boolean isFirstPacket = true;
    private final Codec<AdvancementDataPlayer.a> codec;

    public AdvancementDataPlayer(DataFixer datafixer, PlayerList playerlist, AdvancementDataWorld advancementdataworld, Path path, EntityPlayer entityplayer) {
        this.playerList = playerlist;
        this.playerSavePath = path;
        this.player = entityplayer;
        this.tree = advancementdataworld.tree();
        int i = 1343;

        this.codec = DataFixTypes.ADVANCEMENTS.<AdvancementDataPlayer.a>wrapCodec(AdvancementDataPlayer.a.CODEC, datafixer, 1343);
        this.load(advancementdataworld);
    }

    public void setPlayer(EntityPlayer entityplayer) {
        this.player = entityplayer;
    }

    public void stopListening() {
        for (CriterionTrigger<?> criteriontrigger : BuiltInRegistries.TRIGGER_TYPES) {
            criteriontrigger.removePlayerListeners(this);
        }

    }

    public void reload(AdvancementDataWorld advancementdataworld) {
        this.stopListening();
        this.progress.clear();
        this.visible.clear();
        this.rootsToUpdate.clear();
        this.progressChanged.clear();
        this.isFirstPacket = true;
        this.lastSelectedTab = null;
        this.tree = advancementdataworld.tree();
        this.load(advancementdataworld);
    }

    private void registerListeners(AdvancementDataWorld advancementdataworld) {
        for (AdvancementHolder advancementholder : advancementdataworld.getAllAdvancements()) {
            this.registerListeners(advancementholder);
        }

    }

    private void checkForAutomaticTriggers(AdvancementDataWorld advancementdataworld) {
        for (AdvancementHolder advancementholder : advancementdataworld.getAllAdvancements()) {
            Advancement advancement = advancementholder.value();

            if (advancement.criteria().isEmpty()) {
                this.award(advancementholder, "");
                advancement.rewards().grant(this.player);
            }
        }

    }

    private void load(AdvancementDataWorld advancementdataworld) {
        if (Files.isRegularFile(this.playerSavePath, new LinkOption[0])) {
            try (Reader reader = Files.newBufferedReader(this.playerSavePath, StandardCharsets.UTF_8)) {
                JsonElement jsonelement = StrictJsonParser.parse(reader);
                AdvancementDataPlayer.a advancementdataplayer_a = (AdvancementDataPlayer.a) this.codec.parse(JsonOps.INSTANCE, jsonelement).getOrThrow(JsonParseException::new);

                this.applyFrom(advancementdataworld, advancementdataplayer_a);
            } catch (JsonIOException | IOException ioexception) {
                AdvancementDataPlayer.LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, ioexception);
            } catch (JsonParseException jsonparseexception) {
                AdvancementDataPlayer.LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, jsonparseexception);
            }
        }

        this.checkForAutomaticTriggers(advancementdataworld);
        this.registerListeners(advancementdataworld);
    }

    public void save() {
        if (org.mospigot.config.MoSpigotConfig.disableAdvancementSaving) return; // Spigot
        JsonElement jsonelement = (JsonElement) this.codec.encodeStart(JsonOps.INSTANCE, this.asData()).getOrThrow();

        try {
            FileUtils.createDirectoriesSafe(this.playerSavePath.getParent());

            try (Writer writer = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8)) {
                AdvancementDataPlayer.GSON.toJson(jsonelement, AdvancementDataPlayer.GSON.newJsonWriter(writer));
            }
        } catch (JsonIOException | IOException ioexception) {
            AdvancementDataPlayer.LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, ioexception);
        }

    }

    private void applyFrom(AdvancementDataWorld advancementdataworld, AdvancementDataPlayer.a advancementdataplayer_a) {
        advancementdataplayer_a.forEach((minecraftkey, advancementprogress) -> {
            AdvancementHolder advancementholder = advancementdataworld.get(minecraftkey);

            if (advancementholder == null) {
                if (!minecraftkey.getNamespace().equals("minecraft")) return; // CraftBukkit
                AdvancementDataPlayer.LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", minecraftkey, this.playerSavePath);
            } else {
                this.startProgress(advancementholder, advancementprogress);
                this.progressChanged.add(advancementholder);
                this.markForVisibilityUpdate(advancementholder);
            }
        });
    }

    private AdvancementDataPlayer.a asData() {
        Map<MinecraftKey, AdvancementProgress> map = new LinkedHashMap();

        this.progress.forEach((advancementholder, advancementprogress) -> {
            if (advancementprogress.hasProgress()) {
                map.put(advancementholder.id(), advancementprogress);
            }

        });
        return new AdvancementDataPlayer.a(map);
    }

    public boolean award(AdvancementHolder advancementholder, String s) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancementholder);
        boolean flag1 = advancementprogress.isDone();

        if (advancementprogress.grantProgress(s)) {
            this.unregisterListeners(advancementholder);
            this.progressChanged.add(advancementholder);
            flag = true;
            if (!flag1 && advancementprogress.isDone()) {
                this.player.level().getCraftServer().getPluginManager().callEvent(new org.bukkit.event.player.PlayerAdvancementDoneEvent(this.player.getBukkitEntity(), advancementholder.toBukkit())); // CraftBukkit
                advancementholder.value().rewards().grant(this.player);
                advancementholder.value().display().ifPresent((advancementdisplay) -> {
                    if (advancementdisplay.shouldAnnounceChat() && this.player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
                        this.playerList.broadcastSystemMessage(advancementdisplay.getType().createAnnouncement(advancementholder, this.player), false);
                    }

                });
            }
        }

        if (!flag1 && advancementprogress.isDone()) {
            this.markForVisibilityUpdate(advancementholder);
        }

        return flag;
    }

    public boolean revoke(AdvancementHolder advancementholder, String s) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancementholder);
        boolean flag1 = advancementprogress.isDone();

        if (advancementprogress.revokeProgress(s)) {
            this.registerListeners(advancementholder);
            this.progressChanged.add(advancementholder);
            flag = true;
        }

        if (flag1 && !advancementprogress.isDone()) {
            this.markForVisibilityUpdate(advancementholder);
        }

        return flag;
    }

    private void markForVisibilityUpdate(AdvancementHolder advancementholder) {
        AdvancementNode advancementnode = this.tree.get(advancementholder);

        if (advancementnode != null) {
            this.rootsToUpdate.add(advancementnode.root());
        }

    }

    private void registerListeners(AdvancementHolder advancementholder) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancementholder);

        if (!advancementprogress.isDone()) {
            for (Map.Entry<String, Criterion<?>> map_entry : advancementholder.value().criteria().entrySet()) {
                CriterionProgress criterionprogress = advancementprogress.getCriterion((String) map_entry.getKey());

                if (criterionprogress != null && !criterionprogress.isDone()) {
                    this.registerListener(advancementholder, (String) map_entry.getKey(), (Criterion) map_entry.getValue());
                }
            }

        }
    }

    private <T extends CriterionInstance> void registerListener(AdvancementHolder advancementholder, String s, Criterion<T> criterion) {
        criterion.trigger().addPlayerListener(this, new CriterionTrigger.a(criterion.triggerInstance(), advancementholder, s));
    }

    private void unregisterListeners(AdvancementHolder advancementholder) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancementholder);

        for (Map.Entry<String, Criterion<?>> map_entry : advancementholder.value().criteria().entrySet()) {
            CriterionProgress criterionprogress = advancementprogress.getCriterion((String) map_entry.getKey());

            if (criterionprogress != null && (criterionprogress.isDone() || advancementprogress.isDone())) {
                this.removeListener(advancementholder, (String) map_entry.getKey(), (Criterion) map_entry.getValue());
            }
        }

    }

    private <T extends CriterionInstance> void removeListener(AdvancementHolder advancementholder, String s, Criterion<T> criterion) {
        criterion.trigger().removePlayerListener(this, new CriterionTrigger.a(criterion.triggerInstance(), advancementholder, s));
    }

    public void flushDirty(EntityPlayer entityplayer, boolean flag) {
        if (this.isFirstPacket || !this.rootsToUpdate.isEmpty() || !this.progressChanged.isEmpty()) {
            Map<MinecraftKey, AdvancementProgress> map = new HashMap();
            Set<AdvancementHolder> set = new HashSet();
            Set<MinecraftKey> set1 = new HashSet();

            for (AdvancementNode advancementnode : this.rootsToUpdate) {
                this.updateTreeVisibility(advancementnode, set, set1);
            }

            this.rootsToUpdate.clear();

            for (AdvancementHolder advancementholder : this.progressChanged) {
                if (this.visible.contains(advancementholder)) {
                    map.put(advancementholder.id(), (AdvancementProgress) this.progress.get(advancementholder));
                }
            }

            this.progressChanged.clear();
            if (!map.isEmpty() || !set.isEmpty() || !set1.isEmpty()) {
                entityplayer.connection.send(new PacketPlayOutAdvancements(this.isFirstPacket, set, set1, map, flag));
            }
        }

        this.isFirstPacket = false;
    }

    public void setSelectedTab(@Nullable AdvancementHolder advancementholder) {
        AdvancementHolder advancementholder1 = this.lastSelectedTab;

        if (advancementholder != null && advancementholder.value().isRoot() && advancementholder.value().display().isPresent()) {
            this.lastSelectedTab = advancementholder;
        } else {
            this.lastSelectedTab = null;
        }

        if (advancementholder1 != this.lastSelectedTab) {
            this.player.connection.send(new PacketPlayOutSelectAdvancementTab(this.lastSelectedTab == null ? null : this.lastSelectedTab.id()));
        }

    }

    public AdvancementProgress getOrStartProgress(AdvancementHolder advancementholder) {
        AdvancementProgress advancementprogress = (AdvancementProgress) this.progress.get(advancementholder);

        if (advancementprogress == null) {
            advancementprogress = new AdvancementProgress();
            this.startProgress(advancementholder, advancementprogress);
        }

        return advancementprogress;
    }

    private void startProgress(AdvancementHolder advancementholder, AdvancementProgress advancementprogress) {
        advancementprogress.update(advancementholder.value().requirements());
        this.progress.put(advancementholder, advancementprogress);
    }

    private void updateTreeVisibility(AdvancementNode advancementnode, Set<AdvancementHolder> set, Set<MinecraftKey> set1) {
        AdvancementVisibilityEvaluator.evaluateVisibility(advancementnode, (advancementnode1) -> {
            return this.getOrStartProgress(advancementnode1.holder()).isDone();
        }, (advancementnode1, flag) -> {
            AdvancementHolder advancementholder = advancementnode1.holder();

            if (flag) {
                if (this.visible.add(advancementholder)) {
                    set.add(advancementholder);
                    if (this.progress.containsKey(advancementholder)) {
                        this.progressChanged.add(advancementholder);
                    }
                }
            } else if (this.visible.remove(advancementholder)) {
                set1.add(advancementholder.id());
            }

        });
    }

    private static record a(Map<MinecraftKey, AdvancementProgress> map) {

        public static final Codec<AdvancementDataPlayer.a> CODEC = Codec.unboundedMap(MinecraftKey.CODEC, AdvancementProgress.CODEC).xmap(AdvancementDataPlayer.a::new, AdvancementDataPlayer.a::map);

        public void forEach(BiConsumer<MinecraftKey, AdvancementProgress> biconsumer) {
            this.map.entrySet().stream().sorted(Entry.comparingByValue()).forEach((entry) -> {
                biconsumer.accept((MinecraftKey) entry.getKey(), (AdvancementProgress) entry.getValue());
            });
        }
    }
}
