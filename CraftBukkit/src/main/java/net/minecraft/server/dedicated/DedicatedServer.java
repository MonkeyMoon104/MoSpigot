package net.minecraft.server.dedicated;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.SystemUtils;
import net.minecraft.ThreadNamedUncaughtExceptionHandler;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.IMinecraftServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerCommand;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.gui.ServerGUI;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.level.progress.WorldLoadListenerFactory;
import net.minecraft.server.network.ITextFilter;
import net.minecraft.server.network.ServerTextFilter;
import net.minecraft.server.packs.repository.ResourcePackRepository;
import net.minecraft.server.players.NameReferencingFileConverter;
import net.minecraft.server.players.UserCache;
import net.minecraft.server.rcon.RemoteControlCommandListener;
import net.minecraft.server.rcon.thread.RemoteControlListener;
import net.minecraft.server.rcon.thread.RemoteStatusListener;
import net.minecraft.util.MathHelper;
import net.minecraft.util.debugchart.DebugSampleSubscriptionTracker;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.RemoteSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.monitoring.jmx.MinecraftServerBeans;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.entity.TileEntitySkull;
import net.minecraft.world.level.storage.Convertable;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.server.WorldLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.io.IoBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.util.TerminalCompletionHandler;
import org.bukkit.craftbukkit.util.TerminalConsoleWriterThread;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
// CraftBukkit end

public class DedicatedServer extends MinecraftServer implements IMinecraftServer {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONVERSION_RETRY_DELAY_MS = 5000;
    private static final int CONVERSION_RETRIES = 2;
    private final List<ServerCommand> consoleInput = Collections.synchronizedList(Lists.newArrayList());
    @Nullable
    private RemoteStatusListener queryThreadGs4;
    // private final RemoteControlCommandListener rconConsoleSource; // CraftBukkit - remove field
    @Nullable
    private RemoteControlListener rconThread;
    public DedicatedServerSettings settings;
    @Nullable
    private ServerGUI gui;
    @Nullable
    private final ServerTextFilter serverTextFilter;
    @Nullable
    private RemoteSampleLogger tickTimeLogger;
    @Nullable
    private DebugSampleSubscriptionTracker debugSampleSubscriptionTracker;
    public ServerLinks serverLinks;

    // CraftBukkit start - Signature changed
    public DedicatedServer(joptsimple.OptionSet options, WorldLoader.a worldLoader, Thread thread, Convertable.ConversionSession convertable_conversionsession, ResourcePackRepository resourcepackrepository, WorldStem worldstem, DedicatedServerSettings dedicatedserversettings, DataFixer datafixer, Services services, WorldLoadListenerFactory worldloadlistenerfactory) {
        super(options, worldLoader, thread, convertable_conversionsession, resourcepackrepository, worldstem, Proxy.NO_PROXY, datafixer, services, worldloadlistenerfactory);
        // CraftBukkit end
        this.settings = dedicatedserversettings;
        // this.rconConsoleSource = new RemoteControlCommandListener(this); // CraftBukkit - remove field
        this.serverTextFilter = ServerTextFilter.createFromConfig(dedicatedserversettings.getProperties());
        this.serverLinks = createServerLinks(dedicatedserversettings);
    }

