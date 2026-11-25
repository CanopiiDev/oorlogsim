package nl.oorlogsim.inventory;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Bewaart de "eigen" staat van een speler (inventory, xp, leven, honger, effecten, gamemode)
 * zolang hij in OorlogSim-werelden is. Staat op schijf, zodat een crash niets kost.
 */
public final class SnapshotStore {
    private final JavaPlugin plugin;
    private final File dir;

    public SnapshotStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "playerdata");
        dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    /** Slaat de huidige staat op, tenzij er al een snapshot is (die is dan de echte eigen inventory). */
    public void saveIfAbsent(Player p) {
        if (has(p.getUniqueId())) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", p.getName());
        y.set("inventory", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(p.getInventory().getContents())));
        y.set("level", p.getLevel());
        y.set("exp", (double) p.getExp());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", (double) p.getSaturation());
        y.set("fire-ticks", p.getFireTicks());
        y.set("gamemode", p.getGameMode().name());
        y.set("allow-flight", p.getAllowFlight());
        y.set("effects", new ArrayList<>(p.getActivePotionEffects()));
        try {
            y.save(file(p.getUniqueId()));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Kon inventory van " + p.getName() + " niet opslaan", e);
        }
    }

    /** Zet de opgeslagen staat terug en verwijdert het snapshot. false als er geen snapshot was. */
    @SuppressWarnings("unchecked")
    public boolean restore(Player p) {
        File f = file(p.getUniqueId());
        if (!f.exists()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try {
            p.getInventory().clear();
            String inv = y.getString("inventory");
            if (inv != null) {
                ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(inv));
                int size = p.getInventory().getSize();
                if (items.length != size) {
                    ItemStack[] resized = new ItemStack[size];
                    System.arraycopy(items, 0, resized, 0, Math.min(size, items.length));
                    items = resized;
                }
                p.getInventory().setContents(items);
            }
            p.setLevel(y.getInt("level"));
            p.setExp((float) Math.max(0, Math.min(1, y.getDouble("exp"))));
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = max == null ? 20 : max.getValue();
            p.setHealth(Math.max(0.5, Math.min(maxHealth, y.getDouble("health", 20))));
            p.setFoodLevel(y.getInt("food", 20));
            p.setSaturation((float) y.getDouble("saturation", 5));
            p.setFireTicks(y.getInt("fire-ticks", 0));
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            List<PotionEffect> effects = (List<PotionEffect>) y.getList("effects", List.of());
            for (Object o : effects) if (o instanceof PotionEffect pe) p.addPotionEffect(pe);
            try {
                p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
            } catch (IllegalArgumentException ignored) {
                p.setGameMode(GameMode.SURVIVAL);
            }
            p.setAllowFlight(y.getBoolean("allow-flight", p.getGameMode() == GameMode.CREATIVE));
            p.updateInventory();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Kon inventory van " + p.getName() + " niet terugzetten; bestand blijft bewaard: " + f.getName(), e);
            return false;
        }
        if (!f.delete()) plugin.getLogger().warning("Kon " + f.getName() + " niet verwijderen");
        return true;
    }
}
