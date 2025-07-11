package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.PacketPlayOutScoreboardDisplayObjective;
import net.minecraft.network.protocol.game.PacketPlayOutScoreboardObjective;
import net.minecraft.network.protocol.game.PacketPlayOutScoreboardScore;
import net.minecraft.network.protocol.game.PacketPlayOutScoreboardTeam;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PersistentScoreboard;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardObjective;
import net.minecraft.world.scores.ScoreboardScore;
import net.minecraft.world.scores.ScoreboardTeam;

public class ScoreboardServer extends Scoreboard {

    public static final SavedDataType<PersistentScoreboard> TYPE = new SavedDataType<PersistentScoreboard>("scoreboard", (persistentbase_a) -> {
        return persistentbase_a.levelOrThrow().getScoreboard().createData();
    }, (persistentbase_a) -> {
        ScoreboardServer scoreboardserver = persistentbase_a.levelOrThrow().getScoreboard();
        Codec<PersistentScoreboard.a> codec = PersistentScoreboard.a.CODEC; // CraftBukkit - decompile error

        Objects.requireNonNull(scoreboardserver);
        return codec.xmap(scoreboardserver::createData, PersistentScoreboard::pack);
    }, DataFixTypes.SAVED_DATA_SCOREBOARD);
    private final MinecraftServer server;
    private final Set<ScoreboardObjective> trackedObjectives = Sets.newHashSet();
    private final List<Runnable> dirtyListeners = Lists.newArrayList();

    public ScoreboardServer(MinecraftServer minecraftserver) {
        this.server = minecraftserver;
    }

    @Override
    protected void onScoreChanged(ScoreHolder scoreholder, ScoreboardObjective scoreboardobjective, ScoreboardScore scoreboardscore) {
        super.onScoreChanged(scoreholder, scoreboardobjective, scoreboardscore);
        if (this.trackedObjectives.contains(scoreboardobjective)) {
            this.broadcastAll(new PacketPlayOutScoreboardScore(scoreholder.getScoreboardName(), scoreboardobjective.getName(), scoreboardscore.value(), Optional.ofNullable(scoreboardscore.display()), Optional.ofNullable(scoreboardscore.numberFormat()))); // CraftBukkit
        }

        this.setDirty();
    }

    @Override
    protected void onScoreLockChanged(ScoreHolder scoreholder, ScoreboardObjective scoreboardobjective) {
        super.onScoreLockChanged(scoreholder, scoreboardobjective);
        this.setDirty();
    }

    @Override
    public void onPlayerRemoved(ScoreHolder scoreholder) {
        super.onPlayerRemoved(scoreholder);
        this.broadcastAll(new ClientboundResetScorePacket(scoreholder.getScoreboardName(), (String) null)); // CraftBukkit
        this.setDirty();
    }

    @Override
    public void onPlayerScoreRemoved(ScoreHolder scoreholder, ScoreboardObjective scoreboardobjective) {
        super.onPlayerScoreRemoved(scoreholder, scoreboardobjective);
        if (this.trackedObjectives.contains(scoreboardobjective)) {
            this.broadcastAll(new ClientboundResetScorePacket(scoreholder.getScoreboardName(), scoreboardobjective.getName())); // CraftBukkit
        }

        this.setDirty();
    }

    @Override
    public void setDisplayObjective(DisplaySlot displayslot, @Nullable ScoreboardObjective scoreboardobjective) {
        ScoreboardObjective scoreboardobjective1 = this.getDisplayObjective(displayslot);

        super.setDisplayObjective(displayslot, scoreboardobjective);
        if (scoreboardobjective1 != scoreboardobjective && scoreboardobjective1 != null) {
            if (this.getObjectiveDisplaySlotCount(scoreboardobjective1) > 0) {
                this.broadcastAll(new PacketPlayOutScoreboardDisplayObjective(displayslot, scoreboardobjective)); // CraftBukkit
            } else {
                this.stopTrackingObjective(scoreboardobjective1);
            }
        }

        if (scoreboardobjective != null) {
            if (this.trackedObjectives.contains(scoreboardobjective)) {
                this.broadcastAll(new PacketPlayOutScoreboardDisplayObjective(displayslot, scoreboardobjective)); // CraftBukkit
            } else {
                this.startTrackingObjective(scoreboardobjective);
            }
        }

        this.setDirty();
    }

