package net.minecraft.server;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.jtracy.DiscontinuousFrame;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.FileUtils;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.SystemUtils;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.ICommandListener;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestHarnessTicker;
import net.minecraft.nbt.NBTBase;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatMessageType;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.PacketPlayOutServerDifficulty;
import net.minecraft.network.protocol.game.PacketPlayOutUpdateTime;
import net.minecraft.network.protocol.status.ServerPing;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.bossevents.BossBattleCustomData;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.server.level.DemoPlayerInteractManager;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.PlayerInteractManager;
import net.minecraft.server.level.WorldProviderNormal;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.level.progress.WorldLoadListener;
import net.minecraft.server.level.progress.WorldLoadListenerFactory;
import net.minecraft.server.network.ITextFilter;
import net.minecraft.server.network.ServerConnection;
import net.minecraft.server.packs.EnumResourcePackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ResourcePackLoader;
import net.minecraft.server.packs.repository.ResourcePackRepository;
import net.minecraft.server.packs.resources.IReloadableResourceManager;
import net.minecraft.server.packs.resources.IResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.OpListEntry;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserCache;
import net.minecraft.server.players.WhiteList;
import net.minecraft.tags.TagDataPack;
import net.minecraft.util.CryptographyException;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MinecraftEncryption;
import net.minecraft.util.ModCheck;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.TimeRange;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.GameProfilerTick;
import net.minecraft.util.profiling.MethodProfilerResults;
import net.minecraft.util.profiling.MethodProfilerResultsEmpty;
import net.minecraft.util.profiling.MethodProfilerResultsField;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.IAsyncTaskHandlerReentrant;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.MobSpawnerCat;
import net.minecraft.world.entity.npc.MobSpawnerTrader;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewer;
import net.minecraft.world.item.crafting.CraftingManager;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.DataPackConfiguration;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.MobSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.WorldSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.MobSpawnerPatrol;
import net.minecraft.world.level.levelgen.MobSpawnerPhantom;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.feature.WorldGenFeatureConfigured;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.IWorldDataServer;
import net.minecraft.world.level.storage.PersistentCommandStorage;
import net.minecraft.world.level.storage.SaveData;
import net.minecraft.world.level.storage.SavedFile;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.WorldNBTStorage;
import net.minecraft.world.level.storage.WorldPersistentData;
import net.minecraft.world.phys.Vec2F;
import net.minecraft.world.phys.Vec3D;
import org.mospigot.config.MoSpigotConfig;
import org.mospigot.discord.DiscordWebhook;
import org.slf4j.Logger;

// CraftBukkit start
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.io.File;
import java.util.Random;
import jline.console.ConsoleReader;
import joptsimple.OptionSet;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.util.datafix.DataConverterRegistry;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.WorldDataServer;
import net.minecraft.world.level.storage.WorldInfo;
import net.minecraft.world.level.validation.ContentValidationException;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.Main;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.server.ServerLoadEvent;
// CraftBukkit end

import org.bukkit.craftbukkit.SpigotTimings; // Spigot

public abstract class MinecraftServer extends IAsyncTaskHandlerReentrant<TickTask> implements ServerInfo, ChunkIOErrorReporter, ICommandListener {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    private static final long OVERLOADED_THRESHOLD_NANOS = 30L * TimeRange.NANOSECONDS_PER_SECOND / 20L; // CraftBukkit
    private static final int OVERLOADED_TICKS_THRESHOLD = 20;
    private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeRange.NANOSECONDS_PER_SECOND;
    private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
    private static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeRange.NANOSECONDS_PER_SECOND;
    private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeRange.NANOSECONDS_PER_MILLISECOND;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    private static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MIMINUM_AUTOSAVE_TICKS = 100;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final WorldSettings DEMO_SETTINGS = new WorldSettings("Demo World", EnumGamemode.SURVIVAL, false, EnumDifficulty.NORMAL, false, new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
    public static final GameProfile ANONYMOUS_PLAYER_PROFILE = new GameProfile(SystemUtils.NIL_UUID, "Anonymous Player");
    public Convertable.ConversionSession storageSource;
    public final WorldNBTStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder;
    private Consumer<MethodProfilerResults> onMetricsRecordingStopped;
    private Consumer<Path> onMetricsRecordingFinished;
    private boolean willStartRecordingMetrics;
    @Nullable
    private MinecraftServer.TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    private ServerConnection connection;
    public final WorldLoadListenerFactory progressListenerFactory;
    @Nullable
    private ServerPing status;
    @Nullable
    private ServerPing.a statusIcon;
    private final RandomSource random;
    public final DataFixer fixerUpper;
    private String localIp;
    private int port;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private Map<ResourceKey<World>, WorldServer> levels;
    private PlayerList playerList;
    private volatile boolean running;
    private boolean stopped;
    private int tickCount;
    private int ticksUntilAutosave;
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private boolean pvp;
    private boolean allowFlight;
    @Nullable
    private String motd;
    private int playerIdleTimeout;
    private final long[] tickTimesNanos;
    private long aggregatedTickTimesNanos;
    @Nullable
    private KeyPair keyPair;
    @Nullable
    private GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarningNanos;
    protected final Services services;
    private long lastServerStatus;
    public final Thread serverThread;
    private long lastTickNanos;
    private long taskExecutionStartNanos;
    private long idleTimeNanos;
    private long nextTickTimeNanos;
    private boolean waitingForNextTick;
    private long delayedTasksMaxNextTickTimeNanos;
    private boolean mayHaveDelayedTasks;
    private final ResourcePackRepository packRepository;
    private final ScoreboardServer scoreboard;
    @Nullable
    private PersistentCommandStorage commandStorage;
    private final BossBattleCustomData customBossEvents;
    private final CustomFunctionData functionManager;
    private boolean enforceWhitelist;
    private float smoothedTickTimeMillis;
    public final Executor executor;
    @Nullable
    private String serverId;
    public MinecraftServer.ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    private final ServerTickRateManager tickRateManager;
    protected SaveData worldData;
    private final PotionBrewer potionBrewing;
    private FuelValues fuelValues;
    private int emptyTicks;
    private volatile boolean isSaving;
    private static final AtomicReference<RuntimeException> fatalException = new AtomicReference();
    private final SuppressedExceptionCollector suppressedExceptions;
    private final DiscontinuousFrame tickFrame;

    // CraftBukkit start
    public final WorldLoader.a worldLoader;
    public org.bukkit.craftbukkit.CraftServer server;
    public OptionSet options;
    public org.bukkit.command.ConsoleCommandSender console;
    public ConsoleReader reader;
    public static int currentTick = (int) (System.currentTimeMillis() / 50);
    public java.util.Queue<Runnable> processQueue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    public CommandDispatcher vanillaCommandDispatcher;
    private boolean forceTicks;
    // CraftBukkit end
    // Spigot start
    public static final int TPS = 20;
    public static final int TICK_TIME = 1000000000 / TPS;
    private static final int SAMPLE_INTERVAL = 100;
    public final double[] recentTps = new double[ 3 ];
    // Spigot end

    public static <S extends MinecraftServer> S spin(Function<Thread, S> function) {
        AtomicReference<S> atomicreference = new AtomicReference();
        Thread thread = new Thread(() -> {
            ((MinecraftServer) atomicreference.get()).runServer();
        }, "MoSpigot");

        thread.setUncaughtExceptionHandler((thread1, throwable) -> {
            MinecraftServer.LOGGER.error("Uncaught exception in server thread", throwable);
        });
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S s0 = (S) (function.apply(thread));

        atomicreference.set(s0);
        thread.start();
        return s0;
    }

    public MinecraftServer(OptionSet options, WorldLoader.a worldLoader, Thread thread, Convertable.ConversionSession convertable_conversionsession, ResourcePackRepository resourcepackrepository, WorldStem worldstem, Proxy proxy, DataFixer datafixer, Services services, WorldLoadListenerFactory worldloadlistenerfactory) {
        super("Server");
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
        this.onMetricsRecordingStopped = (methodprofilerresults) -> {
            this.stopRecordingMetrics();
        };
        this.onMetricsRecordingFinished = (path) -> {
        };
        this.random = RandomSource.create();
        this.port = -1;
        this.levels = Maps.newLinkedHashMap();
        this.running = true;
        this.ticksUntilAutosave = 6000;
        this.tickTimesNanos = new long[100];
        this.aggregatedTickTimesNanos = 0L;
        this.lastTickNanos = SystemUtils.getNanos();
        this.taskExecutionStartNanos = SystemUtils.getNanos();
        this.nextTickTimeNanos = SystemUtils.getNanos();
        this.waitingForNextTick = false;
        this.scoreboard = new ScoreboardServer(this);
        this.customBossEvents = new BossBattleCustomData();
        this.suppressedExceptions = new SuppressedExceptionCollector();
        this.registries = worldstem.registries();
        this.worldData = worldstem.worldData();
        if (false && !this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).containsKey(WorldDimension.OVERWORLD)) { // CraftBukkit - initialised later
            throw new IllegalStateException("Missing Overworld dimension data");
        } else {
            this.proxy = proxy;
            this.packRepository = resourcepackrepository;
            this.resources = new MinecraftServer.ReloadableResources(worldstem.resourceManager(), worldstem.dataPackResources());
            this.services = services;
            if (services.profileCache() != null) {
                services.profileCache().setExecutor(this);
            }

            // this.connection = new ServerConnection(this); // Spigot
            this.tickRateManager = new ServerTickRateManager(this);
            this.progressListenerFactory = worldloadlistenerfactory;
            this.storageSource = convertable_conversionsession;
            this.playerDataStorage = convertable_conversionsession.createPlayerStorage();
            this.fixerUpper = datafixer;
            this.functionManager = new CustomFunctionData(this, this.resources.managers.getFunctionLibrary());
            HolderGetter<Block> holdergetter = this.registries.compositeAccess().lookupOrThrow(Registries.BLOCK).filterFeatures(this.worldData.enabledFeatures());

            this.structureTemplateManager = new StructureTemplateManager(worldstem.resourceManager(), convertable_conversionsession, datafixer, holdergetter);
            this.serverThread = thread;
            this.executor = SystemUtils.backgroundExecutor();
            this.potionBrewing = PotionBrewer.bootstrap(this.worldData.enabledFeatures());
            this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
            this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
            this.tickFrame = TracyClient.createDiscontinuousFrame("Server Tick");
        }
        // CraftBukkit start
        this.options = options;
        this.worldLoader = worldLoader;
        this.vanillaCommandDispatcher = worldstem.dataPackResources().commands; // CraftBukkit
        // Try to see if we're actually running in a terminal, disable jline if not
        if (System.console() == null && System.getProperty("jline.terminal") == null) {
            System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
            Main.useJline = false;
        }

        try {
            reader = new ConsoleReader(System.in, System.out);
            reader.setExpandEvents(false); // Avoid parsing exceptions for uncommonly used event designators
        } catch (Throwable e) {
            try {
                // Try again with jline disabled for Windows users without C++ 2008 Redistributable
                System.setProperty("jline.terminal", "jline.UnsupportedTerminal");
                System.setProperty("user.language", "en");
                Main.useJline = false;
                reader = new ConsoleReader(System.in, System.out);
                reader.setExpandEvents(false);
            } catch (IOException ex) {
                LOGGER.warn((String) null, ex);
            }
        }
        Runtime.getRuntime().addShutdownHook(new org.bukkit.craftbukkit.util.ServerShutdownThread(this));
        // CraftBukkit end
    }

