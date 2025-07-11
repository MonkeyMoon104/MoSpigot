package net.minecraft.world.level.block.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.ICommandListener;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.ChatClickable;
import net.minecraft.network.chat.ChatComponentUtils;
import net.minecraft.network.chat.ChatModifier;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.MathHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockSign;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2F;
import net.minecraft.world.phys.Vec3D;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.Objects;
import net.minecraft.server.level.EntityPlayer;
import org.bukkit.block.sign.Side;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
// CraftBukkit end

public class TileEntitySign extends TileEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEXT_LINE_WIDTH = 90;
    private static final int TEXT_LINE_HEIGHT = 10;
    private static final boolean DEFAULT_IS_WAXED = false;
    @Nullable
    public UUID playerWhoMayEdit;
    private SignText frontText;
    private SignText backText;
    private boolean isWaxed;

    public TileEntitySign(BlockPosition blockposition, IBlockData iblockdata) {
        this(TileEntityTypes.SIGN, blockposition, iblockdata);
    }

    public TileEntitySign(TileEntityTypes tileentitytypes, BlockPosition blockposition, IBlockData iblockdata) {
        super(tileentitytypes, blockposition, iblockdata);
        this.isWaxed = false;
        this.frontText = this.createDefaultSignText();
        this.backText = this.createDefaultSignText();
    }

    protected SignText createDefaultSignText() {
        return new SignText();
    }

    public boolean isFacingFrontText(EntityHuman entityhuman) {
        Block block = this.getBlockState().getBlock();

        if (block instanceof BlockSign blocksign) {
            Vec3D vec3d = blocksign.getSignHitboxCenterPosition(this.getBlockState());
            double d0 = entityhuman.getX() - ((double) this.getBlockPos().getX() + vec3d.x);
            double d1 = entityhuman.getZ() - ((double) this.getBlockPos().getZ() + vec3d.z);
            float f = blocksign.getYRotationDegrees(this.getBlockState());
            float f1 = (float) (MathHelper.atan2(d1, d0) * (double) (180F / (float) Math.PI)) - 90.0F;

            return MathHelper.degreesDifferenceAbs(f, f1) <= 90.0F;
        } else {
            return false;
        }
    }

    public SignText getText(boolean flag) {
        return flag ? this.frontText : this.backText;
    }

    public SignText getFrontText() {
        return this.frontText;
    }

    public SignText getBackText() {
        return this.backText;
    }

    public int getTextLineHeight() {
        return 10;
    }

    public int getMaxTextLineWidth() {
        return 90;
    }

    @Override
    protected void saveAdditional(ValueOutput valueoutput) {
        super.saveAdditional(valueoutput);
        valueoutput.store("front_text", SignText.DIRECT_CODEC, this.frontText);
        valueoutput.store("back_text", SignText.DIRECT_CODEC, this.backText);
        valueoutput.putBoolean("is_waxed", this.isWaxed);
    }

    @Override
    protected void loadAdditional(ValueInput valueinput) {
        super.loadAdditional(valueinput);
        this.frontText = (SignText) valueinput.read("front_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
        this.backText = (SignText) valueinput.read("back_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
        this.isWaxed = valueinput.getBooleanOr("is_waxed", false);
    }

    private SignText loadLines(SignText signtext) {
        for (int i = 0; i < 4; ++i) {
            IChatBaseComponent ichatbasecomponent = this.loadLine(signtext.getMessage(i, false));
            IChatBaseComponent ichatbasecomponent1 = this.loadLine(signtext.getMessage(i, true));

            signtext = signtext.setMessage(i, ichatbasecomponent, ichatbasecomponent1);
        }

        return signtext;
    }

    private IChatBaseComponent loadLine(IChatBaseComponent ichatbasecomponent) {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            try {
                return ChatComponentUtils.updateForEntity(createCommandSourceStack((EntityHuman) null, worldserver, this.worldPosition), ichatbasecomponent, (Entity) null, 0);
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }
        }

        return ichatbasecomponent;
    }

    public void updateSignText(EntityHuman entityhuman, boolean flag, List<FilteredText> list) {
        if (!this.isWaxed() && entityhuman.getUUID().equals(this.getPlayerWhoMayEdit()) && this.level != null) {
            this.updateText((signtext) -> {
                return this.setMessages(entityhuman, list, signtext, flag); // CraftBukkit
            }, flag);
            this.setAllowedPlayerEditor((UUID) null);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        } else {
            TileEntitySign.LOGGER.warn("Player {} just tried to change non-editable sign", entityhuman.getName().getString());
            ((EntityPlayer) entityhuman).connection.send(this.getUpdatePacket()); // CraftBukkit
        }
    }

    public boolean updateText(UnaryOperator<SignText> unaryoperator, boolean flag) {
        SignText signtext = this.getText(flag);

        return this.setText((SignText) unaryoperator.apply(signtext), flag);
    }

    private SignText setMessages(EntityHuman entityhuman, List<FilteredText> list, SignText signtext, boolean front) { // CraftBukkit
        SignText originalText = signtext; // CraftBukkit
        for (int i = 0; i < list.size(); ++i) {
            FilteredText filteredtext = (FilteredText) list.get(i);
            ChatModifier chatmodifier = signtext.getMessage(i, entityhuman.isTextFilteringEnabled()).getStyle();

            if (entityhuman.isTextFilteringEnabled()) {
                signtext = signtext.setMessage(i, IChatBaseComponent.literal(filteredtext.filteredOrEmpty()).setStyle(chatmodifier));
            } else {
                signtext = signtext.setMessage(i, IChatBaseComponent.literal(filteredtext.raw()).setStyle(chatmodifier), IChatBaseComponent.literal(filteredtext.filteredOrEmpty()).setStyle(chatmodifier));
            }
        }

        // CraftBukkit start
        Player player = ((EntityPlayer) entityhuman).getBukkitEntity();
        String[] lines = new String[4];

        for (int i = 0; i < list.size(); ++i) {
            lines[i] = CraftChatMessage.fromComponent(signtext.getMessage(i, entityhuman.isTextFilteringEnabled()));
        }

        SignChangeEvent event = new SignChangeEvent(CraftBlock.at(this.level, this.worldPosition), player, lines.clone(), (front) ? Side.FRONT : Side.BACK);
        entityhuman.level().getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return originalText;
        }

        IChatBaseComponent[] components = org.bukkit.craftbukkit.block.CraftSign.sanitizeLines(event.getLines());
        for (int i = 0; i < components.length; i++) {
            if (!Objects.equals(lines[i], event.getLine(i))) {
                signtext = signtext.setMessage(i, components[i]);
            }
        }
        // CraftBukkit end

        return signtext;
    }

    public boolean setText(SignText signtext, boolean flag) {
        return flag ? this.setFrontText(signtext) : this.setBackText(signtext);
    }

    private boolean setBackText(SignText signtext) {
        if (signtext != this.backText) {
            this.backText = signtext;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    private boolean setFrontText(SignText signtext) {
        if (signtext != this.frontText) {
            this.frontText = signtext;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean canExecuteClickCommands(boolean flag, EntityHuman entityhuman) {
        return this.isWaxed() && this.getText(flag).hasAnyClickCommands(entityhuman);
    }

    public boolean executeClickCommandsIfPresent(WorldServer worldserver, EntityHuman entityhuman, BlockPosition blockposition, boolean flag) {
        boolean flag1 = false;

        // CraftBukkit start - decompile error
        for(IChatBaseComponent ichatbasecomponent : this.getText(flag).getMessages(entityhuman.isTextFilteringEnabled())) {
            ChatModifier chatmodifier = ichatbasecomponent.getStyle();
            ChatClickable chatclickable = chatmodifier.getClickEvent();
            byte b0 = 0;

            //$FF: b0->value
            //0->net/minecraft/network/chat/ChatClickable$RunCommand
            //1->net/minecraft/network/chat/ChatClickable$h
            //2->net/minecraft/network/chat/ChatClickable$d
            {
                if (chatclickable instanceof ChatClickable.RunCommand) {
                    ChatClickable.RunCommand chatclickable_runcommand = (ChatClickable.RunCommand)chatclickable;

                    worldserver.getServer().getCommands().performPrefixedCommand(createCommandSourceStack(entityhuman, worldserver, blockposition), chatclickable_runcommand.command());
                    flag1 = true;
                } else if (chatclickable instanceof ChatClickable.h) {
                    ChatClickable.h chatclickable_h = (ChatClickable.h)chatclickable;

                    entityhuman.openDialog(chatclickable_h.dialog());
                    flag1 = true;
                } else if (chatclickable instanceof ChatClickable.d) {
                    ChatClickable.d chatclickable_d = (ChatClickable.d)chatclickable;

                    worldserver.getServer().handleCustomClickAction(chatclickable_d.id(), chatclickable_d.payload(), (EntityPlayer) entityhuman); // CraftBukkit - add player
                    flag1 = true;
                }
                // CraftBukkit end
            }
        }

        return flag1;
    }

    // CraftBukkit start
    private final ICommandListener commandSource = new ICommandListener() {

        @Override
        public void sendSystemMessage(IChatBaseComponent ichatbasecomponent) {}

        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandListenerWrapper wrapper) {
            return wrapper.getEntity() != null ? wrapper.getEntity().getBukkitEntity() : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, TileEntitySign.this);
        }

        @Override
        public boolean acceptsSuccess() {
            return false;
        }

        @Override
        public boolean acceptsFailure() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    };

    private CommandListenerWrapper createCommandSourceStack(@Nullable EntityHuman entityhuman, WorldServer worldserver, BlockPosition blockposition) {
        // CraftBukkit end
        String s = entityhuman == null ? "Sign" : entityhuman.getName().getString();
        IChatBaseComponent ichatbasecomponent = (IChatBaseComponent) (entityhuman == null ? IChatBaseComponent.literal("Sign") : entityhuman.getDisplayName());

        // CraftBukkit - commandSource
        return new CommandListenerWrapper(commandSource, Vec3D.atCenterOf(blockposition), Vec2F.ZERO, worldserver, 2, s, ichatbasecomponent, worldserver.getServer(), entityhuman);
    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        return this.saveCustomOnly(holderlookup_a);
    }

    public void setAllowedPlayerEditor(@Nullable UUID uuid) {
        this.playerWhoMayEdit = uuid;
    }

    @Nullable
    public UUID getPlayerWhoMayEdit() {
        // CraftBukkit start - unnecessary sign ticking removed, so do this lazily
        if (this.level != null && this.playerWhoMayEdit != null) {
            clearInvalidPlayerWhoMayEdit(this, this.level, this.playerWhoMayEdit);
        }
        // CraftBukkit end
        return this.playerWhoMayEdit;
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3); // CraftBukkit - skip notify if world is null (SPIGOT-5122)
    }

    public boolean isWaxed() {
        return this.isWaxed;
    }

    public boolean setWaxed(boolean flag) {
        if (this.isWaxed != flag) {
            this.isWaxed = flag;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean playerIsTooFarAwayToEdit(UUID uuid) {
        EntityHuman entityhuman = this.level.getPlayerByUUID(uuid);

        return entityhuman == null || !entityhuman.canInteractWithBlock(this.getBlockPos(), 4.0D);
    }

    public static void tick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntitySign tileentitysign) {
        UUID uuid = tileentitysign.getPlayerWhoMayEdit();

        if (uuid != null) {
            tileentitysign.clearInvalidPlayerWhoMayEdit(tileentitysign, world, uuid);
        }

    }

    private void clearInvalidPlayerWhoMayEdit(TileEntitySign tileentitysign, World world, UUID uuid) {
        if (tileentitysign.playerIsTooFarAwayToEdit(uuid)) {
            tileentitysign.setAllowedPlayerEditor((UUID) null);
        }

    }

    public SoundEffect getSignInteractionFailedSoundEvent() {
        return SoundEffects.WAXED_SIGN_INTERACT_FAIL;
    }
}
