package nl.oorlogsim.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.config.Messages;
import nl.oorlogsim.game.logic.TeamId;
import nl.oorlogsim.storage.GameMap;
import nl.oorlogsim.storage.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Admin-overzicht van alle maps: welke spawns gezet zijn, aan/uit, en teleporteren. */
public final class MapOverviewGui extends Menu {
    private final OorlogSim plugin;
    private final List<GameMap> maps;

    public MapOverviewGui(OorlogSim plugin) {
        this.plugin = plugin;
        this.maps = new ArrayList<>(plugin.maps().all()).stream().limit(54).toList();
        this.inventory = Bukkit.createInventory(this, rows(maps.size()) * 9, plugin.messages().get("admin.overview-title"));
        fill();
    }

    private void fill() {
        Messages msg = plugin.messages();
        inventory.clear();
        for (int i = 0; i < maps.size(); i++) {
            GameMap m = maps.get(i);
            Material mat = !m.isReady() ? Material.RED_CONCRETE : m.enabled() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
            ItemStack it = new ItemStack(mat);
            String status = m.isReady() ? msg.raw("admin.overview-ready") : msg.raw("admin.overview-missing");
            List<Component> lore = msg.list("admin.overview-lore",
                    "team1", plugin.cfg().team(TeamId.KD1).display(),
                    "team2", plugin.cfg().team(TeamId.KD2).display(),
                    "kd1", m.spawn(TeamId.KD1) != null ? msg.raw("admin.overview-check") : msg.raw("admin.overview-cross"),
                    "kd2", m.spawn(TeamId.KD2) != null ? msg.raw("admin.overview-check") : msg.raw("admin.overview-cross"),
                    "enabled", m.enabled() ? msg.raw("admin.overview-on") : msg.raw("admin.overview-off"));
            it.editMeta(meta -> {
                meta.displayName(msg.parse("<yellow>" + m.displayName() + " <gray>(" + m.id() + ") " + status)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
                meta.lore(lore.stream().map(c -> c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
            });
            inventory.setItem(i, it);
        }
    }

    public static void open(OorlogSim plugin, Player p) {
        p.openInventory(new MapOverviewGui(plugin).getInventory());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot < 0 || slot >= maps.size() || !(e.getWhoClicked() instanceof Player p)) return;
        GameMap m = maps.get(slot);
        if (e.isShiftClick()) {
            m.enabled(!m.enabled());
            plugin.maps().save();
            plugin.messages().send(p, m.enabled() ? "admin.map-enabled" : "admin.map-disabled", "map", m.id());
            fill();
            return;
        }
        TeamId team = e.isRightClick() ? TeamId.KD2 : TeamId.KD1;
        StoredLocation sl = m.spawn(team);
        Location loc = sl == null ? null : sl.toLocation();
        if (loc == null) {
            plugin.messages().send(p, "general.not-set", "what", "Spawn " + team.name() + " van " + m.id());
            return;
        }
        p.closeInventory();
        p.teleport(loc);
        plugin.messages().send(p, "admin.teleported");
    }
}
