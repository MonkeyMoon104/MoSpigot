package net.minecraft.server.waypoints;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.collect.UnmodifiableIterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.waypoints.WaypointManager;
import net.minecraft.world.waypoints.WaypointTransmitter;

public class ServerWaypointManager implements WaypointManager<WaypointTransmitter> {

    private final Set<WaypointTransmitter> waypoints = new HashSet();
    private final Set<EntityPlayer> players = new HashSet();
    private final Table<EntityPlayer, WaypointTransmitter, WaypointTransmitter.c> connections = HashBasedTable.create();

    public ServerWaypointManager() {}

    public void trackWaypoint(WaypointTransmitter waypointtransmitter) {
        this.waypoints.add(waypointtransmitter);

        for (EntityPlayer entityplayer : this.players) {
            this.createConnection(entityplayer, waypointtransmitter);
        }

    }

    public void updateWaypoint(WaypointTransmitter waypointtransmitter) {
        if (this.waypoints.contains(waypointtransmitter)) {
            Map<EntityPlayer, WaypointTransmitter.c> map = Tables.transpose(this.connections).row(waypointtransmitter);
            Sets.SetView<EntityPlayer> sets_setview = Sets.difference(this.players, map.keySet());
            UnmodifiableIterator unmodifiableiterator = ImmutableSet.copyOf(map.entrySet()).iterator();

            while (unmodifiableiterator.hasNext()) {
                Map.Entry<EntityPlayer, WaypointTransmitter.c> map_entry = (Entry) unmodifiableiterator.next();

                this.updateConnection((EntityPlayer) map_entry.getKey(), waypointtransmitter, (WaypointTransmitter.c) map_entry.getValue());
            }

            unmodifiableiterator = sets_setview.iterator();

            while (unmodifiableiterator.hasNext()) {
                EntityPlayer entityplayer = (EntityPlayer) unmodifiableiterator.next();

                this.createConnection(entityplayer, waypointtransmitter);
            }

        }
    }

    public void untrackWaypoint(WaypointTransmitter waypointtransmitter) {
        this.connections.column(waypointtransmitter).forEach((entityplayer, waypointtransmitter_c) -> {
            waypointtransmitter_c.disconnect();
        });
        Tables.transpose(this.connections).row(waypointtransmitter).clear();
        this.waypoints.remove(waypointtransmitter);
    }

    public void addPlayer(EntityPlayer entityplayer) {
        this.players.add(entityplayer);

        for (WaypointTransmitter waypointtransmitter : this.waypoints) {
            this.createConnection(entityplayer, waypointtransmitter);
        }

        if (entityplayer.isTransmittingWaypoint()) {
            this.trackWaypoint((WaypointTransmitter) entityplayer);
        }

    }

    public void updatePlayer(EntityPlayer entityplayer) {
        Map<WaypointTransmitter, WaypointTransmitter.c> map = this.connections.row(entityplayer);
        Sets.SetView<WaypointTransmitter> sets_setview = Sets.difference(this.waypoints, map.keySet());
        UnmodifiableIterator unmodifiableiterator = ImmutableSet.copyOf(map.entrySet()).iterator();

        while (unmodifiableiterator.hasNext()) {
            Map.Entry<WaypointTransmitter, WaypointTransmitter.c> map_entry = (Entry) unmodifiableiterator.next();

            this.updateConnection(entityplayer, (WaypointTransmitter) map_entry.getKey(), (WaypointTransmitter.c) map_entry.getValue());
        }

        unmodifiableiterator = sets_setview.iterator();

        while (unmodifiableiterator.hasNext()) {
            WaypointTransmitter waypointtransmitter = (WaypointTransmitter) unmodifiableiterator.next();

            this.createConnection(entityplayer, waypointtransmitter);
        }

    }

    public void removePlayer(EntityPlayer entityplayer) {
        this.connections.row(entityplayer).values().removeIf((waypointtransmitter_c) -> {
            waypointtransmitter_c.disconnect();
            return true;
        });
        this.untrackWaypoint((WaypointTransmitter) entityplayer);
        this.players.remove(entityplayer);
    }

    public void breakAllConnections() {
        this.connections.values().forEach(WaypointTransmitter.c::disconnect);
        this.connections.clear();
    }

    public void remakeConnections(WaypointTransmitter waypointtransmitter) {
        for (EntityPlayer entityplayer : this.players) {
            this.createConnection(entityplayer, waypointtransmitter);
        }

    }

    public Set<WaypointTransmitter> transmitters() {
        return this.waypoints;
    }

    private static boolean isLocatorBarEnabledFor(EntityPlayer entityplayer) {
        return entityplayer.level().getGameRules().getBoolean(GameRules.RULE_LOCATOR_BAR); // CraftBukkit - per-world
    }

    private void createConnection(EntityPlayer entityplayer, WaypointTransmitter waypointtransmitter) {
        if (entityplayer != waypointtransmitter) {
            if (isLocatorBarEnabledFor(entityplayer)) {
                waypointtransmitter.makeWaypointConnectionWith(entityplayer).ifPresentOrElse((waypointtransmitter_c) -> {
                    this.connections.put(entityplayer, waypointtransmitter, waypointtransmitter_c);
                    waypointtransmitter_c.connect();
                }, () -> {
                    WaypointTransmitter.c waypointtransmitter_c = (WaypointTransmitter.c) this.connections.remove(entityplayer, waypointtransmitter);

                    if (waypointtransmitter_c != null) {
                        waypointtransmitter_c.disconnect();
                    }

                });
            }
        }
    }

    private void updateConnection(EntityPlayer entityplayer, WaypointTransmitter waypointtransmitter, WaypointTransmitter.c waypointtransmitter_c) {
        if (entityplayer != waypointtransmitter) {
            if (isLocatorBarEnabledFor(entityplayer)) {
                if (!waypointtransmitter_c.isBroken()) {
                    waypointtransmitter_c.update();
                } else {
                    waypointtransmitter.makeWaypointConnectionWith(entityplayer).ifPresentOrElse((waypointtransmitter_c1) -> {
                        waypointtransmitter_c1.connect();
                        this.connections.put(entityplayer, waypointtransmitter, waypointtransmitter_c1);
                    }, () -> {
                        waypointtransmitter_c.disconnect();
                        this.connections.remove(entityplayer, waypointtransmitter);
                    });
                }
            }
        }
    }
}
