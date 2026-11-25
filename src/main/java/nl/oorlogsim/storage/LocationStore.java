package nl.oorlogsim.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Lobby-spawn, startknop en KitPvP-spawns (locations.yml). */
public final class LocationStore {
    private final YamlFile file;
    private StoredLocation lobby;
    private StoredLocation button;
    private final List<StoredLocation> kitpvpSpawns = new ArrayList<>();
    private final Random random = new Random();

    public LocationStore(JavaPlugin plugin) {
        this.file = new YamlFile(plugin, "locations.yml");
        load();
    }

    public void load() {
        file.reload();
        lobby = StoredLocation.read(file.yml().getConfigurationSection("lobby"));
        button = StoredLocation.read(file.yml().getConfigurationSection("button"));
        kitpvpSpawns.clear();
        ConfigurationSection sec = file.yml().getConfigurationSection("kitpvp-spawns");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                StoredLocation l = StoredLocation.read(sec.getConfigurationSection(k));
                if (l != null) kitpvpSpawns.add(l);
            }
        }
    }

    private void save() {
        file.yml().set("lobby", null);
        file.yml().set("button", null);
        file.yml().set("kitpvp-spawns", null);
        if (lobby != null) lobby.write(file.yml().createSection("lobby"));
        if (button != null) button.write(file.yml().createSection("button"));
        for (int i = 0; i < kitpvpSpawns.size(); i++) {
            kitpvpSpawns.get(i).write(file.yml().createSection("kitpvp-spawns." + i));
        }
        file.save();
    }

    public StoredLocation lobby() { return lobby; }
    public void lobby(StoredLocation l) { lobby = l; save(); }
    public StoredLocation button() { return button; }
    public void button(StoredLocation l) { button = l; save(); }

    public List<StoredLocation> kitpvpSpawns() { return Collections.unmodifiableList(kitpvpSpawns); }
    public int addKitpvpSpawn(StoredLocation l) { kitpvpSpawns.add(l); save(); return kitpvpSpawns.size(); }
    public void clearKitpvpSpawns() { kitpvpSpawns.clear(); save(); }

    public StoredLocation randomKitpvpSpawn() {
        if (kitpvpSpawns.isEmpty()) return null;
        return kitpvpSpawns.get(random.nextInt(kitpvpSpawns.size()));
    }
}
