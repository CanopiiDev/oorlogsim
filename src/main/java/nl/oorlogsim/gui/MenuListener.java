package nl.oorlogsim.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) menu.onClick(e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }
}
