package nl.oorlogsim.rollback;

import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.storage.GameMap;
import nl.oorlogsim.storage.Region;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Momentopname van een map vóór het potje (snapshots/&lt;map&gt;.schem) en terugzetten erna.
 * Met FAWE gebeurt het zware werk async; met gewone WorldEdit op de main thread.
 * Een achtergebleven bestand (crash) wordt bij opstarten teruggezet.
 */
public final class RollbackService {
    private final OorlogSim plugin;
    private final File dir;
    private final WorldEditBridge bridge;
    private final boolean async;
    /** Maps die nu worden teruggezet of waarvan terugzetten mislukte. */
    private final Set<String> busy = ConcurrentHashMap.newKeySet();

    public RollbackService(OorlogSim plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "snapshots");
        var pm = Bukkit.getPluginManager();
        boolean fawe = pm.getPlugin("FastAsyncWorldEdit") != null;
        boolean we = fawe || pm.getPlugin("WorldEdit") != null;
        WorldEditBridge b = null;
        if (we) {
            try {
                b = new WorldEditBridge();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "WorldEdit gevonden maar de API werkt niet", t);
            }
        } else {
            plugin.getLogger().warning("FastAsyncWorldEdit/WorldEdit niet gevonden: gebieden instellen en rollback werken niet.");
        }
        this.bridge = b;
        this.async = fawe;
    }

    public boolean available() {
        return bridge != null;
    }

    /** Huidige FAWE/WorldEdit-selectie van de speler, of null zonder WorldEdit. */
    public WorldEditBridge.Selection selection(org.bukkit.entity.Player p) {
        return bridge == null ? null : bridge.selection(p);
    }

    private static String key(String mapId) {
        return mapId.toLowerCase(Locale.ROOT);
    }

    public boolean isBusy(GameMap m) {
        return busy.contains(key(m.id()));
    }

    public boolean isBusy(String mapId) {
        return busy.contains(key(mapId));
    }

    private File file(String mapId) {
        return new File(dir, key(mapId) + ".schem");
    }

    public boolean hasSnapshot(String mapId) {
        return file(mapId).exists();
    }

    private void run(Runnable heavy) {
        if (async) Bukkit.getScheduler().runTaskAsynchronously(plugin, heavy);
        else heavy.run();
    }

    private void onMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    /** Slaat het gebied van de map op. Voltooit op de main thread. */
    public CompletableFuture<Void> snapshot(GameMap m) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Region r = m.region();
        World w = r == null ? null : Bukkit.getWorld(r.world());
        if (bridge == null || r == null || w == null) {
            result.completeExceptionally(new IllegalStateException("Geen WorldEdit, gebied of wereld voor map " + m.id()));
            return result;
        }
        File f = file(m.id());
        if (f.exists()) {
            result.completeExceptionally(new IllegalStateException("Er staat nog een momentopname van " + m.id() + " die niet is teruggezet"));
            return result;
        }
        run(() -> {
            try {
                bridge.save(w, r, f);
                onMain(() -> result.complete(null));
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Momentopname van map " + m.id() + " mislukt", t);
                f.delete();
                onMain(() -> result.completeExceptionally(t));
            }
        });
        return result;
    }

    /** Gooit een momentopname weg zonder terug te zetten (potje ging niet door). */
    public void discard(GameMap m) {
        File f = file(m.id());
        if (f.exists() && !f.delete()) plugin.getLogger().warning("Kon " + f + " niet verwijderen");
    }

    /** Zet de map terug. done(true/false) wordt op de main thread aangeroepen. */
    public void restore(GameMap m, Consumer<Boolean> done) {
        File f = file(m.id());
        Region r = m.region();
        String worldName = r != null ? r.world() : plugin.cfg().mapsWorld;
        World w = Bukkit.getWorld(worldName);
        if (bridge == null || !f.exists() || w == null) {
            if (w == null && f.exists()) plugin.getLogger().warning("Wereld " + worldName + " niet geladen; map " + m.id() + " niet teruggezet");
            if (f.exists()) busy.add(key(m.id()));
            done.accept(false);
            return;
        }
        busy.add(key(m.id()));
        if (r != null) clearEntities(w, r);
        run(() -> {
            try {
                bridge.paste(w, f);
                onMain(() -> {
                    busy.remove(key(m.id()));
                    if (!f.delete()) plugin.getLogger().warning("Kon " + f + " niet verwijderen");
                    plugin.getLogger().info("Map " + m.id() + " teruggezet.");
                    done.accept(true);
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Terugzetten van map " + m.id() + " mislukt; bestand blijft bewaard: " + f, t);
                onMain(() -> done.accept(false));
            }
        });
    }

    /** Losse items, pijlen, TNT e.d. in het gebied weghalen. */
    private static void clearEntities(World w, Region r) {
        BoundingBox box = new BoundingBox(r.minX(), r.minY(), r.minZ(), r.maxX() + 1, r.maxY() + 1, r.maxZ() + 1);
        for (Entity e : w.getNearbyEntities(box)) {
            if (e instanceof Item || e instanceof Projectile || e instanceof TNTPrimed || e instanceof FallingBlock
                    || e instanceof ExperienceOrb || e instanceof AreaEffectCloud) {
                e.remove();
            }
        }
    }

    /** Bij opstarten: achtergebleven momentopnames (crash) terugzetten. */
    public void restorePending() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".schem"));
        if (files == null) return;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - ".schem".length());
            GameMap m = plugin.maps().get(id).orElse(null);
            if (m == null) {
                plugin.getLogger().warning("Momentopname " + f.getName() + " hoort bij een onbekende map; niet teruggezet.");
                continue;
            }
            plugin.getLogger().info("Achtergebleven momentopname gevonden, map " + m.id() + " wordt teruggezet...");
            restore(m, ok -> { });
        }
    }
}
