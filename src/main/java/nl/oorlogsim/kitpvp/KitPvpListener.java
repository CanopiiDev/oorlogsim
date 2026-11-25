package nl.oorlogsim.kitpvp;

import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public final class KitPvpListener implements Listener {
    private final OorlogSim plugin;

    public KitPvpListener(OorlogSim plugin) {
        this.plugin = plugin;
    }

    private KitPvpManager kp() {
        return plugin.kitpvp();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !kp().isIn(victim)) return;
        if (victim.getGameMode() == org.bukkit.GameMode.CREATIVE || victim.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Player attacker = Players.attacker(e);
        if (attacker != null && !attacker.equals(victim)) plugin.combat().hit(victim.getUniqueId(), attacker.getUniqueId());
        if (Players.isLethal(victim, e.getFinalDamage())) {
            e.setCancelled(true);
            kp().handleDeath(victim, killerOf(victim, attacker));
        }
    }

    private Player killerOf(Player victim, Player attacker) {
        if (attacker != null && !attacker.equals(victim)) return attacker;
        UUID last = plugin.combat().lastAttacker(victim.getUniqueId(), plugin.cfg().combatTagSeconds);
        Player p = last == null ? null : Bukkit.getPlayer(last);
        return p != null && kp().isIn(p) ? p : null;
    }

    /** Vangnet voor echte doden. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!kp().isIn(p)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        Player killer = killerOf(p, p.getKiller());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        });
        pendingKiller.put(p.getUniqueId(), killer == null ? null : killer.getUniqueId());
    }

    private final java.util.Map<UUID, UUID> pendingKiller = new java.util.HashMap<>();

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!pendingKiller.containsKey(p.getUniqueId())) return;
        UUID killerId = pendingKiller.remove(p.getUniqueId());
        var loc = kp().spawnLocation();
        if (loc != null) e.setRespawnLocation(loc);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || !kp().isIn(p)) return;
            Player killer = killerId == null ? null : Bukkit.getPlayer(killerId);
            kp().handleDeath(p, killer);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (kp().isIn(e.getPlayer()) && !plugin.cfg().kitpvpBlockBreak && !e.getPlayer().hasPermission("oorlogsim.admin")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (kp().isIn(e.getPlayer()) && !plugin.cfg().kitpvpBlockPlace && !e.getPlayer().hasPermission("oorlogsim.admin")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && kp().isIn(p) && !plugin.cfg().kitpvpHunger && e.getFoodLevel() < p.getFoodLevel()) {
            e.setCancelled(true);
        }
    }
}
