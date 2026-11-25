package nl.oorlogsim.stats;

import net.kyori.adventure.text.Component;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.storage.StoredLocation;
import nl.oorlogsim.storage.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Zwevende leaderboards (TextDisplay-entities). De entities zijn niet persistent:
 * ze worden bij opstarten, chunk-load en elke refresh opnieuw gemaakt als ze ontbreken.
 */
public final class LeaderboardManager implements Listener {
    public record Board(String id, LeaderboardType type, StoredLocation location) {}

    private final OorlogSim plugin;
    private final YamlFile file;
    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final Map<String, UUID> entities = new HashMap<>();
    private BukkitTask task;

    public LeaderboardManager(OorlogSim plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "leaderboards.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        restartTask();
    }

    private void load() {
        boards.clear();
        ConfigurationSection sec = file.yml().getConfigurationSection("leaderboards");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            LeaderboardType type = LeaderboardType.parse(sec.getString(id + ".type"));
            StoredLocation loc = StoredLocation.read(sec.getConfigurationSection(id + ".location"));
            if (type != null && loc != null) boards.put(id.toLowerCase(Locale.ROOT), new Board(id, type, loc));
        }
    }

    private void save() {
        file.yml().set("leaderboards", null);
        for (Board b : boards.values()) {
            file.yml().set("leaderboards." + b.id() + ".type", b.type().id());
            b.location().write(file.yml().createSection("leaderboards." + b.id() + ".location"));
        }
        file.save();
    }

    public void restartTask() {
        if (task != null) task.cancel();
        long period = plugin.cfg().leaderboardRefreshSeconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, period);
    }

    /** Nieuw id als er geen is opgegeven: type, type-2, type-3, ... */
    public String freeId(LeaderboardType type) {
        String base = type.id();
        if (!boards.containsKey(base)) return base;
        int i = 2;
        while (boards.containsKey(base + "-" + i)) i++;
        return base + "-" + i;
    }

    public void create(String id, LeaderboardType type, Location loc) {
        remove(id);
        Board b = new Board(id, type, StoredLocation.of(loc));
        boards.put(id.toLowerCase(Locale.ROOT), b);
        save();
        refresh(b);
    }

    public boolean remove(String id) {
        Board b = boards.remove(id.toLowerCase(Locale.ROOT));
        despawn(id.toLowerCase(Locale.ROOT));
        if (b != null) save();
        return b != null;
    }

    public Collection<Board> list() {
        return Collections.unmodifiableCollection(boards.values());
    }

    private void despawn(String key) {
        UUID uuid = entities.remove(key);
        if (uuid != null) {
            var e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }
    }

    public void refreshAll() {
        for (Board b : boards.values()) refresh(b);
    }

    private Component render(Board b) {
        var msg = plugin.messages();
        List<StatsService.Entry> top = plugin.stats().top(b.type().stat(), plugin.cfg().leaderboardSize);
        Component text = msg.get("leaderboard.titles." + b.type().id());
        for (int i = 0; i < plugin.cfg().leaderboardSize; i++) {
            text = text.append(Component.newline());
            if (i < top.size()) {
                StatsService.Entry e = top.get(i);
                String value = b.type() == LeaderboardType.TOP_DAMAGE
                        ? plugin.formatDamage(e.value())
                        : String.valueOf((long) e.value());
                text = text.append(msg.get("leaderboard.line", "rank", String.valueOf(i + 1),
                        "player", msg.escape(e.name() == null ? "?" : e.name()), "value", value));
            } else {
                text = text.append(msg.get("leaderboard.empty-line", "rank", String.valueOf(i + 1)));
            }
        }
        return text;
    }

    private void refresh(Board b) {
        Location loc = b.location().toLocation();
        if (loc == null || !loc.isChunkLoaded()) return;
        String key = b.id().toLowerCase(Locale.ROOT);
        UUID uuid = entities.get(key);
        var existing = uuid == null ? null : Bukkit.getEntity(uuid);
        Component text = render(b);
        if (existing instanceof TextDisplay td && td.isValid()) {
            td.text(text);
            return;
        }
        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setLineWidth(plugin.cfg().leaderboardLineWidth);
            d.text(text);
        });
        entities.put(key, td.getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (Board b : boards.values()) {
            StoredLocation l = b.location();
            if (l.world().equals(e.getWorld().getName())
                    && ((int) Math.floor(l.x())) >> 4 == e.getChunk().getX()
                    && ((int) Math.floor(l.z())) >> 4 == e.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, () -> refresh(b));
            }
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (String key : new ArrayList<>(entities.keySet())) despawn(key);
    }
}
