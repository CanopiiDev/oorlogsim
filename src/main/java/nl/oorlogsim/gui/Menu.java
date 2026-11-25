package nl.oorlogsim.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Basis voor kist-menu's van OorlogSim. Klikken wordt altijd geannuleerd. */
public abstract class Menu implements InventoryHolder {
    protected Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract void onClick(InventoryClickEvent event);

    protected static int rows(int items) {
        return Math.max(1, Math.min(6, (items + 8) / 9));
    }
}
