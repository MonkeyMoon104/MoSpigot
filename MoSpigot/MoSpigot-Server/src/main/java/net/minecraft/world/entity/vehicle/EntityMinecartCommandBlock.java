package net.minecraft.world.entity.vehicle;

import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.CommandBlockListenerAbstract;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3D;

public class EntityMinecartCommandBlock extends EntityMinecartAbstract {

    public static final DataWatcherObject<String> DATA_ID_COMMAND_NAME = DataWatcher.<String>defineId(EntityMinecartCommandBlock.class, DataWatcherRegistry.STRING);
    static final DataWatcherObject<IChatBaseComponent> DATA_ID_LAST_OUTPUT = DataWatcher.<IChatBaseComponent>defineId(EntityMinecartCommandBlock.class, DataWatcherRegistry.COMPONENT);
    private final CommandBlockListenerAbstract commandBlock = new EntityMinecartCommandBlock.a();
    private static final int ACTIVATION_DELAY = 4;
    private int lastActivated;

    public EntityMinecartCommandBlock(EntityTypes<? extends EntityMinecartCommandBlock> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected Item getDropItem() {
        return Items.MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.COMMAND_BLOCK_MINECART);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityMinecartCommandBlock.DATA_ID_COMMAND_NAME, "");
        datawatcher_a.define(EntityMinecartCommandBlock.DATA_ID_LAST_OUTPUT, CommonComponents.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.commandBlock.load(valueinput);
        this.getEntityData().set(EntityMinecartCommandBlock.DATA_ID_COMMAND_NAME, this.getCommandBlock().getCommand());
        this.getEntityData().set(EntityMinecartCommandBlock.DATA_ID_LAST_OUTPUT, this.getCommandBlock().getLastOutput());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        this.commandBlock.save(valueoutput);
    }

    @Override
    public IBlockData getDefaultDisplayBlockState() {
        return Blocks.COMMAND_BLOCK.defaultBlockState();
    }

    public CommandBlockListenerAbstract getCommandBlock() {
        return this.commandBlock;
    }

    @Override
    public void activateMinecart(int i, int j, int k, boolean flag) {
        if (flag && this.tickCount - this.lastActivated >= 4) {
            this.getCommandBlock().performCommand(this.level());
            this.lastActivated = this.tickCount;
        }

    }

    @Override
    public EnumInteractionResult interact(EntityHuman entityhuman, EnumHand enumhand) {
        return this.commandBlock.usedBy(entityhuman);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        super.onSyncedDataUpdated(datawatcherobject);
        if (EntityMinecartCommandBlock.DATA_ID_LAST_OUTPUT.equals(datawatcherobject)) {
            try {
                this.commandBlock.setLastOutput((IChatBaseComponent) this.getEntityData().get(EntityMinecartCommandBlock.DATA_ID_LAST_OUTPUT));
            } catch (Throwable throwable) {
                ;
            }
        } else if (EntityMinecartCommandBlock.DATA_ID_COMMAND_NAME.equals(datawatcherobject)) {
            this.commandBlock.setCommand((String) this.getEntityData().get(EntityMinecartCommandBlock.DATA_ID_COMMAND_NAME));
        }

    }

    public class a extends CommandBlockListenerAbstract {

        public a() {}

        @Override
        public WorldServer getLevel() {
            return (WorldServer) EntityMinecartCommandBlock.this.level();
        }

        @Override
        public void onUpdated() {
            EntityMinecartCommandBlock.this.getEntityData().set(EntityMinecartCommandBlock.DATA_ID_COMMAND_NAME, this.getCommand());
            EntityMinecartCommandBlock.this.getEntityData().set(EntityMinecartCommandBlock.DATA_ID_LAST_OUTPUT, this.getLastOutput());
        }

        @Override
        public Vec3D getPosition() {
            return EntityMinecartCommandBlock.this.position();
        }

        public EntityMinecartCommandBlock getMinecart() {
            return EntityMinecartCommandBlock.this;
        }

        @Override
        public CommandListenerWrapper createCommandSourceStack() {
            return new CommandListenerWrapper(this, EntityMinecartCommandBlock.this.position(), EntityMinecartCommandBlock.this.getRotationVector(), this.getLevel(), 2, this.getName().getString(), EntityMinecartCommandBlock.this.getDisplayName(), this.getLevel().getServer(), EntityMinecartCommandBlock.this);
        }

        @Override
        public boolean isValid() {
            return !EntityMinecartCommandBlock.this.isRemoved();
        }

        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandListenerWrapper wrapper) {
            return (org.bukkit.craftbukkit.entity.CraftMinecartCommand) EntityMinecartCommandBlock.this.getBukkitEntity();
        }
        // CraftBukkit end
    }
}
