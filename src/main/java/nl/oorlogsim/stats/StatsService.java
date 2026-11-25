package nl.oorlogsim.stats;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * All-time stats per speler. In het geheugen, weggeschreven naar stats.yml
 * (asynchroon elke minuut als er iets veranderd is, en synchroon bij afsluiten).
 */
public final class StatsService {
    public record Entry(UUID id, String name, double value) {}

    private static final class PlayerStats {
        volatile String name;
        final Map<StatType, Double> values = new ConcurrentHashMap<>();
        PlayerStats(String name) { this.name = name; }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public StatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) saveAsync(); }, 1200L, 1200L);
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection sec = players.getConfigurationSection(key);
                if (sec == null) continue;
                PlayerStats ps = new PlayerStats(sec.getString("name", "?"));
                for (StatType t : StatType.values()) {
                    if (sec.contains(t.key())) ps.values.put(t, sec.getDouble(t.key()));
                }
                stats.put(id, ps);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            String base = "players." + e.getKey();
            y.set(base + ".name", e.getValue().name);
            for (Map.Entry<StatType, Double> v : e.getValue().values.entrySet()) {
                y.set(base + "." + v.getKey().key(), v.getValue());
            }
        }
        return y.saveToString();
    }

    private void write(String data) {
        try {
            file.getParentFile().mkdirs();
            File tmp = new File(file.getParentFile(), "stats.yml.tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Kon stats.yml niet opslaan", e);
        }
    }

    public void saveAsync() {
        dirty = false;
        String data = serialize();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    public void saveSync() {
        dirty = false;
        write(serialize());
    }

    private PlayerStats of(UUID id, String name) {
        PlayerStats ps = stats.computeIfAbsent(id, k -> new PlayerStats(name));
        if (name != null) ps.name = name;
        return ps;
    }

    public void add(UUID id, String name, StatType type, double amount) {
        of(id, name).values.merge(type, amount, Double::sum);
        dirty = true;
    }

    /** Zet de waarde alleen als die hoger is dan de huidige (bijv. beste killstreak). */
    public void max(UUID id, String name, StatType type, double value) {
        of(id, name).values.merge(type, value, Math::max);
        dirty = true;
    }

    public double get(UUID id, StatType type) {
        PlayerStats ps = stats.get(id);
        return ps == null ? 0 : ps.values.getOrDefault(type, 0.0);
    }

    public Optional<UUID> findByName(String name) {
        return stats.entrySet().stream()
                .filter(e -> e.getValue().name != null && e.getValue().name.equalsIgnoreCase(name))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public String nameOf(UUID id) {
        PlayerStats ps = stats.get(id);
        return ps == null ? null : ps.name;
    }

    public List<Entry> top(StatType type, int n) {
        return stats.entrySet().stream()
                .filter(e -> e.getValue().values.getOrDefault(type, 0.0) > 0)
                .map(e -> new Entry(e.getKey(), e.getValue().name, e.getValue().values.get(type)))
                .sorted(Comparator.comparingDouble(Entry::value).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                .limit(n)
                .toList();
    }
}
