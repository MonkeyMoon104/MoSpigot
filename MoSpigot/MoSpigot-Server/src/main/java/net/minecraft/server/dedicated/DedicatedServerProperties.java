package net.minecraft.server.dedicated;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatDeserializer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.level.DataPackConfiguration;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.ChunkProviderFlat;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.GeneratorSettingsFlat;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

// CraftBukkit start
import joptsimple.OptionSet;
// CraftBukkit end

public class DedicatedServerProperties extends PropertyManager<DedicatedServerProperties> {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern SHA1 = Pattern.compile("^[a-fA-F0-9]{40}$");
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults();
    public final boolean debug = this.get("debug", false); // CraftBukkit
    public final boolean onlineMode = this.get("online-mode", true);
    public final boolean preventProxyConnections = this.get("prevent-proxy-connections", false);
    public final String serverIp = this.get("server-ip", "");
    public final boolean pvp = this.get("pvp", true);
    public final boolean allowFlight = this.get("allow-flight", false);
    public final String motd = this.get("motd", "A Minecraft Server");
    public final String bugReportLink = this.get("bug-report-link", "");
    public final boolean forceGameMode = this.get("force-gamemode", false);
    public final boolean enforceWhitelist = this.get("enforce-whitelist", false);
    public final EnumDifficulty difficulty;
    public final EnumGamemode gamemode;
    public final String levelName;
    public final int serverPort;
    @Nullable
    public final Boolean announcePlayerAchievements;
    public final boolean enableQuery;
    public final int queryPort;
    public final boolean enableRcon;
    public final int rconPort;
    public final String rconPassword;
    public final boolean hardcore;
    public final boolean allowNether;
    public final boolean spawnMonsters;
    public final boolean useNativeTransport;
    public final boolean enableCommandBlock;
    public final int spawnProtection;
    public final int opPermissionLevel;
    public final int functionPermissionLevel;
    public final long maxTickTime;
    public final int maxChainedNeighborUpdates;
    public final int rateLimitPacketsPerSecond;
    public final int viewDistance;
    public final int simulationDistance;
    public final int maxPlayers;
    public final int networkCompressionThreshold;
    public final boolean broadcastRconToOps;
    public final boolean broadcastConsoleToOps;
    public final int maxWorldSize;
    public final boolean syncChunkWrites;
    public final String regionFileComression;
    public final boolean enableJmxMonitoring;
    public final boolean enableStatus;
    public final boolean hideOnlinePlayers;
    public final int entityBroadcastRangePercentage;
    public final String textFilteringConfig;
    public final int textFilteringVersion;
    public final Optional<MinecraftServer.ServerResourcePackInfo> serverResourcePackInfo;
    public final DataPackConfiguration initialDataPackConfiguration;
    public final PropertyManager<DedicatedServerProperties>.EditableProperty<Integer> playerIdleTimeout;
    public final PropertyManager<DedicatedServerProperties>.EditableProperty<Boolean> whiteList;
    public final boolean enforceSecureProfile;
    public final boolean logIPs;
    public int pauseWhenEmptySeconds;
    private final DedicatedServerProperties.WorldDimensionData worldDimensionData;
    public final WorldOptions worldOptions;
    public boolean acceptsTransfers;