    private void readScoreboard(WorldPersistentData worldpersistentdata) {
        worldpersistentdata.computeIfAbsent(ScoreboardServer.TYPE);
    }

    protected abstract boolean initServer() throws IOException;

    protected void loadLevel(String s) { // CraftBukkit
        if (!JvmProfiler.INSTANCE.isRunning()) {
            ;
        }

        boolean flag = false;
        ProfiledDuration profiledduration = JvmProfiler.INSTANCE.onWorldLoadedStarted();

        loadWorld0(s); // CraftBukkit

        if (profiledduration != null) {
            profiledduration.finish(true);
        }

        if (flag) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to stop JFR profiling", throwable);
            }
        }

    }

    protected void forceDifficulty() {}

    // CraftBukkit start
    private void loadWorld0(String s) {
        Convertable.ConversionSession worldSession = this.storageSource;

        IRegistryCustom.Dimension iregistrycustom_dimension = this.registries.compositeAccess();
        IRegistry<WorldDimension> dimensions = iregistrycustom_dimension.lookupOrThrow(Registries.LEVEL_STEM);
        for (WorldDimension worldDimension : dimensions) {
            ResourceKey<WorldDimension> dimensionKey = dimensions.getResourceKey(worldDimension).get();

            WorldServer world;
            int dimension = 0;

            if (dimensionKey == WorldDimension.NETHER) {
                if (server.getAllowNether()) {
                    dimension = -1;
                } else {
                    continue;
                }
            } else if (dimensionKey == WorldDimension.END) {
                if (server.getAllowEnd()) {
                    dimension = 1;
                } else {
                    continue;
                }
            } else if (dimensionKey != WorldDimension.OVERWORLD) {
                dimension = -999;
            }

            String worldType = (dimension == -999) ? dimensionKey.location().getNamespace() + "_" + dimensionKey.location().getPath() : org.bukkit.World.Environment.getEnvironment(dimension).toString().toLowerCase(Locale.ROOT);
            String name = (dimensionKey == WorldDimension.OVERWORLD) ? s : s + "_" + worldType;
            if (dimension != 0) {
                File newWorld = Convertable.getStorageFolder(new File(name).toPath(), dimensionKey).toFile();
                File oldWorld = Convertable.getStorageFolder(new File(s).toPath(), dimensionKey).toFile();
                File oldLevelDat = new File(new File(s), "level.dat"); // The data folders exist on first run as they are created in the PersistentCollection constructor above, but the level.dat won't

                if (!newWorld.isDirectory() && oldWorld.isDirectory() && oldLevelDat.isFile()) {
                    MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder required ----");
                    MinecraftServer.LOGGER.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    MinecraftServer.LOGGER.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    MinecraftServer.LOGGER.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        MinecraftServer.LOGGER.warn("A file or folder already exists at " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            MinecraftServer.LOGGER.info("Success! To restore " + worldType + " in the future, simply move " + newWorld + " to " + oldWorld);
                            // Migrate world data too.
                            try {
                                com.google.common.io.Files.copy(oldLevelDat, new File(new File(name), "level.dat"));
                                org.apache.commons.io.FileUtils.copyDirectory(new File(new File(s), "data"), new File(new File(name), "data"));
                            } catch (IOException exception) {
                                MinecraftServer.LOGGER.warn("Unable to migrate world data.");
                            }
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            MinecraftServer.LOGGER.warn("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        MinecraftServer.LOGGER.warn("Could not create path for " + newWorld + "!");
                        MinecraftServer.LOGGER.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                try {
                    worldSession = Convertable.createDefault(server.getWorldContainer().toPath()).validateAndCreateAccess(name, dimensionKey);
                } catch (IOException | ContentValidationException ex) {
                    throw new RuntimeException(ex);
                }
            }

            Dynamic<?> dynamic;
            if (worldSession.hasWorldData()) {
                WorldInfo worldinfo;

                try {
                    dynamic = worldSession.getDataTag();
                    worldinfo = worldSession.getSummary(dynamic);
                } catch (NbtException | ReportedNbtException | IOException ioexception) {
                    Convertable.b convertable_b = worldSession.getLevelDirectory();

                    MinecraftServer.LOGGER.warn("Failed to load world data from {}", convertable_b.dataFile(), ioexception);
                    MinecraftServer.LOGGER.info("Attempting to use fallback");

                    try {
                        dynamic = worldSession.getDataTagFallback();
                        worldinfo = worldSession.getSummary(dynamic);
                    } catch (NbtException | ReportedNbtException | IOException ioexception1) {
                        MinecraftServer.LOGGER.error("Failed to load world data from {}", convertable_b.oldDataFile(), ioexception1);
                        MinecraftServer.LOGGER.error("Failed to load world data from {} and {}. World files may be corrupted. Shutting down.", convertable_b.dataFile(), convertable_b.oldDataFile());
                        return;
                    }

                    worldSession.restoreLevelDataFromOld();
                }

                if (worldinfo.requiresManualConversion()) {
                    MinecraftServer.LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                    return;
                }

                if (!worldinfo.isCompatible()) {
                    MinecraftServer.LOGGER.info("This world was created by an incompatible version.");
                    return;
                }
            } else {
                dynamic = null;
            }

            org.bukkit.generator.ChunkGenerator gen = this.server.getGenerator(name);
            org.bukkit.generator.BiomeProvider biomeProvider = this.server.getBiomeProvider(name);

            WorldDataServer worlddata;
            WorldLoader.a worldloader_a = this.worldLoader;
            IRegistry<WorldDimension> iregistry = worldloader_a.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);
            if (dynamic != null) {
                LevelDataAndDimensions leveldataanddimensions = Convertable.getLevelDataAndDimensions(dynamic, worldloader_a.dataConfiguration(), iregistry, worldloader_a.datapackWorldgen());

                worlddata = (WorldDataServer) leveldataanddimensions.worldData();
            } else {
                WorldSettings worldsettings;
                WorldOptions worldoptions;
                WorldDimensions worlddimensions;

                if (this.isDemo()) {
                    worldsettings = MinecraftServer.DEMO_SETTINGS;
                    worldoptions = WorldOptions.DEMO_OPTIONS;
                    worlddimensions = WorldPresets.createNormalWorldDimensions(worldloader_a.datapackWorldgen());
                } else {
                    DedicatedServerProperties dedicatedserverproperties = ((DedicatedServer) this).getProperties();

                    worldsettings = new WorldSettings(dedicatedserverproperties.levelName, dedicatedserverproperties.gamemode, dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty, false, new GameRules(worldloader_a.dataConfiguration().enabledFeatures()), worldloader_a.dataConfiguration());
                    worldoptions = options.has("bonusChest") ? dedicatedserverproperties.worldOptions.withBonusChest(true) : dedicatedserverproperties.worldOptions;
                    worlddimensions = dedicatedserverproperties.createDimensions(worldloader_a.datapackWorldgen());
                }

                WorldDimensions.b worlddimensions_b = worlddimensions.bake(iregistry);
                Lifecycle lifecycle = worlddimensions_b.lifecycle().add(worldloader_a.datapackWorldgen().allRegistriesLifecycle());

                worlddata = new WorldDataServer(worldsettings, worldoptions, worlddimensions_b.specialWorldProperty(), lifecycle);
            }
            worlddata.checkName(name); // CraftBukkit - Migration did not rewrite the level.dat; This forces 1.8 to take the last loaded world as respawn (in this case the end)
            if (options.has("forceUpgrade")) {
                net.minecraft.server.Main.forceUpgrade(worldSession, worlddata, DataConverterRegistry.getDataFixer(), options.has("eraseCache"), () -> {
                    return true;
                }, iregistrycustom_dimension, options.has("recreateRegionFiles"));
            }

            WorldDataServer iworlddataserver = worlddata;
            boolean flag = worlddata.isDebugWorld();
            WorldOptions worldoptions = worlddata.worldGenOptions();
            long i = worldoptions.seed();
            long j = BiomeManager.obfuscateSeed(i);
            List<MobSpawner> list = ImmutableList.of(new MobSpawnerPhantom(), new MobSpawnerPatrol(), new MobSpawnerCat(), new VillageSiege(), new MobSpawnerTrader(iworlddataserver));
            WorldDimension worlddimension = (WorldDimension) dimensions.getValue(dimensionKey);

            org.bukkit.generator.WorldInfo worldInfo = new org.bukkit.craftbukkit.generator.CraftWorldInfo(iworlddataserver, worldSession, org.bukkit.World.Environment.getEnvironment(dimension), worlddimension.type().value());
            if (biomeProvider == null && gen != null) {
                biomeProvider = gen.getDefaultBiomeProvider(worldInfo);
            }

            ResourceKey<World> worldKey = ResourceKey.create(Registries.DIMENSION, dimensionKey.location());

            if (dimensionKey == WorldDimension.OVERWORLD) {
                this.worldData = worlddata;
                this.worldData.setGameType(((DedicatedServer) this).getProperties().gamemode); // From DedicatedServer.init

                WorldLoadListener worldloadlistener = this.progressListenerFactory.create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));

                world = new WorldServer(this, this.executor, worldSession, iworlddataserver, worldKey, worlddimension, worldloadlistener, flag, j, list, true, (RandomSequences) null, org.bukkit.World.Environment.getEnvironment(dimension), gen, biomeProvider);
                WorldPersistentData worldpersistentdata = world.getDataStorage();
                this.readScoreboard(worldpersistentdata);
                this.server.scoreboardManager = new org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager(this, world.getScoreboard());
                this.commandStorage = new PersistentCommandStorage(worldpersistentdata);
            } else {
                WorldLoadListener worldloadlistener = this.progressListenerFactory.create(worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
                world = new WorldServer(this, this.executor, worldSession, iworlddataserver, worldKey, worlddimension, worldloadlistener, flag, j, ImmutableList.of(), true, this.overworld().getRandomSequences(), org.bukkit.World.Environment.getEnvironment(dimension), gen, biomeProvider);
            }

            worlddata.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
            this.initWorld(world, worlddata, worldData, worldoptions);

            this.addLevel(world);
            this.getPlayerList().addWorldborderListener(world);

            if (worlddata.getCustomBossEvents() != null) {
                this.getCustomBossEvents().load(worlddata.getCustomBossEvents(), this.registryAccess());
            }
        }
        this.forceDifficulty();
        for (WorldServer worldserver : this.getAllLevels()) {
            this.prepareLevels(worldserver.getChunkSource().chunkMap.progressListener, worldserver);
            worldserver.entityManager.tick(); // SPIGOT-6526: Load pending entities so they are available to the API
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(worldserver.getWorld()));
        }

        this.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);
        this.server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        this.connection.acceptConnections();
    }

    public void initWorld(WorldServer worldserver, IWorldDataServer iworlddataserver, SaveData saveData, WorldOptions worldoptions) {
        boolean flag = saveData.isDebugWorld();
        // CraftBukkit start
        if (worldserver.generator != null) {
            worldserver.getWorld().getPopulators().addAll(worldserver.generator.getDefaultPopulators(worldserver.getWorld()));
        }
        WorldBorder worldborder = worldserver.getWorldBorder();
        worldborder.applySettings(iworlddataserver.getWorldBorder()); // CraftBukkit - move up so that WorldBorder is set during WorldInitEvent
        this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldInitEvent(worldserver.getWorld())); // CraftBukkit - SPIGOT-5569: Call WorldInitEvent before any chunks are generated

        if (!iworlddataserver.isInitialized()) {
            try {
                setInitialSpawn(worldserver, iworlddataserver, worldoptions.generateBonusChest(), flag);
                iworlddataserver.setInitialized(true);
                if (flag) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception initializing level");

                try {
                    worldserver.fillReportDetails(crashreport);
                } catch (Throwable throwable1) {
                    ;
                }

                throw new ReportedException(crashreport);
            }

            iworlddataserver.setInitialized(true);
        }

    }
    // CraftBukkit end

    private static void setInitialSpawn(WorldServer worldserver, IWorldDataServer iworlddataserver, boolean flag, boolean flag1) {
        if (flag1) {
            iworlddataserver.setSpawn(BlockPosition.ZERO.above(80), 0.0F);
        } else {
            ChunkProviderServer chunkproviderserver = worldserver.getChunkSource();
            ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(chunkproviderserver.randomState().sampler().findSpawnPosition());
            // CraftBukkit start
            if (worldserver.generator != null) {
                Random rand = new Random(worldserver.getSeed());
                org.bukkit.Location spawn = worldserver.generator.getFixedSpawnLocation(worldserver.getWorld(), rand);

                if (spawn != null) {
                    if (spawn.getWorld() != worldserver.getWorld()) {
                        throw new IllegalStateException("Cannot set spawn point for " + iworlddataserver.getLevelName() + " to be in another world (" + spawn.getWorld().getName() + ")");
                    } else {
                        iworlddataserver.setSpawn(new BlockPosition(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()), spawn.getYaw());
                        return;
                    }
                }
            }
            // CraftBukkit end
            int i = chunkproviderserver.getGenerator().getSpawnHeight(worldserver);

            if (i < worldserver.getMinY()) {
                BlockPosition blockposition = chunkcoordintpair.getWorldPosition();

                i = worldserver.getHeight(HeightMap.Type.WORLD_SURFACE, blockposition.getX() + 8, blockposition.getZ() + 8);
            }

            iworlddataserver.setSpawn(chunkcoordintpair.getWorldPosition().offset(8, i, 8), 0.0F);
            int j = 0;
            int k = 0;
            int l = 0;
            int i1 = -1;

            for (int j1 = 0; j1 < MathHelper.square(11); ++j1) {
                if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
                    BlockPosition blockposition1 = WorldProviderNormal.getSpawnPosInChunk(worldserver, new ChunkCoordIntPair(chunkcoordintpair.x + j, chunkcoordintpair.z + k));

                    if (blockposition1 != null) {
                        iworlddataserver.setSpawn(blockposition1, 0.0F);
                        break;
                    }
                }

                if (j == k || j < 0 && j == -k || j > 0 && j == 1 - k) {
                    int k1 = l;

                    l = -i1;
                    i1 = k1;
                }

                j += l;
                k += i1;
            }

            if (flag) {
                worldserver.registryAccess().lookup(Registries.CONFIGURED_FEATURE).flatMap((iregistry) -> {
                    return iregistry.get(MiscOverworldFeatures.BONUS_CHEST);
                }).ifPresent((holder_c) -> {
                    ((WorldGenFeatureConfigured) holder_c.value()).place(worldserver, chunkproviderserver.getGenerator(), worldserver.random, iworlddataserver.getSpawnPos());
                });
            }

        }
    }

    private void setupDebugLevel(SaveData savedata) {
        savedata.setDifficulty(EnumDifficulty.PEACEFUL);
        savedata.setDifficultyLocked(true);
        IWorldDataServer iworlddataserver = savedata.overworldData();

        iworlddataserver.setRaining(false);
        iworlddataserver.setThundering(false);
        iworlddataserver.setClearWeatherTime(1000000000);
        iworlddataserver.setDayTime(6000L);
        iworlddataserver.setGameType(EnumGamemode.SPECTATOR);
    }

    // CraftBukkit start
    public void prepareLevels(WorldLoadListener worldloadlistener, WorldServer worldserver) {
        // WorldServer worldserver = this.overworld();
        this.forceTicks = true;
        // CraftBukkit end

        MinecraftServer.LOGGER.info("Preparing start region for dimension {}", worldserver.dimension().location());
        BlockPosition blockposition = worldserver.getSharedSpawnPos();

        worldloadlistener.updateSpawnPos(new ChunkCoordIntPair(blockposition));
        ChunkProviderServer chunkproviderserver = worldserver.getChunkSource();

        this.nextTickTimeNanos = SystemUtils.getNanos();
        worldserver.setDefaultSpawnPos(blockposition, worldserver.getSharedSpawnAngle());
        int i = worldserver.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS); // CraftBukkit - per-world
        int j = i > 0 ? MathHelper.square(WorldLoadListener.calculateDiameter(i)) : 0;

        while (chunkproviderserver.getTickingGenerated() < j) {
            // CraftBukkit start
            // this.nextTickTimeNanos = SystemUtils.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
            this.executeModerately();
        }

        // this.nextTickTimeNanos = SystemUtils.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.executeModerately();

        if (true) {
            WorldServer worldserver1 = worldserver;
            // CraftBukkit end
            TicketStorage ticketstorage = (TicketStorage) worldserver1.getDataStorage().get(TicketStorage.TYPE);

            if (ticketstorage != null) {
                ticketstorage.activateAllDeactivatedTickets();
            }
        }

        // CraftBukkit start
        // this.nextTickTimeNanos = SystemUtils.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.executeModerately();
        // CraftBukkit end
        worldloadlistener.stop();
        // CraftBukkit start
        // this.updateMobSpawningFlags();
        worldserver.setSpawnSettings(this.isSpawningMonsters());

        this.forceTicks = false;
        // CraftBukkit end
    }

    public EnumGamemode getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract int getOperatorUserPermissionLevel();

    public abstract int getFunctionCompilationLevel();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean flag, boolean flag1, boolean flag2) {
        boolean flag3 = false;

        for (WorldServer worldserver : this.getAllLevels()) {
            if (!flag) {
                MinecraftServer.LOGGER.info("Saving chunks for level '{}'/{}", worldserver, worldserver.dimension().location());
            }

            worldserver.save((IProgressUpdate) null, flag1, worldserver.noSave && !flag2);
            flag3 = true;
        }

        // CraftBukkit start - moved to WorldServer.save
        /*
        WorldServer worldserver1 = this.overworld();
        IWorldDataServer iworlddataserver = this.worldData.overworldData();

        iworlddataserver.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
        this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
        */
        // CraftBukkit end
        if (flag1) {
            for (WorldServer worldserver2 : this.getAllLevels()) {
                MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", worldserver2.getChunkSource().chunkMap.getStorageName());
            }

            MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }

        return flag3;
    }

    public boolean saveEverything(boolean flag, boolean flag1, boolean flag2) {
        boolean flag3;

        try {
            this.isSaving = true;
            this.getPlayerList().saveAll();
            flag3 = this.saveAllChunks(flag, flag1, flag2);
        } finally {
            this.isSaving = false;
        }

        return flag3;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    // CraftBukkit start
    private boolean hasStopped = false;
    private final Object stopLock = new Object();
    public final boolean hasStopped() {
        synchronized (stopLock) {
            return hasStopped;
        }
    }
    // CraftBukkit end

    public void stopServer() {
        // CraftBukkit start - prevent double stopping on multiple threads
        synchronized(stopLock) {
            if (hasStopped) return;
            hasStopped = true;
        }
        // CraftBukkit end
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        MinecraftServer.LOGGER.info("Stopping server");
        if (MoSpigotConfig.discordWebhookEnabled) {
            DiscordWebhook.send(MoSpigotConfig.discordWebhookUrl, "🔴 **Server spento.**");
        }

        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
        }
        // CraftBukkit end
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            MinecraftServer.LOGGER.info("Saving players");
            this.playerList.saveAll();
            this.playerList.removeAll();
            try { Thread.sleep(100); } catch (InterruptedException ex) {} // CraftBukkit - SPIGOT-625 - give server at least a chance to send packets
        }

        MinecraftServer.LOGGER.info("Saving worlds");

        for (WorldServer worldserver : this.getAllLevels()) {
            if (worldserver != null) {
                worldserver.noSave = false;
            }
        }

        while (this.levels.values().stream().anyMatch((worldserver1) -> {
            return worldserver1.getChunkSource().chunkMap.hasWork();
        })) {
            this.nextTickTimeNanos = SystemUtils.getNanos() + TimeRange.NANOSECONDS_PER_MILLISECOND;

            for (WorldServer worldserver1 : this.getAllLevels()) {
                worldserver1.getChunkSource().deactivateTicketsOnClosing();
                worldserver1.getChunkSource().tick(() -> {
                    return true;
                }, false);
            }

            this.waitUntilNextTick();
        }

        this.saveAllChunks(false, true, false);

        for (WorldServer worldserver2 : this.getAllLevels()) {
            if (worldserver2 != null) {
                try {
                    worldserver2.close();
                } catch (IOException ioexception) {
                    MinecraftServer.LOGGER.error("Exception closing the level", ioexception);
                }
            }
        }

        this.isSaving = false;
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException ioexception1) {
            MinecraftServer.LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), ioexception1);
        }
        // Spigot start
        if (org.mospigot.config.MoSpigotConfig.saveUserCacheOnStopOnly) {
            LOGGER.info("Saving usercache.json");
            this.getProfileCache().save();
        }
        // Spigot end

    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String s) {
        this.localIp = s;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean flag) {
        this.running = false;
        if (flag) {
            try {
                this.serverThread.join();
            } catch (InterruptedException interruptedexception) {
                MinecraftServer.LOGGER.error("Error while shutting down", interruptedexception);
            }
        }

    }

    // Spigot Start
    private static double calcTps(double avg, double exp, double tps)
    {
        return ( avg * exp ) + ( tps * ( 1 - exp ) );
    }
    // Spigot End

    protected void runServer() {
        try {
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTimeNanos = SystemUtils.getNanos();
            this.statusIcon = (ServerPing.a) this.loadStatusIcon().orElse(null); // CraftBukkit - decompile error
            this.status = this.buildServerStatus();

            // Spigot start
            Arrays.fill( recentTps, 20 );
            long tickSection = SystemUtils.getMillis(), tickCount = 1;
            while (this.running) {
                long i;

                if (!this.isPaused() && this.tickRateManager.isSprinting() && this.tickRateManager.checkShouldSprintThisTick()) {
                    i = 0L;
                    this.nextTickTimeNanos = SystemUtils.getNanos();
                    this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                } else {
                    i = this.tickRateManager.nanosecondsPerTick();
                    long j = SystemUtils.getNanos() - this.nextTickTimeNanos;

                    if (j > MinecraftServer.OVERLOADED_THRESHOLD_NANOS + 20L * i && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= MinecraftServer.OVERLOADED_WARNING_INTERVAL_NANOS + 100L * i) {
                        long k = j / i;

                        if (server.getWarnOnOverload()) // CraftBukkit
                        MinecraftServer.LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", j / TimeRange.NANOSECONDS_PER_MILLISECOND, k);
                        this.nextTickTimeNanos += k * i;
                        this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                    }
                }
                // Spigot start
                if ( tickCount++ % SAMPLE_INTERVAL == 0 )
                {
                    long curTime = SystemUtils.getMillis();
                    double currentTps = 1E3 / ( curTime - tickSection ) * SAMPLE_INTERVAL;
                    recentTps[0] = calcTps( recentTps[0], 0.92, currentTps ); // 1/exp(5sec/1min)
                    recentTps[1] = calcTps( recentTps[1], 0.9835, currentTps ); // 1/exp(5sec/5min)
                    recentTps[2] = calcTps( recentTps[2], 0.9945, currentTps ); // 1/exp(5sec/15min)
                    tickSection = curTime;
                }
                // Spigot end

                boolean flag = i == 0L;

                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    this.debugCommandProfiler = new MinecraftServer.TimeProfiler(SystemUtils.getNanos(), this.tickCount);
                }

                MinecraftServer.currentTick = (int) (System.currentTimeMillis() / 50); // CraftBukkit
                this.nextTickTimeNanos += i;

                try {
                    Profiler.a profiler_a = Profiler.use(this.createProfiler());

                    try {
                        GameProfilerFiller gameprofilerfiller = Profiler.get();

                        gameprofilerfiller.push("tick");
                        this.tickFrame.start();
                        this.tickServer(flag ? () -> {
                            return false;
                        } : this::haveTime);
                        this.tickFrame.end();
                        gameprofilerfiller.popPush("nextTickWait");
                        this.mayHaveDelayedTasks = true;
                        this.delayedTasksMaxNextTickTimeNanos = Math.max(SystemUtils.getNanos() + i, this.nextTickTimeNanos);
                        this.startMeasuringTaskExecutionTime();
                        this.waitUntilNextTick();
                        this.finishMeasuringTaskExecutionTime();
                        if (flag) {
                            this.tickRateManager.endTickWork();
                        }

                        gameprofilerfiller.pop();
                        this.logFullTickTime();
                    } catch (Throwable throwable) {
                        if (profiler_a != null) {
                            try {
                                profiler_a.close();
                            } catch (Throwable throwable1) {
                                throwable.addSuppressed(throwable1);
                            }
                        }

                        throw throwable;
                    }

                    if (profiler_a != null) {
                        profiler_a.close();
                    }
                } finally {
                    this.endMetricsRecordingTick();
                }

                this.isReady = true;
                JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
            }
        } catch (Throwable throwable2) {
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable2);
            CrashReport crashreport = constructOrExtractCrashReport(throwable2);

            this.fillSystemReport(crashreport.getSystemReport());
            Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + SystemUtils.getFilenameFormattedDateTime() + "-server.txt");

            if (crashreport.saveToFile(path, ReportType.CRASH)) {
                MinecraftServer.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashreport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable throwable3) {
                MinecraftServer.LOGGER.error("Exception stopping the server", throwable3);
            } finally {
                if (this.services.profileCache() != null) {
                    this.services.profileCache().clearExecutor();
                }

                org.mospigot.WatchdogThread.doStop(); // Spigot
                // CraftBukkit start - Restore terminal to original settings
                try {
                    reader.getTerminal().restore();
                } catch (Exception ignored) {
                }
                // CraftBukkit end
                this.onServerExit();
            }

        }

    }

    private void logFullTickTime() {
        long i = SystemUtils.getNanos();

        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logSample(i - this.lastTickNanos);
        }

        this.lastTickNanos = i;
    }

    private void startMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = SystemUtils.getNanos();
            this.idleTimeNanos = 0L;
        }

    }

    private void finishMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            SampleLogger samplelogger = this.getTickTimeLogger();

            samplelogger.logPartialSample(SystemUtils.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            samplelogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }

    }

    private static CrashReport constructOrExtractCrashReport(Throwable throwable) {
        ReportedException reportedexception = null;

        for (Throwable throwable1 = throwable; throwable1 != null; throwable1 = throwable1.getCause()) {
            if (throwable1 instanceof ReportedException reportedexception1) {
                reportedexception = reportedexception1;
            }
        }

        CrashReport crashreport;

        if (reportedexception != null) {
            crashreport = reportedexception.getReport();
            if (reportedexception != throwable) {
                crashreport.addCategory("Wrapped in").setDetailError("Wrapping exception", throwable);
            }
        } else {
            crashreport = new CrashReport("Exception in server tick loop", throwable);
        }

        return crashreport;
    }

    private boolean haveTime() {
        // CraftBukkit start
        return this.forceTicks || this.runningTask() || SystemUtils.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    private void executeModerately() {
        this.runAllTasks();
        java.util.concurrent.locks.LockSupport.parkNanos("executing tasks", 1000L);
        // CraftBukkit end
    }

    public static boolean throwIfFatalException() {
        RuntimeException runtimeexception = (RuntimeException) MinecraftServer.fatalException.get();

        if (runtimeexception != null) {
            throw runtimeexception;
        } else {
            return true;
        }
    }

    public static void setFatalException(RuntimeException runtimeexception) {
        MinecraftServer.fatalException.compareAndSet(null, runtimeexception); // CraftBukkit - decompile error
    }

    @Override
    public void managedBlock(BooleanSupplier booleansupplier) {
        super.managedBlock(() -> {
            return throwIfFatalException() && booleansupplier.getAsBoolean();
        });
    }

    protected void waitUntilNextTick() {
        this.runAllTasks();
        this.waitingForNextTick = true;

        try {
            this.managedBlock(() -> {
                return !this.haveTime();
            });
        } finally {
            this.waitingForNextTick = false;
        }

    }

    @Override
    public void waitForTasks() {
        boolean flag = this.isTickTimeLoggingEnabled();
        long i = flag ? SystemUtils.getNanos() : 0L;
        long j = this.waitingForNextTick ? this.nextTickTimeNanos - SystemUtils.getNanos() : 100000L;

        LockSupport.parkNanos("waiting for tasks", j);
        if (flag) {
            this.idleTimeNanos += SystemUtils.getNanos() - i;
        }

    }

    @Override
    public TickTask wrapRunnable(Runnable runnable) {
        return new TickTask(this.tickCount, runnable);
    }

    protected boolean shouldRun(TickTask ticktask) {
        return ticktask.getTick() + 3 < this.tickCount || this.haveTime();
    }

    @Override
    public boolean pollTask() {
        boolean flag = this.pollTaskInternal();

        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            return true;
        } else {
            if (this.tickRateManager.isSprinting() || this.haveTime()) {
                for (WorldServer worldserver : this.getAllLevels()) {
                    if (worldserver.getChunkSource().pollTask()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public void doRunTask(TickTask ticktask) { // CraftBukkit - decompile error
        Profiler.get().incrementCounter("runTask");
        super.doRunTask(ticktask);
    }

    private Optional<ServerPing.a> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png")).filter((path) -> {
            return Files.isRegularFile(path, new LinkOption[0]);
        }).or(() -> {
            return this.storageSource.getIconFile().filter((path) -> {
                return Files.isRegularFile(path, new LinkOption[0]);
            });
        });

        return optional.flatMap((path) -> {
            try {
                BufferedImage bufferedimage = ImageIO.read(path.toFile());

                Preconditions.checkState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide");
                Preconditions.checkState(bufferedimage.getHeight() == 64, "Must be 64 pixels high");
                ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();

                ImageIO.write(bufferedimage, "PNG", bytearrayoutputstream);
                return Optional.of(new ServerPing.a(bytearrayoutputstream.toByteArray()));
            } catch (Exception exception) {
                MinecraftServer.LOGGER.error("Couldn't load server icon", exception);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public Path getServerDirectory() {
        return Path.of("");
    }

    public void onServerCrash(CrashReport crashreport) {}

    public void onServerExit() {}

    public boolean isPaused() {
        return false;
    }

    public void tickServer(BooleanSupplier booleansupplier) {
        org.mospigot.WatchdogThread.tick(); // Spigot
        long i = SystemUtils.getNanos();
        int j = this.pauseWhileEmptySeconds() * 20;

        if (j > 0) {
            if (this.playerList.getPlayerCount() == 0 && !this.tickRateManager.isSprinting()) {
                ++this.emptyTicks;
            } else {
                this.emptyTicks = 0;
            }

            if (this.emptyTicks >= j) {
                if (this.emptyTicks == j) {
                    MinecraftServer.LOGGER.info("Server empty for {} seconds, pausing", this.pauseWhileEmptySeconds());
                    this.autoSave();
                }

                this.server.getScheduler().mainThreadHeartbeat(); // CraftBukkit
                this.tickConnection();
                return;
            }
        }

        SpigotTimings.serverTickTimer.startTiming(); // Spigot
        ++this.tickCount;
        this.tickRateManager.tick();
        this.tickChildren(booleansupplier);
        if (i - this.lastServerStatus >= MinecraftServer.STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = i;
            this.status = this.buildServerStatus();
        }

        --this.ticksUntilAutosave;
        if (this.autosavePeriod > 0 && this.ticksUntilAutosave <= 0) { // CraftBukkit
            this.autoSave();
        }

        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("tallying");
        long k = SystemUtils.getNanos() - i;
        int l = this.tickCount % 100;

        this.aggregatedTickTimesNanos -= this.tickTimesNanos[l];
        this.aggregatedTickTimesNanos += k;
        this.tickTimesNanos[l] = k;
        this.smoothedTickTimeMillis = this.smoothedTickTimeMillis * 0.8F + (float) k / (float) TimeRange.NANOSECONDS_PER_MILLISECOND * 0.19999999F;
        this.logTickMethodTime(i);
        gameprofilerfiller.pop();
        SpigotTimings.serverTickTimer.stopTiming(); // Spigot
        org.mospigot.CustomTimingsHandler.tick(); // Spigot
    }

    private void autoSave() {
        this.ticksUntilAutosave = this.autosavePeriod; // CraftBukkit
        SpigotTimings.worldSaveTimer.startTiming(); // Spigot
        MinecraftServer.LOGGER.debug("Autosave started");
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("save");
        this.saveEverything(true, false, false);
        gameprofilerfiller.pop();
        MinecraftServer.LOGGER.debug("Autosave finished");
        SpigotTimings.worldSaveTimer.stopTiming(); // Spigot
    }

    private void logTickMethodTime(long i) {
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logPartialSample(SystemUtils.getNanos() - i, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        }

    }

    private int computeNextAutosaveInterval() {
        float f;

        if (this.tickRateManager.isSprinting()) {
            long i = this.getAverageTickTimeNanos() + 1L;

            f = (float) TimeRange.NANOSECONDS_PER_SECOND / (float) i;
        } else {
            f = this.tickRateManager.tickrate();
        }

        int j = 300;

        return Math.max(100, (int) (f * 300.0F));
    }

    public void onTickRateChanged() {
        int i = this.computeNextAutosaveInterval();

        if (i < this.ticksUntilAutosave) {
            this.ticksUntilAutosave = i;
        }

    }

    protected abstract SampleLogger getTickTimeLogger();

    public abstract boolean isTickTimeLoggingEnabled();

    private ServerPing buildServerStatus() {
        ServerPing.ServerPingPlayerSample serverping_serverpingplayersample = this.buildPlayerStatus();

        return new ServerPing(IChatBaseComponent.nullToEmpty(this.motd), Optional.of(serverping_serverpingplayersample), Optional.of(ServerPing.ServerData.current()), Optional.ofNullable(this.statusIcon), this.enforceSecureProfile());
    }

    private ServerPing.ServerPingPlayerSample buildPlayerStatus() {
        List<EntityPlayer> list = this.playerList.getPlayers();
        int i = this.getMaxPlayers();

        if (this.hidesOnlinePlayers()) {
            return new ServerPing.ServerPingPlayerSample(i, list.size(), List.of());
        } else {
            int j = Math.min(list.size(), 12);
            ObjectArrayList<GameProfile> objectarraylist = new ObjectArrayList(j);
            int k = MathHelper.nextInt(this.random, 0, list.size() - j);

            for (int l = 0; l < j; ++l) {
                EntityPlayer entityplayer = (EntityPlayer) list.get(k + l);

                objectarraylist.add(entityplayer.allowsListing() ? entityplayer.getGameProfile() : MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
            }

            SystemUtils.shuffle(objectarraylist, this.random);
            return new ServerPing.ServerPingPlayerSample(i, list.size(), objectarraylist);
        }
    }

    protected void tickChildren(BooleanSupplier booleansupplier) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        this.getPlayerList().getPlayers().forEach((entityplayer) -> {
            entityplayer.connection.suspendFlushing();
        });
        SpigotTimings.schedulerTimer.startTiming(); // Spigot
        this.server.getScheduler().mainThreadHeartbeat(); // CraftBukkit
        SpigotTimings.schedulerTimer.stopTiming(); // Spigot
        gameprofilerfiller.push("commandFunctions");
        SpigotTimings.commandFunctionsTimer.startTiming(); // Spigot
        this.getFunctions().tick();
        SpigotTimings.commandFunctionsTimer.stopTiming(); // Spigot
        gameprofilerfiller.popPush("levels");

        // CraftBukkit start
        // Run tasks that are waiting on processing
        SpigotTimings.processQueueTimer.startTiming(); // Spigot
        while (!processQueue.isEmpty()) {
            processQueue.remove().run();
        }
        SpigotTimings.processQueueTimer.stopTiming(); // Spigot

        SpigotTimings.timeUpdateTimer.startTiming(); // Spigot
        // Send time updates to everyone, it will get the right time from the world the player is in.
        if (this.tickCount % 20 == 0) {
            for (int i = 0; i < this.getPlayerList().players.size(); ++i) {
                EntityPlayer entityplayer = (EntityPlayer) this.getPlayerList().players.get(i);
                entityplayer.connection.send(new PacketPlayOutUpdateTime(entityplayer.level().getGameTime(), entityplayer.getPlayerTime(), entityplayer.level().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT))); // Add support for per player time
            }
        }
        SpigotTimings.timeUpdateTimer.stopTiming(); // Spigot

        for (WorldServer worldserver : this.getAllLevels()) {
            gameprofilerfiller.push(() -> {
                String s = String.valueOf(worldserver);

                return s + " " + String.valueOf(worldserver.dimension().location());
            });
            /* Drop global time updates
            if (this.tickCount % 20 == 0) {
                gameprofilerfiller.push("timeSync");
                this.synchronizeTime(worldserver);
                gameprofilerfiller.pop();
            }
            // CraftBukkit end */

            gameprofilerfiller.push("tick");

            try {
                worldserver.timings.doTick.startTiming(); // Spigot
                worldserver.tick(booleansupplier);
                worldserver.timings.doTick.stopTiming(); // Spigot
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception ticking world");

                worldserver.fillReportDetails(crashreport);
                throw new ReportedException(crashreport);
            }

            gameprofilerfiller.pop();
            gameprofilerfiller.pop();
        }

        gameprofilerfiller.popPush("connection");
        SpigotTimings.connectionTimer.startTiming(); // Spigot
        this.tickConnection();
        SpigotTimings.connectionTimer.stopTiming(); // Spigot
        gameprofilerfiller.popPush("players");
        SpigotTimings.playerListTimer.startTiming(); // Spigot
        this.playerList.tick();
        SpigotTimings.playerListTimer.stopTiming(); // Spigot
        if (this.tickRateManager.runsNormally()) {
            GameTestHarnessTicker.SINGLETON.tick();
        }

        gameprofilerfiller.popPush("server gui refresh");

        SpigotTimings.tickablesTimer.startTiming(); // Spigot
        for (int i = 0; i < this.tickables.size(); ++i) {
            ((Runnable) this.tickables.get(i)).run();
        }
        SpigotTimings.tickablesTimer.stopTiming(); // Spigot

        gameprofilerfiller.popPush("send chunks");

        for (EntityPlayer entityplayer : this.playerList.getPlayers()) {
            entityplayer.connection.chunkSender.sendNextChunks(entityplayer);
            entityplayer.connection.resumeFlushing();
        }

        gameprofilerfiller.pop();
    }

    public void tickConnection() {
        this.getConnection().tick();
    }

    private void synchronizeTime(WorldServer worldserver) {
        this.playerList.broadcastAll(new PacketPlayOutUpdateTime(worldserver.getGameTime(), worldserver.getDayTime(), worldserver.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)), worldserver.dimension());
    }

    public void forceTimeSynchronization() {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("timeSync");

        for (WorldServer worldserver : this.getAllLevels()) {
            this.synchronizeTime(worldserver);
        }

        gameprofilerfiller.pop();
    }

    public boolean isLevelEnabled(World world) {
        return true;
    }

    public void addTickable(Runnable runnable) {
        this.tickables.add(runnable);
    }

    protected void setId(String s) {
        this.serverId = s;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public Path getFile(String s) {
        return this.getServerDirectory().resolve(s);
    }

    public final WorldServer overworld() {
        return (WorldServer) this.levels.get(World.OVERWORLD);
    }

    @Nullable
    public WorldServer getLevel(ResourceKey<World> resourcekey) {
        return (WorldServer) this.levels.get(resourcekey);
    }

    // CraftBukkit start
    public void addLevel(WorldServer level) {
        Map<ResourceKey<World>, WorldServer> oldLevels = this.levels;
        Map<ResourceKey<World>, WorldServer> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.put(level.dimension(), level);
        this.levels = Collections.unmodifiableMap(newLevels);
    }

    public void removeLevel(WorldServer level) {
        Map<ResourceKey<World>, WorldServer> oldLevels = this.levels;
        Map<ResourceKey<World>, WorldServer> newLevels = Maps.newLinkedHashMap(oldLevels);
        newLevels.remove(level.dimension());
        this.levels = Collections.unmodifiableMap(newLevels);
    }
    // CraftBukkit end

    public Set<ResourceKey<World>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<WorldServer> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return this.playerList.getMaxPlayers();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    @DontObfuscate
    public String getServerModName() {
        return "Spigot"; // Spigot - Spigot > // CraftBukkit - cb > vanilla!
    }

    public SystemReport fillSystemReport(SystemReport systemreport) {
        systemreport.setDetail("Server Running", () -> {
            return Boolean.toString(this.running);
        });
        if (this.playerList != null) {
            systemreport.setDetail("Player Count", () -> {
                int i = this.playerList.getPlayerCount();

                return i + " / " + this.playerList.getMaxPlayers() + "; " + String.valueOf(this.playerList.getPlayers());
            });
        }

        systemreport.setDetail("Active Data Packs", () -> {
            return ResourcePackRepository.displayPackList(this.packRepository.getSelectedPacks());
        });
        systemreport.setDetail("Available Data Packs", () -> {
            return ResourcePackRepository.displayPackList(this.packRepository.getAvailablePacks());
        });
        systemreport.setDetail("Enabled Feature Flags", () -> {
            return (String) FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(MinecraftKey::toString).collect(Collectors.joining(", "));
        });
        systemreport.setDetail("World Generation", () -> {
            return this.worldData.worldGenSettingsLifecycle().toString();
        });
        systemreport.setDetail("World Seed", () -> {
            return String.valueOf(this.worldData.worldGenOptions().seed());
        });
        SuppressedExceptionCollector suppressedexceptioncollector = this.suppressedExceptions;

        Objects.requireNonNull(this.suppressedExceptions);
        systemreport.setDetail("Suppressed Exceptions", suppressedexceptioncollector::dump);
        if (this.serverId != null) {
            systemreport.setDetail("Server Id", () -> {
                return this.serverId;
            });
        }

        return this.fillServerSystemReport(systemreport);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport systemreport);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(IChatBaseComponent ichatbasecomponent) {
        MinecraftServer.LOGGER.info(ichatbasecomponent.getString());
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int i) {
        this.port = i;
    }

    @Nullable
    public GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile gameprofile) {
        this.singleplayerProfile = gameprofile;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        MinecraftServer.LOGGER.info("Generating keypair");

        try {
            this.keyPair = MinecraftEncryption.generateKeyPair();
        } catch (CryptographyException cryptographyexception) {
            throw new IllegalStateException("Failed to generate key pair", cryptographyexception);
        }
    }

    public void setDifficulty(EnumDifficulty enumdifficulty, boolean flag) {
        if (flag || !this.worldData.isDifficultyLocked()) {
            this.worldData.setDifficulty(this.worldData.isHardcore() ? EnumDifficulty.HARD : enumdifficulty);
            this.updateMobSpawningFlags();
            this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
        }
    }

    public int getScaledTrackingDistance(int i) {
        return i;
    }

    private void updateMobSpawningFlags() {
        for (WorldServer worldserver : this.getAllLevels()) {
            worldserver.setSpawnSettings(this.isSpawningMonsters());
        }

    }

    public void setDifficultyLocked(boolean flag) {
        this.worldData.setDifficultyLocked(flag);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(EntityPlayer entityplayer) {
        WorldData worlddata = entityplayer.level().getLevelData();

        entityplayer.connection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
    }

    public boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != EnumDifficulty.PEACEFUL;
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean flag) {
        this.isDemo = flag;
    }

    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean flag) {
        this.onlineMode = flag;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean flag) {
        this.preventProxyConnections = flag;
    }

    public abstract boolean isEpollEnabled();

    public boolean isPvpAllowed() {
        return this.pvp;
    }

    public void setPvpAllowed(boolean flag) {
        this.pvp = flag;
    }

    public boolean isFlightAllowed() {
        return this.allowFlight;
    }

    public void setFlightAllowed(boolean flag) {
        this.allowFlight = flag;
    }

    public abstract boolean isCommandBlockEnabled();

    @Override
    public String getMotd() {
        return this.motd;
    }

    public void setMotd(String s) {
        this.motd = s;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList playerlist) {
        this.playerList = playerlist;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(EnumGamemode enumgamemode) {
        this.worldData.setGameType(enumgamemode);
    }

    public ServerConnection getConnection() {
        return this.connection == null ? this.connection = new ServerConnection(this) : this.connection; // Spigot
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean hasGui() {
        return false;
    }

    public boolean publishServer(@Nullable EnumGamemode enumgamemode, boolean flag, int i) {
        return false;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isUnderSpawnProtection(WorldServer worldserver, BlockPosition blockposition, EntityHuman entityhuman) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int i) {
        this.playerIdleTimeout = i;
    }

    public MinecraftSessionService getSessionService() {
        return this.services.sessionService();
    }

    @Nullable
    public SignatureValidator getProfileKeySignatureValidator() {
        return this.services.profileKeySignatureValidator();
    }

    public GameProfileRepository getProfileRepository() {
        return this.services.profileRepository();
    }

    @Nullable
    public UserCache getProfileCache() {
        return this.services.profileCache();
    }

    @Nullable
    public ServerPing getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable runnable) {
        if (this.isStopped()) {
            throw new RejectedExecutionException("Server already shutting down");
        } else {
            super.executeIfPossible(runnable);
        }
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTimeNanos;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public int getSpawnRadius(@Nullable WorldServer worldserver) {
        return worldserver != null ? worldserver.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) : 10;
    }

    public AdvancementDataWorld getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public CustomFunctionData getFunctions() {
        return this.functionManager;
    }

    public CompletableFuture<Void> reloadResources(Collection<String> collection) {
        CompletableFuture<Void> completablefuture = CompletableFuture.supplyAsync(() -> {
            Stream<String> stream = collection.stream(); // CraftBukkit - decompile error
            ResourcePackRepository resourcepackrepository = this.packRepository;

            Objects.requireNonNull(this.packRepository);
            return stream.map(resourcepackrepository::getPack).filter(Objects::nonNull).map(ResourcePackLoader::open).collect(ImmutableList.toImmutableList()); // CraftBukkit - decompile error
        }, this).thenCompose((immutablelist) -> {
            IReloadableResourceManager ireloadableresourcemanager = new ResourceManager(EnumResourcePackType.SERVER_DATA, immutablelist);
            List<IRegistry.a<?>> list = TagDataPack.loadTagsForExistingRegistries(ireloadableresourcemanager, this.registries.compositeAccess());

            return DataPackResources.loadResources(ireloadableresourcemanager, this.registries, list, this.worldData.enabledFeatures(), this.isDedicatedServer() ? CommandDispatcher.ServerType.DEDICATED : CommandDispatcher.ServerType.INTEGRATED, this.getFunctionCompilationLevel(), this.executor, this).whenComplete((datapackresources, throwable) -> {
                if (throwable != null) {
                    ireloadableresourcemanager.close();
                }

            }).thenApply((datapackresources) -> {
                return new MinecraftServer.ReloadableResources(ireloadableresourcemanager, datapackresources);
            });
        }).thenAcceptAsync((minecraftserver_reloadableresources) -> {
            this.resources.close();
            this.resources = minecraftserver_reloadableresources;
            this.server.syncCommands(); // SPIGOT-5884: Lost on reload
            this.packRepository.setSelected(collection);
            WorldDataConfiguration worlddataconfiguration = new WorldDataConfiguration(getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures());

            this.worldData.setDataConfiguration(worlddataconfiguration);
            this.resources.managers.updateStaticRegistryTags();
            this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
            this.getPlayerList().saveAll();
            this.getPlayerList().reloadResources();
            this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
            this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
            this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
        }, this);

        if (this.isSameThread()) {
            Objects.requireNonNull(completablefuture);
            this.managedBlock(completablefuture::isDone);
        }

        return completablefuture;
    }

    public static WorldDataConfiguration configurePackRepository(ResourcePackRepository resourcepackrepository, WorldDataConfiguration worlddataconfiguration, boolean flag, boolean flag1) {
        DataPackConfiguration datapackconfiguration = worlddataconfiguration.dataPacks();
        FeatureFlagSet featureflagset = flag ? FeatureFlagSet.of() : worlddataconfiguration.enabledFeatures();
        FeatureFlagSet featureflagset1 = flag ? FeatureFlags.REGISTRY.allFlags() : worlddataconfiguration.enabledFeatures();

        resourcepackrepository.reload();
        if (flag1) {
            return configureRepositoryWithSelection(resourcepackrepository, List.of("vanilla"), featureflagset, false);
        } else {
            Set<String> set = Sets.newLinkedHashSet();

            for (String s : datapackconfiguration.getEnabled()) {
                if (resourcepackrepository.isAvailable(s)) {
                    set.add(s);
                } else {
                    MinecraftServer.LOGGER.warn("Missing data pack {}", s);
                }
            }

            for (ResourcePackLoader resourcepackloader : resourcepackrepository.getAvailablePacks()) {
                String s1 = resourcepackloader.getId();

                if (!datapackconfiguration.getDisabled().contains(s1)) {
                    FeatureFlagSet featureflagset2 = resourcepackloader.getRequestedFeatures();
                    boolean flag2 = set.contains(s1);

                    if (!flag2 && resourcepackloader.getPackSource().shouldAddAutomatically()) {
                        if (featureflagset2.isSubsetOf(featureflagset1)) {
                            MinecraftServer.LOGGER.info("Found new data pack {}, loading it automatically", s1);
                            set.add(s1);
                        } else {
                            MinecraftServer.LOGGER.info("Found new data pack {}, but can't load it due to missing features {}", s1, FeatureFlags.printMissingFlags(featureflagset1, featureflagset2));
                        }
                    }

                    if (flag2 && !featureflagset2.isSubsetOf(featureflagset1)) {
                        MinecraftServer.LOGGER.warn("Pack {} requires features {} that are not enabled for this world, disabling pack.", s1, FeatureFlags.printMissingFlags(featureflagset1, featureflagset2));
                        set.remove(s1);
                    }
                }
            }

            if (set.isEmpty()) {
                MinecraftServer.LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            return configureRepositoryWithSelection(resourcepackrepository, set, featureflagset, true);
        }
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(ResourcePackRepository resourcepackrepository, Collection<String> collection, FeatureFlagSet featureflagset, boolean flag) {
        resourcepackrepository.setSelected(collection);
        enableForcedFeaturePacks(resourcepackrepository, featureflagset);
        DataPackConfiguration datapackconfiguration = getSelectedPacks(resourcepackrepository, flag);
        FeatureFlagSet featureflagset1 = resourcepackrepository.getRequestedFeatureFlags().join(featureflagset);

        return new WorldDataConfiguration(datapackconfiguration, featureflagset1);
    }

    private static void enableForcedFeaturePacks(ResourcePackRepository resourcepackrepository, FeatureFlagSet featureflagset) {
        FeatureFlagSet featureflagset1 = resourcepackrepository.getRequestedFeatureFlags();
        FeatureFlagSet featureflagset2 = featureflagset.subtract(featureflagset1);

        if (!featureflagset2.isEmpty()) {
            Set<String> set = new ObjectArraySet(resourcepackrepository.getSelectedIds());

            for (ResourcePackLoader resourcepackloader : resourcepackrepository.getAvailablePacks()) {
                if (featureflagset2.isEmpty()) {
                    break;
                }

                if (resourcepackloader.getPackSource() == PackSource.FEATURE) {
                    String s = resourcepackloader.getId();
                    FeatureFlagSet featureflagset3 = resourcepackloader.getRequestedFeatures();

                    if (!featureflagset3.isEmpty() && featureflagset3.intersects(featureflagset2) && featureflagset3.isSubsetOf(featureflagset)) {
                        if (!set.add(s)) {
                            throw new IllegalStateException("Tried to force '" + s + "', but it was already enabled");
                        }

                        MinecraftServer.LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", s);
                        featureflagset2 = featureflagset2.subtract(featureflagset3);
                    }
                }
            }

            resourcepackrepository.setSelected(set);
        }
    }

    private static DataPackConfiguration getSelectedPacks(ResourcePackRepository resourcepackrepository, boolean flag) {
        Collection<String> collection = resourcepackrepository.getSelectedIds();
        List<String> list = ImmutableList.copyOf(collection);
        List<String> list1 = flag ? resourcepackrepository.getAvailableIds().stream().filter((s) -> {
            return !collection.contains(s);
        }).toList() : List.of();

        return new DataPackConfiguration(list, list1);
    }

    public void kickUnlistedPlayers(CommandListenerWrapper commandlistenerwrapper) {
        if (this.isEnforceWhitelist()) {
            PlayerList playerlist = commandlistenerwrapper.getServer().getPlayerList();
            WhiteList whitelist = playerlist.getWhiteList();

            for (EntityPlayer entityplayer : Lists.newArrayList(playerlist.getPlayers())) {
                if (!whitelist.isWhiteListed(entityplayer.getGameProfile())) {
                    entityplayer.connection.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.not_whitelisted"));
                }
            }

        }
    }

    public ResourcePackRepository getPackRepository() {
        return this.packRepository;
    }

    public CommandDispatcher getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandListenerWrapper createCommandSourceStack() {
        WorldServer worldserver = this.overworld();

        return new CommandListenerWrapper(this, worldserver == null ? Vec3D.ZERO : Vec3D.atLowerCornerOf(worldserver.getSharedSpawnPos()), Vec2F.ZERO, worldserver, 4, "Server", IChatBaseComponent.literal("Server"), this, (Entity) null);
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public CraftingManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ScoreboardServer getScoreboard() {
        return this.scoreboard;
    }

    public PersistentCommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public BossBattleCustomData getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean flag) {
        this.enforceWhitelist = flag;
    }

    public float getCurrentSmoothedTickTime() {
        return this.smoothedTickTimeMillis;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public long getAverageTickTimeNanos() {
        return this.aggregatedTickTimesNanos / (long) Math.min(100, Math.max(this.tickCount, 1));
    }

    public long[] getTickTimesNanos() {
        return this.tickTimesNanos;
    }

    public int getProfilePermissions(GameProfile gameprofile) {
        if (this.getPlayerList().isOp(gameprofile)) {
            OpListEntry oplistentry = (OpListEntry) this.getPlayerList().getOps().get(gameprofile);

            return oplistentry != null ? oplistentry.getLevel() : (this.isSingleplayerOwner(gameprofile) ? 4 : (this.isSingleplayer() ? (this.getPlayerList().isAllowCommandsForAllPlayers() ? 4 : 0) : this.getOperatorUserPermissionLevel()));
        } else {
            return 0;
        }
    }

    public abstract boolean isSingleplayerOwner(GameProfile gameprofile);

    public void dumpServerProperties(Path path) throws IOException {}

    private void saveDebugReport(Path path) {
        Path path1 = path.resolve("levels");

        try {
            for (Map.Entry<ResourceKey<World>, WorldServer> map_entry : this.levels.entrySet()) {
                MinecraftKey minecraftkey = ((ResourceKey) map_entry.getKey()).location();
                Path path2 = path1.resolve(minecraftkey.getNamespace()).resolve(minecraftkey.getPath());

                Files.createDirectories(path2);
                ((WorldServer) map_entry.getValue()).saveDebugReport(path2);
            }

            this.dumpGameRules(path.resolve("gamerules.txt"));
            this.dumpClasspath(path.resolve("classpath.txt"));
            this.dumpMiscStats(path.resolve("stats.txt"));
            this.dumpThreads(path.resolve("threads.txt"));
            this.dumpServerProperties(path.resolve("server.properties.txt"));
            this.dumpNativeModules(path.resolve("modules.txt"));
        } catch (IOException ioexception) {
            MinecraftServer.LOGGER.warn("Failed to save debug report", ioexception);
        }

    }

    private void dumpMiscStats(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            writer.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
            writer.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
            writer.write(String.format(Locale.ROOT, "queue: %s\n", SystemUtils.backgroundExecutor()));
        }

    }

    private void dumpGameRules(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            final List<String> list = Lists.newArrayList();
            final GameRules gamerules = this.getGameRules();

            gamerules.visitGameRuleTypes(new GameRules.GameRuleVisitor() {
                @Override
                public <T extends GameRules.GameRuleValue<T>> void visit(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.GameRuleDefinition<T> gamerules_gameruledefinition) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", gamerules_gamerulekey.getId(), gamerules.getRule(gamerules_gamerulekey)));
                }
            });

            for (String s : list) {
                writer.write(s);
            }
        }

    }

    private void dumpClasspath(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            String s = System.getProperty("java.class.path");
            String s1 = System.getProperty("path.separator");

            for (String s2 : Splitter.on(s1).split(s)) {
                writer.write(s2);
                writer.write("\n");
            }
        }

    }

    private void dumpThreads(Path path) throws IOException {
        ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);

        Arrays.sort(athreadinfo, Comparator.comparing(ThreadInfo::getThreadName));

        try (Writer writer = Files.newBufferedWriter(path)) {
            for (ThreadInfo threadinfo : athreadinfo) {
                writer.write(threadinfo.toString());
                writer.write(10);
            }
        }

    }

    private void dumpNativeModules(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            List<NativeModuleLister.a> list;

            try {
                list = Lists.newArrayList(NativeModuleLister.listModules());
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to list native modules", throwable);
                return;
            }

            list.sort(Comparator.comparing((nativemodulelister_a) -> {
                return nativemodulelister_a.name;
            }));

            for (NativeModuleLister.a nativemodulelister_a : list) {
                writer.write(nativemodulelister_a.toString());
                writer.write(10);
            }

        }
    }

    // CraftBukkit start
    public boolean isDebugging() {
        return false;
    }

    @Deprecated
    public static MinecraftServer getServer() {
        return (Bukkit.getServer() instanceof CraftServer) ? ((CraftServer) Bukkit.getServer()).getServer() : null;
    }

    @Deprecated
    public static IRegistryCustom getDefaultRegistryAccess() {
        return CraftRegistry.getMinecraftRegistry();
    }
    // CraftBukkit end

    private GameProfilerFiller createProfiler() {
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(new ServerMetricsSamplersProvider(SystemUtils.timeSource, this.isDedicatedServer()), SystemUtils.timeSource, SystemUtils.ioPool(), new MetricsPersister("server"), this.onMetricsRecordingStopped, (path) -> {
                this.executeBlocking(() -> {
                    this.saveDebugReport(path.resolve("server"));
                });
                this.onMetricsRecordingFinished.accept(path);
            });
            this.willStartRecordingMetrics = false;
        }

        this.metricsRecorder.startTick();
        return GameProfilerTick.decorateFiller(this.metricsRecorder.getProfiler(), GameProfilerTick.createTickProfiler("Server"));
    }

    public void endMetricsRecordingTick() {
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<MethodProfilerResults> consumer, Consumer<Path> consumer1) {
        this.onMetricsRecordingStopped = (methodprofilerresults) -> {
            this.stopRecordingMetrics();
            consumer.accept(methodprofilerresults);
        };
        this.onMetricsRecordingFinished = consumer1;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
    }

    public Path getWorldPath(SavedFile savedfile) {
        return this.storageSource.getLevelPath(savedfile);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public SaveData getWorldData() {
        return this.worldData;
    }

    public IRegistryCustom.Dimension registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public ReloadableServerRegistries.a reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public ITextFilter createTextFilterForPlayer(EntityPlayer entityplayer) {
        return ITextFilter.DUMMY;
    }

    public PlayerInteractManager createGameModeForPlayer(EntityPlayer entityplayer) {
        return (PlayerInteractManager) (this.isDemo() ? new DemoPlayerInteractManager(entityplayer) : new PlayerInteractManager(entityplayer));
    }

    @Nullable
    public EnumGamemode getForcedGameType() {
        return null;
    }

    public IResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public MethodProfilerResults stopTimeProfiler() {
        if (this.debugCommandProfiler == null) {
            return MethodProfilerResultsEmpty.EMPTY;
        } else {
            MethodProfilerResults methodprofilerresults = this.debugCommandProfiler.stop(SystemUtils.getNanos(), this.tickCount);

            this.debugCommandProfiler = null;
            return methodprofilerresults;
        }
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(IChatBaseComponent ichatbasecomponent, ChatMessageType.a chatmessagetype_a, @Nullable String s) {
        String s1 = chatmessagetype_a.decorate(ichatbasecomponent).getString();

        if (s != null) {
            MinecraftServer.LOGGER.info("[{}] {}", s, s1);
        } else {
            MinecraftServer.LOGGER.info("{}", s1);
        }

    }

    // CraftBukkit start
    public final java.util.concurrent.ExecutorService chatExecutor = java.util.concurrent.Executors.newCachedThreadPool(
            new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Chat Thread - #%d").build());
    // CraftBukkit end

    public ChatDecorator getChatDecorator() {
        return ChatDecorator.PLAIN;
    }

    public boolean logIPs() {
        return true;
    }

    public void subscribeToDebugSample(EntityPlayer entityplayer, RemoteDebugSampleType remotedebugsampletype) {}

    // CraftBukkit start - add player
    public void handleCustomClickAction(MinecraftKey minecraftkey, Optional<NBTBase> optional, EntityPlayer player) {
        MinecraftServer.LOGGER.debug("Received custom click action {} with payload {}", minecraftkey, optional.orElse(null)); // CraftBukkit - decompile error
        CraftEventFactory.callPlayerCustomClickEvent(minecraftkey, optional, player);
        // CraftBukkit end
    }

    public boolean acceptsTransfers() {
        return false;
    }

    private void storeChunkIoError(CrashReport crashreport, ChunkCoordIntPair chunkcoordintpair, RegionStorageInfo regionstorageinfo) {
        SystemUtils.ioPool().execute(() -> {
            try {
                Path path = this.getFile("debug");

                FileUtils.createDirectoriesSafe(path);
                String s = FileUtils.sanitizeName(regionstorageinfo.level());
                Path path1 = path.resolve("chunk-" + s + "-" + SystemUtils.getFilenameFormattedDateTime() + "-server.txt");
                FileStore filestore = Files.getFileStore(path);
                long i = filestore.getUsableSpace();

                if (i < 8192L) {
                    MinecraftServer.LOGGER.warn("Not storing chunk IO report due to low space on drive {}", filestore.name());
                    return;
                }

                CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Chunk Info");

                Objects.requireNonNull(regionstorageinfo);
                crashreportsystemdetails.setDetail("Level", regionstorageinfo::level);
                crashreportsystemdetails.setDetail("Dimension", () -> {
                    return regionstorageinfo.dimension().location().toString();
                });
                Objects.requireNonNull(regionstorageinfo);
                crashreportsystemdetails.setDetail("Storage", regionstorageinfo::type);
                Objects.requireNonNull(chunkcoordintpair);
                crashreportsystemdetails.setDetail("Position", chunkcoordintpair::toString);
                crashreport.saveToFile(path1, ReportType.CHUNK_IO_ERROR);
                MinecraftServer.LOGGER.info("Saved details to {}", crashreport.getSaveFile());
            } catch (Exception exception) {
                MinecraftServer.LOGGER.warn("Failed to store chunk IO exception", exception);
            }

        });
    }

    @Override
    public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo regionstorageinfo, ChunkCoordIntPair chunkcoordintpair) {
        MinecraftServer.LOGGER.error("Failed to load chunk {},{}", new Object[]{chunkcoordintpair.x, chunkcoordintpair.z, throwable});
        this.suppressedExceptions.addEntry("chunk/load", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk load failure"), chunkcoordintpair, regionstorageinfo);
    }

    @Override
    public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo regionstorageinfo, ChunkCoordIntPair chunkcoordintpair) {
        MinecraftServer.LOGGER.error("Failed to save chunk {},{}", new Object[]{chunkcoordintpair.x, chunkcoordintpair.z, throwable});
        this.suppressedExceptions.addEntry("chunk/save", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk save failure"), chunkcoordintpair, regionstorageinfo);
    }

    public void reportPacketHandlingException(Throwable throwable, PacketType<?> packettype) {
        this.suppressedExceptions.addEntry("packet/" + packettype.toString(), throwable);
    }

    public PotionBrewer potionBrewing() {
        return this.potionBrewing;
    }

    public FuelValues fuelValues() {
        return this.fuelValues;
    }

    public ServerLinks serverLinks() {
        return ServerLinks.EMPTY;
    }

    protected int pauseWhileEmptySeconds() {
        return 0;
    }

    public static record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable IChatBaseComponent prompt) {

    }

    public static record ReloadableResources(IReloadableResourceManager resourceManager, DataPackResources managers) implements AutoCloseable {

        public void close() {
            this.resourceManager.close();
        }
    }

    private static class TimeProfiler {

        final long startNanos;
        final int startTick;

        TimeProfiler(long i, int j) {
            this.startNanos = i;
            this.startTick = j;
        }

        MethodProfilerResults stop(final long i, final int j) {
            return new MethodProfilerResults() {
                @Override
                public List<MethodProfilerResultsField> getTimes(String s) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path path) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return i;
                }

                @Override
                public int getEndTimeTicks() {
                    return j;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }
}
