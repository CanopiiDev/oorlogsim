package nl.oorlogsim.kitpvp;

import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.game.logic.KillStreaks;
import nl.oorlogsim.stats.StatType;
import nl.oorlogsim.storage.KitType;
import nl.oorlogsim.storage.StoredLocation;
import nl.oorlogsim.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/** KitPvP: iedereen tegen iedereen, continu, direct respawnen met de KitPvP-kit. */
public final class KitPvpManager {
    private final OorlogSim plugin;
    private final KillStreaks streaks = new KillStreaks();

    public KitPvpManager(OorlogSim plugin) {
        this.plugin = plugin;
    }

    public World world() {
        return Bukkit.getWorld(plugin.cfg().kitpvpWorld);
    }

    public boolean isIn(Player p) {
        return p.getWorld().getName().equals(plugin.cfg().kitpvpWorld);
    }

    public Location spawnLocation() {
        StoredLocation s = plugin.locations().randomKitpvpSpawn();
        Location loc = s == null ? null : s.toLocation();
        if (loc == null && world() != null) loc = world().getSpawnLocation();
        return loc;
    }

    public int streak(UUID id) {
        return streaks.current(id);
    }

    public void onEnter(Player p) {
        streaks.reset(p.getUniqueId());
        equip(p);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && isIn(p)) Players.teleport(p, spawnLocation());
        });
        plugin.messages().send(p, "kitpvp.welcome");
    }

    public void onLeave(Player p) {
        streaks.reset(p.getUniqueId());
        plugin.combat().clear(p.getUniqueId());
    }

    private void equip(Player p) {
        Players.reset(p, GameMode.SURVIVAL);
        plugin.kits().apply(KitType.KITPVP, p);
        plugin.legacyPvp().update(p);
    }

    /** Speler is "dood": stats bijwerken, berichten, direct respawnen. */
    public void handleDeath(Player victim, Player killer) {
        UUID vid = victim.getUniqueId();
        plugin.stats().add(vid, victim.getName(), StatType.KITPVP_DEATHS, 1);
        streaks.reset(vid);
        plugin.combat().clear(vid);
        var msg = plugin.messages();
        String victimName = "<white>" + msg.escape(victim.getName()) + "</white>";
        if (killer != null && !killer.equals(victim)) {
            int streak = streaks.kill(killer.getUniqueId());
            plugin.stats().add(killer.getUniqueId(), killer.getName(), StatType.KITPVP_KILLS, 1);
            plugin.stats().max(killer.getUniqueId(), killer.getName(), StatType.KITPVP_BEST_STREAK, streak);
            if (plugin.cfg().kitpvpHealOnKill) {
                killer.setHealth(Players.maxHealth(killer));
            }
            broadcast("kitpvp.killed-by", "victim", victimName, "killer", msg.escape(killer.getName()), "streak", String.valueOf(streak));
            int from = plugin.cfg().kitpvpAnnounceStreakFrom;
            if (from > 0 && streak >= from) {
                broadcast("kitpvp.streak", "player", msg.escape(killer.getName()), "streak", String.valueOf(streak));
            }
        } else {
            broadcast("kitpvp.died", "victim", victimName);
        }
        equip(victim);
        Players.teleport(victim, spawnLocation());
    }

    private void broadcast(String key, String... kv) {
        World w = world();
        if (w == null) return;
        for (Player p : w.getPlayers()) plugin.messages().send(p, key, kv);
    }
}
