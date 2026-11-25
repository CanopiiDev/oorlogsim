package nl.oorlogsim.os;

import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.game.logic.TeamId;
import nl.oorlogsim.gui.MapVoteGui;
import nl.oorlogsim.storage.StoredLocation;
import nl.oorlogsim.util.Players;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.UUID;

/** Events voor de OS-lobby en het OS-potje. */
public final class OsListener implements Listener {
    private final OorlogSim plugin;

    public OsListener(OorlogSim plugin) {
        this.plugin = plugin;
    }

    private boolean inLobby(Player p) {
        return p.getWorld().getName().equals(plugin.cfg().lobbyWorld);
    }

    private boolean inMaps(Player p) {
        return p.getWorld().getName().equals(plugin.cfg().mapsWorld);
    }

    private OsGame os() {
        return plugin.os();
    }

    // ------------------------------------------------------------------ lobby

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!inLobby(p) || e.getHand() != EquipmentSlot.HAND) return;

        // Startknop
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            StoredLocation button = plugin.locations().button();
            Block b = e.getClickedBlock();
            if (button != null && button.sameBlock(b.getLocation())) {
                os().pressButton(p);
                return;
            }
        }

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String tag = LobbyItems.tagOf(plugin, e.getItem());
        if (tag == null) return;
        e.setCancelled(true);
        switch (tag) {
            case LobbyItems.TEAM_KD1 -> os().chooseTeam(p, TeamId.KD1);
            case LobbyItems.TEAM_KD2 -> os().chooseTeam(p, TeamId.KD2);
            case LobbyItems.VOTE -> MapVoteGui.open(plugin, p);
            default -> { }
        }
    }

    private boolean lockedInLobby(Player p) {
        return inLobby(p) && p.getGameMode() != GameMode.CREATIVE;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (lockedInLobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (lockedInLobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && lockedInLobby(p)
                && !(e.getInventory().getHolder(false) instanceof nl.oorlogsim.gui.Menu)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && inLobby(p) && plugin.cfg().lobbyInvulnerable) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ schade

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageFilter(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (inLobby(victim)) {
            if (plugin.cfg().lobbyInvulnerable) {
                e.setCancelled(true);
                if (e.getCause() == EntityDamageEvent.DamageCause.VOID) Players.teleport(victim, os().lobbySpawn());
            }
            return;
        }
        if (!inMaps(victim) || !os().isParticipant(victim.getUniqueId())) return;
        if (!os().isAlive(victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        Player attacker = Players.attacker(e);
        if (attacker == null || attacker.equals(victim)) return;
        Optional<TeamId> at = os().participantTeam(attacker.getUniqueId());
        if (at.isEmpty() || !os().isAlive(attacker.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.cfg().friendlyFire && at.equals(os().participantTeam(victim.getUniqueId()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageResult(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !inMaps(victim) || !os().isAlive(victim.getUniqueId())) return;
        Player attacker = Players.attacker(e);
        double dmg = e.getFinalDamage();
        if (attacker != null && !attacker.equals(victim) && os().isAlive(attacker.getUniqueId())) {
            os().recordDamage(attacker, victim, dmg);
        }
        if (Players.isLethal(victim, dmg)) {
            e.setCancelled(true);
            UUID killer = attacker != null && !attacker.equals(victim)
                    ? attacker.getUniqueId()
                    : plugin.combat().lastAttacker(victim.getUniqueId(), plugin.cfg().combatTagSeconds);
            os().eliminate(victim, killer, OsGame.Reason.KILLED);
        }
    }

    /** Vangnet als een speler toch echt doodgaat (bijv. /kill met absorption). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!inMaps(p) || !os().isParticipant(p.getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        UUID killer = p.getKiller() != null ? p.getKiller().getUniqueId()
                : plugin.combat().lastAttacker(p.getUniqueId(), plugin.cfg().combatTagSeconds);
        os().eliminate(p, killer, OsGame.Reason.KILLED);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!os().isParticipant(p.getUniqueId())) return;
        var map = os().map();
        TeamId team = os().participantTeam(p.getUniqueId()).orElse(TeamId.KD1);
        var loc = map == null || map.spawn(team) == null ? null : map.spawn(team).toLocation();
        if (loc != null) e.setRespawnLocation(loc);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (os().isParticipant(p.getUniqueId()) && !os().isAlive(p.getUniqueId())) {
                Players.reset(p, GameMode.SPECTATOR);
            }
        });
    }

    // ------------------------------------------------------------------ regels op de map

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inMaps(e.getPlayer()) && os().isParticipant(e.getPlayer().getUniqueId()) && !plugin.cfg().osBlockBreak) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inMaps(e.getPlayer()) && os().isParticipant(e.getPlayer().getUniqueId()) && !plugin.cfg().osBlockPlace) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && inMaps(p) && os().isParticipant(p.getUniqueId()) && !plugin.cfg().osHunger
                && e.getFoodLevel() < p.getFoodLevel()) {
            e.setCancelled(true);
        }
    }
}
