package org.bukkit.craftbukkit.block;

import java.util.Set;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketListenerPlayOut;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CraftBlockEntityState<T extends TileEntity> extends CraftBlockState implements TileState {

    private final T tileEntity;
    private final T snapshot;

    public CraftBlockEntityState(World world, T tileEntity) {
        super(world, tileEntity.getBlockPos(), tileEntity.getBlockState());

        this.tileEntity = tileEntity;

        // copy tile entity data:
        this.snapshot = this.createSnapshot(tileEntity);
        this.load(snapshot);
    }

    protected CraftBlockEntityState(CraftBlockEntityState<T> state, Location location) {
        super(state, location);
        this.tileEntity = createSnapshot(state.snapshot);
        this.snapshot = tileEntity;
        loadData(state.getSnapshotNBT());
    }

    public void refreshSnapshot() {
        this.load(tileEntity);
    }

    protected IRegistryCustom getRegistryAccess() {
        GeneratorAccess worldHandle = getWorldHandle();
        return (worldHandle != null) ? worldHandle.registryAccess() : MinecraftServer.getDefaultRegistryAccess();
    }

    private T createSnapshot(T tileEntity) {
        if (tileEntity == null) {
            return null;
        }

        NBTTagCompound nbtTagCompound = tileEntity.saveWithFullMetadata(getRegistryAccess());
        T snapshot = (T) TileEntity.loadStatic(getPosition(), getHandle(), nbtTagCompound, getRegistryAccess());

        return snapshot;
    }

    public Set<DataComponentType<?>> applyComponents(DataComponentMap datacomponentmap, DataComponentPatch datacomponentpatch) {
        Set<DataComponentType<?>> result = snapshot.applyComponentsSet(datacomponentmap, datacomponentpatch);
        load(snapshot);
        return result;
    }

    public DataComponentMap collectComponents() {
        return snapshot.collectComponents();
    }

    private ValueInput createInput(NBTTagCompound nbtTagCompound) {
        return TagValueInput.create(ProblemReporter.DISCARDING, getRegistryAccess(), nbtTagCompound);
    }

    private TagValueOutput createOutput() {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING, getRegistryAccess());
    }

    // Loads the specified data into the snapshot TileEntity.
    public void loadData(NBTTagCompound nbtTagCompound) {
        loadData(createInput(nbtTagCompound));
    }

    // Loads the specified data into the snapshot TileEntity.
    public void loadData(ValueInput input) {
        snapshot.loadWithComponents(input);
        load(snapshot);
    }

    // copies the TileEntity-specific data, retains the position
    private void copyData(T from, T to) {
        NBTTagCompound nbtTagCompound = from.saveWithFullMetadata(getRegistryAccess());
        to.loadWithComponents(createInput(nbtTagCompound));
    }

    // gets the wrapped TileEntity
    protected T getTileEntity() {
        return tileEntity;
    }

    // gets the cloned TileEntity which is used to store the captured data
    protected T getSnapshot() {
        return snapshot;
    }

    // gets the current TileEntity from the world at this position
    protected TileEntity getTileEntityFromWorld() {
        requirePlaced();

        return getWorldHandle().getBlockEntity(this.getPosition());
    }

    // gets the NBT data of the TileEntity represented by this block state
    public NBTTagCompound getSnapshotNBT() {
        // update snapshot
        applyTo(snapshot);

        return snapshot.saveWithFullMetadata(getRegistryAccess());
    }

    public ValueInput getSnapshotInput() {
        return createInput(getSnapshotNBT());
    }

    public TagValueOutput getItemNBT() {
        // update snapshot
        applyTo(snapshot);

        // See TileEntity#saveToItem
        TagValueOutput output = createOutput();
        snapshot.saveCustomOnly(output);
        snapshot.removeComponentsFromTag(output);
        return output;
    }

    public void addEntityType(TagValueOutput nbt) {
        TileEntity.addEntityType(nbt, snapshot.getType());
    }

    // gets the packet data of the TileEntity represented by this block state
    public NBTTagCompound getUpdateNBT() {
        // update snapshot
        applyTo(snapshot);

        return snapshot.getUpdateTag(getRegistryAccess());
    }

    // copies the data of the given tile entity to this block state
    protected void load(T tileEntity) {
        if (tileEntity != null && tileEntity != snapshot) {
            copyData(tileEntity, snapshot);
        }
    }

    // applies the TileEntity data of this block state to the given TileEntity
    protected void applyTo(T tileEntity) {
        if (tileEntity != null && tileEntity != snapshot) {
            copyData(snapshot, tileEntity);
        }
    }

    protected boolean isApplicable(TileEntity tileEntity) {
        return tileEntity != null && this.tileEntity.getClass() == tileEntity.getClass();
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        boolean result = super.update(force, applyPhysics);

        if (result && this.isPlaced()) {
            TileEntity tile = getTileEntityFromWorld();

            if (isApplicable(tile)) {
                applyTo((T) tile);
                tile.setChanged();
            }
        }

        return result;
    }

    @Override
    public PersistentDataContainer getPersistentDataContainer() {
        return this.getSnapshot().persistentDataContainer;
    }

    @Nullable
    public Packet<PacketListenerPlayOut> getUpdatePacket(@NotNull Location location) {
        return new PacketPlayOutTileEntityData(CraftLocation.toBlockPosition(location), snapshot.getType(), getUpdateNBT());
    }

    @Override
    public CraftBlockEntityState<T> copy() {
        return new CraftBlockEntityState<>(this, null);
    }

    @Override
    public CraftBlockEntityState<T> copy(Location location) {
        return new CraftBlockEntityState<>(this, location);
    }
}
