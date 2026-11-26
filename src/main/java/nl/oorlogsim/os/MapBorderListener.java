package nl.oorlogsim.os;

import net.kyori.adventure.text.Component;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.storage.Region;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tijdens een OS: de rand van het map-gebied is een muur (van bodem tot bouwlimiet),
 * en buiten het gebied kan niets gebroken of geplaatst worden, ook niet door explosies of vloeistof.
 */
public final class MapBorderListener implements Listener {
    private final OorlogSim plugin;
    private final Map<UUID, Long> lastWarning = new ConcurrentHashMap<>();

    public MapBorderListener(OorlogSim plugin) {
        this.plugin = plugin;
    }

    /** Gebied als deze speler meedoet aan het lopende potje en in die wereld staat. */
    private Region regionFor(Player p) {
        if (!plugin.cfg().borderEnabled) return null;
        Region r = plugin.os().activeRegion();
        if (r == null || !plugin.os().isParticipant(p.getUniqueId())) return null;
        return p.getWorld().getName().equals(r.world()) ? r : null;
    }

    private Region activeIn(org.bukkit.World w) {
        Region r = plugin.os().activeRegion();
        return r != null && w.getName().equals(r.world()) ? r : null;
    }

    private static boolean inside(Region r, Block b) {
        return r.contains(b.getX(), b.getY(), b.getZ());
    }

    private void warn(Player p, String key) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastWarning.put(p.getUniqueId(), now);
        Component c = plugin.messages().get(key);
        p.sendActionBar(c);
    }

    // ------------------------------------------------------------------ muur

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to.getX() == from.getX() && to.getZ() == from.getZ()) return;
        Player p = e.getPlayer();
        Region r = regionFor(p);
        if (r == null || r.containsXZ(to.getX(), to.getZ())) return;
        if (r.containsXZ(from.getX(), from.getZ())) {
            // De stap gaat gewoon niet door: je loopt tegen de muur.
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
            p.setVelocity(p.getVelocity().setX(0).setZ(0));
            warn(p, "match.border");
        } else {
            // Al buiten het gebied (zou niet moeten): terug naar de spawn van het eigen team.
            plugin.os().participantTeam(p.getUniqueId()).ifPresent(t -> {
                var map = plugin.os().map();
                var spawn = map == null || map.spawn(t) == null ? null : map.spawn(t).toLocation();
                if (spawn != null) e.setTo(spawn);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN || cause == PlayerTeleportEvent.TeleportCause.COMMAND) return;
        Player p = e.getPlayer();
        Region r = regionFor(p);
        Location to = e.getTo();
        if (r == null || !to.getWorld().getName().equals(r.world())) return;
        if (!r.containsXZ(to.getX(), to.getZ())) {
            e.setCancelled(true);
            warn(p, "match.border");
        }
    }

    // ------------------------------------------------------------------ niet buiten de map bouwen/minen

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Region r = regionFor(e.getPlayer());
        if (r != null && !inside(r, e.getBlock())) {
            e.setCancelled(true);
            warn(e.getPlayer(), "match.build-outside");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Region r = regionFor(e.getPlayer());
        if (r != null && !inside(r, e.getBlockPlaced())) {
            e.setCancelled(true);
            warn(e.getPlayer(), "match.build-outside");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Region r = regionFor(e.getPlayer());
        if (r != null && !inside(r, e.getBlock())) {
            e.setCancelled(true);
            warn(e.getPlayer(), "match.build-outside");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        Region r = activeIn(e.getLocation().getWorld());
        if (r == null || !r.containsXZ(e.getLocation().getX(), e.getLocation().getZ())) return;
        trim(r, e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Region r = activeIn(e.getBlock().getWorld());
        if (r == null || !r.containsXZ(e.getBlock().getX(), e.getBlock().getZ())) return;
        trim(r, e.blockList());
    }

    private static void trim(Region r, List<Block> blocks) {
        blocks.removeIf(b -> !inside(r, b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Region r = activeIn(e.getBlock().getWorld());
        if (r != null && inside(r, e.getBlock()) && !inside(r, e.getToBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        Region r = activeIn(e.getBlock().getWorld());
        if (r != null && inside(r, e.getSource()) && !inside(r, e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        Region r = activeIn(e.getBlock().getWorld());
        if (r == null || !inside(r, e.getBlock())) return;
        for (Block b : e.getBlocks()) {
            if (!inside(r, b.getRelative(e.getDirection()))) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
