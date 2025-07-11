package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.EnumChatFormat;
import net.minecraft.FileUtils;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatMessageType;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatMutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.PacketPlayOutAbilities;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEffect;
import net.minecraft.network.protocol.game.PacketPlayOutEntityStatus;
import net.minecraft.network.protocol.game.PacketPlayOutExperience;
import net.minecraft.network.protocol.game.PacketPlayOutGameStateChange;
import net.minecraft.network.protocol.game.PacketPlayOutHeldItemSlot;
import net.minecraft.network.protocol.game.PacketPlayOutLogin;
import net.minecraft.network.protocol.game.PacketPlayOutNamedSoundEffect;
import net.minecraft.network.protocol.game.PacketPlayOutRecipeUpdate;
import net.minecraft.network.protocol.game.PacketPlayOutRespawn;
import net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeam;
import net.minecraft.network.protocol.game.PacketPlayOutServerDifficulty;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnPosition;
import net.minecraft.network.protocol.game.PacketPlayOutUpdateTime;
import net.minecraft.network.protocol.game.PacketPlayOutViewDistance;
import net.minecraft.network.protocol.status.ServerPing;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.AdvancementDataPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ScoreboardServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.ServerStatisticManager;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityEnderPearl;
import net.minecraft.world.item.crafting.CraftingManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.border.IWorldBorderListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.SavedFile;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.WorldNBTStorage;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreboardObjective;
import net.minecraft.world.scores.ScoreboardTeam;
import net.minecraft.world.scores.ScoreboardTeamBase;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.stream.Collectors;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.LoginListener;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
// CraftBukkit end

