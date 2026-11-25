package nl.oorlogsim.storage;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/** De twee kits (OS en KitPvP), opgeslagen in kits.yml. Bevat inventory, armor en offhand. */
public final class KitStore {
    private final JavaPlugin plugin;
    private final YamlFile file;
    private final Map<KitType, ItemStack[]> kits = new EnumMap<>(KitType.class);

    public KitStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "kits.yml");
        load();
    }

    public void load() {
        file.reload();
        kits.clear();
        for (KitType t : KitType.values()) {
            String data = file.yml().getString("kits." + t.id());
            if (data == null) continue;
            try {
                kits.put(t, ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data)));
            } catch (Exception e) {
                plugin.getLogger().warning("Kit '" + t.id() + "' kon niet geladen worden: " + e.getMessage());
            }
        }
    }

    public void set(KitType type, Player from) {
        ItemStack[] contents = from.getInventory().getContents().clone();
        kits.put(type, contents);
        file.yml().set("kits." + type.id(), Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        file.save();
    }

    public boolean has(KitType type) {
        return kits.containsKey(type);
    }

    /** Leegt de inventory en zet de kit erin (leeg als de kit nog niet is ingesteld). */
    public void apply(KitType type, Player player) {
        player.getInventory().clear();
        ItemStack[] kit = kits.get(type);
        if (kit != null) {
            ItemStack[] copy = new ItemStack[kit.length];
            for (int i = 0; i < kit.length; i++) copy[i] = kit[i] == null ? null : kit[i].clone();
            int size = player.getInventory().getSize();
            if (copy.length != size) {
                ItemStack[] resized = new ItemStack[size];
                System.arraycopy(copy, 0, resized, 0, Math.min(size, copy.length));
                copy = resized;
            }
            player.getInventory().setContents(copy);
        }
        player.updateInventory();
    }
}