    @Override
    public boolean initServer() throws IOException {
        Thread thread = new Thread("Server console handler") {
            public void run() {
                // CraftBukkit start
                if (!org.bukkit.craftbukkit.Main.useConsole) {
                    return;
                }
                jline.console.ConsoleReader bufferedreader = reader;

                // MC-33041, SPIGOT-5538: if System.in is not valid due to javaw, then return
                try {
                    System.in.available();
                } catch (IOException ex) {
                    return;
                }
                // CraftBukkit end

                String s;

                try {
                    // CraftBukkit start - JLine disabling compatibility
                    while (!DedicatedServer.this.isStopped() && DedicatedServer.this.isRunning()) {
                        if (org.bukkit.craftbukkit.Main.useJline) {
                            s = bufferedreader.readLine(">", null);
                        } else {
                            s = bufferedreader.readLine();
                        }

                        // SPIGOT-5220: Throttle if EOF (ctrl^d) or stdin is /dev/null
                        if (s == null) {
                            try {
                                Thread.sleep(50L);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        if (s.trim().length() > 0) { // Trim to filter lines which are just spaces
                            DedicatedServer.this.handleConsoleInput(s, DedicatedServer.this.createCommandSourceStack());
                        }
                        // CraftBukkit end
                    }
                } catch (IOException ioexception) {
                    DedicatedServer.LOGGER.error("Exception handling console input", ioexception);
                }

            }
        };

        // CraftBukkit start - TODO: handle command-line logging arguments
        java.util.logging.Logger global = java.util.logging.Logger.getLogger("");
        global.setUseParentHandlers(false);
        for (java.util.logging.Handler handler : global.getHandlers()) {
            global.removeHandler(handler);
        }
        global.addHandler(new org.bukkit.craftbukkit.util.ForwardLogHandler());

        final org.apache.logging.log4j.core.Logger logger = ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger());
        for (org.apache.logging.log4j.core.Appender appender : logger.getAppenders().values()) {
            if (appender instanceof org.apache.logging.log4j.core.appender.ConsoleAppender) {
                logger.removeAppender(appender);
            }
        }

        TerminalConsoleWriterThread writerThread = new TerminalConsoleWriterThread(System.out, this.reader);
        this.reader.setCompletionHandler(new TerminalCompletionHandler(writerThread, this.reader.getCompletionHandler()));
        writerThread.start();

        System.setOut(IoBuilder.forLogger(logger).setLevel(Level.INFO).buildPrintStream());
        System.setErr(IoBuilder.forLogger(logger).setLevel(Level.WARN).buildPrintStream());
        // CraftBukkit end

        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(DedicatedServer.LOGGER));
        thread.start();
        DedicatedServer.LOGGER.info("Starting minecraft server version {}", SharedConstants.getCurrentVersion().name());
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            DedicatedServer.LOGGER.warn("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        DedicatedServer.LOGGER.info("Loading properties");
        DedicatedServerProperties dedicatedserverproperties = this.settings.getProperties();

        if (this.isSingleplayer()) {
            this.setLocalIp("127.0.0.1");
        } else {
            this.setUsesAuthentication(dedicatedserverproperties.onlineMode);
            this.setPreventProxyConnections(dedicatedserverproperties.preventProxyConnections);
            this.setLocalIp(dedicatedserverproperties.serverIp);
        }

        this.setPvpAllowed(dedicatedserverproperties.pvp);
        this.setFlightAllowed(dedicatedserverproperties.allowFlight);
        this.setMotd(dedicatedserverproperties.motd);
        super.setPlayerIdleTimeout((Integer) dedicatedserverproperties.playerIdleTimeout.get());
        this.setEnforceWhitelist(dedicatedserverproperties.enforceWhitelist);
        // this.worldData.setGameType(dedicatedserverproperties.gamemode); // CraftBukkit - moved to world loading
        DedicatedServer.LOGGER.info("Default game type: {}", dedicatedserverproperties.gamemode);
        InetAddress inetaddress = null;

        if (!this.getLocalIp().isEmpty()) {
            inetaddress = InetAddress.getByName(this.getLocalIp());
        }

        if (this.getPort() < 0) {
            this.setPort(dedicatedserverproperties.serverPort);
        }

        this.initializeKeyPair();
        DedicatedServer.LOGGER.info("Starting Minecraft server on {}:{}", this.getLocalIp().isEmpty() ? "*" : this.getLocalIp(), this.getPort());

        try {
            this.getConnection().startTcpServerListener(inetaddress, this.getPort());
        } catch (IOException ioexception) {
            DedicatedServer.LOGGER.warn("**** FAILED TO BIND TO PORT!");
            DedicatedServer.LOGGER.warn("The exception was: {}", ioexception.toString());
            DedicatedServer.LOGGER.warn("Perhaps a server is already running on that port?");
            return false;
        }

        // CraftBukkit start
        this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
        server.loadPlugins();
        server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.STARTUP);
        // CraftBukkit end

        if (!this.usesAuthentication()) {
            DedicatedServer.LOGGER.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            DedicatedServer.LOGGER.warn("The server will make no attempt to authenticate usernames. Beware.");
            DedicatedServer.LOGGER.warn("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            DedicatedServer.LOGGER.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
        }

        if (this.convertOldUsers()) {
            this.getProfileCache().save();
        }

        if (!NameReferencingFileConverter.serverReadyAfterUserconversion(this)) {
            return false;
        } else {
            // this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage)); // CraftBukkit - moved up
            this.debugSampleSubscriptionTracker = new DebugSampleSubscriptionTracker(this.getPlayerList());
            this.tickTimeLogger = new RemoteSampleLogger(TpsDebugDimensions.values().length, this.debugSampleSubscriptionTracker, RemoteDebugSampleType.TICK_TIME);
            long i = SystemUtils.getNanos();

            TileEntitySkull.setup(this.services, this);
            UserCache.setUsesAuthentication(this.usesAuthentication());
            DedicatedServer.LOGGER.info("Preparing level \"{}\"", this.getLevelIdName());
            this.loadLevel(storageSource.getLevelId()); // CraftBukkit
            long j = SystemUtils.getNanos() - i;
            String s = String.format(Locale.ROOT, "%.3fs", (double) j / 1.0E9D);

            DedicatedServer.LOGGER.info("Done ({})! For help, type \"help\"", s);
            if (dedicatedserverproperties.announcePlayerAchievements != null) {
                ((GameRules.GameRuleBoolean) this.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)).set(dedicatedserverproperties.announcePlayerAchievements, this.overworld()); // CraftBukkit - per-world
            }

            if (dedicatedserverproperties.enableQuery) {
                DedicatedServer.LOGGER.info("Starting GS4 status listener");
                this.queryThreadGs4 = RemoteStatusListener.create(this);
            }

            if (dedicatedserverproperties.enableRcon) {
                DedicatedServer.LOGGER.info("Starting remote control listener");
                this.rconThread = RemoteControlListener.create(this);
            }

            if (this.getMaxTickLength() > 0L) {
                Thread thread1 = new Thread(new ThreadWatchdog(this));

                thread1.setUncaughtExceptionHandler(new ThreadNamedUncaughtExceptionHandler(DedicatedServer.LOGGER));
                thread1.setName("Server Watchdog");
                thread1.setDaemon(true);
                thread1.start();
            }

            if (dedicatedserverproperties.enableJmxMonitoring) {
                MinecraftServerBeans.registerJmxMonitoring(this);
                DedicatedServer.LOGGER.info("JMX monitoring enabled");
            }

            return true;
        }
    }