public abstract class PlayerList {

    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final IChatBaseComponent CHAT_FILTERED_FULL = IChatBaseComponent.translatable("chat.filtered_full");
    public static final IChatBaseComponent DUPLICATE_LOGIN_DISCONNECT_MESSAGE = IChatBaseComponent.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
    private final MinecraftServer server;
    public final List<EntityPlayer> players = new java.util.concurrent.CopyOnWriteArrayList(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
    private final Map<UUID, EntityPlayer> playersByUUID = Maps.newHashMap();
    private final GameProfileBanList bans;
    private final IpBanList ipBans;
    private final OpList ops;
    private final WhiteList whitelist;
    // CraftBukkit start
    // private final Map<UUID, ServerStatisticManager> stats;
    // private final Map<UUID, AdvancementDataPlayer> advancements;
    // CraftBukkit end
    public final WorldNBTStorage playerIo;
    private boolean doWhiteList;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    public int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCommandsForAllPlayers;
    private static final boolean ALLOW_LOGOUTIVATOR = false;
    private int sendAllPlayerInfoIn;

    // CraftBukkit start
    private CraftServer cserver;

    public PlayerList(MinecraftServer minecraftserver, LayeredRegistryAccess<RegistryLayer> layeredregistryaccess, WorldNBTStorage worldnbtstorage, int i) {
        this.cserver = minecraftserver.server = new CraftServer((DedicatedServer) minecraftserver, this);
        minecraftserver.console = org.bukkit.craftbukkit.command.ColouredConsoleSender.getInstance();
        minecraftserver.reader.addCompleter(new org.bukkit.craftbukkit.command.ConsoleCommandCompleter(minecraftserver.server));
        // CraftBukkit end

        this.bans = new GameProfileBanList(PlayerList.USERBANLIST_FILE);
        this.ipBans = new IpBanList(PlayerList.IPBANLIST_FILE);
        this.ops = new OpList(PlayerList.OPLIST_FILE);
        this.whitelist = new WhiteList(PlayerList.WHITELIST_FILE);
        // CraftBukkit start
        // this.stats = Maps.newHashMap();
        // this.advancements = Maps.newHashMap();
        // CraftBukkit end
        this.server = minecraftserver;
        this.registries = layeredregistryaccess;
        this.maxPlayers = i;
        this.playerIo = worldnbtstorage;
    }

    public void placeNewPlayer(NetworkManager networkmanager, EntityPlayer entityplayer, CommonListenerCookie commonlistenercookie) {
        GameProfile gameprofile = entityplayer.getGameProfile();
        UserCache usercache = this.server.getProfileCache();
        String s;

        if (usercache != null) {
            Optional<GameProfile> optional = usercache.get(gameprofile.getId());

            s = (String) optional.map(GameProfile::getName).orElse(gameprofile.getName());
            usercache.add(gameprofile);
        } else {
            s = gameprofile.getName();
        }

        try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(entityplayer.problemPath(), PlayerList.LOGGER)) {
            Optional<ValueInput> optional1 = this.load(entityplayer, problemreporter_j);
            // CraftBukkit start - Better rename detection
            if (optional1.isPresent()) {
                ValueInput valueinput = optional1.get();
                ValueInput bukkit = valueinput.childOrEmpty("bukkit");
                s = bukkit.getStringOr("lastKnownName", s);
            }
            // CraftBukkit end
            ResourceKey<World> resourcekey = (ResourceKey) optional1.flatMap((valueinput) -> {
                return valueinput.read("Dimension", World.RESOURCE_KEY_CODEC);
            }).orElse(entityplayer.level().dimension()); // CraftBukkit - SPIGOT-7507: If no dimension, fall back to existing dimension loaded from "WorldUUID", which in turn defaults to World.OVERWORLD
            WorldServer worldserver = this.server.getLevel(resourcekey);
            WorldServer worldserver1;

            if (worldserver == null) {
                PlayerList.LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourcekey);
                worldserver1 = this.server.overworld();
            } else {
                worldserver1 = worldserver;
            }

            entityplayer.setServerLevel(worldserver1);
            if (optional1.isEmpty()) {
                entityplayer.snapTo(entityplayer.adjustSpawnLocation(worldserver1, worldserver1.getSharedSpawnPos()).getBottomCenter(), worldserver1.getSharedSpawnAngle(), 0.0F);
            }

            worldserver1.waitForChunkAndEntities(entityplayer.chunkPosition(), 1);
            String s1 = networkmanager.getLoggableAddress(this.server.logIPs());

            // CraftBukkit - Moved message to after join
            // PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", new Object[]{entityplayer.getName().getString(), s1, entityplayer.getId(), entityplayer.getX(), entityplayer.getY(), entityplayer.getZ()});
            WorldData worlddata = worldserver1.getLevelData();

            entityplayer.loadGameTypes((ValueInput) optional1.orElse(null)); // CraftBukkit - decompile error
            PlayerConnection playerconnection = new PlayerConnection(this.server, networkmanager, entityplayer, commonlistenercookie);

            networkmanager.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess()), playerconnection), playerconnection);
            GameRules gamerules = worldserver1.getGameRules();
            boolean flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
            boolean flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
            boolean flag2 = gamerules.getBoolean(GameRules.RULE_LIMITED_CRAFTING);

            playerconnection.send(new PacketPlayOutLogin(entityplayer.getId(), worlddata.isHardcore(), this.server.levelKeys(), this.getMaxPlayers(), this.viewDistance, this.simulationDistance, flag1, !flag, flag2, entityplayer.createCommonSpawnInfo(worldserver1), this.server.enforceSecureProfile()));
            entityplayer.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
            playerconnection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
            playerconnection.send(new PacketPlayOutAbilities(entityplayer.getAbilities()));
            playerconnection.send(new PacketPlayOutHeldItemSlot(entityplayer.getInventory().getSelectedSlot()));
            CraftingManager craftingmanager = this.server.getRecipeManager();

            playerconnection.send(new PacketPlayOutRecipeUpdate(craftingmanager.getSynchronizedItemProperties(), craftingmanager.getSynchronizedStonecutterRecipes()));
            this.sendPlayerPermissionLevel(entityplayer);
            entityplayer.getStats().markAllDirty();
            entityplayer.getRecipeBook().sendInitialRecipeBook(entityplayer);
            this.updateEntireScoreboard(worldserver1.getScoreboard(), entityplayer);
            this.server.invalidateStatus();
            IChatMutableComponent ichatmutablecomponent;

            if (entityplayer.getGameProfile().getName().equalsIgnoreCase(s)) {
                ichatmutablecomponent = IChatBaseComponent.translatable("multiplayer.player.joined", entityplayer.getDisplayName());
            } else {
                ichatmutablecomponent = IChatBaseComponent.translatable("multiplayer.player.joined.renamed", entityplayer.getDisplayName(), s);
            }
            // CraftBukkit start
            ichatmutablecomponent.withStyle(EnumChatFormat.YELLOW);
            String joinMessage = CraftChatMessage.fromComponent(ichatmutablecomponent);

            playerconnection.teleport(entityplayer.getX(), entityplayer.getY(), entityplayer.getZ(), entityplayer.getYRot(), entityplayer.getXRot());
            ServerPing serverping = this.server.getStatus();

            if (serverping != null && !commonlistenercookie.transferred()) {
                entityplayer.sendServerStatus(serverping);
            }

            // entityplayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players)); // CraftBukkit - replaced with loop below
            this.players.add(entityplayer);
            this.playersByUUID.put(entityplayer.getUUID(), entityplayer);
            // this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(entityplayer))); // CraftBukkit - replaced with loop below

            // CraftBukkit start
            CraftPlayer bukkitPlayer = entityplayer.getBukkitEntity();

            // Ensure that player inventory is populated with its viewer
            entityplayer.containerMenu.transferTo(entityplayer.containerMenu, bukkitPlayer);

            PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(bukkitPlayer, joinMessage);
            cserver.getPluginManager().callEvent(playerJoinEvent);

            if (!entityplayer.connection.isAcceptingMessages()) {
                return;
            }

            joinMessage = playerJoinEvent.getJoinMessage();

            if (joinMessage != null && joinMessage.length() > 0) {
                for (IChatBaseComponent line : org.bukkit.craftbukkit.util.CraftChatMessage.fromString(joinMessage)) {
                    server.getPlayerList().broadcastSystemMessage(line, false);
                }
            }
            // CraftBukkit end

            // CraftBukkit start - sendAll above replaced with this loop
            ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(entityplayer));

            for (int i = 0; i < this.players.size(); ++i) {
                EntityPlayer entityplayer1 = (EntityPlayer) this.players.get(i);

                if (entityplayer1.getBukkitEntity().canSee(bukkitPlayer)) {
                    entityplayer1.connection.send(packet);
                }

                if (!bukkitPlayer.canSee(entityplayer1.getBukkitEntity())) {
                    continue;
                }

                entityplayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(entityplayer1)));
            }
            entityplayer.sentListPacket = true;
            // CraftBukkit end

            entityplayer.refreshEntityData(entityplayer); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn

            this.sendLevelInfo(entityplayer, worldserver1);

            // CraftBukkit start - Only add if the player wasn't moved in the event
            if (entityplayer.level() == worldserver1 && !worldserver1.players().contains(entityplayer)) {
                worldserver1.addNewPlayer(entityplayer);
                this.server.getCustomBossEvents().onPlayerConnect(entityplayer);
            }

            worldserver1 = entityplayer.level(); // CraftBukkit - Update in case join event changed it
            // CraftBukkit end
            this.sendActivePlayerEffects(entityplayer);
            optional1.ifPresent((valueinput) -> {
                entityplayer.loadAndSpawnEnderPearls(valueinput);
                entityplayer.loadAndSpawnParentVehicle(valueinput);
            });
            entityplayer.initInventoryMenu();
            // CraftBukkit - Moved from above, added world
            PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", entityplayer.getName().getString(), s1, entityplayer.getId(), worldserver1.serverLevelData.getLevelName(), entityplayer.getX(), entityplayer.getY(), entityplayer.getZ());
        }

    }

    public void updateEntireScoreboard(ScoreboardServer scoreboardserver, EntityPlayer entityplayer) {
        Set<ScoreboardObjective> set = Sets.newHashSet();

        for (ScoreboardTeam scoreboardteam : scoreboardserver.getPlayerTeams()) {
            entityplayer.connection.send(PacketPlayOutScoreboardTeam.createAddOrModifyPacket(scoreboardteam, true));
        }

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            ScoreboardObjective scoreboardobjective = scoreboardserver.getDisplayObjective(displayslot);

            if (scoreboardobjective != null && !set.contains(scoreboardobjective)) {
                for (Packet<?> packet : scoreboardserver.getStartTrackingPackets(scoreboardobjective)) {
                    entityplayer.connection.send(packet);
                }

                set.add(scoreboardobjective);
            }
        }

    }

    public void addWorldborderListener(WorldServer worldserver) {
        if (playerIo != null) return; // CraftBukkit
        worldserver.getWorldBorder().addListener(new IWorldBorderListener() {
            @Override
            public void onBorderSizeSet(WorldBorder worldborder, double d0) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(worldborder), worldborder.world); // CraftBukkit
            }

            @Override
            public void onBorderSizeLerping(WorldBorder worldborder, double d0, double d1, long i) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(worldborder), worldborder.world); // CraftBukkit
            }

            @Override
            public void onBorderCenterSet(WorldBorder worldborder, double d0, double d1) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(worldborder), worldborder.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder worldborder, int i) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(worldborder), worldborder.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder worldborder, int i) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(worldborder), worldborder.world); // CraftBukkit
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder worldborder, double d0) {}

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder worldborder, double d0) {}
        });
    }

    public Optional<ValueInput> load(EntityPlayer entityplayer, ProblemReporter problemreporter) {
        NBTTagCompound nbttagcompound = this.server.getWorldData().getLoadedPlayerTag();
        Optional<ValueInput> optional;

        if (this.server.isSingleplayerOwner(entityplayer.getGameProfile()) && nbttagcompound != null) {
            ValueInput valueinput = TagValueInput.create(problemreporter, entityplayer.registryAccess(), nbttagcompound);

            optional = Optional.of(valueinput);
            entityplayer.load(valueinput);
            PlayerList.LOGGER.debug("loading single player");
        } else {
            optional = this.playerIo.load(entityplayer, problemreporter);
        }

        return optional;
    }

    protected void save(EntityPlayer entityplayer) {
        if (!entityplayer.getBukkitEntity().isPersistent()) return; // CraftBukkit
        this.playerIo.save(entityplayer);
        ServerStatisticManager serverstatisticmanager = (ServerStatisticManager) entityplayer.getStats(); // CraftBukkit

        if (serverstatisticmanager != null) {
            serverstatisticmanager.save();
        }

        AdvancementDataPlayer advancementdataplayer = (AdvancementDataPlayer) entityplayer.getAdvancements(); // CraftBukkit

        if (advancementdataplayer != null) {
            advancementdataplayer.save();
        }

    }

    public String remove(EntityPlayer entityplayer) { // CraftBukkit - return string
        WorldServer worldserver = entityplayer.level();

        entityplayer.awardStat(StatisticList.LEAVE_GAME);

        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        // See SPIGOT-5799, SPIGOT-6145
        if (entityplayer.containerMenu != entityplayer.inventoryMenu) {
            entityplayer.closeContainer();
        }

        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(entityplayer.getBukkitEntity(), entityplayer.kickLeaveMessage != null ? entityplayer.kickLeaveMessage : "\u00A7e" + entityplayer.getScoreboardName() + " left the game");
        cserver.getPluginManager().callEvent(playerQuitEvent);
        entityplayer.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

        entityplayer.doTick(); // SPIGOT-924
        // CraftBukkit end

        this.save(entityplayer);
        if (entityplayer.isPassenger()) {
            Entity entity = entityplayer.getRootVehicle();

            if (entity.hasExactlyOnePlayerPassenger()) {
                PlayerList.LOGGER.debug("Removing player mount");
                entityplayer.stopRiding();
                entity.getPassengersAndSelf().forEach((entity1) -> {
                    entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT); // CraftBukkit - add Bukkit remove cause
                });
            }
        }

        entityplayer.unRide();

        for (EntityEnderPearl entityenderpearl : entityplayer.getEnderPearls()) {
            entityenderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT); // CraftBukkit - add Bukkit remove cause
        }

        worldserver.removePlayerImmediately(entityplayer, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        entityplayer.getAdvancements().stopListening();
        this.players.remove(entityplayer);
        this.server.getCustomBossEvents().onPlayerDisconnect(entityplayer);
        UUID uuid = entityplayer.getUUID();
        EntityPlayer entityplayer1 = (EntityPlayer) this.playersByUUID.get(uuid);

        if (entityplayer1 == entityplayer) {
            this.playersByUUID.remove(uuid);
            // CraftBukkit start
            // this.stats.remove(uuid);
            // this.advancements.remove(uuid);
            // CraftBukkit end
        }

        // CraftBukkit start
        // this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(entityplayer.getUUID())));
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(entityplayer.getUUID()));
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer entityplayer2 = (EntityPlayer) this.players.get(i);

            if (entityplayer2.getBukkitEntity().canSee(entityplayer.getBukkitEntity())) {
                entityplayer2.connection.send(packet);
            } else {
                entityplayer2.getBukkitEntity().onEntityRemove(entityplayer);
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        cserver.getScoreboardManager().removePlayer(entityplayer.getBukkitEntity());
        // CraftBukkit end

        return playerQuitEvent.getQuitMessage(); // CraftBukkit
    }

    // CraftBukkit start - Whole method, SocketAddress to LoginListener, added hostname to signature, return EntityPlayer
    public EntityPlayer canPlayerLogin(LoginListener loginlistener, GameProfile gameprofile) {
        // Moved from processLogin
        UUID uuid = gameprofile.getId();
        Set<EntityPlayer> set = Sets.newIdentityHashSet();

        for (EntityPlayer entityplayer : this.players) {
            if (entityplayer.getUUID().equals(uuid)) {
                set.add(entityplayer);
            }
        }

        for (EntityPlayer entityplayer2 : set) {
            save(entityplayer2); // CraftBukkit - Force the player's inventory to be saved
            entityplayer2.connection.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        }

        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome.
        SocketAddress socketaddress = loginlistener.connection.getRemoteAddress();

        EntityPlayer entity = new EntityPlayer(this.server, this.server.getLevel(World.OVERWORLD), gameprofile, ClientInformation.createDefault());
        entity.transferCookieConnection = loginlistener;
        Player player = entity.getBukkitEntity();
        PlayerLoginEvent event = new PlayerLoginEvent(player, loginlistener.connection.hostname, ((java.net.InetSocketAddress) socketaddress).getAddress());

        if (this.bans.isBanned(gameprofile)) {
            GameProfileBanEntry gameprofilebanentry = (GameProfileBanEntry) this.bans.get(gameprofile);
            IChatMutableComponent ichatmutablecomponent = IChatBaseComponent.translatable("multiplayer.disconnect.banned.reason", gameprofilebanentry.getReason());

            if (gameprofilebanentry.getExpires() != null) {
                ichatmutablecomponent.append((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.banned.expiration", PlayerList.BAN_DATE_FORMAT.format(gameprofilebanentry.getExpires())));
            }

            // return ichatmutablecomponent;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, CraftChatMessage.fromComponent(ichatmutablecomponent));
        } else if (!this.isWhiteListed(gameprofile)) {
            IChatMutableComponent ichatmutablecomponent = IChatBaseComponent.translatable("multiplayer.disconnect.not_whitelisted");
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, CraftChatMessage.fromComponent(ichatmutablecomponent));
        } else if (this.ipBans.isBanned(socketaddress)) {
            IpBanEntry ipbanentry = this.ipBans.get(socketaddress);
            IChatMutableComponent ichatmutablecomponent1 = IChatBaseComponent.translatable("multiplayer.disconnect.banned_ip.reason", ipbanentry.getReason());

            if (ipbanentry.getExpires() != null) {
                ichatmutablecomponent1.append((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.banned_ip.expiration", PlayerList.BAN_DATE_FORMAT.format(ipbanentry.getExpires())));
            }

            // return ichatmutablecomponent1;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, CraftChatMessage.fromComponent(ichatmutablecomponent1));
        } else {
            // return this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameprofile) ? IChatBaseComponent.translatable("multiplayer.disconnect.server_full") : null;
            if (this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameprofile)) {
                event.disallow(PlayerLoginEvent.Result.KICK_FULL, "The server is full");
            }
        }

        cserver.getPluginManager().callEvent(event);
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            loginlistener.disconnect(event.getKickMessage());
            return null;
        }
        return entity;
    }

    public boolean disconnectAllPlayersWithProfile(GameProfile gameprofile, EntityPlayer player) { // CraftBukkit - added EntityPlayer
        /* CraftBukkit startMoved up
        UUID uuid = gameprofile.getId();
        Set<EntityPlayer> set = Sets.newIdentityHashSet();

        for (EntityPlayer entityplayer : this.players) {
            if (entityplayer.getUUID().equals(uuid)) {
                set.add(entityplayer);
            }
        }

        EntityPlayer entityplayer1 = (EntityPlayer) this.playersByUUID.get(gameprofile.getId());

        if (entityplayer1 != null) {
            set.add(entityplayer1);
        }

        for (EntityPlayer entityplayer2 : set) {
            entityplayer2.connection.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        }

        return !set.isEmpty();
        */
        return player == null;
        // CraftBukkit end
    }

    // CraftBukkit start
    public EntityPlayer respawn(EntityPlayer entityplayer, boolean flag, Entity.RemovalReason entity_removalreason, RespawnReason reason) {
        return this.respawn(entityplayer, flag, entity_removalreason, reason, null);
    }

    public EntityPlayer respawn(EntityPlayer entityplayer, boolean flag, Entity.RemovalReason entity_removalreason, RespawnReason reason, Location location) {
        entityplayer.stopRiding(); // CraftBukkit
        this.players.remove(entityplayer);
        entityplayer.level().removePlayerImmediately(entityplayer, entity_removalreason);
        /* CraftBukkit start
        TeleportTransition teleporttransition = entityplayer.findRespawnPositionAndUseSpawnBlock(!flag, TeleportTransition.DO_NOTHING);
        WorldServer worldserver = teleporttransition.newLevel();
        EntityPlayer entityplayer1 = new EntityPlayer(this.server, worldserver, entityplayer.getGameProfile(), entityplayer.clientInformation());
        // */
        EntityPlayer entityplayer1 = entityplayer;
        World fromWorld = entityplayer.level();
        entityplayer.wonGame = false;
        // CraftBukkit end

        entityplayer1.connection = entityplayer.connection;
        entityplayer1.restoreFrom(entityplayer, flag);
        entityplayer1.setId(entityplayer.getId());
        entityplayer1.setMainArm(entityplayer.getMainArm());
        // CraftBukkit - not required, just copies old location into reused entity
        /*
        if (!teleporttransition.missingRespawnBlock()) {
            entityplayer1.copyRespawnPosition(entityplayer);
        }
         */
        // CraftBukkit end

        for (String s : entityplayer.getTags()) {
            entityplayer1.addTag(s);
        }

        // CraftBukkit start - fire PlayerRespawnEvent
        TeleportTransition teleporttransition;
        if (location == null) {
            teleporttransition = entityplayer.findRespawnPositionAndUseSpawnBlock(!flag, TeleportTransition.DO_NOTHING, reason);

            if (!flag) entityplayer.reset(); // SPIGOT-4785
        } else {
            teleporttransition = new TeleportTransition(((CraftWorld) location.getWorld()).getHandle(), CraftLocation.toVec3D(location), Vec3D.ZERO, location.getYaw(), location.getPitch(), TeleportTransition.DO_NOTHING);
        }
        WorldServer worldserver = teleporttransition.newLevel();
        entityplayer1.spawnIn(worldserver, flag);
        entityplayer1.unsetRemoved();
        entityplayer1.setShiftKeyDown(false);
        Vec3D vec3d = teleporttransition.position();

        entityplayer1.forceSetPositionRotation(vec3d.x, vec3d.y, vec3d.z, teleporttransition.yRot(), teleporttransition.xRot());
        // CraftBukkit end
        if (teleporttransition.missingRespawnBlock()) {
            entityplayer1.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            entityplayer1.setRespawnPosition(null, false, PlayerSpawnChangeEvent.Cause.RESET); // CraftBukkit - SPIGOT-5988: Clear respawn location when obstructed
        }

        byte b0 = (byte) (flag ? 1 : 0);
        WorldServer worldserver1 = entityplayer1.level();
        WorldData worlddata = worldserver1.getLevelData();

        entityplayer1.connection.send(new PacketPlayOutRespawn(entityplayer1.createCommonSpawnInfo(worldserver1), b0));
        entityplayer1.connection.teleport(CraftLocation.toBukkit(entityplayer1.position(), worldserver1.getWorld(), entityplayer1.getYRot(), entityplayer1.getXRot())); // CraftBukkit
        entityplayer1.connection.send(new PacketPlayOutSpawnPosition(worldserver.getSharedSpawnPos(), worldserver.getSharedSpawnAngle()));
        entityplayer1.connection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        entityplayer1.connection.send(new PacketPlayOutExperience(entityplayer1.experienceProgress, entityplayer1.totalExperience, entityplayer1.experienceLevel));
        this.sendActivePlayerEffects(entityplayer1);
        this.sendLevelInfo(entityplayer1, worldserver);
        this.sendPlayerPermissionLevel(entityplayer1);
        if (!entityplayer.connection.isDisconnected()) {
            worldserver.addRespawnedPlayer(entityplayer1);
            this.players.add(entityplayer1);
            this.playersByUUID.put(entityplayer1.getUUID(), entityplayer1);
        }
        // entityplayer1.initInventoryMenu();
        entityplayer1.setHealth(entityplayer1.getHealth());
        EntityPlayer.RespawnConfig entityplayer_respawnconfig = entityplayer1.getRespawnConfig();

        if (!flag && entityplayer_respawnconfig != null) {
            WorldServer worldserver2 = this.server.getLevel(entityplayer_respawnconfig.dimension());

            if (worldserver2 != null) {
                BlockPosition blockposition = entityplayer_respawnconfig.pos();
                IBlockData iblockdata = worldserver2.getBlockState(blockposition);

                if (iblockdata.is(Blocks.RESPAWN_ANCHOR)) {
                    entityplayer1.connection.send(new PacketPlayOutNamedSoundEffect(SoundEffects.RESPAWN_ANCHOR_DEPLETE, SoundCategory.BLOCKS, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), 1.0F, 1.0F, worldserver.getRandom().nextLong()));
                }
            }
        }
        // Added from changeDimension
        sendAllPlayerInfo(entityplayer); // Update health, etc...
        entityplayer.onUpdateAbilities();
        for (MobEffect mobEffect : entityplayer.getActiveEffects()) {
            entityplayer.connection.send(new PacketPlayOutEntityEffect(entityplayer.getId(), mobEffect, false)); // blend = false
        }

        // Fire advancement trigger
        entityplayer.triggerDimensionChangeTriggers(worldserver);

        // Don't fire on respawn
        if (fromWorld != worldserver) {
            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(entityplayer.getBukkitEntity(), fromWorld.getWorld());
            server.server.getPluginManager().callEvent(event);
        }

        // Save player file again if they were disconnected
        if (entityplayer.connection.isDisconnected()) {
            this.save(entityplayer);
        }
        // CraftBukkit end

        return entityplayer1;
    }

    public void sendActivePlayerEffects(EntityPlayer entityplayer) {
        this.sendActiveEffects(entityplayer, entityplayer.connection);
    }

    public void sendActiveEffects(EntityLiving entityliving, PlayerConnection playerconnection) {
        for (MobEffect mobeffect : entityliving.getActiveEffects()) {
            playerconnection.send(new PacketPlayOutEntityEffect(entityliving.getId(), mobeffect, false));
        }

    }

    public void sendPlayerPermissionLevel(EntityPlayer entityplayer) {
        GameProfile gameprofile = entityplayer.getGameProfile();
        int i = this.server.getProfilePermissions(gameprofile);

        this.sendPlayerPermissionLevel(entityplayer, i);
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            // CraftBukkit start
            for (int i = 0; i < this.players.size(); ++i) {
                final EntityPlayer target = (EntityPlayer) this.players.get(i);

                target.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.a.UPDATE_LATENCY), this.players.stream().filter(new Predicate<EntityPlayer>() {
                    @Override
                    public boolean test(EntityPlayer input) {
                        return target.getBukkitEntity().canSee(input.getBukkitEntity());
                    }
                }).collect(Collectors.toList())));
            }
            // CraftBukkit end
            this.sendAllPlayerInfoIn = 0;
        }

    }

    public void broadcastAll(Packet<?> packet) {
        for (EntityPlayer entityplayer : this.players) {
            entityplayer.connection.send(packet);
        }

    }

    // CraftBukkit start - add a world/entity limited version
    public void broadcastAll(Packet packet, EntityHuman entityhuman) {
        for (int i = 0; i < this.players.size(); ++i) {
            EntityPlayer entityplayer =  this.players.get(i);
            if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                continue;
            }
            ((EntityPlayer) this.players.get(i)).connection.send(packet);
        }
    }

    public void broadcastAll(Packet packet, World world) {
        for (int i = 0; i < world.players().size(); ++i) {
            ((EntityPlayer) world.players().get(i)).connection.send(packet);
        }

    }
    // CraftBukkit end

    public void broadcastAll(Packet<?> packet, ResourceKey<World> resourcekey) {
        for (EntityPlayer entityplayer : this.players) {
            if (entityplayer.level().dimension() == resourcekey) {
                entityplayer.connection.send(packet);
            }
        }

    }

    public void broadcastSystemToTeam(EntityHuman entityhuman, IChatBaseComponent ichatbasecomponent) {
        ScoreboardTeamBase scoreboardteambase = entityhuman.getTeam();

        if (scoreboardteambase != null) {
            for (String s : scoreboardteambase.getPlayers()) {
                EntityPlayer entityplayer = this.getPlayerByName(s);

                if (entityplayer != null && entityplayer != entityhuman) {
                    entityplayer.sendSystemMessage(ichatbasecomponent);
                }
            }

        }
    }

    public void broadcastSystemToAllExceptTeam(EntityHuman entityhuman, IChatBaseComponent ichatbasecomponent) {
        ScoreboardTeamBase scoreboardteambase = entityhuman.getTeam();

        if (scoreboardteambase == null) {
            this.broadcastSystemMessage(ichatbasecomponent, false);
        } else {
            for (int i = 0; i < this.players.size(); ++i) {
                EntityPlayer entityplayer = (EntityPlayer) this.players.get(i);

                if (entityplayer.getTeam() != scoreboardteambase) {
                    entityplayer.sendSystemMessage(ichatbasecomponent);
                }
            }

        }
    }

    public String[] getPlayerNamesArray() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            astring[i] = ((EntityPlayer) this.players.get(i)).getGameProfile().getName();
        }

        return astring;
    }

    public GameProfileBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(GameProfile gameprofile) {
        this.ops.add(new OpListEntry(gameprofile, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(gameprofile)));
        EntityPlayer entityplayer = this.getPlayer(gameprofile.getId());

        if (entityplayer != null) {
            this.sendPlayerPermissionLevel(entityplayer);
        }

    }

    public void deop(GameProfile gameprofile) {
        this.ops.remove(gameprofile);
        EntityPlayer entityplayer = this.getPlayer(gameprofile.getId());

        if (entityplayer != null) {
            this.sendPlayerPermissionLevel(entityplayer);
        }

    }

    private void sendPlayerPermissionLevel(EntityPlayer entityplayer, int i) {
        if (entityplayer.connection != null) {
            byte b0;

            if (i <= 0) {
                b0 = 24;
            } else if (i >= 4) {
                b0 = 28;
            } else {
                b0 = (byte) (24 + i);
            }

            entityplayer.connection.send(new PacketPlayOutEntityStatus(entityplayer, b0));
        }

        entityplayer.getBukkitEntity().recalculatePermissions(); // CraftBukkit
        this.server.getCommands().sendCommands(entityplayer);
    }

    public boolean isWhiteListed(GameProfile gameprofile) {
        return !this.doWhiteList || this.ops.contains(gameprofile) || this.whitelist.contains(gameprofile);
    }

    public boolean isOp(GameProfile gameprofile) {
        return this.ops.contains(gameprofile) || this.server.isSingleplayerOwner(gameprofile) && this.server.getWorldData().isAllowCommands() || this.allowCommandsForAllPlayers;
    }

    @Nullable
    public EntityPlayer getPlayerByName(String s) {
        int i = this.players.size();

        for (int j = 0; j < i; ++j) {
            EntityPlayer entityplayer = (EntityPlayer) this.players.get(j);

            if (entityplayer.getGameProfile().getName().equalsIgnoreCase(s)) {
                return entityplayer;
            }
        }

        return null;
    }

    public void broadcast(@Nullable EntityHuman entityhuman, double d0, double d1, double d2, double d3, ResourceKey<World> resourcekey, Packet<?> packet) {
        for (int i = 0; i < this.players.size(); ++i) {
            EntityPlayer entityplayer = (EntityPlayer) this.players.get(i);

            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
               continue;
            }
            // CraftBukkit end

            if (entityplayer != entityhuman && entityplayer.level().dimension() == resourcekey) {
                double d4 = d0 - entityplayer.getX();
                double d5 = d1 - entityplayer.getY();
                double d6 = d2 - entityplayer.getZ();

                if (d4 * d4 + d5 * d5 + d6 * d6 < d3 * d3) {
                    entityplayer.connection.send(packet);
                }
            }
        }

    }

    public void saveAll() {
        for (int i = 0; i < this.players.size(); ++i) {
            this.save((EntityPlayer) this.players.get(i));
        }

    }

    public WhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public OpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {}

    public void sendLevelInfo(EntityPlayer entityplayer, WorldServer worldserver) {
        WorldBorder worldborder = entityplayer.level().getWorldBorder(); // CraftBukkit

        entityplayer.connection.send(new ClientboundInitializeBorderPacket(worldborder));
        entityplayer.connection.send(new PacketPlayOutUpdateTime(worldserver.getGameTime(), worldserver.getDayTime(), worldserver.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        entityplayer.connection.send(new PacketPlayOutSpawnPosition(worldserver.getSharedSpawnPos(), worldserver.getSharedSpawnAngle()));
        if (worldserver.isRaining()) {
            // CraftBukkit start - handle player weather
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.START_RAINING, 0.0F));
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, worldserver.getRainLevel(1.0F)));
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, worldserver.getThunderLevel(1.0F)));
            entityplayer.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            entityplayer.updateWeather(-worldserver.rainLevel, worldserver.rainLevel, -worldserver.thunderLevel, worldserver.thunderLevel);
            // CraftBukkit end
        }

        entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.LEVEL_CHUNKS_LOAD_START, 0.0F));
        this.server.tickRateManager().updateJoiningPlayer(entityplayer);
    }

    public void sendAllPlayerInfo(EntityPlayer entityplayer) {
        entityplayer.inventoryMenu.sendAllDataToRemote();
        // entityplayer.resetSentInfo();
        entityplayer.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
        entityplayer.refreshEntityData(entityplayer); // CraftBukkkit - SPIGOT-7218: sync metadata
        entityplayer.connection.send(new PacketPlayOutHeldItemSlot(entityplayer.getInventory().getSelectedSlot()));
        // CraftBukkit start - from GameRules
        int i = entityplayer.level().getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23;
        entityplayer.connection.send(new PacketPlayOutEntityStatus(entityplayer, (byte) i));
        float immediateRespawn = entityplayer.level().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN) ? 1.0F: 0.0F;
        entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.IMMEDIATE_RESPAWN, immediateRespawn));
        // CraftBukkit end
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public boolean isUsingWhitelist() {
        return this.doWhiteList;
    }

    public void setUsingWhiteList(boolean flag) {
        this.doWhiteList = flag;
    }

    public List<EntityPlayer> getPlayersWithAddress(String s) {
        List<EntityPlayer> list = Lists.newArrayList();

        for (EntityPlayer entityplayer : this.players) {
            if (entityplayer.getIpAddress().equals(s)) {
                list.add(entityplayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    @Nullable
    public NBTTagCompound getSingleplayerData() {
        return null;
    }

    public void setAllowCommandsForAllPlayers(boolean flag) {
        this.allowCommandsForAllPlayers = flag;
    }

    public void removeAll() {
        // CraftBukkit start - disconnect safely
        for (EntityPlayer player : this.players) {
            player.connection.disconnect(CraftChatMessage.fromStringOrEmpty(this.server.server.getShutdownMessage())); // CraftBukkit - add custom shutdown message
        }
        // CraftBukkit end

    }

    // CraftBukkit start
    public void broadcastMessage(IChatBaseComponent[] iChatBaseComponents) {
        for (IChatBaseComponent component : iChatBaseComponents) {
            broadcastSystemMessage(component, false);
        }
    }
    // CraftBukkit end

    public void broadcastSystemMessage(IChatBaseComponent ichatbasecomponent, boolean flag) {
        this.broadcastSystemMessage(ichatbasecomponent, (entityplayer) -> {
            return ichatbasecomponent;
        }, flag);
    }

    public void broadcastSystemMessage(IChatBaseComponent ichatbasecomponent, Function<EntityPlayer, IChatBaseComponent> function, boolean flag) {
        this.server.sendSystemMessage(ichatbasecomponent);

        for (EntityPlayer entityplayer : this.players) {
            IChatBaseComponent ichatbasecomponent1 = (IChatBaseComponent) function.apply(entityplayer);

            if (ichatbasecomponent1 != null) {
                entityplayer.sendSystemMessage(ichatbasecomponent1, flag);
            }
        }

    }

    public void broadcastChatMessage(PlayerChatMessage playerchatmessage, CommandListenerWrapper commandlistenerwrapper, ChatMessageType.a chatmessagetype_a) {
        Objects.requireNonNull(commandlistenerwrapper);
        this.broadcastChatMessage(playerchatmessage, commandlistenerwrapper::shouldFilterMessageTo, commandlistenerwrapper.getPlayer(), chatmessagetype_a);
    }

    public void broadcastChatMessage(PlayerChatMessage playerchatmessage, EntityPlayer entityplayer, ChatMessageType.a chatmessagetype_a) {
        Objects.requireNonNull(entityplayer);
        this.broadcastChatMessage(playerchatmessage, entityplayer::shouldFilterMessageTo, entityplayer, chatmessagetype_a);
    }

    private void broadcastChatMessage(PlayerChatMessage playerchatmessage, Predicate<EntityPlayer> predicate, @Nullable EntityPlayer entityplayer, ChatMessageType.a chatmessagetype_a) {
        boolean flag = this.verifyChatTrusted(playerchatmessage);

        this.server.logChatMessage(playerchatmessage.decoratedContent(), chatmessagetype_a, flag ? null : "Not Secure");
        OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(playerchatmessage);
        boolean flag1 = false;

        for (EntityPlayer entityplayer1 : this.players) {
            boolean flag2 = predicate.test(entityplayer1);

            entityplayer1.sendChatMessage(outgoingchatmessage, flag2, chatmessagetype_a);
            flag1 |= flag2 && playerchatmessage.isFullyFiltered();
        }

        if (flag1 && entityplayer != null) {
            entityplayer.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }

    }

    private boolean verifyChatTrusted(PlayerChatMessage playerchatmessage) {
        return playerchatmessage.hasSignature() && !playerchatmessage.hasExpiredServer(Instant.now());
    }

    // CraftBukkit start
    public ServerStatisticManager getPlayerStats(EntityPlayer entityhuman) {
        ServerStatisticManager serverstatisticmanager = entityhuman.getStats();
        return serverstatisticmanager == null ? getPlayerStats(entityhuman.getUUID(), entityhuman.getDisplayName().getString()) : serverstatisticmanager;
    }

    public ServerStatisticManager getPlayerStats(UUID uuid, String displayName) {
        EntityPlayer entityhuman = this.getPlayer(uuid);
        ServerStatisticManager serverstatisticmanager = entityhuman == null ? null : (ServerStatisticManager) entityhuman.getStats();
        // CraftBukkit end

        if (serverstatisticmanager == null) {
            File file = this.server.getWorldPath(SavedFile.PLAYER_STATS_DIR).toFile();
            File file1 = new File(file, String.valueOf(uuid) + ".json");

            if (!file1.exists()) {
                File file2 = new File(file, displayName + ".json"); // CraftBukkit
                Path path = file2.toPath();

                if (FileUtils.isPathNormalized(path) && FileUtils.isPathPortable(path) && path.startsWith(file.getPath()) && file2.isFile()) {
                    file2.renameTo(file1);
                }
            }

            serverstatisticmanager = new ServerStatisticManager(this.server, file1);
            // this.stats.put(uuid, serverstatisticmanager); // CraftBukkit
        }

        return serverstatisticmanager;
    }

    public AdvancementDataPlayer getPlayerAdvancements(EntityPlayer entityplayer) {
        UUID uuid = entityplayer.getUUID();
        AdvancementDataPlayer advancementdataplayer = (AdvancementDataPlayer) entityplayer.getAdvancements(); // CraftBukkit

        if (advancementdataplayer == null) {
            Path path = this.server.getWorldPath(SavedFile.PLAYER_ADVANCEMENTS_DIR).resolve(String.valueOf(uuid) + ".json");

            advancementdataplayer = new AdvancementDataPlayer(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, entityplayer);
            // this.advancements.put(uuid, advancementdataplayer); // CraftBukkit
        }

        advancementdataplayer.setPlayer(entityplayer);
        return advancementdataplayer;
    }

    public void setViewDistance(int i) {
        this.viewDistance = i;
        this.broadcastAll(new PacketPlayOutViewDistance(i));

        for (WorldServer worldserver : this.server.getAllLevels()) {
            if (worldserver != null) {
                worldserver.getChunkSource().setViewDistance(i);
            }
        }

    }

    public void setSimulationDistance(int i) {
        this.simulationDistance = i;
        this.broadcastAll(new ClientboundSetSimulationDistancePacket(i));

        for (WorldServer worldserver : this.server.getAllLevels()) {
            if (worldserver != null) {
                worldserver.getChunkSource().setSimulationDistance(i);
            }
        }

    }

    public List<EntityPlayer> getPlayers() {
        return this.players;
    }

    @Nullable
    public EntityPlayer getPlayer(UUID uuid) {
        return (EntityPlayer) this.playersByUUID.get(uuid);
    }

    public boolean canBypassPlayerLimit(GameProfile gameprofile) {
        return false;
    }

    public void reloadResources() {
        // CraftBukkit start
        /*for (AdvancementDataPlayer advancementdataplayer : this.advancements.values()) {
            advancementdataplayer.reload(this.server.getAdvancements());
        }*/

        for (EntityPlayer player : players) {
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player, false); // CraftBukkit - trigger immediate flush of advancements
        }
        // CraftBukkit end

        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        // CraftBukkit start
        reloadRecipes();
    }

    public void reloadRecipes() {
        // CraftBukkit end
        CraftingManager craftingmanager = this.server.getRecipeManager();
        PacketPlayOutRecipeUpdate packetplayoutrecipeupdate = new PacketPlayOutRecipeUpdate(craftingmanager.getSynchronizedItemProperties(), craftingmanager.getSynchronizedStonecutterRecipes());

        for (EntityPlayer entityplayer : this.players) {
            entityplayer.connection.send(packetplayoutrecipeupdate);
            entityplayer.getRecipeBook().sendInitialRecipeBook(entityplayer);
        }

    }

    public boolean isAllowCommandsForAllPlayers() {
        return this.allowCommandsForAllPlayers;
    }
}
