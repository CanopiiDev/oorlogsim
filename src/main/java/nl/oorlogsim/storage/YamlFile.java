package nl.oorlogsim.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Klein hulpje voor een YAML-bestand in de plugin-map. */
public final class YamlFile {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yml;

    public YamlFile(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), name);
        reload();
    }

    public void reload() {
        yml = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration yml() {
        return yml;
    }

    public void save() {
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Kon " + file.getName() + " niet opslaan", e);
        }
    }
}
