package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelFutureListener;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportedException;
import net.minecraft.SystemUtils;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PlayerConnectionUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.thread.IAsyncTaskHandler;
import org.slf4j.Logger;

// CraftBukkit start
import io.netty.buffer.ByteBuf;
import java.util.concurrent.ExecutionException;
import net.minecraft.EnumChatFormat;
import net.minecraft.network.EnumProtocol;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnPosition;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.EntityPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener, CraftPlayer.TransferCookieConnection {

    @Override
    public boolean isTransferred() {
        return this.transferred;
    }

    @Override
    public EnumProtocol getProtocol() {
        return protocol();
    }

    @Override
    public void sendPacket(Packet<?> packet) {
        send(packet);
    }

    @Override
    public void kickPlayer(IChatBaseComponent reason) {
        disconnect(reason);
    }
    // CraftBukkit end
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final int CLOSED_LISTENER_TIMEOUT = 15000;
    private static final IChatBaseComponent TIMEOUT_DISCONNECTION_MESSAGE = IChatBaseComponent.translatable("disconnect.timeout");
    static final IChatBaseComponent DISCONNECT_UNEXPECTED_QUERY = IChatBaseComponent.translatable("multiplayer.disconnect.unexpected_query_response");
    protected final MinecraftServer server;
    protected final NetworkManager connection;
    private final boolean transferred;
    private long keepAliveTime;
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private long closedListenerTime;
    private boolean closed = false;
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;

    public ServerCommonPacketListenerImpl(MinecraftServer minecraftserver, NetworkManager networkmanager, CommonListenerCookie commonlistenercookie, EntityPlayer player) { // CraftBukkit
        this.server = minecraftserver;
        this.connection = networkmanager;
        this.keepAliveTime = SystemUtils.getMillis();
        this.latency = commonlistenercookie.latency();
        this.transferred = commonlistenercookie.transferred();
        // CraftBukkit start - add fields and methods
        this.player = player;
        this.player.transferCookieConnection = this;
        this.cserver = minecraftserver.server;
    }
    protected final EntityPlayer player;
    protected final org.bukkit.craftbukkit.CraftServer cserver;
    public boolean processedDisconnect;

    public CraftPlayer getCraftPlayer() {
        return (this.player == null) ? null : (CraftPlayer) this.player.getBukkitEntity();
        // CraftBukkit end
    }

    private void close() {
        if (!this.closed) {
            this.closedListenerTime = SystemUtils.getMillis();
            this.closed = true;
        }

    }

    @Override
    public void onDisconnect(DisconnectionDetails disconnectiondetails) {
        if (this.isSingleplayerOwner()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }

    }

    @Override
    public void onPacketError(Packet packet, Exception exception) throws ReportedException {
        ServerCommonPacketListener.super.onPacketError(packet, exception);
        this.server.reportPacketHandlingException(exception, packet.type());
    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket serverboundkeepalivepacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundkeepalivepacket, this, this.player.level()); // CraftBukkit
        if (this.keepAlivePending && serverboundkeepalivepacket.getId() == this.keepAliveChallenge) {
            int i = (int) (SystemUtils.getMillis() - this.keepAliveTime);

            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
        }

    }

    @Override
    public void handlePong(ServerboundPongPacket serverboundpongpacket) {}

    // CraftBukkit start
    private static final MinecraftKey CUSTOM_REGISTER = MinecraftKey.withDefaultNamespace("register");
    private static final MinecraftKey CUSTOM_UNREGISTER = MinecraftKey.withDefaultNamespace("unregister");

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket serverboundcustompayloadpacket) {
        if (!(serverboundcustompayloadpacket.payload() instanceof DiscardedPayload)) {
            return;
        }
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundcustompayloadpacket, this, this.player.level());
        MinecraftKey identifier = serverboundcustompayloadpacket.payload().type().id();
        ByteBuf payload = ((DiscardedPayload)serverboundcustompayloadpacket.payload()).data();

        if (identifier.equals(CUSTOM_REGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    getCraftPlayer().addChannel(channel);
                }
            } catch (Exception ex) {
                PlayerConnection.LOGGER.error("Couldn\'t register custom payload", ex);
                this.disconnect(IChatBaseComponent.literal("Invalid payload REGISTER!"));
            }
        } else if (identifier.equals(CUSTOM_UNREGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    getCraftPlayer().removeChannel(channel);
                }
            } catch (Exception ex) {
                PlayerConnection.LOGGER.error("Couldn\'t unregister custom payload", ex);
                this.disconnect(IChatBaseComponent.literal("Invalid payload UNREGISTER!"));
            }
        } else {
            try {
                byte[] data = new byte[payload.readableBytes()];
                payload.readBytes(data);
                cserver.getMessenger().dispatchIncomingMessage(player.getBukkitEntity(), identifier.toString(), data);
            } catch (Exception ex) {
                PlayerConnection.LOGGER.error("Couldn\'t dispatch custom payload", ex);
                this.disconnect(IChatBaseComponent.literal("Invalid custom payload!"));
            }
        }

    }

    public final boolean isDisconnected() {
        return !this.player.joining && !this.connection.isConnected();
    }
    // CraftBukkit end

    @Override
    public void handleCustomClickAction(ServerboundCustomClickActionPacket serverboundcustomclickactionpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundcustomclickactionpacket, this, (IAsyncTaskHandler) this.server);
        this.server.handleCustomClickAction(serverboundcustomclickactionpacket.id(), serverboundcustomclickactionpacket.payload(), this.player); // CraftBukkit - add player
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket serverboundresourcepackpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundresourcepackpacket, this, (IAsyncTaskHandler) this.server);
        if (serverboundresourcepackpacket.action() == ServerboundResourcePackPacket.a.DECLINED && this.server.isResourcePackRequired()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().getName(), serverboundresourcepackpacket.id());
            this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.requiredTexturePrompt.disconnect"));
        }
        this.cserver.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(getCraftPlayer(), serverboundresourcepackpacket.id(), PlayerResourcePackStatusEvent.Status.values()[serverboundresourcepackpacket.action().ordinal()])); // CraftBukkit

    }

    @Override
    public void handleCookieResponse(ServerboundCookieResponsePacket serverboundcookieresponsepacket) {
        // CraftBukkit start
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundcookieresponsepacket, this, (IAsyncTaskHandler) this.server);
        if (this.player.getBukkitEntity().handleCookieResponse(serverboundcookieresponsepacket)) {
            return;
        }
        // CraftBukkit end
        this.disconnect(ServerCommonPacketListenerImpl.DISCONNECT_UNEXPECTED_QUERY);
    }

    protected void keepConnectionAlive() {
        Profiler.get().push("keepAlive");
        long i = SystemUtils.getMillis();

        if (!this.isSingleplayerOwner() && i - this.keepAliveTime >= 25000L) { // CraftBukkit
            if (this.keepAlivePending) {
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
            } else if (this.checkIfClosed(i)) {
                this.keepAlivePending = true;
                this.keepAliveTime = i;
                this.keepAliveChallenge = i;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }

        Profiler.get().pop();
    }

    private boolean checkIfClosed(long i) {
        if (this.closed) {
            if (i - this.closedListenerTime >= 15000L) {
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE);
            }

            return false;
        } else {
            return true;
        }
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> packet) {
        this.send(packet, (ChannelFutureListener) null);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelfuturelistener) {
        // CraftBukkit start
        if (packet == null) {
            return;
        } else if (packet instanceof PacketPlayOutSpawnPosition) {
            PacketPlayOutSpawnPosition packet6 = (PacketPlayOutSpawnPosition) packet;
            this.player.compassTarget = CraftLocation.toBukkit(packet6.pos, this.getCraftPlayer().getWorld());
        }
        // CraftBukkit end
        if (packet.isTerminal()) {
            this.close();
        }

        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(packet, channelfuturelistener, flag);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Packet being sent");

            crashreportsystemdetails.setDetail("Packet class", () -> {
                return packet.getClass().getCanonicalName();
            });
            throw new ReportedException(crashreport);
        }
    }

    public void disconnect(IChatBaseComponent ichatbasecomponent) {
        this.disconnect(new DisconnectionDetails(ichatbasecomponent));
    }

    public void disconnect(DisconnectionDetails disconnectiondetails) {
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        if (!this.cserver.isPrimaryThread()) {
            Waitable waitable = new Waitable() {
                @Override
                protected Object evaluate() {
                    ServerCommonPacketListenerImpl.this.disconnect(disconnectiondetails);
                    return null;
                }
            };

            this.server.processQueue.add(waitable);

            try {
                waitable.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        String leaveMessage = EnumChatFormat.YELLOW + this.player.getScoreboardName() + " left the game.";

        PlayerKickEvent event = new PlayerKickEvent(this.player.getBukkitEntity(), CraftChatMessage.fromComponent(disconnectiondetails.reason()), leaveMessage);

        if (this.cserver.getServer().isRunning()) {
            this.cserver.getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        this.player.kickLeaveMessage = event.getLeaveMessage(); // CraftBukkit - SPIGOT-3034: Forward leave message to PlayerQuitEvent
        // Send the possibly modified leave message
        disconnect0(new DisconnectionDetails(CraftChatMessage.fromString(event.getReason(), true)[0], disconnectiondetails.report(), disconnectiondetails.bugReportLink()));
    }

    private void disconnect0(DisconnectionDetails disconnectiondetails) {
        // CraftBukkit end
        this.connection.send(new ClientboundDisconnectPacket(disconnectiondetails.reason()), PacketSendListener.thenRun(() -> {
            this.connection.disconnect(disconnectiondetails);
        }));
        this.onDisconnect(disconnectiondetails); // CraftBukkit - fire quit instantly
        this.connection.setReadOnly();
        MinecraftServer minecraftserver = this.server;
        NetworkManager networkmanager = this.connection;

        Objects.requireNonNull(this.connection);
        // CraftBukkit - Don't wait
        minecraftserver.wrapRunnable(networkmanager::handleDisconnection);
    }

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.playerProfile());
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    protected CommonListenerCookie createCookie(ClientInformation clientinformation) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, clientinformation, this.transferred);
    }
}
