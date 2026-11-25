package nl.oorlogsim.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Alle teksten uit messages.yml. Placeholders worden als {naam} geschreven en
 * meegegeven als paren: send(speler, "lobby.voted", "map", "Kasteel").
 */
public final class Messages {
    private final JavaPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration yml;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        yml = YamlConfiguration.loadConfiguration(file);
        var in = plugin.getResource("messages.yml");
        if (in != null) {
            yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    /** Ruwe tekst met placeholders ingevuld (nog geen MiniMessage). */
    public String raw(String key, String... kv) {
        String s = yml.getString(key);
        if (s == null) {
            plugin.getLogger().warning("Bericht ontbreekt in messages.yml: " + key);
            s = key;
        }
        return fill(s, kv);
    }

    public String fill(String template, String... kv) {
        String s = template.replace("{prefix}", yml.getString("prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return s;
    }

    public Component get(String key, String... kv) {
        return mm.deserialize(raw(key, kv));
    }

    public Component parse(String miniMessage) {
        return mm.deserialize(miniMessage);
    }

    public List<Component> list(String key, String... kv) {
        List<Component> out = new ArrayList<>();
        for (String line : yml.getStringList(key)) out.add(mm.deserialize(fill(line, kv)));
        return out;
    }

    public void send(Audience to, String key, String... kv) {
        String s = raw(key, kv);
        if (!s.isEmpty()) to.sendMessage(mm.deserialize(s));
    }

    public void sendList(Audience to, String key, String... kv) {
        list(key, kv).forEach(to::sendMessage);
    }

    /** Escapet tekst die van spelers komt (bijv. namen) zodat er geen MiniMessage-tags in komen. */
    public String escape(String text) {
        return mm.escapeTags(text);
    }
}
