package nl.oorlogsim.world;

import nl.oorlogsim.OorlogSim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Regelt alles wat er gebeurt als een speler een OorlogSim-wereld binnenkomt of verlaat:
 * inventory opslaan/terugzetten, lobby, KitPvP, OS-afmelding en 1.8-PvP.
 */
public final class WorldListener implements Listener {
    private final OorlogSim plugin;

    public WorldListener(OorlogSim plugin) {
        this.plugin = plugin;
    }

    private boolean protect(Player p, WorldRole from, WorldRole to) {
        if (!plugin.cfg().protectInventories) return false;
        // Bouwende admins: maps-wereld zonder inventory-wissel
        boolean mapsOnly = (from == WorldRole.MAPS || to == WorldRole.MAPS) && (from == WorldRole.OTHER || to == WorldRole.OTHER);
        return !(mapsOnly && p.hasPermission("oorlogsim.bypass.inventory"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        WorldRole from = plugin.role(e.getFrom());
        WorldRole to = plugin.role(p.getWorld());
        transition(p, from, to);
    }

    public void transition(Player p, WorldRole from, WorldRole to) {
        if (from == to) return;
        switch (from) {
            case LOBBY -> plugin.os().onLeaveLobby(p);
            case MAPS -> plugin.os().onParticipantGone(p);
            case KITPVP -> plugin.kitpvp().onLeave(p);
            default -> { }
        }
        if (from == WorldRole.OTHER && to.isPluginWorld() && protect(p, from, to)) {
            plugin.snapshots().saveIfAbsent(p);
        }
        if (to == WorldRole.OTHER && from.isPluginWorld()) {
            plugin.snapshots().restore(p);
        }
        plugin.legacyPvp().update(p);
        switch (to) {
            case LOBBY -> plugin.os().onEnterLobby(p);
            case KITPVP -> plugin.kitpvp().onEnter(p);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        WorldRole role = plugin.role(p.getWorld());
        plugin.legacyPvp().update(p);
        switch (role) {
            case OTHER -> plugin.snapshots().restore(p);
            case LOBBY -> {
                if (plugin.cfg().protectInventories) plugin.snapshots().saveIfAbsent(p);
                plugin.os().onEnterLobby(p);
            }
            case KITPVP -> {
                if (plugin.cfg().protectInventories) plugin.snapshots().saveIfAbsent(p);
                plugin.kitpvp().onEnter(p);
            }
            case MAPS -> {
                // Niet meer in een potje (bijv. herverbonden): terug naar de lobby.
                if (!plugin.os().isParticipant(p.getUniqueId()) && !p.hasPermission("oorlogsim.admin")) {
                    Location lobby = plugin.os().lobbySpawn();
                    if (lobby != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (p.isOnline()) {
                                p.teleport(lobby);
                                plugin.messages().send(p, "match.rejoin-lobby");
                            }
                        });
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        WorldRole role = plugin.role(p.getWorld());
        switch (role) {
            case LOBBY -> plugin.os().onLeaveLobby(p);
            case MAPS -> plugin.os().onParticipantGone(p);
            case KITPVP -> plugin.kitpvp().onLeave(p);
            default -> { }
        }
        plugin.names().clear(p);
        plugin.legacyPvp().remove(p);
    }

    /** Hulpje voor commando's: bestaat de wereld? */
    public static World world(String name) {
        return Bukkit.getWorld(name);
    }
}
