package nl.oorlogsim.gui;

import net.kyori.adventure.text.format.TextDecoration;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.storage.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Menu in de lobby om op een map te stemmen. */
public final class MapVoteGui extends Menu {
    private final OorlogSim plugin;
    private final List<GameMap> maps;

    public MapVoteGui(OorlogSim plugin) {
        this.plugin = plugin;
        this.maps = plugin.maps().playable().stream().limit(54).toList();
        this.inventory = Bukkit.createInventory(this, rows(maps.size()) * 9, plugin.messages().get("lobby.vote-title"));
        for (int i = 0; i < maps.size(); i++) {
            GameMap m = maps.get(i);
            ItemStack it = new ItemStack(Material.FILLED_MAP);
            it.editMeta(meta -> {
                meta.displayName(plugin.messages().parse("<yellow>" + m.displayName()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                meta.lore(List.of(plugin.messages().get("lobby.vote-lore", "votes", String.valueOf(plugin.os().votes().count(m.id())))
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
            });
            inventory.setItem(i, it);
        }
    }

    public static void open(OorlogSim plugin, Player p) {
        if (plugin.maps().playable().isEmpty()) {
            plugin.messages().send(p, "lobby.no-maps");
            return;
        }
        p.openInventory(new MapVoteGui(plugin).getInventory());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot < 0 || slot >= maps.size() || !(e.getWhoClicked() instanceof Player p)) return;
        plugin.os().vote(p, maps.get(slot));
        p.closeInventory();
    }
}