    // CraftBukkit start
    public DedicatedServerProperties(Properties properties, OptionSet optionset) {
        super(properties, optionset);
        // CraftBukkit end
        this.difficulty = (EnumDifficulty) this.get("difficulty", dispatchNumberOrString(EnumDifficulty::byId, EnumDifficulty::byName), EnumDifficulty::getKey, EnumDifficulty.EASY);
        this.gamemode = (EnumGamemode) this.get("gamemode", dispatchNumberOrString(EnumGamemode::byId, EnumGamemode::byName), EnumGamemode::getName, EnumGamemode.SURVIVAL);
        this.levelName = this.get("level-name", "world");
        this.serverPort = this.get("server-port", 25565);
        this.announcePlayerAchievements = this.getLegacyBoolean("announce-player-achievements");
        this.enableQuery = this.get("enable-query", false);
        this.queryPort = this.get("query.port", 25565);
        this.enableRcon = this.get("enable-rcon", false);
        this.rconPort = this.get("rcon.port", 25575);
        this.rconPassword = this.get("rcon.password", "");
        this.hardcore = this.get("hardcore", false);
        this.allowNether = this.get("allow-nether", true);
        this.spawnMonsters = this.get("spawn-monsters", true);
        this.useNativeTransport = this.get("use-native-transport", true);
        this.enableCommandBlock = this.get("enable-command-block", false);
        this.spawnProtection = this.get("spawn-protection", 16);
        this.opPermissionLevel = this.get("op-permission-level", 4);
        this.functionPermissionLevel = this.get("function-permission-level", 2);
        this.maxTickTime = this.get("max-tick-time", TimeUnit.MINUTES.toMillis(1L));
        this.maxChainedNeighborUpdates = this.get("max-chained-neighbor-updates", 1000000);
        this.rateLimitPacketsPerSecond = this.get("rate-limit", 0);
        this.viewDistance = this.get("view-distance", 10);
        this.simulationDistance = this.get("simulation-distance", 10);
        this.maxPlayers = this.get("max-players", 20);
        this.networkCompressionThreshold = this.get("network-compression-threshold", 256);
        this.broadcastRconToOps = this.get("broadcast-rcon-to-ops", true);
        this.broadcastConsoleToOps = this.get("broadcast-console-to-ops", true);
        this.maxWorldSize = this.get("max-world-size", (integer) -> {
            return MathHelper.clamp(integer, 1, 29999984);
        }, 29999984);
        this.syncChunkWrites = this.get("sync-chunk-writes", true);
        this.regionFileComression = this.get("region-file-compression", "deflate");
        this.enableJmxMonitoring = this.get("enable-jmx-monitoring", false);
        this.enableStatus = this.get("enable-status", true);
        this.hideOnlinePlayers = this.get("hide-online-players", false);
        this.entityBroadcastRangePercentage = this.get("entity-broadcast-range-percentage", (integer) -> {
            return MathHelper.clamp(integer, 10, 1000);
        }, 100);
        this.textFilteringConfig = this.get("text-filtering-config", "");
        this.textFilteringVersion = this.get("text-filtering-version", 0);
        this.playerIdleTimeout = this.getMutable("player-idle-timeout", 0);
        this.whiteList = this.getMutable("white-list", false);
        this.enforceSecureProfile = this.get("enforce-secure-profile", true);
        this.logIPs = this.get("log-ips", true);
        this.pauseWhenEmptySeconds = this.get("pause-when-empty-seconds", 60);
        this.acceptsTransfers = this.get("accepts-transfers", false);
        String s = this.get("level-seed", "");
        boolean flag = this.get("generate-structures", true);
        long i = WorldOptions.parseSeed(s).orElse(WorldOptions.randomSeed());

        this.worldOptions = new WorldOptions(i, flag, false);
        this.worldDimensionData = new DedicatedServerProperties.WorldDimensionData((JsonObject) this.get("generator-settings", (s1) -> {
            return ChatDeserializer.parse(!s1.isEmpty() ? s1 : "{}");
        }, new JsonObject()), (String) this.get("level-type", (s1) -> {
            return s1.toLowerCase(Locale.ROOT);
        }, WorldPresets.NORMAL.location().toString()));
        this.serverResourcePackInfo = getServerPackInfo(this.get("resource-pack-id", ""), this.get("resource-pack", ""), this.get("resource-pack-sha1", ""), this.getLegacyString("resource-pack-hash"), this.get("require-resource-pack", false), this.get("resource-pack-prompt", ""));
        this.initialDataPackConfiguration = getDatapackConfig(this.get("initial-enabled-packs", String.join(",", WorldDataConfiguration.DEFAULT.dataPacks().getEnabled())), this.get("initial-disabled-packs", String.join(",", WorldDataConfiguration.DEFAULT.dataPacks().getDisabled())));
    }

    // CraftBukkit start
    public static DedicatedServerProperties fromFile(Path path, OptionSet optionset) {
        return new DedicatedServerProperties(loadFromFile(path), optionset);
    }

    @Override
    protected DedicatedServerProperties reload(IRegistryCustom iregistrycustom, Properties properties, OptionSet optionset) {
        return new DedicatedServerProperties(properties, optionset);
        // CraftBukkit end
    }

    @Nullable
    private static IChatBaseComponent parseResourcePackPrompt(String s) {
        if (!Strings.isNullOrEmpty(s)) {
            try {
                JsonElement jsonelement = StrictJsonParser.parse(s);

                return (IChatBaseComponent) ComponentSerialization.CODEC.parse(IRegistryCustom.EMPTY.createSerializationContext(JsonOps.INSTANCE), jsonelement).resultOrPartial((s1) -> {
                    DedicatedServerProperties.LOGGER.warn("Failed to parse resource pack prompt '{}': {}", s, s1);
                }).orElse(null); // CraftBukkit - decompile error
            } catch (Exception exception) {
                DedicatedServerProperties.LOGGER.warn("Failed to parse resource pack prompt '{}'", s, exception);
            }
        }

        return null;
    }