    @Override
    public boolean isSpawningMonsters() {
        return this.settings.getProperties().spawnMonsters && super.isSpawningMonsters();
    }

    @Override
    public DedicatedServerProperties getProperties() {
        return this.settings.getProperties();
    }

    @Override
    public void forceDifficulty() {
        this.setDifficulty(this.getProperties().difficulty, true);
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport systemreport) {
        systemreport.setDetail("Is Modded", () -> {
            return this.getModdedStatus().fullDescription();
        });
        systemreport.setDetail("Type", () -> {
            return "Dedicated Server (map_server.txt)";
        });
        return systemreport;
    }

    @Override
    public void dumpServerProperties(Path path) throws IOException {
        DedicatedServerProperties dedicatedserverproperties = this.getProperties();

        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(String.format(Locale.ROOT, "sync-chunk-writes=%s%n", dedicatedserverproperties.syncChunkWrites));
            writer.write(String.format(Locale.ROOT, "gamemode=%s%n", dedicatedserverproperties.gamemode));
            writer.write(String.format(Locale.ROOT, "spawn-monsters=%s%n", dedicatedserverproperties.spawnMonsters));
            writer.write(String.format(Locale.ROOT, "entity-broadcast-range-percentage=%d%n", dedicatedserverproperties.entityBroadcastRangePercentage));
            writer.write(String.format(Locale.ROOT, "max-world-size=%d%n", dedicatedserverproperties.maxWorldSize));
            writer.write(String.format(Locale.ROOT, "view-distance=%d%n", dedicatedserverproperties.viewDistance));
            writer.write(String.format(Locale.ROOT, "simulation-distance=%d%n", dedicatedserverproperties.simulationDistance));
            writer.write(String.format(Locale.ROOT, "generate-structures=%s%n", dedicatedserverproperties.worldOptions.generateStructures()));
            writer.write(String.format(Locale.ROOT, "use-native=%s%n", dedicatedserverproperties.useNativeTransport));
            writer.write(String.format(Locale.ROOT, "rate-limit=%d%n", dedicatedserverproperties.rateLimitPacketsPerSecond));
        }

    }

    @Override
    public void onServerExit() {
        if (this.serverTextFilter != null) {
            this.serverTextFilter.close();
        }

        if (this.gui != null) {
            this.gui.close();
        }

        if (this.rconThread != null) {
            this.rconThread.stop();
        }

        if (this.queryThreadGs4 != null) {
            this.queryThreadGs4.stop();
        }

        System.exit(0); // CraftBukkit
    }

    @Override
    public void tickConnection() {
        super.tickConnection();
        this.handleConsoleInputs();
    }

    @Override
    public boolean isLevelEnabled(World world) {
        return world.dimension() == World.NETHER ? this.getProperties().allowNether : true;
    }

    public void handleConsoleInput(String s, CommandListenerWrapper commandlistenerwrapper) {
        this.consoleInput.add(new ServerCommand(s, commandlistenerwrapper));
    }

    public void handleConsoleInputs() {
        while (!this.consoleInput.isEmpty()) {
            ServerCommand servercommand = (ServerCommand) this.consoleInput.remove(0);

            // CraftBukkit start - ServerCommand for preprocessing
            ServerCommandEvent event = new ServerCommandEvent(console, servercommand.msg);
            server.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            servercommand = new ServerCommand(event.getCommand(), servercommand.source);

            // this.getCommands().performPrefixedCommand(servercommand.source, servercommand.msg); // Called in dispatchServerCommand
            server.dispatchServerCommand(console, servercommand);
            // CraftBukkit end
        }

    }

    @Override
    public boolean isDedicatedServer() {
        return true;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return this.getProperties().rateLimitPacketsPerSecond;
    }

    @Override
    public boolean isEpollEnabled() {
        return this.getProperties().useNativeTransport;
    }

    @Override
    public DedicatedPlayerList getPlayerList() {
        return (DedicatedPlayerList) super.getPlayerList();
    }

    @Override
    public boolean isPublished() {
        return true;
    }

    @Override
    public String getServerIp() {
        return this.getLocalIp();
    }

    @Override
    public int getServerPort() {
        return this.getPort();
    }

    @Override
    public String getServerName() {
        return this.getMotd();
    }

    public void showGui() {
        if (this.gui == null) {
            this.gui = ServerGUI.showFrameFor(this);
        }

    }

    @Override
    public boolean hasGui() {
        return this.gui != null;
    }

    @Override
    public boolean isCommandBlockEnabled() {
        return this.getProperties().enableCommandBlock;
    }

    @Override
    public int getSpawnProtectionRadius() {
        return this.getProperties().spawnProtection;
    }

    @Override
    public boolean isUnderSpawnProtection(WorldServer worldserver, BlockPosition blockposition, EntityHuman entityhuman) {
        if (worldserver.dimension() != World.OVERWORLD) {
            return false;
        } else if (this.getPlayerList().getOps().isEmpty()) {
            return false;
        } else if (this.getPlayerList().isOp(entityhuman.getGameProfile())) {
            return false;
        } else if (this.getSpawnProtectionRadius() <= 0) {
            return false;
        } else {
            BlockPosition blockposition1 = worldserver.getSharedSpawnPos();
            int i = MathHelper.abs(blockposition.getX() - blockposition1.getX());
            int j = MathHelper.abs(blockposition.getZ() - blockposition1.getZ());
            int k = Math.max(i, j);

            return k <= this.getSpawnProtectionRadius();
        }
    }

    @Override
    public boolean repliesToStatus() {
        return this.getProperties().enableStatus;
    }

    @Override
    public boolean hidesOnlinePlayers() {
        return this.getProperties().hideOnlinePlayers;
    }

    @Override
    public int getOperatorUserPermissionLevel() {
        return this.getProperties().opPermissionLevel;
    }

    @Override
    public int getFunctionCompilationLevel() {
        return this.getProperties().functionPermissionLevel;
    }

    @Override
    public void setPlayerIdleTimeout(int i) {
        super.setPlayerIdleTimeout(i);
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.playerIdleTimeout.update(this.registryAccess(), i);
        });
    }

    @Override
    public boolean shouldRconBroadcast() {
        return this.getProperties().broadcastRconToOps;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.getProperties().broadcastConsoleToOps;
    }

    @Override
    public int getAbsoluteMaxWorldSize() {
        return this.getProperties().maxWorldSize;
    }

    @Override
    public int getCompressionThreshold() {
        return this.getProperties().networkCompressionThreshold;
    }

    @Override
    public boolean enforceSecureProfile() {
        DedicatedServerProperties dedicatedserverproperties = this.getProperties();

        return dedicatedserverproperties.enforceSecureProfile && dedicatedserverproperties.onlineMode && this.services.canValidateProfileKeys();
    }

    @Override
    public boolean logIPs() {
        return this.getProperties().logIPs;
    }

    protected boolean convertOldUsers() {
        boolean flag = false;

        for (int i = 0; !flag && i <= 2; ++i) {
            if (i > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag = NameReferencingFileConverter.convertUserBanlist(this);
        }

        boolean flag1 = false;

        for (int j = 0; !flag1 && j <= 2; ++j) {
            if (j > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag1 = NameReferencingFileConverter.convertIpBanlist(this);
        }

        boolean flag2 = false;

        for (int k = 0; !flag2 && k <= 2; ++k) {
            if (k > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
                this.waitForRetry();
            }

            flag2 = NameReferencingFileConverter.convertOpsList(this);
        }

        boolean flag3 = false;

        for (int l = 0; !flag3 && l <= 2; ++l) {
            if (l > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag3 = NameReferencingFileConverter.convertWhiteList(this);
        }

        boolean flag4 = false;

        for (int i1 = 0; !flag4 && i1 <= 2; ++i1) {
            if (i1 > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the player save files, retrying in a few seconds");
                this.waitForRetry();
            }

            flag4 = NameReferencingFileConverter.convertPlayers(this);
        }

        return flag || flag1 || flag2 || flag3 || flag4;
    }

    private void waitForRetry() {
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException interruptedexception) {
            ;
        }
    }

    public long getMaxTickLength() {
        return this.getProperties().maxTickTime;
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        return this.getProperties().maxChainedNeighborUpdates;
    }

    @Override
    public String getPluginNames() {
        // CraftBukkit start - Whole method
        StringBuilder result = new StringBuilder();
        org.bukkit.plugin.Plugin[] plugins = server.getPluginManager().getPlugins();

        result.append(server.getName());
        result.append(" on Bukkit ");
        result.append(server.getBukkitVersion());

        if (plugins.length > 0 && server.getQueryPlugins()) {
            result.append(": ");

            for (int i = 0; i < plugins.length; i++) {
                if (i > 0) {
                    result.append("; ");
                }

                result.append(plugins[i].getDescription().getName());
                result.append(" ");
                result.append(plugins[i].getDescription().getVersion().replaceAll(";", ","));
            }
        }

        return result.toString();
        // CraftBukkit end
    }

    @Override
    public String runCommand(String s) {
        // CraftBukkit start - fire RemoteServerCommandEvent
        throw new UnsupportedOperationException("Not supported - remote source required.");
    }

    public String runCommand(RemoteControlCommandListener rconConsoleSource, String s) {
        rconConsoleSource.prepareForCommand();
        this.executeBlocking(() -> {
            CommandListenerWrapper wrapper = rconConsoleSource.createCommandSourceStack();
            RemoteServerCommandEvent event = new RemoteServerCommandEvent(rconConsoleSource.getBukkitSender(wrapper), s);
            server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            ServerCommand serverCommand = new ServerCommand(event.getCommand(), wrapper);
            server.dispatchServerCommand(event.getSender(), serverCommand);
        });
        return rconConsoleSource.getCommandResponse();
        // CraftBukkit end
    }

    public void storeUsingWhiteList(boolean flag) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.whiteList.update(this.registryAccess(), flag);
        });
    }

    @Override
    public void stopServer() {
        super.stopServer();
        SystemUtils.shutdownExecutors();
        TileEntitySkull.clear();
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile gameprofile) {
        return false;
    }

    @Override
    public int getScaledTrackingDistance(int i) {
        return this.getProperties().entityBroadcastRangePercentage * i / 100;
    }

    @Override
    public String getLevelIdName() {
        return this.storageSource.getLevelId();
    }

    @Override
    public boolean forceSynchronousWrites() {
        return this.settings.getProperties().syncChunkWrites;
    }

    @Override
    public ITextFilter createTextFilterForPlayer(EntityPlayer entityplayer) {
        return this.serverTextFilter != null ? this.serverTextFilter.createContext(entityplayer.getGameProfile()) : ITextFilter.DUMMY;
    }

    @Nullable
    @Override
    public EnumGamemode getForcedGameType() {
        return this.settings.getProperties().forceGameMode ? this.worldData.getGameType() : null;
    }

    @Override
    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return this.settings.getProperties().serverResourcePackInfo;
    }

    @Override
    public void endMetricsRecordingTick() {
        super.endMetricsRecordingTick();
        this.debugSampleSubscriptionTracker.tick(this.getTickCount());
    }

    @Override
    public SampleLogger getTickTimeLogger() {
        return this.tickTimeLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return this.debugSampleSubscriptionTracker.shouldLogSamples(RemoteDebugSampleType.TICK_TIME);
    }

    @Override
    public void subscribeToDebugSample(EntityPlayer entityplayer, RemoteDebugSampleType remotedebugsampletype) {
        this.debugSampleSubscriptionTracker.subscribe(entityplayer, remotedebugsampletype);
    }

    @Override
    public boolean acceptsTransfers() {
        return this.settings.getProperties().acceptsTransfers;
    }

    @Override
    public ServerLinks serverLinks() {
        return this.serverLinks;
    }

    @Override
    public int pauseWhileEmptySeconds() {
        return this.settings.getProperties().pauseWhenEmptySeconds;
    }

    private static ServerLinks createServerLinks(DedicatedServerSettings dedicatedserversettings) {
        Optional<URI> optional = parseBugReportLink(dedicatedserversettings.getProperties());

        return (ServerLinks) optional.map((uri) -> {
            return new ServerLinks(List.of(ServerLinks.KnownLinkType.BUG_REPORT.create(uri)));
        }).orElse(ServerLinks.EMPTY);
    }

    private static Optional<URI> parseBugReportLink(DedicatedServerProperties dedicatedserverproperties) {
        String s = dedicatedserverproperties.bugReportLink;

        if (s.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(SystemUtils.parseAndValidateUntrustedUri(s));
            } catch (Exception exception) {
                DedicatedServer.LOGGER.warn("Failed to parse bug link {}", s, exception);
                return Optional.empty();
            }
        }
    }

    // CraftBukkit start
    public boolean isDebugging() {
        return this.getProperties().debug;
    }

    @Override
    public CommandSender getBukkitSender(CommandListenerWrapper wrapper) {
        return console;
    }
    // CraftBukkit end
}
