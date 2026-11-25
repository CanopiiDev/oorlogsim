package nl.oorlogsim.storage;

import nl.oorlogsim.game.logic.TeamId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Alle OS-maps, opgeslagen in maps.yml. Map-id's zijn hoofdletterongevoelig. */
public final class MapRegistry {
    private final YamlFile file;
    private final Map<String, GameMap> maps = new LinkedHashMap<>();

    public MapRegistry(JavaPlugin plugin) {
        this.file = new YamlFile(plugin, "maps.yml");
        load();
    }

    private static String key(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    public void load() {
        file.reload();
        maps.clear();
        ConfigurationSection root = file.yml().getConfigurationSection("maps");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            GameMap map = new GameMap(id);
            map.displayName(sec.getString("display-name", id));
            map.enabled(sec.getBoolean("enabled", true));
            for (TeamId t : TeamId.values()) {
                StoredLocation loc = StoredLocation.read(sec.getConfigurationSection("spawns." + t.name()));
                if (loc != null) map.spawn(t, loc);
            }
            maps.put(key(id), map);
        }
    }

    public void save() {
        file.yml().set("maps", null);
        for (GameMap map : maps.values()) {
            String base = "maps." + map.id();
            file.yml().set(base + ".display-name", map.displayName());
            file.yml().set(base + ".enabled", map.enabled());
            for (Map.Entry<TeamId, StoredLocation> e : map.spawns().entrySet()) {
                e.getValue().write(file.yml().createSection(base + ".spawns." + e.getKey().name()));
            }
        }
        file.save();
    }

    public Optional<GameMap> get(String id) {
        return Optional.ofNullable(maps.get(key(id)));
    }

    public GameMap getOrCreate(String id) {
        return maps.computeIfAbsent(key(id), k -> new GameMap(id));
    }

    public boolean create(String id) {
        if (maps.containsKey(key(id))) return false;
        maps.put(key(id), new GameMap(id));
        save();
        return true;
    }

    public boolean delete(String id) {
        boolean removed = maps.remove(key(id)) != null;
        if (removed) save();
        return removed;
    }

    public Collection<GameMap> all() {
        return Collections.unmodifiableCollection(maps.values());
    }

    /** Maps die aan staan en waarvan beide spawns gezet zijn. */
    public List<GameMap> playable() {
        return maps.values().stream().filter(m -> m.enabled() && m.isReady()).toList();
    }
}
