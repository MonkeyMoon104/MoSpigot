package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.EnumChatFormat;
import net.minecraft.SystemUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.HashedStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatMessageType;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PlayerConnectionUtils;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatus;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.PacketListenerPlayIn;
import net.minecraft.network.protocol.game.PacketPlayInAbilities;
import net.minecraft.network.protocol.game.PacketPlayInAdvancements;
import net.minecraft.network.protocol.game.PacketPlayInArmAnimation;
import net.minecraft.network.protocol.game.PacketPlayInAutoRecipe;
import net.minecraft.network.protocol.game.PacketPlayInBEdit;
import net.minecraft.network.protocol.game.PacketPlayInBeacon;
import net.minecraft.network.protocol.game.PacketPlayInBlockDig;
import net.minecraft.network.protocol.game.PacketPlayInBlockPlace;
import net.minecraft.network.protocol.game.PacketPlayInBoatMove;
import net.minecraft.network.protocol.game.PacketPlayInChat;
import net.minecraft.network.protocol.game.PacketPlayInClientCommand;
import net.minecraft.network.protocol.game.PacketPlayInCloseWindow;
import net.minecraft.network.protocol.game.PacketPlayInDifficultyChange;
import net.minecraft.network.protocol.game.PacketPlayInDifficultyLock;
import net.minecraft.network.protocol.game.PacketPlayInEnchantItem;
import net.minecraft.network.protocol.game.PacketPlayInEntityAction;
import net.minecraft.network.protocol.game.PacketPlayInEntityNBTQuery;
import net.minecraft.network.protocol.game.PacketPlayInFlying;
import net.minecraft.network.protocol.game.PacketPlayInHeldItemSlot;
import net.minecraft.network.protocol.game.PacketPlayInItemName;
import net.minecraft.network.protocol.game.PacketPlayInJigsawGenerate;
import net.minecraft.network.protocol.game.PacketPlayInRecipeDisplayed;
import net.minecraft.network.protocol.game.PacketPlayInRecipeSettings;
import net.minecraft.network.protocol.game.PacketPlayInSetCommandBlock;
import net.minecraft.network.protocol.game.PacketPlayInSetCommandMinecart;
import net.minecraft.network.protocol.game.PacketPlayInSetCreativeSlot;
import net.minecraft.network.protocol.game.PacketPlayInSetJigsaw;
import net.minecraft.network.protocol.game.PacketPlayInSpectate;
import net.minecraft.network.protocol.game.PacketPlayInSteerVehicle;
import net.minecraft.network.protocol.game.PacketPlayInStruct;
import net.minecraft.network.protocol.game.PacketPlayInTabComplete;
import net.minecraft.network.protocol.game.PacketPlayInTeleportAccept;
import net.minecraft.network.protocol.game.PacketPlayInTileNBTQuery;
import net.minecraft.network.protocol.game.PacketPlayInTrSel;
import net.minecraft.network.protocol.game.PacketPlayInUpdateSign;
import net.minecraft.network.protocol.game.PacketPlayInUseEntity;
import net.minecraft.network.protocol.game.PacketPlayInUseItem;
import net.minecraft.network.protocol.game.PacketPlayInVehicleMove;
import net.minecraft.network.protocol.game.PacketPlayInWindowClick;
import net.minecraft.network.protocol.game.PacketPlayOutAutoRecipe;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.network.protocol.game.PacketPlayOutHeldItemSlot;
import net.minecraft.network.protocol.game.PacketPlayOutNBTQuery;
import net.minecraft.network.protocol.game.PacketPlayOutPosition;
import net.minecraft.network.protocol.game.PacketPlayOutTabComplete;
import net.minecraft.network.protocol.game.PacketPlayOutVehicleMove;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.CommandGamemode;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.FutureChain;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.TickThrottler;
import net.minecraft.util.UtilColor;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.IInventory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityExperienceOrb;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.IJumpable;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.EnumChatVisibility;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerAnvil;
import net.minecraft.world.inventory.ContainerBeacon;
import net.minecraft.world.inventory.ContainerMerchant;
import net.minecraft.world.inventory.ContainerRecipeBook;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemBlock;
import net.minecraft.world.item.ItemBucket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.CommandBlockListenerAbstract;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockCommand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityCommand;
import net.minecraft.world.level.block.entity.TileEntityJigsaw;
import net.minecraft.world.level.block.entity.TileEntitySign;
import net.minecraft.world.level.block.entity.TileEntityStructure;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.OperatorBoolean;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;
import org.slf4j.Logger;

// CraftBukkit start
import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.protocol.game.PacketPlayOutAttachEntity;
import net.minecraft.network.protocol.game.PacketPlayOutEntityEquipment;
import net.minecraft.network.protocol.game.PacketPlayOutSetSlot;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.inventory.InventoryClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.RayTrace;
import net.minecraft.world.phys.MovingObjectPosition;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftInput;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.LazyPlayerSet;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.util.Vector;
// CraftBukkit end

public class PlayerConnection extends ServerCommonPacketListenerImpl implements GameProtocols.a, PacketListenerPlayIn, ServerPlayerConnection, TickablePacketListener {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
    private static final int TRACKED_MESSAGE_DISCONNECT_THRESHOLD = 4096;
    private static final int MAXIMUM_FLYING_TICKS = 80;
    private static final IChatBaseComponent CHAT_VALIDATION_FAILED = IChatBaseComponent.translatable("multiplayer.disconnect.chat_validation_failed");
    private static final IChatBaseComponent INVALID_COMMAND_SIGNATURE = IChatBaseComponent.translatable("chat.disabled.invalid_command_signature").withStyle(EnumChatFormat.RED);
    private static final int MAX_COMMAND_SUGGESTIONS = 1000;
    public EntityPlayer player;
    public final PlayerChunkSender chunkSender;
    private int tickCount;
    private int ackBlockChangesUpTo = -1;
    private final TickThrottler chatSpamThrottler = new TickThrottler(20, 200);
    private final TickThrottler dropSpamThrottler = new TickThrottler(20, 1480);
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    @Nullable
    private Entity lastVehicle;
    private double vehicleFirstGoodX;
    private double vehicleFirstGoodY;
    private double vehicleFirstGoodZ;
    private double vehicleLastGoodX;
    private double vehicleLastGoodY;
    private double vehicleLastGoodZ;
    @Nullable
    private Vec3D awaitingPositionFromClient;
    private int awaitingTeleport;
    private int awaitingTeleportTime;
    private boolean clientIsFloating;
    private int aboveGroundTickCount;
    private boolean clientVehicleIsFloating;
    private int aboveGroundVehicleTickCount;
    private int receivedMovePacketCount;
    private int knownMovePacketCount;
    private boolean receivedMovementThisTick;
    @Nullable
    private RemoteChatSession chatSession;
    private SignedMessageChain.b signedMessageDecoder;
    private final LastSeenMessagesValidator lastSeenMessages = new LastSeenMessagesValidator(20);
    private int nextChatIndex;
    private final MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
    private final FutureChain chatMessageChain;
    private boolean waitingForSwitchToConfig;

    public PlayerConnection(MinecraftServer minecraftserver, NetworkManager networkmanager, EntityPlayer entityplayer, CommonListenerCookie commonlistenercookie) {
        super(minecraftserver, networkmanager, commonlistenercookie, entityplayer); // CraftBukkit
        this.chunkSender = new PlayerChunkSender(networkmanager.isMemoryConnection());
        this.player = entityplayer;
        entityplayer.connection = this;
        entityplayer.getTextFilter().join();
        UUID uuid = entityplayer.getUUID();

        Objects.requireNonNull(minecraftserver);
        this.signedMessageDecoder = SignedMessageChain.b.unsigned(uuid, minecraftserver::enforceSecureProfile);
        this.chatMessageChain = new FutureChain(minecraftserver.chatExecutor); // CraftBukkit - async chat
    }

    // CraftBukkit start - add fields and methods
    private int lastTick = MinecraftServer.currentTick;
    private int allowedPlayerTicks = 1;
    private int lastDropTick = MinecraftServer.currentTick;
    private int lastBookTick  = MinecraftServer.currentTick;
    private int dropCount = 0;

    private boolean hasMoved = false;
    private double lastPosX = Double.MAX_VALUE;
    private double lastPosY = Double.MAX_VALUE;
    private double lastPosZ = Double.MAX_VALUE;
    private float lastPitch = Float.MAX_VALUE;
    private float lastYaw = Float.MAX_VALUE;
    private boolean justTeleported = false;
    // CraftBukkit end

    @Override
    public void tick() {
        org.bukkit.craftbukkit.SpigotTimings.playerConnectionTimer.startTiming(); // Spigot
        if (this.ackBlockChangesUpTo > -1) {
            this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
            this.ackBlockChangesUpTo = -1;
        }

        this.resetPosition();
        this.player.xo = this.player.getX();
        this.player.yo = this.player.getY();
        this.player.zo = this.player.getZ();
        this.player.doTick();
        this.player.absSnapTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
        ++this.tickCount;
        this.knownMovePacketCount = this.receivedMovePacketCount;
        if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {
            if (++this.aboveGroundTickCount > this.getMaximumFlyingTicks(this.player)) {
                PlayerConnection.LOGGER.warn("{} was kicked for floating too long!", this.player.getName().getString());
                this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.flying"));
                return;
            }
        } else {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }

        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodX = this.lastVehicle.getX();
            this.vehicleFirstGoodY = this.lastVehicle.getY();
            this.vehicleFirstGoodZ = this.lastVehicle.getZ();
            this.vehicleLastGoodX = this.lastVehicle.getX();
            this.vehicleLastGoodY = this.lastVehicle.getY();
            this.vehicleLastGoodZ = this.lastVehicle.getZ();
            if (this.clientVehicleIsFloating && this.lastVehicle.getControllingPassenger() == this.player) {
                if (++this.aboveGroundVehicleTickCount > this.getMaximumFlyingTicks(this.lastVehicle)) {
                    PlayerConnection.LOGGER.warn("{} was kicked for floating a vehicle too long!", this.player.getName().getString());
                    this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.flying"));
                    return;
                }
            } else {
                this.clientVehicleIsFloating = false;
                this.aboveGroundVehicleTickCount = 0;
            }
        } else {
            this.lastVehicle = null;
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
        }