    @Override
    public boolean addPlayerToTeam(String s, ScoreboardTeam scoreboardteam) {
        if (super.addPlayerToTeam(s, scoreboardteam)) {
            this.broadcastAll(PacketPlayOutScoreboardTeam.createPlayerPacket(scoreboardteam, s, PacketPlayOutScoreboardTeam.a.ADD)); // CraftBukkit
            this.updatePlayerWaypoint(s);
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void removePlayerFromTeam(String s, ScoreboardTeam scoreboardteam) {
        super.removePlayerFromTeam(s, scoreboardteam);
        this.broadcastAll(PacketPlayOutScoreboardTeam.createPlayerPacket(scoreboardteam, s, PacketPlayOutScoreboardTeam.a.REMOVE)); // CraftBukkit
        this.updatePlayerWaypoint(s);
        this.setDirty();
    }

    @Override
    public void onObjectiveAdded(ScoreboardObjective scoreboardobjective) {
        super.onObjectiveAdded(scoreboardobjective);
        this.setDirty();
    }

    @Override
    public void onObjectiveChanged(ScoreboardObjective scoreboardobjective) {
        super.onObjectiveChanged(scoreboardobjective);
        if (this.trackedObjectives.contains(scoreboardobjective)) {
            this.broadcastAll(new PacketPlayOutScoreboardObjective(scoreboardobjective, 2)); // CraftBukkit
        }

        this.setDirty();
    }

    @Override
    public void onObjectiveRemoved(ScoreboardObjective scoreboardobjective) {
        super.onObjectiveRemoved(scoreboardobjective);
        if (this.trackedObjectives.contains(scoreboardobjective)) {
            this.stopTrackingObjective(scoreboardobjective);
        }

        this.setDirty();
    }

    @Override
    public void onTeamAdded(ScoreboardTeam scoreboardteam) {
        super.onTeamAdded(scoreboardteam);
        this.broadcastAll(PacketPlayOutScoreboardTeam.createAddOrModifyPacket(scoreboardteam, true)); // CraftBukkit
        this.setDirty();
    }

    @Override
    public void onTeamChanged(ScoreboardTeam scoreboardteam) {
        super.onTeamChanged(scoreboardteam);
        this.broadcastAll(PacketPlayOutScoreboardTeam.createAddOrModifyPacket(scoreboardteam, false)); // CraftBukkit
        this.updateTeamWaypoints(scoreboardteam);
        this.setDirty();
    }

    @Override
    public void onTeamRemoved(ScoreboardTeam scoreboardteam) {
        super.onTeamRemoved(scoreboardteam);
        this.broadcastAll(PacketPlayOutScoreboardTeam.createRemovePacket(scoreboardteam)); // CraftBukkit
        this.updateTeamWaypoints(scoreboardteam);
        this.setDirty();
    }

    public void addDirtyListener(Runnable runnable) {
        this.dirtyListeners.add(runnable);
    }

    protected void setDirty() {
        for (Runnable runnable : this.dirtyListeners) {
            runnable.run();
        }

    }

    public List<Packet<?>> getStartTrackingPackets(ScoreboardObjective scoreboardobjective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new PacketPlayOutScoreboardObjective(scoreboardobjective, 0));

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == scoreboardobjective) {
                list.add(new PacketPlayOutScoreboardDisplayObjective(displayslot, scoreboardobjective));
            }
        }

        for (PlayerScoreEntry playerscoreentry : this.listPlayerScores(scoreboardobjective)) {
            list.add(new PacketPlayOutScoreboardScore(playerscoreentry.owner(), scoreboardobjective.getName(), playerscoreentry.value(), Optional.ofNullable(playerscoreentry.display()), Optional.ofNullable(playerscoreentry.numberFormatOverride())));
        }

        return list;
    }

    public void startTrackingObjective(ScoreboardObjective scoreboardobjective) {
        List<Packet<?>> list = this.getStartTrackingPackets(scoreboardobjective);

        for (EntityPlayer entityplayer : this.server.getPlayerList().getPlayers()) {
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            for (Packet<?> packet : list) {
                entityplayer.connection.send(packet);
            }
        }

        this.trackedObjectives.add(scoreboardobjective);
    }

    public List<Packet<?>> getStopTrackingPackets(ScoreboardObjective scoreboardobjective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new PacketPlayOutScoreboardObjective(scoreboardobjective, 1));

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == scoreboardobjective) {
                list.add(new PacketPlayOutScoreboardDisplayObjective(displayslot, scoreboardobjective));
            }
        }

        return list;
    }

    public void stopTrackingObjective(ScoreboardObjective scoreboardobjective) {
        List<Packet<?>> list = this.getStopTrackingPackets(scoreboardobjective);

        for (EntityPlayer entityplayer : this.server.getPlayerList().getPlayers()) {
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            for (Packet<?> packet : list) {
                entityplayer.connection.send(packet);
            }
        }

        this.trackedObjectives.remove(scoreboardobjective);
    }

    public int getObjectiveDisplaySlotCount(ScoreboardObjective scoreboardobjective) {
        int i = 0;

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == scoreboardobjective) {
                ++i;
            }
        }

        return i;
    }

    private PersistentScoreboard createData() {
        PersistentScoreboard persistentscoreboard = new PersistentScoreboard(this);

        Objects.requireNonNull(persistentscoreboard);
        this.addDirtyListener(persistentscoreboard::setDirty);
        return persistentscoreboard;
    }

    private PersistentScoreboard createData(PersistentScoreboard.a persistentscoreboard_a) {
        PersistentScoreboard persistentscoreboard = this.createData();

        persistentscoreboard.loadFrom(persistentscoreboard_a);
        return persistentscoreboard;
    }

    private void updatePlayerWaypoint(String s) {
        EntityPlayer entityplayer = this.server.getPlayerList().getPlayerByName(s);

        if (entityplayer != null) {
            WorldServer worldserver = entityplayer.level();

            if (worldserver instanceof WorldServer) {
                worldserver.getWaypointManager().remakeConnections(entityplayer);
            }
        }

    }

    private void updateTeamWaypoints(ScoreboardTeam scoreboardteam) {
        for (WorldServer worldserver : this.server.getAllLevels()) {
            scoreboardteam.getPlayers().stream().map((s) -> {
                return this.server.getPlayerList().getPlayerByName(s);
            }).filter(Objects::nonNull).forEach((entityplayer) -> {
                worldserver.getWaypointManager().remakeConnections(entityplayer);
            });
        }

    }

    // CraftBukkit start - Send to players
    private void broadcastAll(Packet packet) {
        for (EntityPlayer entityplayer : (List<EntityPlayer>) this.server.getPlayerList().players) {
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() == this) {
                entityplayer.connection.send(packet);
            }
        }
    }
    // CraftBukkit end
}