    private static Optional<MinecraftServer.ServerResourcePackInfo> getServerPackInfo(String s, String s1, String s2, @Nullable String s3, boolean flag, String s4) {
        if (s1.isEmpty()) {
            return Optional.empty();
        } else {
            String s5;

            if (!s2.isEmpty()) {
                s5 = s2;
                if (!Strings.isNullOrEmpty(s3)) {
                    DedicatedServerProperties.LOGGER.warn("resource-pack-hash is deprecated and found along side resource-pack-sha1. resource-pack-hash will be ignored.");
                }
            } else if (!Strings.isNullOrEmpty(s3)) {
                DedicatedServerProperties.LOGGER.warn("resource-pack-hash is deprecated. Please use resource-pack-sha1 instead.");
                s5 = s3;
            } else {
                s5 = "";
            }

            if (s5.isEmpty()) {
                DedicatedServerProperties.LOGGER.warn("You specified a resource pack without providing a sha1 hash. Pack will be updated on the client only if you change the name of the pack.");
            } else if (!DedicatedServerProperties.SHA1.matcher(s5).matches()) {
                DedicatedServerProperties.LOGGER.warn("Invalid sha1 for resource-pack-sha1");
            }

            IChatBaseComponent ichatbasecomponent = parseResourcePackPrompt(s4);
            UUID uuid;

            if (s.isEmpty()) {
                uuid = UUID.nameUUIDFromBytes(s1.getBytes(StandardCharsets.UTF_8));
                DedicatedServerProperties.LOGGER.warn("resource-pack-id missing, using default of {}", uuid);
            } else {
                try {
                    uuid = UUID.fromString(s);
                } catch (IllegalArgumentException illegalargumentexception) {
                    DedicatedServerProperties.LOGGER.warn("Failed to parse '{}' into UUID", s);
                    return Optional.empty();
                }
            }

            return Optional.of(new MinecraftServer.ServerResourcePackInfo(uuid, s1, s5, flag, ichatbasecomponent));
        }
    }

    private static DataPackConfiguration getDatapackConfig(String s, String s1) {
        List<String> list = DedicatedServerProperties.COMMA_SPLITTER.splitToList(s);
        List<String> list1 = DedicatedServerProperties.COMMA_SPLITTER.splitToList(s1);

        return new DataPackConfiguration(list, list1);
    }

    public WorldDimensions createDimensions(HolderLookup.a holderlookup_a) {
        return this.worldDimensionData.create(holderlookup_a);
    }

    public static record WorldDimensionData(JsonObject generatorSettings, String levelType) {

        private static final Map<String, ResourceKey<WorldPreset>> LEGACY_PRESET_NAMES = Map.of("default", WorldPresets.NORMAL, "largebiomes", WorldPresets.LARGE_BIOMES);

        public WorldDimensions create(HolderLookup.a holderlookup_a) {
            HolderLookup<WorldPreset> holderlookup = holderlookup_a.lookupOrThrow(Registries.WORLD_PRESET);
            Holder.c<WorldPreset> holder_c = (Holder.c) holderlookup.get(WorldPresets.NORMAL).or(() -> {
                return holderlookup.listElements().findAny();
            }).orElseThrow(() -> {
                return new IllegalStateException("Invalid datapack contents: can't find default preset");
            });
            Optional<ResourceKey<WorldPreset>> optional = Optional.ofNullable(MinecraftKey.tryParse(this.levelType)).map((minecraftkey) -> { // CraftBukkit - decompile error
                return ResourceKey.create(Registries.WORLD_PRESET, minecraftkey);
            }).or(() -> {
                return Optional.ofNullable(DedicatedServerProperties.WorldDimensionData.LEGACY_PRESET_NAMES.get(this.levelType)); // CraftBukkit - decompile error
            });

            Objects.requireNonNull(holderlookup);
            Holder<WorldPreset> holder = (Holder) optional.flatMap(holderlookup::get).orElseGet(() -> {
                DedicatedServerProperties.LOGGER.warn("Failed to parse level-type {}, defaulting to {}", this.levelType, holder_c.key().location());
                return holder_c;
            });
            WorldDimensions worlddimensions = ((WorldPreset) holder.value()).createWorldDimensions();

            if (holder.is(WorldPresets.FLAT)) {
                RegistryOps<JsonElement> registryops = holderlookup_a.<JsonElement>createSerializationContext(JsonOps.INSTANCE);
                DataResult<GeneratorSettingsFlat> dataresult = GeneratorSettingsFlat.CODEC.parse(new Dynamic(registryops, this.generatorSettings())); // CraftBukkit - decompile error
                Logger logger = DedicatedServerProperties.LOGGER;

                Objects.requireNonNull(logger);
                Optional<GeneratorSettingsFlat> optional1 = dataresult.resultOrPartial(logger::error);

                if (optional1.isPresent()) {
                    return worlddimensions.replaceOverworldGenerator(holderlookup_a, new ChunkProviderFlat((GeneratorSettingsFlat) optional1.get()));
                }
            }

            return worlddimensions;
        }
    }
}