        this.keepConnectionAlive();
        this.chatSpamThrottler.tick();
        this.dropSpamThrottler.tick();
        if (this.player.getLastActionTime() > 0L && this.server.getPlayerIdleTimeout() > 0 && SystemUtils.getMillis() - this.player.getLastActionTime() > (long) this.server.getPlayerIdleTimeout() * 1000L * 60L) {
            this.player.resetLastActionTime(); // CraftBukkit - SPIGOT-854
            this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.idling"));
        }
        org.bukkit.craftbukkit.SpigotTimings.playerConnectionTimer.stopTiming(); // Spigot

    }

    private int getMaximumFlyingTicks(Entity entity) {
        double d0 = entity.getGravity();

        if (d0 < (double) 1.0E-5F) {
            return Integer.MAX_VALUE;
        } else {
            double d1 = 0.08D / d0;

            return MathHelper.ceil(80.0D * Math.max(d1, 1.0D));
        }
    }

    public void resetPosition() {
        this.firstGoodX = this.player.getX();
        this.firstGoodY = this.player.getY();
        this.firstGoodZ = this.player.getZ();
        this.lastGoodX = this.player.getX();
        this.lastGoodY = this.player.getY();
        this.lastGoodZ = this.player.getZ();
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected() && !this.waitingForSwitchToConfig;
    }

    @Override
    public boolean shouldHandleMessage(Packet<?> packet) {
        return super.shouldHandleMessage(packet) ? true : this.waitingForSwitchToConfig && this.connection.isConnected() && packet instanceof ServerboundConfigurationAcknowledgedPacket;
    }

    @Override
    protected GameProfile playerProfile() {
        return this.player.getGameProfile();
    }

    private <T, R> CompletableFuture<R> filterTextPacket(T t0, BiFunction<ITextFilter, T, CompletableFuture<R>> bifunction) {
        return ((CompletableFuture) bifunction.apply(this.player.getTextFilter(), t0)).thenApply((object) -> {
            if (!this.isAcceptingMessages()) {
                PlayerConnection.LOGGER.debug("Ignoring packet due to disconnection");
                throw new CancellationException("disconnected");
            } else {
                return object;
            }
        });
    }

    private CompletableFuture<FilteredText> filterTextPacket(String s) {
        return this.filterTextPacket(s, ITextFilter::processStreamMessage);
    }

    private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> list) {
        return this.filterTextPacket(list, ITextFilter::processMessageBundle);
    }

    @Override
    public void handlePlayerInput(PacketPlayInSteerVehicle packetplayinsteervehicle) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinsteervehicle, this, this.player.level());
        // CraftBukkit start
        if (!packetplayinsteervehicle.input().equals(this.player.getLastClientInput())) {
            PlayerInputEvent event = new PlayerInputEvent(this.player.getBukkitEntity(), new CraftInput(packetplayinsteervehicle.input()));
            this.cserver.getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        this.player.setLastClientInput(packetplayinsteervehicle.input());
        if (this.player.hasClientLoaded()) {
            this.player.resetLastActionTime();
            // CraftBukkit start
            boolean shift = packetplayinsteervehicle.input().shift();
            if (this.player.isShiftKeyDown() != shift) {
                PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getCraftPlayer(), shift);
                this.cserver.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    this.player.setShiftKeyDown(shift);
                }
            }
            // CraftBukkit end
        }

    }

    private static boolean containsInvalidValues(double d0, double d1, double d2, float f, float f1) {
        return Double.isNaN(d0) || Double.isNaN(d1) || Double.isNaN(d2) || !Floats.isFinite(f1) || !Floats.isFinite(f);
    }

    private static double clampHorizontal(double d0) {
        return MathHelper.clamp(d0, -3.0E7D, 3.0E7D);
    }

    private static double clampVertical(double d0) {
        return MathHelper.clamp(d0, -2.0E7D, 2.0E7D);
    }

    @Override
    public void handleMoveVehicle(PacketPlayInVehicleMove packetplayinvehiclemove) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinvehiclemove, this, this.player.level());
        if (containsInvalidValues(packetplayinvehiclemove.position().x(), packetplayinvehiclemove.position().y(), packetplayinvehiclemove.position().z(), packetplayinvehiclemove.yRot(), packetplayinvehiclemove.xRot())) {
            this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.invalid_vehicle_movement"));
        } else if (!this.updateAwaitingTeleport() && this.player.hasClientLoaded()) {
            Entity entity = this.player.getRootVehicle();

            if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lastVehicle) {
                WorldServer worldserver = this.player.level();
                // CraftBukkit - store current player position
                double prevX = player.getX();
                double prevY = player.getY();
                double prevZ = player.getZ();
                float prevYaw = player.getYRot();
                float prevPitch = player.getXRot();
                // CraftBukkit end
                double d0 = entity.getX();
                double d1 = entity.getY();
                double d2 = entity.getZ();
                double d3 = clampHorizontal(packetplayinvehiclemove.position().x());
                double d4 = clampVertical(packetplayinvehiclemove.position().y());
                double d5 = clampHorizontal(packetplayinvehiclemove.position().z());
                float f = MathHelper.wrapDegrees(packetplayinvehiclemove.yRot());
                float f1 = MathHelper.wrapDegrees(packetplayinvehiclemove.xRot());
                double d6 = d3 - this.vehicleFirstGoodX;
                double d7 = d4 - this.vehicleFirstGoodY;
                double d8 = d5 - this.vehicleFirstGoodZ;
                double d9 = entity.getDeltaMovement().lengthSqr();
                double d10 = d6 * d6 + d7 * d7 + d8 * d8;

                // CraftBukkit start - handle custom speeds and skipped ticks
                this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                this.lastTick = (int) (System.currentTimeMillis() / 50);

                ++this.receivedMovePacketCount;
                int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                if (i > Math.max(this.allowedPlayerTicks, 5)) {
                    PlayerConnection.LOGGER.debug(this.player.getScoreboardName() + " is sending move packets too frequently (" + i + " packets since last tick)");
                    i = 1;
                }

                if (d10 > 0) {
                    allowedPlayerTicks -= 1;
                } else {
                    allowedPlayerTicks = 20;
                }
                double speed;
                if (player.getAbilities().flying) {
                    speed = player.getAbilities().flyingSpeed * 20f;
                } else {
                    speed = player.getAbilities().walkingSpeed * 10f;
                }
                speed *= 2f; // TODO: Get the speed of the vehicle instead of the player

                if (d10 - d9 > Math.max(100.0D, Math.pow((double) (org.mospigot.config.MoSpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                // CraftBukkit end
                    PlayerConnection.LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", new Object[]{entity.getName().getString(), this.player.getName().getString(), d6, d7, d8});
                    this.send(PacketPlayOutVehicleMove.fromEntity(entity));
                    return;
                }

                AxisAlignedBB axisalignedbb = entity.getBoundingBox();

                d6 = d3 - this.vehicleLastGoodX;
                d7 = d4 - this.vehicleLastGoodY;
                d8 = d5 - this.vehicleLastGoodZ;
                boolean flag = entity.verticalCollisionBelow;

                if (entity instanceof EntityLiving) {
                    EntityLiving entityliving = (EntityLiving) entity;

                    if (entityliving.onClimbable()) {
                        entityliving.resetFallDistance();
                    }
                }

                entity.move(EnumMoveType.PLAYER, new Vec3D(d6, d7, d8));
                double d11 = d7;

                d6 = d3 - entity.getX();
                d7 = d4 - entity.getY();
                if (d7 > -0.5D || d7 < 0.5D) {
                    d7 = 0.0D;
                }

                d8 = d5 - entity.getZ();
                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                boolean flag1 = false;

                if (d10 > org.mospigot.config.MoSpigotConfig.movedWronglyThreshold) { // Spigot
                    flag1 = true;
                    PlayerConnection.LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", new Object[]{entity.getName().getString(), this.player.getName().getString(), Math.sqrt(d10)});
                }

                if (flag1 && worldserver.noCollision(entity, axisalignedbb) || this.isEntityCollidingWithAnythingNew(worldserver, entity, axisalignedbb, d3, d4, d5)) {
                    entity.absSnapTo(d0, d1, d2, f, f1);
                    player.absSnapTo(d0, d1, d2, this.player.getYRot(), this.player.getXRot()); // CraftBukkit
                    this.send(PacketPlayOutVehicleMove.fromEntity(entity));
                    entity.removeLatestMovementRecording();
                    return;
                }

                entity.absSnapTo(d3, d4, d5, f, f1);
                player.absSnapTo(d3, d4, d5, this.player.getYRot(), this.player.getXRot()); // CraftBukkit
                // CraftBukkit start - fire PlayerMoveEvent
                Player player = this.getCraftPlayer();
                if (!this.hasMoved) {
                    this.lastPosX = prevX;
                    this.lastPosY = prevY;
                    this.lastPosZ = prevZ;
                    this.lastYaw = prevYaw;
                    this.lastPitch = prevPitch;
                    this.hasMoved = true;
                }
                Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                Location to = CraftLocation.toBukkit(packetplayinvehiclemove.position(), player.getWorld(), packetplayinvehiclemove.yRot(), packetplayinvehiclemove.xRot());

                // Prevent 40 event-calls for less than a single pixel of movement >.>
                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                    this.lastPosX = to.getX();
                    this.lastPosY = to.getY();
                    this.lastPosZ = to.getZ();
                    this.lastYaw = to.getYaw();
                    this.lastPitch = to.getPitch();

                    Location oldTo = to.clone();
                    PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                    this.cserver.getPluginManager().callEvent(event);

                    // If the event is cancelled we move the player back to their old location.
                    if (event.isCancelled()) {
                        teleport(from);
                        return;
                    }

                    // If a Plugin has changed the To destination then we teleport the Player
                    // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                    // We only do this if the Event was not cancelled.
                    if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                        this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                        return;
                    }

                    // Check to see if the Players Location has some how changed during the call of the event.
                    // This can happen due to a plugin teleporting the player instead of using .setTo()
                    if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                        this.justTeleported = false;
                        return;
                    }
                }
                // CraftBukkit end

                this.player.level().getChunkSource().move(this.player);
                Vec3D vec3d = new Vec3D(entity.getX() - d0, entity.getY() - d1, entity.getZ() - d2);

                this.handlePlayerKnownMovement(vec3d);
                entity.setOnGroundWithMovement(packetplayinvehiclemove.onGround(), vec3d);
                entity.doCheckFallDamage(vec3d.x, vec3d.y, vec3d.z, packetplayinvehiclemove.onGround());
                this.player.checkMovementStatistics(vec3d.x, vec3d.y, vec3d.z);
                this.clientVehicleIsFloating = d11 >= -0.03125D && !flag && !this.server.isFlightAllowed() && !entity.isFlyingVehicle() && !entity.isNoGravity() && this.noBlocksAround(entity);
                this.vehicleLastGoodX = entity.getX();
                this.vehicleLastGoodY = entity.getY();
                this.vehicleLastGoodZ = entity.getZ();
            }

        }
    }

    private boolean noBlocksAround(Entity entity) {
        return entity.level().getBlockStates(entity.getBoundingBox().inflate(0.0625D).expandTowards(0.0D, -0.55D, 0.0D)).allMatch(BlockBase.BlockData::isAir);
    }

    @Override
    public void handleAcceptTeleportPacket(PacketPlayInTeleportAccept packetplayinteleportaccept) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinteleportaccept, this, this.player.level());
        if (packetplayinteleportaccept.getId() == this.awaitingTeleport) {
            if (this.awaitingPositionFromClient == null) {
                this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.invalid_player_movement"));
                return;
            }

            this.player.absSnapTo(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
            this.lastGoodX = this.awaitingPositionFromClient.x;
            this.lastGoodY = this.awaitingPositionFromClient.y;
            this.lastGoodZ = this.awaitingPositionFromClient.z;
            this.player.hasChangedDimension();
            this.awaitingPositionFromClient = null;
            this.player.level().getChunkSource().move(this.player); // CraftBukkit
        }

    }

    @Override
    public void handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket serverboundplayerloadedpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundplayerloadedpacket, this, this.player.level());
        this.player.setClientLoaded(true);
    }

    @Override
    public void handleRecipeBookSeenRecipePacket(PacketPlayInRecipeDisplayed packetplayinrecipedisplayed) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinrecipedisplayed, this, this.player.level());
        CraftingManager.d craftingmanager_d = this.server.getRecipeManager().getRecipeFromDisplay(packetplayinrecipedisplayed.recipe());

        if (craftingmanager_d != null) {
            this.player.getRecipeBook().removeHighlight(craftingmanager_d.parent().id());
        }

    }

    @Override
    public void handleBundleItemSelectedPacket(ServerboundSelectBundleItemPacket serverboundselectbundleitempacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundselectbundleitempacket, this, this.player.level());
        this.player.containerMenu.setSelectedBundleItemIndex(serverboundselectbundleitempacket.slotId(), serverboundselectbundleitempacket.selectedItemIndex());
    }

    @Override
    public void handleRecipeBookChangeSettingsPacket(PacketPlayInRecipeSettings packetplayinrecipesettings) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinrecipesettings, this, this.player.level());
        CraftEventFactory.callRecipeBookSettingsEvent(this.player, packetplayinrecipesettings.getBookType(), packetplayinrecipesettings.isOpen(), packetplayinrecipesettings.isFiltering()); // CraftBukkit
        this.player.getRecipeBook().setBookSetting(packetplayinrecipesettings.getBookType(), packetplayinrecipesettings.isOpen(), packetplayinrecipesettings.isFiltering());
    }

    @Override
    public void handleSeenAdvancements(PacketPlayInAdvancements packetplayinadvancements) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinadvancements, this, this.player.level());
        if (packetplayinadvancements.getAction() == PacketPlayInAdvancements.Status.OPENED_TAB) {
            MinecraftKey minecraftkey = (MinecraftKey) Objects.requireNonNull(packetplayinadvancements.getTab());
            AdvancementHolder advancementholder = this.server.getAdvancements().get(minecraftkey);

            if (advancementholder != null) {
                this.player.getAdvancements().setSelectedTab(advancementholder);
            }
        }

    }

    @Override
    public void handleCustomCommandSuggestions(PacketPlayInTabComplete packetplayintabcomplete) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayintabcomplete, this, this.player.level());
        // CraftBukkit start
        if (!this.chatSpamThrottler.isIncrementAndUnderThreshold(1, 500) && !this.server.getPlayerList().isOp(this.player.getGameProfile()) && !this.server.isSingleplayerOwner(this.player.getGameProfile())) {
            this.disconnect(IChatBaseComponent.translatable("disconnect.spam"));
            return;
        }
        // CraftBukkit end
        StringReader stringreader = new StringReader(packetplayintabcomplete.getCommand());

        if (stringreader.canRead() && stringreader.peek() == '/') {
            stringreader.skip();
        }

        ParseResults<CommandListenerWrapper> parseresults = this.server.getCommands().getDispatcher().parse(stringreader, this.player.createCommandSourceStack());

        this.server.getCommands().getDispatcher().getCompletionSuggestions(parseresults).thenAccept((suggestions) -> {
            if (suggestions.isEmpty()) return; // CraftBukkit - don't send through empty suggestions - prevents [<args>] from showing for plugins with nothing more to offer
            Suggestions suggestions1 = suggestions.getList().size() <= 1000 ? suggestions : new Suggestions(suggestions.getRange(), suggestions.getList().subList(0, 1000));

            this.send(new PacketPlayOutTabComplete(packetplayintabcomplete.getId(), suggestions1));
        });
    }

    @Override
    public void handleSetCommandBlock(PacketPlayInSetCommandBlock packetplayinsetcommandblock) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinsetcommandblock, this, this.player.level());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(IChatBaseComponent.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendSystemMessage(IChatBaseComponent.translatable("advMode.notAllowed"));
        } else {
            CommandBlockListenerAbstract commandblocklistenerabstract = null;
            TileEntityCommand tileentitycommand = null;
            BlockPosition blockposition = packetplayinsetcommandblock.getPos();
            TileEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof TileEntityCommand) {
                tileentitycommand = (TileEntityCommand) tileentity;
                commandblocklistenerabstract = tileentitycommand.getCommandBlock();
            }

            String s = packetplayinsetcommandblock.getCommand();
            boolean flag = packetplayinsetcommandblock.isTrackOutput();

            if (commandblocklistenerabstract != null) {
                TileEntityCommand.Type tileentitycommand_type = tileentitycommand.getMode();
                IBlockData iblockdata = this.player.level().getBlockState(blockposition);
                EnumDirection enumdirection = (EnumDirection) iblockdata.getValue(BlockCommand.FACING);
                IBlockData iblockdata1;

                switch (packetplayinsetcommandblock.getMode()) {
                    case SEQUENCE:
                        iblockdata1 = Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                        break;
                    case AUTO:
                        iblockdata1 = Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                        break;
                    default:
                        iblockdata1 = Blocks.COMMAND_BLOCK.defaultBlockState();
                }

                IBlockData iblockdata2 = iblockdata1;
                IBlockData iblockdata3 = (IBlockData) ((IBlockData) iblockdata2.setValue(BlockCommand.FACING, enumdirection)).setValue(BlockCommand.CONDITIONAL, packetplayinsetcommandblock.isConditional());

                if (iblockdata3 != iblockdata) {
                    this.player.level().setBlock(blockposition, iblockdata3, 2);
                    tileentity.setBlockState(iblockdata3);
                    this.player.level().getChunkAt(blockposition).setBlockEntity(tileentity);
                }

                commandblocklistenerabstract.setCommand(s);
                commandblocklistenerabstract.setTrackOutput(flag);
                if (!flag) {
                    commandblocklistenerabstract.setLastOutput((IChatBaseComponent) null);
                }

                tileentitycommand.setAutomatic(packetplayinsetcommandblock.isAutomatic());
                if (tileentitycommand_type != packetplayinsetcommandblock.getMode()) {
                    tileentitycommand.onModeSwitch();
                }

                commandblocklistenerabstract.onUpdated();
                if (!UtilColor.isNullOrEmpty(s)) {
                    this.player.sendSystemMessage(IChatBaseComponent.translatable("advMode.setCommand.success", s));
                }
            }

        }
    }

    @Override
    public void handleSetCommandMinecart(PacketPlayInSetCommandMinecart packetplayinsetcommandminecart) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinsetcommandminecart, this, this.player.level());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(IChatBaseComponent.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks()) {
            this.player.sendSystemMessage(IChatBaseComponent.translatable("advMode.notAllowed"));
        } else {
            CommandBlockListenerAbstract commandblocklistenerabstract = packetplayinsetcommandminecart.getCommandBlock(this.player.level());

            if (commandblocklistenerabstract != null) {
                commandblocklistenerabstract.setCommand(packetplayinsetcommandminecart.getCommand());
                commandblocklistenerabstract.setTrackOutput(packetplayinsetcommandminecart.isTrackOutput());
                if (!packetplayinsetcommandminecart.isTrackOutput()) {
                    commandblocklistenerabstract.setLastOutput((IChatBaseComponent) null);
                }

                commandblocklistenerabstract.onUpdated();
                this.player.sendSystemMessage(IChatBaseComponent.translatable("advMode.setCommand.success", packetplayinsetcommandminecart.getCommand()));
            }

        }
    }

    @Override
    public void handlePickItemFromBlock(ServerboundPickItemFromBlockPacket serverboundpickitemfromblockpacket) {
        WorldServer worldserver = this.player.level();

        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundpickitemfromblockpacket, this, worldserver);
        BlockPosition blockposition = serverboundpickitemfromblockpacket.pos();

        if (this.player.canInteractWithBlock(blockposition, 1.0D)) {
            if (worldserver.isLoaded(blockposition)) {
                IBlockData iblockdata = worldserver.getBlockState(blockposition);
                boolean flag = this.player.hasInfiniteMaterials() && serverboundpickitemfromblockpacket.includeData();
                ItemStack itemstack = iblockdata.getCloneItemStack(worldserver, blockposition, flag);

                if (!itemstack.isEmpty()) {
                    if (flag && this.player.getBukkitEntity().hasPermission("minecraft.nbt.copy")) { // Spigot
                        addBlockDataToItem(iblockdata, worldserver, blockposition, itemstack);
                    }

                    this.tryPickItem(itemstack);
                }
            }
        }
    }

    private static void addBlockDataToItem(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, ItemStack itemstack) {
        TileEntity tileentity = iblockdata.hasBlockEntity() ? worldserver.getBlockEntity(blockposition) : null;

        if (tileentity != null) {
            try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(tileentity.problemPath(), PlayerConnection.LOGGER)) {
                TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_j, worldserver.registryAccess());

                tileentity.saveCustomOnly((ValueOutput) tagvalueoutput);
                tileentity.removeComponentsFromTag(tagvalueoutput);
                ItemBlock.setBlockEntityData(itemstack, tileentity.getType(), tagvalueoutput);
                itemstack.applyComponents(tileentity.collectComponents());
            }
        }

    }

    @Override
    public void handlePickItemFromEntity(ServerboundPickItemFromEntityPacket serverboundpickitemfromentitypacket) {
        WorldServer worldserver = this.player.level();

        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundpickitemfromentitypacket, this, worldserver);
        Entity entity = worldserver.getEntityOrPart(serverboundpickitemfromentitypacket.id());

        if (entity != null && this.player.canInteractWithEntity(entity, 3.0D)) {
            ItemStack itemstack = entity.getPickResult();

            if (itemstack != null && !itemstack.isEmpty()) {
                this.tryPickItem(itemstack);
            }

        }
    }

    private void tryPickItem(ItemStack itemstack) {
        if (itemstack.isItemEnabled(this.player.level().enabledFeatures())) {
            PlayerInventory playerinventory = this.player.getInventory();
            int i = playerinventory.findSlotMatchingItem(itemstack);

            if (i != -1) {
                if (PlayerInventory.isHotbarSlot(i)) {
                    playerinventory.setSelectedSlot(i);
                } else {
                    playerinventory.pickSlot(i);
                }
            } else if (this.player.hasInfiniteMaterials()) {
                playerinventory.addAndPickItem(itemstack);
            }

            this.send(new PacketPlayOutHeldItemSlot(playerinventory.getSelectedSlot()));
            this.player.inventoryMenu.broadcastChanges();
        }
    }

    @Override
    public void handleRenameItem(PacketPlayInItemName packetplayinitemname) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinitemname, this, this.player.level());
        Container container = this.player.containerMenu;

        if (container instanceof ContainerAnvil containeranvil) {
            if (!containeranvil.stillValid(this.player)) {
                PlayerConnection.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, containeranvil);
                return;
            }

            containeranvil.setItemName(packetplayinitemname.getName());
        }

    }

    @Override
    public void handleSetBeaconPacket(PacketPlayInBeacon packetplayinbeacon) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinbeacon, this, this.player.level());
        Container container = this.player.containerMenu;

        if (container instanceof ContainerBeacon containerbeacon) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                PlayerConnection.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
                return;
            }

            containerbeacon.updateEffects(packetplayinbeacon.primary(), packetplayinbeacon.secondary());
        }

    }

    @Override
    public void handleSetStructureBlock(PacketPlayInStruct packetplayinstruct) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinstruct, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPosition blockposition = packetplayinstruct.getPos();
            IBlockData iblockdata = this.player.level().getBlockState(blockposition);
            TileEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof TileEntityStructure) {
                TileEntityStructure tileentitystructure = (TileEntityStructure) tileentity;

                tileentitystructure.setMode(packetplayinstruct.getMode());
                tileentitystructure.setStructureName(packetplayinstruct.getName());
                tileentitystructure.setStructurePos(packetplayinstruct.getOffset());
                tileentitystructure.setStructureSize(packetplayinstruct.getSize());
                tileentitystructure.setMirror(packetplayinstruct.getMirror());
                tileentitystructure.setRotation(packetplayinstruct.getRotation());
                tileentitystructure.setMetaData(packetplayinstruct.getData());
                tileentitystructure.setIgnoreEntities(packetplayinstruct.isIgnoreEntities());
                tileentitystructure.setStrict(packetplayinstruct.isStrict());
                tileentitystructure.setShowAir(packetplayinstruct.isShowAir());
                tileentitystructure.setShowBoundingBox(packetplayinstruct.isShowBoundingBox());
                tileentitystructure.setIntegrity(packetplayinstruct.getIntegrity());
                tileentitystructure.setSeed(packetplayinstruct.getSeed());
                if (tileentitystructure.hasStructureName()) {
                    String s = tileentitystructure.getStructureName();

                    if (packetplayinstruct.getUpdateType() == TileEntityStructure.UpdateType.SAVE_AREA) {
                        if (tileentitystructure.saveStructure()) {
                            this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.save_success", s), false);
                        } else {
                            this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.save_failure", s), false);
                        }
                    } else if (packetplayinstruct.getUpdateType() == TileEntityStructure.UpdateType.LOAD_AREA) {
                        if (!tileentitystructure.isStructureLoadable()) {
                            this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.load_not_found", s), false);
                        } else if (tileentitystructure.placeStructureIfSameSize(this.player.level())) {
                            this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.load_success", s), false);
                        } else {
                            this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.load_prepare", s), false);
                        }
                    } else if (packetplayinstruct.getUpdateType() == TileEntityStructure.UpdateType.SCAN_AREA) {
                        if (tileentitystructure.detectSize()) {
                            this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.size_success", s), false);
                        } else {
                            this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.size_failure"), false);
                        }
                    }
                } else {
                    this.player.displayClientMessage(IChatBaseComponent.translatable("structure_block.invalid_structure_name", packetplayinstruct.getName()), false);
                }

                tileentitystructure.setChanged();
                this.player.level().sendBlockUpdated(blockposition, iblockdata, iblockdata, 3);
            }

        }
    }

    @Override
    public void handleSetTestBlock(ServerboundSetTestBlockPacket serverboundsettestblockpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundsettestblockpacket, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPosition blockposition = serverboundsettestblockpacket.position();
            IBlockData iblockdata = this.player.level().getBlockState(blockposition);
            TileEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof TestBlockEntity) {
                TestBlockEntity testblockentity = (TestBlockEntity) tileentity;

                testblockentity.setMode(serverboundsettestblockpacket.mode());
                testblockentity.setMessage(serverboundsettestblockpacket.message());
                testblockentity.setChanged();
                this.player.level().sendBlockUpdated(blockposition, iblockdata, testblockentity.getBlockState(), 3);
            }

        }
    }

    @Override
    public void handleTestInstanceBlockAction(ServerboundTestInstanceBlockActionPacket serverboundtestinstanceblockactionpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundtestinstanceblockactionpacket, this, this.player.level());
        BlockPosition blockposition = serverboundtestinstanceblockactionpacket.pos();

        if (this.player.canUseGameMasterBlocks()) {
            TileEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof TestInstanceBlockEntity) {
                TestInstanceBlockEntity testinstanceblockentity = (TestInstanceBlockEntity) tileentity;

                if (serverboundtestinstanceblockactionpacket.action() != ServerboundTestInstanceBlockActionPacket.a.QUERY && serverboundtestinstanceblockactionpacket.action() != ServerboundTestInstanceBlockActionPacket.a.INIT) {
                    testinstanceblockentity.set(serverboundtestinstanceblockactionpacket.data());
                    if (serverboundtestinstanceblockactionpacket.action() == ServerboundTestInstanceBlockActionPacket.a.RESET) {
                        EntityPlayer entityplayer = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.resetTest(entityplayer::sendSystemMessage);
                    } else if (serverboundtestinstanceblockactionpacket.action() == ServerboundTestInstanceBlockActionPacket.a.SAVE) {
                        EntityPlayer entityplayer1 = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.saveTest(entityplayer1::sendSystemMessage);
                    } else if (serverboundtestinstanceblockactionpacket.action() == ServerboundTestInstanceBlockActionPacket.a.EXPORT) {
                        EntityPlayer entityplayer2 = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.exportTest(entityplayer2::sendSystemMessage);
                    } else if (serverboundtestinstanceblockactionpacket.action() == ServerboundTestInstanceBlockActionPacket.a.RUN) {
                        EntityPlayer entityplayer3 = this.player;

                        Objects.requireNonNull(this.player);
                        testinstanceblockentity.runTest(entityplayer3::sendSystemMessage);
                    }

                    IBlockData iblockdata = this.player.level().getBlockState(blockposition);

                    this.player.level().sendBlockUpdated(blockposition, Blocks.AIR.defaultBlockState(), iblockdata, 3);
                } else {
                    IRegistry<GameTestInstance> iregistry = this.player.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
                    Optional<ResourceKey<GameTestInstance>> optional = serverboundtestinstanceblockactionpacket.data().test(); // CraftBukkit - decompile error

                    Objects.requireNonNull(iregistry);
                    Optional<Holder.c<GameTestInstance>> optional1 = optional.flatMap(iregistry::get);
                    IChatBaseComponent ichatbasecomponent;

                    if (optional1.isPresent()) {
                        ichatbasecomponent = ((GameTestInstance) ((Holder.c) optional1.get()).value()).describe();
                    } else {
                        ichatbasecomponent = IChatBaseComponent.translatable("test_instance.description.no_test").withStyle(EnumChatFormat.RED);
                    }

                    Optional<BaseBlockPosition> optional2;

                    if (serverboundtestinstanceblockactionpacket.action() == ServerboundTestInstanceBlockActionPacket.a.QUERY) {
                        optional2 = serverboundtestinstanceblockactionpacket.data().test().flatMap((resourcekey) -> {
                            return TestInstanceBlockEntity.getStructureSize(this.player.level(), resourcekey);
                        });
                    } else {
                        optional2 = Optional.empty();
                    }

                    this.connection.send(new ClientboundTestInstanceBlockStatus(ichatbasecomponent, optional2));
                }

                return;
            }
        }

    }

    @Override
    public void handleSetJigsawBlock(PacketPlayInSetJigsaw packetplayinsetjigsaw) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinsetjigsaw, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPosition blockposition = packetplayinsetjigsaw.getPos();
            IBlockData iblockdata = this.player.level().getBlockState(blockposition);
            TileEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof TileEntityJigsaw) {
                TileEntityJigsaw tileentityjigsaw = (TileEntityJigsaw) tileentity;

                tileentityjigsaw.setName(packetplayinsetjigsaw.getName());
                tileentityjigsaw.setTarget(packetplayinsetjigsaw.getTarget());
                tileentityjigsaw.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, packetplayinsetjigsaw.getPool()));
                tileentityjigsaw.setFinalState(packetplayinsetjigsaw.getFinalState());
                tileentityjigsaw.setJoint(packetplayinsetjigsaw.getJoint());
                tileentityjigsaw.setPlacementPriority(packetplayinsetjigsaw.getPlacementPriority());
                tileentityjigsaw.setSelectionPriority(packetplayinsetjigsaw.getSelectionPriority());
                tileentityjigsaw.setChanged();
                this.player.level().sendBlockUpdated(blockposition, iblockdata, iblockdata, 3);
            }

        }
    }

    @Override
    public void handleJigsawGenerate(PacketPlayInJigsawGenerate packetplayinjigsawgenerate) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinjigsawgenerate, this, this.player.level());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPosition blockposition = packetplayinjigsawgenerate.getPos();
            TileEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof TileEntityJigsaw) {
                TileEntityJigsaw tileentityjigsaw = (TileEntityJigsaw) tileentity;

                tileentityjigsaw.generate(this.player.level(), packetplayinjigsawgenerate.levels(), packetplayinjigsawgenerate.keepJigsaws());
            }

        }
    }

    @Override
    public void handleSelectTrade(PacketPlayInTrSel packetplayintrsel) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayintrsel, this, this.player.level());
        int i = packetplayintrsel.getItem();
        Container container = this.player.containerMenu;

        if (container instanceof ContainerMerchant containermerchant) {
            // CraftBukkit start
            final org.bukkit.event.inventory.TradeSelectEvent tradeSelectEvent = CraftEventFactory.callTradeSelectEvent(this.player, i, containermerchant);
            if (tradeSelectEvent.isCancelled()) {
                this.player.getBukkitEntity().updateInventory();
                return;
            }
            // CraftBukkit end
            if (!containermerchant.stillValid(this.player)) {
                PlayerConnection.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, containermerchant);
                return;
            }

            containermerchant.setSelectionHint(i);
            containermerchant.tryMoveItems(i);
        }

    }

    @Override
    public void handleEditBook(PacketPlayInBEdit packetplayinbedit) {
        // CraftBukkit start
        if (this.lastBookTick + 20 > MinecraftServer.currentTick) {
            this.disconnect(IChatBaseComponent.literal("Book edited too quickly!"));
            return;
        }
        this.lastBookTick = MinecraftServer.currentTick;
        // CraftBukkit end
        int i = packetplayinbedit.slot();

        if (PlayerInventory.isHotbarSlot(i) || i == 40) {
            List<String> list = Lists.newArrayList();
            Optional<String> optional = packetplayinbedit.title();

            Objects.requireNonNull(list);
            optional.ifPresent(list::add);
            list.addAll(packetplayinbedit.pages());
            Consumer<List<FilteredText>> consumer = optional.isPresent() ? (list1) -> {
                this.signBook((FilteredText) list1.get(0), list1.subList(1, list1.size()), i);
            } : (list1) -> {
                this.updateBookContents(list1, i);
            };

            this.filterTextPacket(list).thenAcceptAsync(consumer, this.server);
        }
    }

    private void updateBookContents(List<FilteredText> list, int i) {
        // CraftBukkit start
        ItemStack handItem = this.player.getInventory().getItem(i);
        ItemStack itemstack = handItem.copy();
        // CraftBukkit end

        if (itemstack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            List<Filterable<String>> list1 = list.stream().map(this::filterableFromOutgoing).toList();

            itemstack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(list1));
            CraftEventFactory.handleEditBookEvent(player, i, handItem, itemstack); // CraftBukkit
        }
    }

    private void signBook(FilteredText filteredtext, List<FilteredText> list, int i) {
        ItemStack itemstack = this.player.getInventory().getItem(i);

        if (itemstack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            ItemStack itemstack1 = itemstack.transmuteCopy(Items.WRITTEN_BOOK);

            itemstack1.remove(DataComponents.WRITABLE_BOOK_CONTENT);
            List<Filterable<IChatBaseComponent>> list1 = (List<Filterable<IChatBaseComponent>>) (List) list.stream().map((filteredtext1) -> { // CraftBukkit - decompile error
                return this.filterableFromOutgoing(filteredtext1).map(IChatBaseComponent::literal);
            }).toList();

            itemstack1.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(this.filterableFromOutgoing(filteredtext), this.player.getName().getString(), 0, list1, true));
            CraftEventFactory.handleEditBookEvent(player, i, itemstack, itemstack1); // CraftBukkit
            this.player.getInventory().setItem(i, itemstack); // CraftBukkit - event factory updates the hand book
        }
    }

    private Filterable<String> filterableFromOutgoing(FilteredText filteredtext) {
        return this.player.isTextFilteringEnabled() ? Filterable.passThrough(filteredtext.filteredOrEmpty()) : Filterable.from(filteredtext);
    }

    @Override
    public void handleEntityTagQuery(PacketPlayInEntityNBTQuery packetplayinentitynbtquery) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinentitynbtquery, this, this.player.level());
        if (this.player.hasPermissions(2)) {
            Entity entity = this.player.level().getEntity(packetplayinentitynbtquery.getEntityId());

            if (entity != null) {
                try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(entity.problemPath(), PlayerConnection.LOGGER)) {
                    TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_j, entity.registryAccess());

                    entity.saveWithoutId(tagvalueoutput);
                    NBTTagCompound nbttagcompound = tagvalueoutput.buildResult();

                    this.send(new PacketPlayOutNBTQuery(packetplayinentitynbtquery.getTransactionId(), nbttagcompound));
                }
            }

        }
    }

    @Override
    public void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket serverboundcontainerslotstatechangedpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundcontainerslotstatechangedpacket, this, this.player.level());
        if (!this.player.isSpectator() && serverboundcontainerslotstatechangedpacket.containerId() == this.player.containerMenu.containerId) {
            Container container = this.player.containerMenu;

            if (container instanceof CrafterMenu) {
                CrafterMenu craftermenu = (CrafterMenu) container;
                IInventory iinventory = craftermenu.getContainer();

                if (iinventory instanceof CrafterBlockEntity) {
                    CrafterBlockEntity crafterblockentity = (CrafterBlockEntity) iinventory;

                    crafterblockentity.setSlotState(serverboundcontainerslotstatechangedpacket.slotId(), serverboundcontainerslotstatechangedpacket.newState());
                }
            }

        }
    }

    @Override
    public void handleBlockEntityTagQuery(PacketPlayInTileNBTQuery packetplayintilenbtquery) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayintilenbtquery, this, this.player.level());
        if (this.player.hasPermissions(2)) {
            TileEntity tileentity = this.player.level().getBlockEntity(packetplayintilenbtquery.getPos());
            NBTTagCompound nbttagcompound = tileentity != null ? tileentity.saveWithoutMetadata((HolderLookup.a) this.player.registryAccess()) : null;

            this.send(new PacketPlayOutNBTQuery(packetplayintilenbtquery.getTransactionId(), nbttagcompound));
        }
    }

    @Override
    public void handleMovePlayer(PacketPlayInFlying packetplayinflying) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinflying, this, this.player.level());
        if (containsInvalidValues(packetplayinflying.getX(0.0D), packetplayinflying.getY(0.0D), packetplayinflying.getZ(0.0D), packetplayinflying.getYRot(0.0F), packetplayinflying.getXRot(0.0F))) {
            this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.invalid_player_movement"));
        } else {
            WorldServer worldserver = this.player.level();

            if (!this.player.wonGame && !this.player.isImmobile()) { // CraftBukkit
                if (this.tickCount == 0) {
                    this.resetPosition();
                }

                if (this.player.hasClientLoaded()) {
                    float f = MathHelper.wrapDegrees(packetplayinflying.getYRot(this.player.getYRot()));
                    float f1 = MathHelper.wrapDegrees(packetplayinflying.getXRot(this.player.getXRot()));

                    if (this.updateAwaitingTeleport()) {
                        this.player.absSnapRotationTo(f, f1);
                    } else {
                        double d0 = clampHorizontal(packetplayinflying.getX(this.player.getX()));
                        double d1 = clampVertical(packetplayinflying.getY(this.player.getY()));
                        double d2 = clampHorizontal(packetplayinflying.getZ(this.player.getZ()));

                        if (this.player.isPassenger()) {
                            this.player.absSnapTo(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                            this.player.level().getChunkSource().move(this.player);
                            this.allowedPlayerTicks = 20; // CraftBukkit
                        } else {
                            // CraftBukkit - Make sure the move is valid but then reset it for plugins to modify
                            double prevX = player.getX();
                            double prevY = player.getY();
                            double prevZ = player.getZ();
                            float prevYaw = player.getYRot();
                            float prevPitch = player.getXRot();
                            // CraftBukkit end
                            double d3 = this.player.getX();
                            double d4 = this.player.getY();
                            double d5 = this.player.getZ();
                            double d6 = d0 - this.firstGoodX;
                            double d7 = d1 - this.firstGoodY;
                            double d8 = d2 - this.firstGoodZ;
                            double d9 = this.player.getDeltaMovement().lengthSqr();
                            double d10 = d6 * d6 + d7 * d7 + d8 * d8;

                            if (this.player.isSleeping()) {
                                if (d10 > 1.0D) {
                                    this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                                }

                            } else {
                                boolean flag = this.player.isFallFlying();

                                if (worldserver.tickRateManager().runsNormally()) {
                                    ++this.receivedMovePacketCount;
                                    int i = this.receivedMovePacketCount - this.knownMovePacketCount;

                                    // CraftBukkit start - handle custom speeds and skipped ticks
                                    this.allowedPlayerTicks += (System.currentTimeMillis() / 50) - this.lastTick;
                                    this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                                    this.lastTick = (int) (System.currentTimeMillis() / 50);

                                    if (i > Math.max(this.allowedPlayerTicks, 5)) {
                                        PlayerConnection.LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getName().getString(), i);
                                        i = 1;
                                    }

                                    if (packetplayinflying.hasRot || d10 > 0) {
                                        allowedPlayerTicks -= 1;
                                    } else {
                                        allowedPlayerTicks = 20;
                                    }
                                    double speed;
                                    if (player.getAbilities().flying) {
                                        speed = player.getAbilities().flyingSpeed * 20f;
                                    } else {
                                        speed = player.getAbilities().walkingSpeed * 10f;
                                    }

                                    if (this.shouldCheckPlayerMovement(flag)) {
                                        float f2 = flag ? 300.0F : 100.0F;

                                        if (d10 - d9 > Math.max(f2, Math.pow((double) (org.mospigot.config.MoSpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2))) {
                                        // CraftBukkit end
                                            PlayerConnection.LOGGER.warn("{} moved too quickly! {},{},{}", new Object[]{this.player.getName().getString(), d6, d7, d8});
                                            this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
                                            return;
                                        }
                                    }
                                }

                                AxisAlignedBB axisalignedbb = this.player.getBoundingBox();

                                d6 = d0 - this.lastGoodX;
                                d7 = d1 - this.lastGoodY;
                                d8 = d2 - this.lastGoodZ;
                                boolean flag1 = d7 > 0.0D;

                                if (this.player.onGround() && !packetplayinflying.isOnGround() && flag1) {
                                    this.player.jumpFromGround();
                                }

                                boolean flag2 = this.player.verticalCollisionBelow;

                                this.player.move(EnumMoveType.PLAYER, new Vec3D(d6, d7, d8));
                                this.player.onGround = packetplayinflying.isOnGround(); // CraftBukkit - SPIGOT-5810, SPIGOT-5835, SPIGOT-6828: reset by this.player.move
                                double d11 = d7;

                                d6 = d0 - this.player.getX();
                                d7 = d1 - this.player.getY();
                                if (d7 > -0.5D || d7 < 0.5D) {
                                    d7 = 0.0D;
                                }

                                d8 = d2 - this.player.getZ();
                                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                                boolean flag3 = false;

                                if (!this.player.isChangingDimension() && d10 > org.mospigot.config.MoSpigotConfig.movedWronglyThreshold && !this.player.isSleeping() && !this.player.isCreative() && !this.player.isSpectator()) { // Spigot
                                    flag3 = true;
                                    PlayerConnection.LOGGER.warn("{} moved wrongly!", this.player.getName().getString());
                                }

                                if (this.player.noPhysics || this.player.isSleeping() || (!flag3 || !worldserver.noCollision(this.player, axisalignedbb)) && !this.isEntityCollidingWithAnythingNew(worldserver, this.player, axisalignedbb, d0, d1, d2)) {
                                    // CraftBukkit start - fire PlayerMoveEvent
                                    // Reset to old location first
                                    this.player.absSnapTo(prevX, prevY, prevZ, prevYaw, prevPitch);

                                    Player player = this.getCraftPlayer();
                                    if (!this.hasMoved) {
                                        this.lastPosX = prevX;
                                        this.lastPosY = prevY;
                                        this.lastPosZ = prevZ;
                                        this.lastYaw = prevYaw;
                                        this.lastPitch = prevPitch;
                                        this.hasMoved = true;
                                    }
                                    Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                                    Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                                    // If the packet contains movement information then we update the To location with the correct XYZ.
                                    if (packetplayinflying.hasPos) {
                                        to.setX(packetplayinflying.x);
                                        to.setY(packetplayinflying.y);
                                        to.setZ(packetplayinflying.z);
                                    }

                                    // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                    if (packetplayinflying.hasRot) {
                                        to.setYaw(packetplayinflying.yRot);
                                        to.setPitch(packetplayinflying.xRot);
                                    }

                                    // Prevent 40 event-calls for less than a single pixel of movement >.>
                                    double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                                    float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                                    if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                                        this.lastPosX = to.getX();
                                        this.lastPosY = to.getY();
                                        this.lastPosZ = to.getZ();
                                        this.lastYaw = to.getYaw();
                                        this.lastPitch = to.getPitch();

                                        Location oldTo = to.clone();
                                        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                                        this.cserver.getPluginManager().callEvent(event);

                                        // If the event is cancelled we move the player back to their old location.
                                        if (event.isCancelled()) {
                                            teleport(from);
                                            return;
                                        }

                                        // If a Plugin has changed the To destination then we teleport the Player
                                        // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                                        // We only do this if the Event was not cancelled.
                                        if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                                            this.player.getBukkitEntity().teleport(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                                            return;
                                        }

                                        // Check to see if the Players Location has some how changed during the call of the event.
                                        // This can happen due to a plugin teleporting the player instead of using .setTo()
                                        if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                                            this.justTeleported = false;
                                            return;
                                        }
                                    }
                                    // CraftBukkit end
                                    this.player.absSnapTo(d0, d1, d2, f, f1);
                                    boolean flag4 = this.player.isAutoSpinAttack();

                                    this.clientIsFloating = d11 >= -0.03125D && !flag2 && !this.player.isSpectator() && !this.server.isFlightAllowed() && !this.player.getAbilities().mayfly && !this.player.hasEffect(MobEffects.LEVITATION) && !flag && !flag4 && this.noBlocksAround(this.player);
                                    this.player.level().getChunkSource().move(this.player);
                                    Vec3D vec3d = new Vec3D(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);

                                    this.player.setOnGroundWithMovement(packetplayinflying.isOnGround(), packetplayinflying.horizontalCollision(), vec3d);
                                    this.player.doCheckFallDamage(vec3d.x, vec3d.y, vec3d.z, packetplayinflying.isOnGround());
                                    this.handlePlayerKnownMovement(vec3d);
                                    if (flag1) {
                                        this.player.resetFallDistance();
                                    }

                                    if (packetplayinflying.isOnGround() || this.player.hasLandedInLiquid() || this.player.onClimbable() || this.player.isSpectator() || flag || flag4) {
                                        this.player.tryResetCurrentImpulseContext();
                                    }

                                    this.player.checkMovementStatistics(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);
                                    this.lastGoodX = this.player.getX();
                                    this.lastGoodY = this.player.getY();
                                    this.lastGoodZ = this.player.getZ();
                                } else {
                                    this.internalTeleport(d3, d4, d5, f, f1); // CraftBukkit - SPIGOT-1807: Don't call teleport event, when the client thinks the player is falling, because the chunks are not loaded on the client yet.
                                    this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, packetplayinflying.isOnGround());
                                    this.player.removeLatestMovementRecording();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean shouldCheckPlayerMovement(boolean flag) {
        if (this.isSingleplayerOwner()) {
            return false;
        } else if (this.player.isChangingDimension()) {
            return false;
        } else {
            GameRules gamerules = this.player.level().getGameRules();

            return gamerules.getBoolean(GameRules.RULE_DISABLE_PLAYER_MOVEMENT_CHECK) ? false : !flag || !gamerules.getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK);
        }
    }

    private boolean updateAwaitingTeleport() {
        if (this.awaitingPositionFromClient != null) {
            if (this.tickCount - this.awaitingTeleportTime > 20) {
                this.awaitingTeleportTime = this.tickCount;
                this.teleport(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
            }
            this.allowedPlayerTicks = 20; // CraftBukkit

            return true;
        } else {
            this.awaitingTeleportTime = this.tickCount;
            return false;
        }
    }

    private boolean isEntityCollidingWithAnythingNew(IWorldReader iworldreader, Entity entity, AxisAlignedBB axisalignedbb, double d0, double d1, double d2) {
        AxisAlignedBB axisalignedbb1 = entity.getBoundingBox().move(d0 - entity.getX(), d1 - entity.getY(), d2 - entity.getZ());
        Iterable<VoxelShape> iterable = iworldreader.getPreMoveCollisions(entity, axisalignedbb1.deflate((double) 1.0E-5F), axisalignedbb.getBottomCenter());
        VoxelShape voxelshape = VoxelShapes.create(axisalignedbb.deflate((double) 1.0E-5F));

        for (VoxelShape voxelshape1 : iterable) {
            if (!VoxelShapes.joinIsNotEmpty(voxelshape1, voxelshape, OperatorBoolean.AND)) {
                return true;
            }
        }

        return false;
    }

    public void teleport(double d0, double d1, double d2, float f, float f1) {
        // CraftBukkit start - Delegate to teleport(Location)
        this.teleport(d0, d1, d2, f, f1, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public boolean teleport(double d0, double d1, double d2, float f, float f1, PlayerTeleportEvent.TeleportCause cause) {
        return this.teleport(new PositionMoveRotation(new Vec3D(d0, d1, d2), Vec3D.ZERO, f, f1), Collections.emptySet(), cause);
        // CraftBukkit end
    }

    public void teleport(PositionMoveRotation positionmoverotation, Set<Relative> set) {
        // CraftBukkit start
        this.teleport(positionmoverotation, set, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public boolean teleport(PositionMoveRotation positionmoverotation, Set<Relative> set, PlayerTeleportEvent.TeleportCause cause) { // CraftBukkit - Return event status
        Player player = this.getCraftPlayer();
        Location from = player.getLocation();
        PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(this.player), positionmoverotation, set);
        Location to = CraftLocation.toBukkit(absolutePosition.position(), this.getCraftPlayer().getWorld(), absolutePosition.yRot(), absolutePosition.xRot());
        // SPIGOT-5171: Triggered on join
        if (from.equals(to)) {
            this.internalTeleport(positionmoverotation, set);
            return true; // CraftBukkit - Return event status
        }

        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from.clone(), to.clone(), cause);
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled() || !to.equals(event.getTo())) {
            set = Collections.emptySet(); // Can't relative teleport
            to = event.isCancelled() ? event.getFrom() : event.getTo();
            positionmoverotation = new PositionMoveRotation(CraftLocation.toVec3D(to), Vec3D.ZERO, to.getYaw(), to.getPitch());
        }

        this.internalTeleport(positionmoverotation, set);
        return !event.isCancelled(); // CraftBukkit - Return event status
    }

    public void teleport(Location dest) {
        this.internalTeleport(dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch());
    }

    private void internalTeleport(double d0, double d1, double d2, float f, float f1) {
        this.internalTeleport(new PositionMoveRotation(new Vec3D(d0, d1, d2), Vec3D.ZERO, f, f1), Collections.emptySet());
    }

    public void internalTeleport(PositionMoveRotation positionmoverotation, Set<Relative> set) {
        if (Float.isNaN(positionmoverotation.yRot())) {
            positionmoverotation = new PositionMoveRotation(positionmoverotation.position(), positionmoverotation.deltaMovement(), 0, positionmoverotation.xRot());
        }
        if (Float.isNaN(positionmoverotation.xRot())) {
            positionmoverotation = new PositionMoveRotation(positionmoverotation.position(), positionmoverotation.deltaMovement(), positionmoverotation.yRot(), 0);
        }

        this.justTeleported = true;
        // CraftBukkit end
        this.awaitingTeleportTime = this.tickCount;
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }

        this.player.teleportSetPosition(positionmoverotation, set);
        this.awaitingPositionFromClient = this.player.position();
        // CraftBukkit start - update last location
        this.lastPosX = this.awaitingPositionFromClient.x;
        this.lastPosY = this.awaitingPositionFromClient.y;
        this.lastPosZ = this.awaitingPositionFromClient.z;
        this.lastYaw = this.player.getYRot();
        this.lastPitch = this.player.getXRot();
        // CraftBukkit end
        this.send(PacketPlayOutPosition.of(this.awaitingTeleport, positionmoverotation, set));
    }

    @Override
    public void handlePlayerAction(PacketPlayInBlockDig packetplayinblockdig) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinblockdig, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (this.player.hasClientLoaded()) {
            BlockPosition blockposition = packetplayinblockdig.getPos();

            this.player.resetLastActionTime();
            PacketPlayInBlockDig.EnumPlayerDigType packetplayinblockdig_enumplayerdigtype = packetplayinblockdig.getAction();

            switch (packetplayinblockdig_enumplayerdigtype) {
                case SWAP_ITEM_WITH_OFFHAND:
                    if (!this.player.isSpectator()) {
                        ItemStack itemstack = this.player.getItemInHand(EnumHand.OFF_HAND);

                        // CraftBukkit start - inspiration taken from DispenserRegistry (See SpigotCraft#394)
                        CraftItemStack mainHand = CraftItemStack.asCraftMirror(itemstack);
                        CraftItemStack offHand = CraftItemStack.asCraftMirror(this.player.getItemInHand(EnumHand.MAIN_HAND));
                        PlayerSwapHandItemsEvent swapItemsEvent = new PlayerSwapHandItemsEvent(getCraftPlayer(), mainHand.clone(), offHand.clone());
                        this.cserver.getPluginManager().callEvent(swapItemsEvent);
                        if (swapItemsEvent.isCancelled()) {
                            return;
                        }
                        if (swapItemsEvent.getOffHandItem().equals(offHand)) {
                            this.player.setItemInHand(EnumHand.OFF_HAND, this.player.getItemInHand(EnumHand.MAIN_HAND));
                        } else {
                            this.player.setItemInHand(EnumHand.OFF_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getOffHandItem()));
                        }
                        if (swapItemsEvent.getMainHandItem().equals(mainHand)) {
                            this.player.setItemInHand(EnumHand.MAIN_HAND, itemstack);
                        } else {
                            this.player.setItemInHand(EnumHand.MAIN_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getMainHandItem()));
                        }
                        // CraftBukkit end
                        this.player.stopUsingItem();
                    }

                    return;
                case DROP_ITEM:
                    if (!this.player.isSpectator()) {
                        // limit how quickly items can be dropped
                        // If the ticks aren't the same then the count starts from 0 and we update the lastDropTick.
                        if (this.lastDropTick != MinecraftServer.currentTick) {
                            this.dropCount = 0;
                            this.lastDropTick = MinecraftServer.currentTick;
                        } else {
                            // Else we increment the drop count and check the amount.
                            this.dropCount++;
                            if (this.dropCount >= 20) {
                                LOGGER.warn(this.player.getScoreboardName() + " dropped their items too quickly!");
                                this.disconnect(IChatBaseComponent.literal("You dropped your items too quickly (Hacking?)"));
                                return;
                            }
                        }
                        // CraftBukkit end
                        this.player.drop(false);
                    }

                    return;
                case DROP_ALL_ITEMS:
                    if (!this.player.isSpectator()) {
                        this.player.drop(true);
                    }

                    return;
                case RELEASE_USE_ITEM:
                    this.player.releaseUsingItem();
                    return;
                case START_DESTROY_BLOCK:
                case ABORT_DESTROY_BLOCK:
                case STOP_DESTROY_BLOCK:
                    this.player.gameMode.handleBlockBreakAction(blockposition, packetplayinblockdig_enumplayerdigtype, packetplayinblockdig.getDirection(), this.player.level().getMaxY(), packetplayinblockdig.getSequence());
                    this.ackBlockChangesUpTo(packetplayinblockdig.getSequence());
                    return;
                default:
                    throw new IllegalArgumentException("Invalid player action");
            }
        }
    }

    private static boolean wasBlockPlacementAttempt(EntityPlayer entityplayer, ItemStack itemstack) {
        if (itemstack.isEmpty()) {
            return false;
        } else {
            Item item = itemstack.getItem();

            return (item instanceof ItemBlock || item instanceof ItemBucket) && !entityplayer.getCooldowns().isOnCooldown(itemstack);
        }
    }

    // Spigot start - limit place/interactions
    private int limitedPackets;
    private long lastLimitedPacket = -1;

    private boolean checkLimit(long timestamp) {
        if (lastLimitedPacket != -1 && timestamp - lastLimitedPacket < 30 && limitedPackets++ >= 4) {
            return false;
        }

        if (lastLimitedPacket == -1 || timestamp - lastLimitedPacket >= 30) {
            lastLimitedPacket = timestamp;
            limitedPackets = 0;
            return true;
        }

        return true;
    }
    // Spigot end

    @Override
    public void handleUseItemOn(PacketPlayInUseItem packetplayinuseitem) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinuseitem, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!checkLimit(packetplayinuseitem.timestamp)) return; // Spigot - check limit
        if (this.player.hasClientLoaded()) {
            this.ackBlockChangesUpTo(packetplayinuseitem.getSequence());
            WorldServer worldserver = this.player.level();
            EnumHand enumhand = packetplayinuseitem.getHand();
            ItemStack itemstack = this.player.getItemInHand(enumhand);

            if (itemstack.isItemEnabled(worldserver.enabledFeatures())) {
                MovingObjectPositionBlock movingobjectpositionblock = packetplayinuseitem.getHitResult();
                Vec3D vec3d = movingobjectpositionblock.getLocation();
                BlockPosition blockposition = movingobjectpositionblock.getBlockPos();

                if (this.player.canInteractWithBlock(blockposition, 1.0D)) {
                    Vec3D vec3d1 = vec3d.subtract(Vec3D.atCenterOf(blockposition));
                    double d0 = 1.0000001D;

                    if (Math.abs(vec3d1.x()) < 1.0000001D && Math.abs(vec3d1.y()) < 1.0000001D && Math.abs(vec3d1.z()) < 1.0000001D) {
                        EnumDirection enumdirection = movingobjectpositionblock.getDirection();

                        this.player.resetLastActionTime();
                        int i = this.player.level().getMaxY();

                        if (blockposition.getY() <= i) {
                            if (this.awaitingPositionFromClient == null && worldserver.mayInteract(this.player, blockposition)) {
                                this.player.stopUsingItem(); // CraftBukkit - SPIGOT-4706
                                EnumInteractionResult enuminteractionresult = this.player.gameMode.useItemOn(this.player, worldserver, itemstack, enumhand, movingobjectpositionblock);

                                if (enuminteractionresult.consumesAction()) {
                                    CriterionTriggers.ANY_BLOCK_USE.trigger(this.player, movingobjectpositionblock.getBlockPos(), itemstack.copy());
                                }

                                if (enumdirection == EnumDirection.UP && !enuminteractionresult.consumesAction() && blockposition.getY() >= i && wasBlockPlacementAttempt(this.player, itemstack)) {
                                    IChatBaseComponent ichatbasecomponent = IChatBaseComponent.translatable("build.tooHigh", i).withStyle(EnumChatFormat.RED);

                                    this.player.sendSystemMessage(ichatbasecomponent, true);
                                } else if (enuminteractionresult instanceof EnumInteractionResult.d) {
                                    EnumInteractionResult.d enuminteractionresult_d = (EnumInteractionResult.d) enuminteractionresult;

                                    if (enuminteractionresult_d.swingSource() == EnumInteractionResult.e.SERVER) {
                                        this.player.swing(enumhand, true);
                                    }
                                }
                            }
                        } else {
                            IChatBaseComponent ichatbasecomponent1 = IChatBaseComponent.translatable("build.tooHigh", i).withStyle(EnumChatFormat.RED);

                            this.player.sendSystemMessage(ichatbasecomponent1, true);
                        }

                        this.send(new PacketPlayOutBlockChange(worldserver, blockposition));
                        this.send(new PacketPlayOutBlockChange(worldserver, blockposition.relative(enumdirection)));
                    } else {
                        PlayerConnection.LOGGER.warn("Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.", new Object[]{this.player.getGameProfile().getName(), vec3d, blockposition});
                    }
                }
            }
        }
    }

    @Override
    public void handleUseItem(PacketPlayInBlockPlace packetplayinblockplace) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinblockplace, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!checkLimit(packetplayinblockplace.timestamp)) return; // Spigot - check limit
        if (this.player.hasClientLoaded()) {
            this.ackBlockChangesUpTo(packetplayinblockplace.getSequence());
            WorldServer worldserver = this.player.level();
            EnumHand enumhand = packetplayinblockplace.getHand();
            ItemStack itemstack = this.player.getItemInHand(enumhand);

            this.player.resetLastActionTime();
            if (!itemstack.isEmpty() && itemstack.isItemEnabled(worldserver.enabledFeatures())) {
                float f = MathHelper.wrapDegrees(packetplayinblockplace.getYRot());
                float f1 = MathHelper.wrapDegrees(packetplayinblockplace.getXRot());

                if (f1 != this.player.getXRot() || f != this.player.getYRot()) {
                    this.player.absSnapRotationTo(f, f1);
                }

                // CraftBukkit start
                // Raytrace to look for 'rogue armswings'
                double d0 = this.player.getX();
                double d1 = this.player.getY() + (double) this.player.getEyeHeight();
                double d2 = this.player.getZ();
                Vec3D vec3d = new Vec3D(d0, d1, d2);

                float f3 = MathHelper.cos(-f * 0.017453292F - 3.1415927F);
                float f4 = MathHelper.sin(-f * 0.017453292F - 3.1415927F);
                float f5 = -MathHelper.cos(-f1 * 0.017453292F);
                float f6 = MathHelper.sin(-f1 * 0.017453292F);
                float f7 = f4 * f5;
                float f8 = f3 * f5;
                double d3 = player.blockInteractionRange();
                Vec3D vec3d1 = vec3d.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
                MovingObjectPosition movingobjectposition = this.player.level().clip(new RayTrace(vec3d, vec3d1, RayTrace.BlockCollisionOption.OUTLINE, RayTrace.FluidCollisionOption.NONE, player));

                boolean cancelled;
                if (movingobjectposition == null || movingobjectposition.getType() != MovingObjectPosition.EnumMovingObjectType.BLOCK) {
                    org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_AIR, itemstack, enumhand);
                    cancelled = event.useItemInHand() == Event.Result.DENY;
                } else {
                    MovingObjectPositionBlock movingobjectpositionblock = (MovingObjectPositionBlock) movingobjectposition;
                    if (player.gameMode.firedInteract && player.gameMode.interactPosition.equals(movingobjectpositionblock.getBlockPos()) && player.gameMode.interactHand == enumhand && ItemStack.isSameItemSameComponents(player.gameMode.interactItemStack, itemstack)) {
                        cancelled = player.gameMode.interactResult;
                    } else {
                        org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), itemstack, true, enumhand, movingobjectpositionblock.getLocation());
                        cancelled = event.useItemInHand() == Event.Result.DENY;
                    }
                    player.gameMode.firedInteract = false;
                }

                if (cancelled) {
                    this.player.getBukkitEntity().updateInventory(); // SPIGOT-2524
                    return;
                }
                itemstack = this.player.getItemInHand(enumhand); // Update in case it was changed in the event
                if (itemstack.isEmpty()) {
                    return;
                }
                // CraftBukkit end
                EnumInteractionResult enuminteractionresult = this.player.gameMode.useItem(this.player, worldserver, itemstack, enumhand);

                if (enuminteractionresult instanceof EnumInteractionResult.d) {
                    EnumInteractionResult.d enuminteractionresult_d = (EnumInteractionResult.d) enuminteractionresult;

                    if (enuminteractionresult_d.swingSource() == EnumInteractionResult.e.SERVER) {
                        this.player.swing(enumhand, true);
                    }
                }

            }
        }
    }

    @Override
    public void handleTeleportToEntityPacket(PacketPlayInSpectate packetplayinspectate) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinspectate, this, this.player.level());
        if (this.player.isSpectator()) {
            for (WorldServer worldserver : this.server.getAllLevels()) {
                Entity entity = packetplayinspectate.getEntity(worldserver);

                if (entity != null) {
                    this.player.teleportTo(worldserver, entity.getX(), entity.getY(), entity.getZ(), Set.of(), entity.getYRot(), entity.getXRot(), true, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE); // CraftBukkit
                    return;
                }
            }
        }

    }

    @Override
    public void handlePaddleBoat(PacketPlayInBoatMove packetplayinboatmove) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinboatmove, this, this.player.level());
        Entity entity = this.player.getControlledVehicle();

        if (entity instanceof AbstractBoat abstractboat) {
            abstractboat.setPaddleState(packetplayinboatmove.getLeft(), packetplayinboatmove.getRight());
        }

    }

    @Override
    public void onDisconnect(DisconnectionDetails disconnectiondetails) {
        // CraftBukkit start - Rarely it would send a disconnect line twice
        if (this.processedDisconnect) {
            return;
        } else {
            this.processedDisconnect = true;
        }
        // CraftBukkit end
        PlayerConnection.LOGGER.info("{} lost connection: {}", this.player.getName().getString(), disconnectiondetails.reason().getString());
        this.removePlayerFromWorld();
        super.onDisconnect(disconnectiondetails);
    }

    private void removePlayerFromWorld() {
        this.chatMessageChain.close();
        // CraftBukkit start - Replace vanilla quit message handling with our own.
        /*
        this.server.invalidateStatus();
        this.server.getPlayerList().broadcastSystemMessage(IChatBaseComponent.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(EnumChatFormat.YELLOW), false);
        */

        this.player.disconnect();
        String quitMessage = this.server.getPlayerList().remove(this.player);
        if ((quitMessage != null) && (quitMessage.length() > 0)) {
            this.server.getPlayerList().broadcastMessage(CraftChatMessage.fromString(quitMessage));
        }
        // CraftBukkit end
        this.player.getTextFilter().leave();
    }

    public void ackBlockChangesUpTo(int i) {
        if (i < 0) {
            throw new IllegalArgumentException("Expected packet sequence nr >= 0");
        } else {
            this.ackBlockChangesUpTo = Math.max(i, this.ackBlockChangesUpTo);
        }
    }

    @Override
    public void handleSetCarriedItem(PacketPlayInHeldItemSlot packetplayinhelditemslot) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinhelditemslot, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (packetplayinhelditemslot.getSlot() >= 0 && packetplayinhelditemslot.getSlot() < PlayerInventory.getSelectionSize()) {
            PlayerItemHeldEvent event = new PlayerItemHeldEvent(this.getCraftPlayer(), this.player.getInventory().getSelectedSlot(), packetplayinhelditemslot.getSlot());
            this.cserver.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                this.send(new PacketPlayOutHeldItemSlot(this.player.getInventory().getSelectedSlot()));
                this.player.resetLastActionTime();
                return;
            }
            // CraftBukkit end
            if (this.player.getInventory().getSelectedSlot() != packetplayinhelditemslot.getSlot() && this.player.getUsedItemHand() == EnumHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.getInventory().setSelectedSlot(packetplayinhelditemslot.getSlot());
            this.player.resetLastActionTime();
        } else {
            PlayerConnection.LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
            this.disconnect(IChatBaseComponent.literal("Invalid hotbar selection (Hacking?)")); // CraftBukkit
        }
    }

    @Override
    public void handleChat(PacketPlayInChat packetplayinchat) {
        // CraftBukkit start - async chat
        // SPIGOT-3638
        if (this.server.isStopped()) {
            return;
        }
        // CraftBukkit end
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(packetplayinchat.lastSeenMessages());

        if (!optional.isEmpty()) {
            this.tryHandleChat(packetplayinchat.message(), () -> {
                PlayerChatMessage playerchatmessage;

                try {
                    playerchatmessage = this.getSignedMessage(packetplayinchat, (LastSeenMessages) optional.get());
                } catch (SignedMessageChain.a signedmessagechain_a) {
                    this.handleMessageDecodeFailure(signedmessagechain_a);
                    return;
                }

                CompletableFuture<FilteredText> completablefuture = this.filterTextPacket(playerchatmessage.signedContent()).thenApplyAsync(Function.identity(), this.server.chatExecutor); // CraftBukkit - async chat
                IChatBaseComponent ichatbasecomponent = this.server.getChatDecorator().decorate(this.player, playerchatmessage.decoratedContent());

                this.chatMessageChain.append(completablefuture, (filteredtext) -> {
                    PlayerChatMessage playerchatmessage1 = playerchatmessage.withUnsignedContent(ichatbasecomponent).filter(filteredtext.mask());

                    this.broadcastChatMessage(playerchatmessage1);
                });
            }, false); // CraftBukkit - async chat
        }
    }

    @Override
    public void handleChatCommand(ServerboundChatCommandPacket serverboundchatcommandpacket) {
        this.tryHandleChat(serverboundchatcommandpacket.command(), () -> {
            // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
            if (player.hasDisconnected()) {
                return;
            }
            // CraftBukkit end
            this.performUnsignedChatCommand(serverboundchatcommandpacket.command());
            this.detectRateSpam("/" + serverboundchatcommandpacket.command()); // Spigot
        }, true); // CraftBukkit - sync commands
    }

    private void performUnsignedChatCommand(String s) {
        // CraftBukkit start
        String command = "/" + s;
        PlayerConnection.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + command);

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(getCraftPlayer(), command, new LazyPlayerSet(server));
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }
        s = event.getMessage().substring(1);
        // CraftBukkit end
        ParseResults<CommandListenerWrapper> parseresults = this.parseCommand(s);

        if (this.server.enforceSecureProfile() && SignableCommand.hasSignableArguments(parseresults)) {
            PlayerConnection.LOGGER.error("Received unsigned command packet from {}, but the command requires signable arguments: {}", this.player.getGameProfile().getName(), s);
            this.player.sendSystemMessage(PlayerConnection.INVALID_COMMAND_SIGNATURE);
        } else {
            this.server.getCommands().performCommand(parseresults, s);
        }
    }

    @Override
    public void handleSignedChatCommand(ServerboundChatCommandSignedPacket serverboundchatcommandsignedpacket) {
        Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(serverboundchatcommandsignedpacket.lastSeenMessages());

        if (!optional.isEmpty()) {
            this.tryHandleChat(serverboundchatcommandsignedpacket.command(), () -> {
                // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
                if (player.hasDisconnected()) {
                    return;
                }
                // CraftBukkit end
                this.performSignedChatCommand(serverboundchatcommandsignedpacket, (LastSeenMessages) optional.get());
                this.detectRateSpam("/" + serverboundchatcommandsignedpacket.command()); // Spigot
            }, true); // CraftBukkit - sync commands
        }
    }

    private void performSignedChatCommand(ServerboundChatCommandSignedPacket serverboundchatcommandsignedpacket, LastSeenMessages lastseenmessages) {
        // CraftBukkit start
        String command = "/" + serverboundchatcommandsignedpacket.command();
        PlayerConnection.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + command);

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(getCraftPlayer(), command, new LazyPlayerSet(server));
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }
        command = event.getMessage().substring(1);

        ParseResults<CommandListenerWrapper> parseresults = this.parseCommand(command);
        // CraftBukkit end

        Map<String, PlayerChatMessage> map;

        try {
            map = (serverboundchatcommandsignedpacket.command().equals(command)) ? this.collectSignedArguments(serverboundchatcommandsignedpacket, SignableCommand.of(parseresults), lastseenmessages) : Collections.emptyMap(); // CraftBukkit
        } catch (SignedMessageChain.a signedmessagechain_a) {
            this.handleMessageDecodeFailure(signedmessagechain_a);
            return;
        }

        CommandSigningContext commandsigningcontext = new CommandSigningContext.a(map);

        parseresults = net.minecraft.commands.CommandDispatcher.<CommandListenerWrapper>mapSource(parseresults, (commandlistenerwrapper) -> {
            return commandlistenerwrapper.withSigningContext(commandsigningcontext, this.chatMessageChain);
        });
        this.server.getCommands().performCommand(parseresults, command); // CraftBukkit
    }

    private void handleMessageDecodeFailure(SignedMessageChain.a signedmessagechain_a) {
        PlayerConnection.LOGGER.warn("Failed to update secure chat state for {}: '{}'", this.player.getGameProfile().getName(), signedmessagechain_a.getComponent().getString());
        this.player.sendSystemMessage(signedmessagechain_a.getComponent().copy().withStyle(EnumChatFormat.RED));
    }

    private <S> Map<String, PlayerChatMessage> collectSignedArguments(ServerboundChatCommandSignedPacket serverboundchatcommandsignedpacket, SignableCommand<S> signablecommand, LastSeenMessages lastseenmessages) throws SignedMessageChain.a {
        List<ArgumentSignatures.a> list = serverboundchatcommandsignedpacket.argumentSignatures().entries();
        List<SignableCommand.a<S>> list1 = signablecommand.arguments();

        if (list.isEmpty()) {
            return this.collectUnsignedArguments(list1);
        } else {
            Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap();

            for (ArgumentSignatures.a argumentsignatures_a : list) {
                SignableCommand.a<S> signablecommand_a = signablecommand.getArgument(argumentsignatures_a.name());

                if (signablecommand_a == null) {
                    this.signedMessageDecoder.setChainBroken();
                    throw createSignedArgumentMismatchException(serverboundchatcommandsignedpacket.command(), list, list1);
                }

                SignedMessageBody signedmessagebody = new SignedMessageBody(signablecommand_a.value(), serverboundchatcommandsignedpacket.timeStamp(), serverboundchatcommandsignedpacket.salt(), lastseenmessages);

                map.put(signablecommand_a.name(), this.signedMessageDecoder.unpack(argumentsignatures_a.signature(), signedmessagebody));
            }

            for (SignableCommand.a<S> signablecommand_a1 : list1) {
                if (!map.containsKey(signablecommand_a1.name())) {
                    throw createSignedArgumentMismatchException(serverboundchatcommandsignedpacket.command(), list, list1);
                }
            }

            return map;
        }
    }

    private <S> Map<String, PlayerChatMessage> collectUnsignedArguments(List<SignableCommand.a<S>> list) throws SignedMessageChain.a {
        Map<String, PlayerChatMessage> map = new HashMap();

        for (SignableCommand.a<S> signablecommand_a : list) {
            SignedMessageBody signedmessagebody = SignedMessageBody.unsigned(signablecommand_a.value());

            map.put(signablecommand_a.name(), this.signedMessageDecoder.unpack((MessageSignature) null, signedmessagebody));
        }

        return map;
    }

    private static <S> SignedMessageChain.a createSignedArgumentMismatchException(String s, List<ArgumentSignatures.a> list, List<SignableCommand.a<S>> list1) {
        String s1 = (String) list.stream().map(ArgumentSignatures.a::name).collect(Collectors.joining(", "));
        String s2 = (String) list1.stream().map(SignableCommand.a::name).collect(Collectors.joining(", "));

        PlayerConnection.LOGGER.error("Signed command mismatch between server and client ('{}'): got [{}] from client, but expected [{}]", new Object[]{s, s1, s2});
        return new SignedMessageChain.a(PlayerConnection.INVALID_COMMAND_SIGNATURE);
    }

    private ParseResults<CommandListenerWrapper> parseCommand(String s) {
        CommandDispatcher<CommandListenerWrapper> commanddispatcher = this.server.getCommands().getDispatcher();

        return commanddispatcher.parse(s, this.player.createCommandSourceStack());
    }

    private void tryHandleChat(String s, Runnable runnable, boolean sync) { // CraftBukkit
        if (isChatMessageIllegal(s)) {
            this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.illegal_characters"));
        } else if (this.player.isRemoved() || this.player.getChatVisibility() == EnumChatVisibility.HIDDEN) { // CraftBukkit - dead men tell no tales
            this.send(new ClientboundSystemChatPacket(IChatBaseComponent.translatable("chat.disabled.options").withStyle(EnumChatFormat.RED), false));
        } else {
            this.player.resetLastActionTime();
            // CraftBukkit start
            if (sync) {
                this.server.execute(runnable);
            } else {
                runnable.run();
            }
            // CraftBukkit end
        }
    }

    private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.b lastseenmessages_b) {
        synchronized (this.lastSeenMessages) {
            Optional optional;

            try {
                LastSeenMessages lastseenmessages = this.lastSeenMessages.applyUpdate(lastseenmessages_b);

                optional = Optional.of(lastseenmessages);
            } catch (LastSeenMessagesValidator.a lastseenmessagesvalidator_a) {
                PlayerConnection.LOGGER.error("Failed to validate message acknowledgements from {}: {}", this.player.getName().getString(), lastseenmessagesvalidator_a.getMessage());
                this.disconnect(PlayerConnection.CHAT_VALIDATION_FAILED);
                return Optional.empty();
            }

            return optional;
        }
    }

    private static boolean isChatMessageIllegal(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (!UtilColor.isAllowedChatCharacter(s.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start - add method
    public void chat(String s, PlayerChatMessage original, boolean async) {
        if (s.isEmpty() || this.player.getChatVisibility() == EnumChatVisibility.HIDDEN) {
            return;
        }
        OutgoingChatMessage outgoing = OutgoingChatMessage.create(original);

        if (!async && s.startsWith("/")) {
            this.handleCommand(s);
        } else if (this.player.getChatVisibility() == EnumChatVisibility.SYSTEM) {
            // Do nothing, this is coming from a plugin
        } else {
            Player player = this.getCraftPlayer();
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(async, player, s, new LazyPlayerSet(server));
            String originalFormat = event.getFormat(), originalMessage = event.getMessage();
            this.cserver.getPluginManager().callEvent(event);

            if (PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) {
                // Evil plugins still listening to deprecated event
                final PlayerChatEvent queueEvent = new PlayerChatEvent(player, event.getMessage(), event.getFormat(), event.getRecipients());
                queueEvent.setCancelled(event.isCancelled());
                Waitable waitable = new Waitable() {
                    @Override
                    protected Object evaluate() {
                        org.bukkit.Bukkit.getPluginManager().callEvent(queueEvent);

                        if (queueEvent.isCancelled()) {
                            return null;
                        }

                        String message = String.format(queueEvent.getFormat(), queueEvent.getPlayer().getDisplayName(), queueEvent.getMessage());
                        if (((LazyPlayerSet) queueEvent.getRecipients()).isLazy()) {
                            if (!org.mospigot.config.MoSpigotConfig.bungee && originalFormat.equals(queueEvent.getFormat()) && originalMessage.equals(queueEvent.getMessage()) && queueEvent.getPlayer().getName().equalsIgnoreCase(queueEvent.getPlayer().getDisplayName())) { // Spigot
                                PlayerConnection.this.server.getPlayerList().broadcastChatMessage(original, PlayerConnection.this.player, ChatMessageType.bind(ChatMessageType.CHAT, (Entity) PlayerConnection.this.player));
                                return null;
                            }

                            for (EntityPlayer recipient : server.getPlayerList().players) {
                                recipient.getBukkitEntity().sendMessage(PlayerConnection.this.player.getUUID(), message);
                            }
                        } else {
                            for (Player player : queueEvent.getRecipients()) {
                                player.sendMessage(PlayerConnection.this.player.getUUID(), message);
                            }
                        }
                        PlayerConnection.this.server.console.sendMessage(message);

                        return null;
                    }};
                if (async) {
                    server.processQueue.add(waitable);
                } else {
                    waitable.run();
                }
                try {
                    waitable.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // This is proper habit for java. If we aren't handling it, pass it on!
                } catch (ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
            } else {
                if (event.isCancelled()) {
                    return;
                }

                s = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());
                if (((LazyPlayerSet) event.getRecipients()).isLazy()) {
                    if (!org.mospigot.config.MoSpigotConfig.bungee && originalFormat.equals(event.getFormat()) && originalMessage.equals(event.getMessage()) && event.getPlayer().getName().equalsIgnoreCase(event.getPlayer().getDisplayName())) { // Spigot
                        PlayerConnection.this.server.getPlayerList().broadcastChatMessage(original, PlayerConnection.this.player, ChatMessageType.bind(ChatMessageType.CHAT, (Entity) PlayerConnection.this.player));
                        return;
                    }

                    for (EntityPlayer recipient : server.getPlayerList().players) {
                        recipient.getBukkitEntity().sendMessage(PlayerConnection.this.player.getUUID(), s);
                    }
                } else {
                    for (Player recipient : event.getRecipients()) {
                        recipient.sendMessage(PlayerConnection.this.player.getUUID(), s);
                    }
                }
                server.console.sendMessage(s);
            }
        }
    }

    private void handleCommand(String s) {
        org.bukkit.craftbukkit.SpigotTimings.playerCommandTimer.startTiming(); // Spigot
        if ( org.mospigot.config.MoSpigotConfig.logCommands ) // Spigot
        this.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + s);

        CraftPlayer player = this.getCraftPlayer();

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, s, new LazyPlayerSet(server));
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            org.bukkit.craftbukkit.SpigotTimings.playerCommandTimer.stopTiming(); // Spigot
            return;
        }

        try {
            if (this.cserver.dispatchCommand(event.getPlayer(), event.getMessage().substring(1))) {
                return;
            }
        } catch (org.bukkit.command.CommandException ex) {
            player.sendMessage(org.bukkit.ChatColor.RED + "An internal error occurred while attempting to perform this command");
            java.util.logging.Logger.getLogger(PlayerConnection.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            return;
        } finally {
            org.bukkit.craftbukkit.SpigotTimings.playerCommandTimer.stopTiming(); // Spigot
        }
    }
    // CraftBukkit end

    private PlayerChatMessage getSignedMessage(PacketPlayInChat packetplayinchat, LastSeenMessages lastseenmessages) throws SignedMessageChain.a {
        SignedMessageBody signedmessagebody = new SignedMessageBody(packetplayinchat.message(), packetplayinchat.timeStamp(), packetplayinchat.salt(), lastseenmessages);

        return this.signedMessageDecoder.unpack(packetplayinchat.signature(), signedmessagebody);
    }

    private void broadcastChatMessage(PlayerChatMessage playerchatmessage) {
        // CraftBukkit start
        String s = playerchatmessage.signedContent();
        if (s.isEmpty()) {
            LOGGER.warn(this.player.getScoreboardName() + " tried to send an empty message");
        } else if (getCraftPlayer().isConversing()) {
            final String conversationInput = s;
            this.server.processQueue.add(new Runnable() {
                @Override
                public void run() {
                    getCraftPlayer().acceptConversationInput(conversationInput);
                }
            });
        } else if (this.player.getChatVisibility() == EnumChatVisibility.SYSTEM) { // Re-add "Command Only" flag check
            this.send(new ClientboundSystemChatPacket(IChatBaseComponent.translatable("chat.cannotSend").withStyle(EnumChatFormat.RED), false));
        } else {
            this.chat(s, playerchatmessage, true);
        }
        // this.server.getPlayerList().broadcastChatMessage(playerchatmessage, this.player, ChatMessageType.bind(ChatMessageType.CHAT, (Entity) this.player));
        // CraftBukkit end
        this.detectRateSpam(s); // Spigot
    }

    // Spigot start - spam exclusions
    private void detectRateSpam(String s) {
        // CraftBukkit start - replaced with thread safe throttle
        for ( String exclude : org.mospigot.config.MoSpigotConfig.spamExclusions )
        {
            if ( exclude != null && s.startsWith( exclude ) )
            {
                return;
            }
        }
        // Spigot end
        // this.chatSpamThrottler.increment();
        if (!this.chatSpamThrottler.isIncrementAndUnderThreshold() && !this.server.getPlayerList().isOp(this.player.getGameProfile()) && !this.server.isSingleplayerOwner(this.player.getGameProfile())) {
            // CraftBukkit end
            this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("disconnect.spam"));
        }

    }

    @Override
    public void handleChatAck(ServerboundChatAckPacket serverboundchatackpacket) {
        synchronized (this.lastSeenMessages) {
            try {
                this.lastSeenMessages.applyOffset(serverboundchatackpacket.offset());
            } catch (LastSeenMessagesValidator.a lastseenmessagesvalidator_a) {
                PlayerConnection.LOGGER.error("Failed to validate message acknowledgement offset from {}: {}", this.player.getName().getString(), lastseenmessagesvalidator_a.getMessage());
                this.disconnect(PlayerConnection.CHAT_VALIDATION_FAILED);
            }

        }
    }

    @Override
    public void handleAnimate(PacketPlayInArmAnimation packetplayinarmanimation) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinarmanimation, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        // CraftBukkit start - Raytrace to look for 'rogue armswings'
        float f1 = this.player.getXRot();
        float f2 = this.player.getYRot();
        double d0 = this.player.getX();
        double d1 = this.player.getY() + (double) this.player.getEyeHeight();
        double d2 = this.player.getZ();
        Location origin = new Location(this.player.level().getWorld(), d0, d1, d2, f2, f1);

        double d3 = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        // SPIGOT-5607: Only call interact event if no block or entity is being clicked. Use bukkit ray trace method, because it handles blocks and entities at the same time
        // SPIGOT-7429: Make sure to call PlayerInteractEvent for spectators and non-pickable entities
        org.bukkit.util.RayTraceResult result = this.player.level().getWorld().rayTrace(origin, origin.getDirection(), d3, org.bukkit.FluidCollisionMode.NEVER, false, 0.1, entity -> {
            Entity handle = ((CraftEntity) entity).getHandle();
            return entity != this.player.getBukkitEntity() && this.player.getBukkitEntity().canSee(entity) && !handle.isSpectator() && handle.isPickable() && !handle.isPassengerOfSameVehicle(player);
        });
        if (result == null) {
            CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.getInventory().getSelectedItem(), EnumHand.MAIN_HAND);
        } else if (this.player.gameMode.getGameModeForPlayer() == EnumGamemode.ADVENTURE) {
            Block block = result.getHitBlock();
            if (block != null) {
                Vector hitPosition = result.getHitPosition().subtract(block.getLocation().toVector());
                CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, block, result.getHitBlockFace(), this.player.getInventory().getSelectedItem(), true, EnumHand.MAIN_HAND, hitPosition);
            }
        }

        // Arm swing animation
        PlayerAnimationEvent event = new PlayerAnimationEvent(this.getCraftPlayer(), (packetplayinarmanimation.getHand() == EnumHand.MAIN_HAND) ? PlayerAnimationType.ARM_SWING : PlayerAnimationType.OFF_ARM_SWING);
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) return;
        // CraftBukkit end
        this.player.swing(packetplayinarmanimation.getHand());
    }

    @Override
    public void handlePlayerCommand(PacketPlayInEntityAction packetplayinentityaction) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinentityaction, this, this.player.level());
        if (this.player.hasClientLoaded()) {
            // CraftBukkit start
            if (this.player.isRemoved()) return;
            switch (packetplayinentityaction.getAction()) {
                case START_SPRINTING:
                case STOP_SPRINTING:
                    PlayerToggleSprintEvent e2 = new PlayerToggleSprintEvent(this.getCraftPlayer(), packetplayinentityaction.getAction() == PacketPlayInEntityAction.EnumPlayerAction.START_SPRINTING);
                    this.cserver.getPluginManager().callEvent(e2);

                    if (e2.isCancelled()) {
                        return;
                    }
                    break;
            }
            // CraftBukkit end
            this.player.resetLastActionTime();
            switch (packetplayinentityaction.getAction()) {
                case START_SPRINTING:
                    this.player.setSprinting(true);
                    break;
                case STOP_SPRINTING:
                    this.player.setSprinting(false);
                    break;
                case STOP_SLEEPING:
                    if (this.player.isSleeping()) {
                        this.player.stopSleepInBed(false, true);
                        this.awaitingPositionFromClient = this.player.position();
                    }
                    break;
                case START_RIDING_JUMP:
                    Entity entity = this.player.getControlledVehicle();

                    if (entity instanceof IJumpable) {
                        IJumpable ijumpable = (IJumpable) entity;
                        int i = packetplayinentityaction.getData();

                        if (ijumpable.canJump() && i > 0) {
                            ijumpable.handleStartJump(i);
                        }
                    }
                    break;
                case STOP_RIDING_JUMP:
                    Entity entity1 = this.player.getControlledVehicle();

                    if (entity1 instanceof IJumpable) {
                        IJumpable ijumpable1 = (IJumpable) entity1;

                        ijumpable1.handleStopJump();
                    }
                    break;
                case OPEN_INVENTORY:
                    Entity entity2 = this.player.getVehicle();

                    if (entity2 instanceof HasCustomInventoryScreen) {
                        HasCustomInventoryScreen hascustominventoryscreen = (HasCustomInventoryScreen) entity2;

                        hascustominventoryscreen.openCustomInventoryScreen(this.player);
                    }
                    break;
                case START_FALL_FLYING:
                    if (!this.player.tryToStartFallFlying()) {
                        this.player.stopFallFlying();
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Invalid client command!");
            }

        }
    }

    public void sendPlayerChatMessage(PlayerChatMessage playerchatmessage, ChatMessageType.a chatmessagetype_a) {
        // CraftBukkit start - SPIGOT-7262: if hidden we have to send as disguised message. Query whether we should send at all (but changing this may not be expected).
        if (!getCraftPlayer().canSeePlayer(playerchatmessage.link().sender())) {
            sendDisguisedChatMessage(playerchatmessage.decoratedContent(), chatmessagetype_a);
            return;
        }
        // CraftBukkit end
        this.send(new ClientboundPlayerChatPacket(this.nextChatIndex++, playerchatmessage.link().sender(), playerchatmessage.link().index(), playerchatmessage.signature(), playerchatmessage.signedBody().pack(this.messageSignatureCache), playerchatmessage.unsignedContent(), playerchatmessage.filterMask(), chatmessagetype_a));
        MessageSignature messagesignature = playerchatmessage.signature();

        if (messagesignature != null) {
            this.messageSignatureCache.push(playerchatmessage.signedBody(), playerchatmessage.signature());
            int i;

            synchronized (this.lastSeenMessages) {
                this.lastSeenMessages.addPending(messagesignature);
                i = this.lastSeenMessages.trackedMessagesCount();
            }

            if (i > 4096) {
                this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.too_many_pending_chats"));
            }

        }
    }

    public void sendDisguisedChatMessage(IChatBaseComponent ichatbasecomponent, ChatMessageType.a chatmessagetype_a) {
        this.send(new ClientboundDisguisedChatPacket(ichatbasecomponent, chatmessagetype_a));
    }

    public SocketAddress getRemoteAddress() {
        return this.connection.getRemoteAddress();
    }

    // Spigot Start
    public SocketAddress getRawAddress()
    {
        return this.connection.channel.remoteAddress();
    }
    // Spigot End

    public void switchToConfig() {
        this.waitingForSwitchToConfig = true;
        this.removePlayerFromWorld();
        this.send(ClientboundStartConfigurationPacket.INSTANCE);
        this.connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
    }

    @Override
    public void handlePingRequest(ServerboundPingRequestPacket serverboundpingrequestpacket) {
        this.connection.send(new ClientboundPongResponsePacket(serverboundpingrequestpacket.getTime()));
    }

    @Override
    public void handleInteract(PacketPlayInUseEntity packetplayinuseentity) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinuseentity, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (this.player.hasClientLoaded()) {
            final WorldServer worldserver = this.player.level();
            final Entity entity = packetplayinuseentity.getTarget(worldserver);
            // Spigot Start
            if ( entity == player && !player.isSpectator() )
            {
                disconnect( IChatBaseComponent.literal( "Cannot interact with self!" ) );
                return;
            }
            // Spigot End

            this.player.resetLastActionTime();
            this.player.setShiftKeyDown(packetplayinuseentity.isUsingSecondaryAction());
            if (entity != null) {
                if (!worldserver.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                    return;
                }

                AxisAlignedBB axisalignedbb = entity.getBoundingBox();

                if (this.player.canInteractWithEntity(axisalignedbb, 3.0D)) {
                    packetplayinuseentity.dispatch(new PacketPlayInUseEntity.c() {
                        private void performInteraction(EnumHand enumhand, PlayerConnection.a playerconnection_a, PlayerInteractEntityEvent event) { // CraftBukkit
                            ItemStack itemstack = PlayerConnection.this.player.getItemInHand(enumhand);

                            if (itemstack.isItemEnabled(worldserver.enabledFeatures())) {
                                ItemStack itemstack1 = itemstack.copy();
                                // CraftBukkit start
                                ItemStack itemInHand = PlayerConnection.this.player.getItemInHand(enumhand);
                                boolean triggerLeashUpdate = itemInHand != null && itemInHand.getItem() == Items.LEAD && entity instanceof EntityInsentient;
                                Item origItem = player.getInventory().getSelectedItem() == null ? null : player.getInventory().getSelectedItem().getItem();

                                cserver.getPluginManager().callEvent(event);

                                // Entity in bucket - SPIGOT-4048 and SPIGOT-6859a
                                if ((entity instanceof Bucketable && entity instanceof EntityLiving && origItem != null && origItem.asItem() == Items.WATER_BUCKET) && (event.isCancelled() || player.getInventory().getSelectedItem() == null || player.getInventory().getSelectedItem().getItem() != origItem)) {
                                    entity.getBukkitEntity().update(player);
                                    player.containerMenu.sendAllDataToRemote();
                                }

                                if (triggerLeashUpdate && (event.isCancelled() || player.getInventory().getSelectedItem() == null || player.getInventory().getSelectedItem().getItem() != origItem)) {
                                    // Refresh the current leash state
                                    send(new PacketPlayOutAttachEntity(entity, ((EntityInsentient) entity).getLeashHolder()));
                                }

                                if (event.isCancelled() || player.getInventory().getSelectedItem() == null || player.getInventory().getSelectedItem().getItem() != origItem) {
                                    // Refresh the current entity metadata
                                    entity.refreshEntityData(player);
                                    // SPIGOT-7136 - Allays
                                    if (entity instanceof Allay) {
                                        send(new PacketPlayOutEntityEquipment(entity.getId(), Arrays.stream(EnumItemSlot.values()).map((slot) -> Pair.of(slot, ((EntityLiving) entity).getItemBySlot(slot).copy())).collect(Collectors.toList())));
                                        player.containerMenu.sendAllDataToRemote();
                                    }
                                }

                                if (event.isCancelled()) {
                                    return;
                                }
                                // CraftBukkit end
                                EnumInteractionResult enuminteractionresult = playerconnection_a.run(PlayerConnection.this.player, entity, enumhand);

                                // CraftBukkit start
                                if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                                    player.containerMenu.sendAllDataToRemote();
                                }
                                // CraftBukkit end

                                if (enuminteractionresult instanceof EnumInteractionResult.d) {
                                    EnumInteractionResult.d enuminteractionresult_d = (EnumInteractionResult.d) enuminteractionresult;
                                    ItemStack itemstack2 = enuminteractionresult_d.wasItemInteraction() ? itemstack1 : ItemStack.EMPTY;

                                    CriterionTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(PlayerConnection.this.player, itemstack2, entity);
                                    if (enuminteractionresult_d.swingSource() == EnumInteractionResult.e.SERVER) {
                                        PlayerConnection.this.player.swing(enumhand, true);
                                    }
                                }

                            }
                        }

                        @Override
                        public void onInteraction(EnumHand enumhand) {
                            this.performInteraction(enumhand, EntityHuman::interactOn, new PlayerInteractEntityEvent(getCraftPlayer(), entity.getBukkitEntity(), (enumhand == EnumHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND)); // CraftBukkit
                        }

                        @Override
                        public void onInteraction(EnumHand enumhand, Vec3D vec3d) {
                            this.performInteraction(enumhand, (entityplayer, entity1, enumhand1) -> {
                                return entity1.interactAt(entityplayer, vec3d, enumhand1);
                            }, new PlayerInteractAtEntityEvent(getCraftPlayer(), entity.getBukkitEntity(), new org.bukkit.util.Vector(vec3d.x, vec3d.y, vec3d.z), (enumhand == EnumHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND)); // CraftBukkit
                        }

                        @Override
                        public void onAttack() {
                            // CraftBukkit
                            if (!(entity instanceof EntityItem) && !(entity instanceof EntityExperienceOrb) && (entity != PlayerConnection.this.player || player.isSpectator())) {
                                label29:
                                {
                                    if (entity instanceof EntityArrow) {
                                        EntityArrow entityarrow = (EntityArrow) entity;

                                        if (!entityarrow.isAttackable()) {
                                            break label29;
                                        }
                                    }

                                    ItemStack itemstack = PlayerConnection.this.player.getItemInHand(EnumHand.MAIN_HAND);

                                    if (!itemstack.isItemEnabled(worldserver.enabledFeatures())) {
                                        return;
                                    }

                                    PlayerConnection.this.player.attack(entity);
                                    // CraftBukkit start
                                    if (!itemstack.isEmpty() && itemstack.getCount() <= -1) {
                                        player.containerMenu.sendAllDataToRemote();
                                    }
                                    // CraftBukkit end
                                    return;
                                }
                            }

                            PlayerConnection.this.disconnect((IChatBaseComponent) IChatBaseComponent.translatable("multiplayer.disconnect.invalid_entity_attacked"));
                            PlayerConnection.LOGGER.warn("Player {} tried to attack an invalid entity", PlayerConnection.this.player.getName().getString());
                        }
                    });
                }
            }

        }
    }

    @Override
    public void handleClientCommand(PacketPlayInClientCommand packetplayinclientcommand) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinclientcommand, this, this.player.level());
        this.player.resetLastActionTime();
        PacketPlayInClientCommand.EnumClientCommand packetplayinclientcommand_enumclientcommand = packetplayinclientcommand.getAction();

        switch (packetplayinclientcommand_enumclientcommand) {
            case PERFORM_RESPAWN:
                if (this.player.wonGame) {
                    this.player.wonGame = false;
                    this.player = this.server.getPlayerList().respawn(this.player, true, Entity.RemovalReason.CHANGED_DIMENSION, RespawnReason.END_PORTAL); // CraftBukkit
                    this.resetPosition();
                    CriterionTriggers.CHANGED_DIMENSION.trigger(this.player, World.END, World.OVERWORLD);
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }

                    this.player = this.server.getPlayerList().respawn(this.player, false, Entity.RemovalReason.KILLED, RespawnReason.DEATH); // CraftBukkit
                    this.resetPosition();
                    if (this.server.isHardcore()) {
                        this.player.setGameMode(EnumGamemode.SPECTATOR);
                        ((GameRules.GameRuleBoolean) this.player.level().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS)).set(false, this.player.level()); // CraftBukkit - per-world
                    }
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
        }

    }

    @Override
    public void handleContainerClose(PacketPlayInCloseWindow packetplayinclosewindow) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinclosewindow, this, this.player.level());

        if (this.player.isImmobile()) return; // CraftBukkit
        CraftEventFactory.handleInventoryCloseEvent(this.player); // CraftBukkit

        this.player.doCloseContainer();
    }

    @Override
    public void handleContainerClick(PacketPlayInWindowClick packetplayinwindowclick) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinwindowclick, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packetplayinwindowclick.containerId() && this.player.containerMenu.stillValid(this.player)) { // CraftBukkit
            boolean cancelled = this.player.isSpectator(); // CraftBukkit - see below if
            if (false/*this.player.isSpectator()*/) { // CraftBukkit
                this.player.containerMenu.sendAllDataToRemote();
            } else if (!this.player.containerMenu.stillValid(this.player)) {
                PlayerConnection.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                int i = packetplayinwindowclick.slotNum();

                if (!this.player.containerMenu.isValidSlotIndex(i)) {
                    PlayerConnection.LOGGER.debug("Player {} clicked invalid slot index: {}, available slots: {}", new Object[]{this.player.getName(), i, this.player.containerMenu.slots.size()});
                } else {
                    boolean flag = packetplayinwindowclick.stateId() != this.player.containerMenu.getStateId();

                    this.player.containerMenu.suppressRemoteUpdates();
                    // CraftBukkit start - Call InventoryClickEvent
                    if (packetplayinwindowclick.slotNum() < -1 && packetplayinwindowclick.slotNum() != -999) {
                        return;
                    }

                    InventoryView inventory = this.player.containerMenu.getBukkitView();
                    SlotType type = inventory.getSlotType(packetplayinwindowclick.slotNum());

                    InventoryClickEvent event;
                    ClickType click = ClickType.UNKNOWN;
                    InventoryAction action = InventoryAction.UNKNOWN;

                    ItemStack itemstack = ItemStack.EMPTY;

                    switch (packetplayinwindowclick.clickType()) {
                        case PICKUP:
                            if (packetplayinwindowclick.buttonNum()== 0) {
                                click = ClickType.LEFT;
                            } else if (packetplayinwindowclick.buttonNum() == 1) {
                                click = ClickType.RIGHT;
                            }
                            if (packetplayinwindowclick.buttonNum() == 0 || packetplayinwindowclick.buttonNum() == 1) {
                                action = InventoryAction.NOTHING; // Don't want to repeat ourselves
                                if (packetplayinwindowclick.slotNum() == -999) {
                                    if (!player.containerMenu.getCarried().isEmpty()) {
                                        action = packetplayinwindowclick.buttonNum() == 0 ? InventoryAction.DROP_ALL_CURSOR : InventoryAction.DROP_ONE_CURSOR;
                                    }
                                } else if (packetplayinwindowclick.slotNum() < 0)  {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum());
                                    if (slot != null) {
                                        ItemStack clickedItem = slot.getItem();
                                        ItemStack cursor = player.containerMenu.getCarried();
                                        if (clickedItem.isEmpty()) {
                                            if (!cursor.isEmpty()) {
                                                action = packetplayinwindowclick.buttonNum() == 0 ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_ONE;
                                            }
                                        } else if (slot.mayPickup(player)) {
                                            if (cursor.isEmpty()) {
                                                action = packetplayinwindowclick.buttonNum() == 0 ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_HALF;
                                            } else if (slot.mayPlace(cursor)) {
                                                if (ItemStack.isSameItemSameComponents(clickedItem, cursor)) {
                                                    int toPlace = packetplayinwindowclick.buttonNum() == 0 ? cursor.getCount() : 1;
                                                    toPlace = Math.min(toPlace, clickedItem.getMaxStackSize() - clickedItem.getCount());
                                                    toPlace = Math.min(toPlace, slot.container.getMaxStackSize() - clickedItem.getCount());
                                                    if (toPlace == 1) {
                                                        action = InventoryAction.PLACE_ONE;
                                                    } else if (toPlace == cursor.getCount()) {
                                                        action = InventoryAction.PLACE_ALL;
                                                    } else if (toPlace < 0) {
                                                        action = toPlace != -1 ? InventoryAction.PICKUP_SOME : InventoryAction.PICKUP_ONE; // this happens with oversized stacks
                                                    } else if (toPlace != 0) {
                                                        action = InventoryAction.PLACE_SOME;
                                                    }
                                                } else if (cursor.getCount() <= slot.getMaxStackSize()) {
                                                    action = InventoryAction.SWAP_WITH_CURSOR;
                                                }
                                            } else if (ItemStack.isSameItemSameComponents(cursor, clickedItem)) {
                                                if (clickedItem.getCount() >= 0) {
                                                    if (clickedItem.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                                                        // As of 1.5, this is result slots only
                                                        action = InventoryAction.PICKUP_ALL;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        // TODO check on updates
                        case QUICK_MOVE:
                            if (packetplayinwindowclick.buttonNum() == 0) {
                                click = ClickType.SHIFT_LEFT;
                            } else if (packetplayinwindowclick.buttonNum() == 1) {
                                click = ClickType.SHIFT_RIGHT;
                            }
                            if (packetplayinwindowclick.buttonNum() == 0 || packetplayinwindowclick.buttonNum() == 1) {
                                if (packetplayinwindowclick.slotNum() < 0) {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum());
                                    if (slot != null && slot.mayPickup(this.player) && slot.hasItem()) {
                                        action = InventoryAction.MOVE_TO_OTHER_INVENTORY;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                            }
                            break;
                        case SWAP:
                            if ((packetplayinwindowclick.buttonNum() >= 0 && packetplayinwindowclick.buttonNum() < 9) || packetplayinwindowclick.buttonNum() == 40) {
                                click = (packetplayinwindowclick.buttonNum() == 40) ? ClickType.SWAP_OFFHAND : ClickType.NUMBER_KEY;
                                Slot clickedSlot = this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum());
                                if (clickedSlot.mayPickup(player)) {
                                    ItemStack hotbar = this.player.getInventory().getItem(packetplayinwindowclick.buttonNum());
                                    boolean canCleanSwap = hotbar.isEmpty() || (clickedSlot.container == player.getInventory() && clickedSlot.mayPlace(hotbar)); // the slot will accept the hotbar item
                                    if (clickedSlot.hasItem()) {
                                        if (canCleanSwap) {
                                            action = InventoryAction.HOTBAR_SWAP;
                                        } else {
                                            action = InventoryAction.HOTBAR_MOVE_AND_READD;
                                        }
                                    } else if (!clickedSlot.hasItem() && !hotbar.isEmpty() && clickedSlot.mayPlace(hotbar)) {
                                        action = InventoryAction.HOTBAR_SWAP;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                } else {
                                    action = InventoryAction.NOTHING;
                                }
                            }
                            break;
                        case CLONE:
                            if (packetplayinwindowclick.buttonNum() == 2) {
                                click = ClickType.MIDDLE;
                                if (packetplayinwindowclick.slotNum() < 0) {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum());
                                    if (slot != null && slot.hasItem() && player.getAbilities().instabuild && player.containerMenu.getCarried().isEmpty()) {
                                        action = InventoryAction.CLONE_STACK;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                            } else {
                                click = ClickType.UNKNOWN;
                                action = InventoryAction.UNKNOWN;
                            }
                            break;
                        case THROW:
                            if (packetplayinwindowclick.slotNum() >= 0) {
                                if (packetplayinwindowclick.buttonNum() == 0) {
                                    click = ClickType.DROP;
                                    Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum());
                                    if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                        action = InventoryAction.DROP_ONE_SLOT;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                } else if (packetplayinwindowclick.buttonNum() == 1) {
                                    click = ClickType.CONTROL_DROP;
                                    Slot slot = this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum());
                                    if (slot != null && slot.hasItem() && slot.mayPickup(player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                        action = InventoryAction.DROP_ALL_SLOT;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                            } else {
                                // Sane default (because this happens when they are holding nothing. Don't ask why.)
                                click = ClickType.LEFT;
                                if (packetplayinwindowclick.buttonNum() == 1) {
                                    click = ClickType.RIGHT;
                                }
                                action = InventoryAction.NOTHING;
                            }
                            break;
                        case QUICK_CRAFT:
                            this.player.containerMenu.clicked(packetplayinwindowclick.slotNum(), packetplayinwindowclick.buttonNum(), packetplayinwindowclick.clickType(), this.player);
                            break;
                        case PICKUP_ALL:
                            click = ClickType.DOUBLE_CLICK;
                            action = InventoryAction.NOTHING;
                            if (packetplayinwindowclick.slotNum() >= 0 && !this.player.containerMenu.getCarried().isEmpty()) {
                                ItemStack cursor = this.player.containerMenu.getCarried();
                                action = InventoryAction.NOTHING;
                                // Quick check for if we have any of the item
                                if (inventory.getTopInventory().contains(CraftItemType.minecraftToBukkit(cursor.getItem())) || inventory.getBottomInventory().contains(CraftItemType.minecraftToBukkit(cursor.getItem()))) {
                                    action = InventoryAction.COLLECT_TO_CURSOR;
                                }
                            }
                            break;
                        default:
                            break;
                    }

                    if (packetplayinwindowclick.clickType() != InventoryClickType.QUICK_CRAFT) {
                        if (click == ClickType.NUMBER_KEY) {
                            event = new InventoryClickEvent(inventory, type, packetplayinwindowclick.slotNum(), click, action, packetplayinwindowclick.buttonNum());
                        } else {
                            event = new InventoryClickEvent(inventory, type, packetplayinwindowclick.slotNum(), click, action);
                        }

                        org.bukkit.inventory.Inventory top = inventory.getTopInventory();
                        if (packetplayinwindowclick.slotNum() == 0 && top instanceof CraftingInventory) {
                            org.bukkit.inventory.Recipe recipe = ((CraftingInventory) top).getRecipe();
                            if (recipe != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new CraftItemEvent(recipe, inventory, type, packetplayinwindowclick.slotNum(), click, action, packetplayinwindowclick.buttonNum());
                                } else {
                                    event = new CraftItemEvent(recipe, inventory, type, packetplayinwindowclick.slotNum(), click, action);
                                }
                            }
                        }

                        if (packetplayinwindowclick.slotNum() == 3 && top instanceof SmithingInventory) {
                            org.bukkit.inventory.ItemStack result = ((SmithingInventory) top).getResult();
                            if (result != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new SmithItemEvent(inventory, type, packetplayinwindowclick.slotNum(), click, action, packetplayinwindowclick.buttonNum());
                                } else {
                                    event = new SmithItemEvent(inventory, type, packetplayinwindowclick.slotNum(), click, action);
                                }
                            }
                        }

                        event.setCancelled(cancelled);
                        Container oldContainer = this.player.containerMenu; // SPIGOT-1224
                        cserver.getPluginManager().callEvent(event);
                        if (this.player.containerMenu != oldContainer) {
                            return;
                        }

                        switch (event.getResult()) {
                            case ALLOW:
                            case DEFAULT:
                                this.player.containerMenu.clicked(i, packetplayinwindowclick.buttonNum(), packetplayinwindowclick.clickType(), this.player);
                                break;
                            case DENY:
                                /* Needs enum constructor in InventoryAction
                                if (action.modifiesOtherSlots()) {

                                } else {
                                    if (action.modifiesCursor()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(-1, -1, this.player.inventory.getCarried()));
                                    }
                                    if (action.modifiesClicked()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(this.player.activeContainer.windowId, packet102windowclick.slot, this.player.activeContainer.getSlot(packet102windowclick.slot).getItem()));
                                    }
                                }*/
                                switch (action) {
                                    // Modified other slots
                                    case PICKUP_ALL:
                                    case MOVE_TO_OTHER_INVENTORY:
                                    case HOTBAR_MOVE_AND_READD:
                                    case HOTBAR_SWAP:
                                    case COLLECT_TO_CURSOR:
                                    case UNKNOWN:
                                        this.player.containerMenu.sendAllDataToRemote();
                                        break;
                                    // Modified cursor and clicked
                                    case PICKUP_SOME:
                                    case PICKUP_HALF:
                                    case PICKUP_ONE:
                                    case PLACE_ALL:
                                    case PLACE_SOME:
                                    case PLACE_ONE:
                                    case SWAP_WITH_CURSOR:
                                        this.player.connection.send(new PacketPlayOutSetSlot(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                                        this.player.connection.send(new PacketPlayOutSetSlot(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), packetplayinwindowclick.slotNum(), this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum()).getItem()));
                                        break;
                                    // Modified clicked only
                                    case DROP_ALL_SLOT:
                                    case DROP_ONE_SLOT:
                                        this.player.connection.send(new PacketPlayOutSetSlot(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), packetplayinwindowclick.slotNum(), this.player.containerMenu.getSlot(packetplayinwindowclick.slotNum()).getItem()));
                                        break;
                                    // Modified cursor only
                                    case DROP_ALL_CURSOR:
                                    case DROP_ONE_CURSOR:
                                    case CLONE_STACK:
                                        this.player.connection.send(new PacketPlayOutSetSlot(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                                        break;
                                    // Nothing
                                    case NOTHING:
                                        break;
                                }
                        }

                        if (event instanceof CraftItemEvent || event instanceof SmithItemEvent) {
                            // Need to update the inventory on crafting to
                            // correctly support custom recipes
                            player.containerMenu.sendAllDataToRemote();
                        }
                    }
                    // CraftBukkit end
                    ObjectIterator objectiterator = Int2ObjectMaps.fastIterable(packetplayinwindowclick.changedSlots()).iterator();

                    while (objectiterator.hasNext()) {
                        Int2ObjectMap.Entry<HashedStack> int2objectmap_entry = (Entry) objectiterator.next();

                        this.player.containerMenu.setRemoteSlotUnsafe(int2objectmap_entry.getIntKey(), (HashedStack) int2objectmap_entry.getValue());
                    }

                    this.player.containerMenu.setRemoteCarried(packetplayinwindowclick.carriedItem());
                    this.player.containerMenu.resumeRemoteUpdates();
                    if (flag) {
                        this.player.containerMenu.broadcastFullState();
                    } else {
                        this.player.containerMenu.broadcastChanges();
                    }

                }
            }
        }
    }

    @Override
    public void handlePlaceRecipe(PacketPlayInAutoRecipe packetplayinautorecipe) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinautorecipe, this, this.player.level());
        this.player.resetLastActionTime();
        if (!this.player.isSpectator() && this.player.containerMenu.containerId == packetplayinautorecipe.containerId()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                PlayerConnection.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                CraftingManager.d craftingmanager_d = this.server.getRecipeManager().getRecipeFromDisplay(packetplayinautorecipe.recipe());

                if (craftingmanager_d != null) {
                    RecipeHolder<?> recipeholder = craftingmanager_d.parent();

                    if (this.player.getRecipeBook().contains(recipeholder.id())) {
                        Container container = this.player.containerMenu;

                        if (container instanceof ContainerRecipeBook) {
                            ContainerRecipeBook containerrecipebook = (ContainerRecipeBook) container;

                            if (recipeholder.value().placementInfo().isImpossibleToPlace()) {
                                PlayerConnection.LOGGER.debug("Player {} tried to place impossible recipe {}", this.player, recipeholder.id().location());
                                return;
                            }

                            // CraftBukkit start - implement PlayerRecipeBookClickEvent
                            org.bukkit.inventory.Recipe recipe = recipeholder.toBukkitRecipe();
                            if (recipe == null) {
                                return;
                            }
                            org.bukkit.event.player.PlayerRecipeBookClickEvent event = CraftEventFactory.callRecipeBookClickEvent(this.player, recipe, packetplayinautorecipe.useMaxItems());

                            // Cast to keyed should be safe as the recipe will never be a MerchantRecipe.
                            recipeholder = this.server.getRecipeManager().byKey(CraftRecipe.toMinecraft(((org.bukkit.Keyed) event.getRecipe()).getKey())).orElse(null);
                            if (recipeholder == null) {
                                return;
                            }

                            ContainerRecipeBook.a containerrecipebook_a = containerrecipebook.handlePlacement(event.isShiftClick(), this.player.isCreative(), recipeholder, this.player.level(), this.player.getInventory());
                            // CraftBukkit end

                            if (containerrecipebook_a == ContainerRecipeBook.a.PLACE_GHOST_RECIPE) {
                                this.send(new PacketPlayOutAutoRecipe(this.player.containerMenu.containerId, craftingmanager_d.display().display()));
                            }
                        }

                    }
                }
            }
        }
    }

    @Override
    public void handleContainerButtonClick(PacketPlayInEnchantItem packetplayinenchantitem) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinenchantitem, this, this.player.level());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packetplayinenchantitem.containerId() && !this.player.isSpectator()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                PlayerConnection.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                boolean flag = this.player.containerMenu.clickMenuButton(this.player, packetplayinenchantitem.buttonId());

                if (flag) {
                    this.player.containerMenu.broadcastChanges();
                }

            }
        }
    }

    @Override
    public void handleSetCreativeModeSlot(PacketPlayInSetCreativeSlot packetplayinsetcreativeslot) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinsetcreativeslot, this, this.player.level());
        if (this.player.hasInfiniteMaterials()) {
            boolean flag = packetplayinsetcreativeslot.slotNum() < 0;
            ItemStack itemstack = packetplayinsetcreativeslot.itemStack();

            if (!itemstack.isItemEnabled(this.player.level().enabledFeatures())) {
                return;
            }

            boolean flag1 = packetplayinsetcreativeslot.slotNum() >= 1 && packetplayinsetcreativeslot.slotNum() <= 45;
            boolean flag2 = itemstack.isEmpty() || itemstack.getCount() <= itemstack.getMaxStackSize();
            if (flag || (flag1 && !ItemStack.matches(this.player.inventoryMenu.getSlot(packetplayinsetcreativeslot.slotNum()).getItem(), packetplayinsetcreativeslot.itemStack()))) { // Insist on valid slot
                // CraftBukkit start - Call click event
                InventoryView inventory = this.player.inventoryMenu.getBukkitView();
                org.bukkit.inventory.ItemStack item = CraftItemStack.asBukkitCopy(packetplayinsetcreativeslot.itemStack());

                SlotType type = SlotType.QUICKBAR;
                if (flag) {
                    type = SlotType.OUTSIDE;
                } else if (packetplayinsetcreativeslot.slotNum() < 36) {
                    if (packetplayinsetcreativeslot.slotNum() >= 5 && packetplayinsetcreativeslot.slotNum() < 9) {
                        type = SlotType.ARMOR;
                    } else {
                        type = SlotType.CONTAINER;
                    }
                }
                InventoryCreativeEvent event = new InventoryCreativeEvent(inventory, type, flag ? -999 : packetplayinsetcreativeslot.slotNum(), item);
                cserver.getPluginManager().callEvent(event);

                itemstack = CraftItemStack.asNMSCopy(event.getCursor());

                switch (event.getResult()) {
                case ALLOW:
                    // Plugin cleared the id / stacksize checks
                    flag2 = true;
                    break;
                case DEFAULT:
                    break;
                case DENY:
                    // Reset the slot
                    if (packetplayinsetcreativeslot.slotNum() >= 0) {
                        this.player.inventoryMenu.sendAllDataToRemote();
                    }
                    return;
                }
            }
            // CraftBukkit end

            if (flag1 && flag2) {
                this.player.inventoryMenu.getSlot(packetplayinsetcreativeslot.slotNum()).setByPlayer(itemstack);
                this.player.inventoryMenu.setRemoteSlot(packetplayinsetcreativeslot.slotNum(), itemstack);
                this.player.inventoryMenu.broadcastChanges();
            } else if (flag && flag2) {
                if (this.dropSpamThrottler.isUnderThreshold()) {
                    this.dropSpamThrottler.increment();
                    this.player.drop(itemstack, true);
                } else {
                    PlayerConnection.LOGGER.warn("Player {} was dropping items too fast in creative mode, ignoring.", this.player.getName().getString());
                }
            }
        }

    }

    @Override
    public void handleSignUpdate(PacketPlayInUpdateSign packetplayinupdatesign) {
        List<String> list = (List) Stream.of(packetplayinupdatesign.getLines()).map(EnumChatFormat::stripFormatting).collect(Collectors.toList());

        this.filterTextPacket(list).thenAcceptAsync((list1) -> {
            this.updateSignText(packetplayinupdatesign, list1);
        }, this.server);
    }

    private void updateSignText(PacketPlayInUpdateSign packetplayinupdatesign, List<FilteredText> list) {
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        WorldServer worldserver = this.player.level();
        BlockPosition blockposition = packetplayinupdatesign.getPos();

        if (worldserver.hasChunkAt(blockposition)) {
            TileEntity tileentity = worldserver.getBlockEntity(blockposition);

            if (!(tileentity instanceof TileEntitySign)) {
                return;
            }

            TileEntitySign tileentitysign = (TileEntitySign) tileentity;

            tileentitysign.updateSignText(this.player, packetplayinupdatesign.isFrontText(), list);
        }

    }

    @Override
    public void handlePlayerAbilities(PacketPlayInAbilities packetplayinabilities) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayinabilities, this, this.player.level());
        // CraftBukkit start
        if (this.player.getAbilities().mayfly && this.player.getAbilities().flying != packetplayinabilities.isFlying()) {
            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(this.player.getBukkitEntity(), packetplayinabilities.isFlying());
            this.cserver.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.player.getAbilities().flying = packetplayinabilities.isFlying(); // Actually set the player's flying status
            } else {
                this.player.onUpdateAbilities(); // Tell the player their ability was reverted
            }
        }
        // CraftBukkit end
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket serverboundclientinformationpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundclientinformationpacket, this, this.player.level());
        boolean flag = this.player.isModelPartShown(PlayerModelPart.HAT);

        this.player.updateOptions(serverboundclientinformationpacket.information());
        if (this.player.isModelPartShown(PlayerModelPart.HAT) != flag) {
            this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.a.UPDATE_HAT, this.player));
        }

    }

    @Override
    public void handleChangeDifficulty(PacketPlayInDifficultyChange packetplayindifficultychange) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayindifficultychange, this, this.player.level());
        if (!this.player.hasPermissions(2) && !this.isSingleplayerOwner()) {
            PlayerConnection.LOGGER.warn("Player {} tried to change difficulty to {} without required permissions", this.player.getGameProfile().getName(), packetplayindifficultychange.difficulty().getDisplayName());
        } else {
            this.server.setDifficulty(packetplayindifficultychange.difficulty(), false);
        }
    }

    @Override
    public void handleChangeGameMode(ServerboundChangeGameModePacket serverboundchangegamemodepacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundchangegamemodepacket, this, this.player.level());
        if (!this.player.hasPermissions(2)) {
            PlayerConnection.LOGGER.warn("Player {} tried to change game mode to {} without required permissions", this.player.getGameProfile().getName(), serverboundchangegamemodepacket.mode().getShortDisplayName());
        } else {
            CommandGamemode.setGameMode(this.player, serverboundchangegamemodepacket.mode());
        }
    }

    @Override
    public void handleLockDifficulty(PacketPlayInDifficultyLock packetplayindifficultylock) {
        PlayerConnectionUtils.ensureRunningOnSameThread(packetplayindifficultylock, this, this.player.level());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficultyLocked(packetplayindifficultylock.isLocked());
        }
    }

    @Override
    public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket serverboundchatsessionupdatepacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundchatsessionupdatepacket, this, this.player.level());
        RemoteChatSession.a remotechatsession_a = serverboundchatsessionupdatepacket.chatSession();
        ProfilePublicKey.a profilepublickey_a = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
        ProfilePublicKey.a profilepublickey_a1 = remotechatsession_a.profilePublicKey();

        if (!Objects.equals(profilepublickey_a, profilepublickey_a1)) {
            if (profilepublickey_a != null && profilepublickey_a1.expiresAt().isBefore(profilepublickey_a.expiresAt())) {
                this.disconnect(ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY);
            } else {
                try {
                    SignatureValidator signaturevalidator = this.server.getProfileKeySignatureValidator();

                    if (signaturevalidator == null) {
                        PlayerConnection.LOGGER.warn("Ignoring chat session from {} due to missing Services public key", this.player.getGameProfile().getName());
                        return;
                    }

                    this.resetPlayerChatState(remotechatsession_a.validate(this.player.getGameProfile(), signaturevalidator));
                } catch (ProfilePublicKey.b profilepublickey_b) {
                    PlayerConnection.LOGGER.error("Failed to validate profile key: {}", profilepublickey_b.getMessage());
                    this.disconnect(profilepublickey_b.getComponent());
                }

            }
        }
    }

    @Override
    public void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket serverboundconfigurationacknowledgedpacket) {
        if (!this.waitingForSwitchToConfig) {
            throw new IllegalStateException("Client acknowledged config, but none was requested");
        } else {
            this.connection.setupInboundProtocol(ConfigurationProtocols.SERVERBOUND, new ServerConfigurationPacketListenerImpl(this.server, this.connection, this.createCookie(this.player.clientInformation()), this.player)); // CraftBukkit
        }
    }

    @Override
    public void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket serverboundchunkbatchreceivedpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundchunkbatchreceivedpacket, this, this.player.level());
        this.chunkSender.onChunkBatchReceivedByClient(serverboundchunkbatchreceivedpacket.desiredChunksPerTick());
    }

    @Override
    public void handleDebugSampleSubscription(ServerboundDebugSampleSubscriptionPacket serverbounddebugsamplesubscriptionpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverbounddebugsamplesubscriptionpacket, this, this.player.level());
        this.server.subscribeToDebugSample(this.player, serverbounddebugsamplesubscriptionpacket.sampleType());
    }

    private void resetPlayerChatState(RemoteChatSession remotechatsession) {
        this.chatSession = remotechatsession;
        this.signedMessageDecoder = remotechatsession.createMessageDecoder(this.player.getUUID());
        this.chatMessageChain.append(() -> {
            this.player.setChatSession(remotechatsession);
            this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.a.INITIALIZE_CHAT), List.of(this.player)));
        });
    }

    // CraftBukkit start - handled in super
    // @Override
    // public void handleCustomPayload(ServerboundCustomPayloadPacket serverboundcustompayloadpacket) {}
    // CraftBukkit end

    @Override
    public void handleClientTickEnd(ServerboundClientTickEndPacket serverboundclienttickendpacket) {
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundclienttickendpacket, this, this.player.level());
        if (!this.receivedMovementThisTick) {
            this.player.setKnownMovement(Vec3D.ZERO);
        }

        this.receivedMovementThisTick = false;
    }

    private void handlePlayerKnownMovement(Vec3D vec3d) {
        if (vec3d.lengthSqr() > (double) 1.0E-5F) {
            this.player.resetLastActionTime();
        }

        this.player.setKnownMovement(vec3d);
        this.receivedMovementThisTick = true;
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return this.player.hasInfiniteMaterials();
    }

    @Override
    public EntityPlayer getPlayer() {
        return this.player;
    }

    @FunctionalInterface
    private interface a {

        EnumInteractionResult run(EntityPlayer entityplayer, Entity entity, EnumHand enumhand);
    }
}
