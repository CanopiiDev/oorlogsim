package nl.oorlogsim.os;

import net.kyori.adventure.text.format.TextDecoration;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.config.PluginConfig;
import nl.oorlogsim.config.TeamSettings;
import nl.oorlogsim.game.logic.TeamId;
import nl.oorlogsim.util.Players;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** De items in de hotbar van de OS-lobby. Herkend via een PDC-tag, niet via materiaal. */
public final class LobbyItems {
    public static final String TEAM_KD1 = "team_kd1";
    public static final String TEAM_KD2 = "team_kd2";
    public static final String VOTE = "vote";

    private LobbyItems() {}

    public static NamespacedKey key(OorlogSim plugin) {
        return new NamespacedKey(plugin, "lobby_item");
    }

    public static void prepare(OorlogSim plugin, Player p) {
        PluginConfig cfg = plugin.cfg();
        if (cfg.lobbyClearInventory) p.getInventory().clear();
        Players.reset(p, GameMode.ADVENTURE);
        for (TeamId id : TeamId.values()) {
            TeamSettings ts = cfg.team(id);
            int slot = id == TeamId.KD1 ? cfg.slotKD1 : cfg.slotKD2;
            p.getInventory().setItem(slot, item(plugin, ts.joinItem(), id == TeamId.KD1 ? TEAM_KD1 : TEAM_KD2,
                    plugin.messages().raw("lobby.item-name", "team", ts.display())));
        }
        if (cfg.voteItemEnabled && cfg.mapSelection == PluginConfig.MapSelection.VOTE) {
            p.getInventory().setItem(cfg.voteSlot, item(plugin, cfg.voteMaterial, VOTE, plugin.messages().raw("lobby.vote-item-name")));
        }
        p.updateInventory();
    }

    private static ItemStack item(OorlogSim plugin, org.bukkit.Material material, String tag, String name) {
        ItemStack it = new ItemStack(material);
        it.editMeta(meta -> {
            meta.displayName(plugin.messages().parse(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, tag);
        });
        return it;
    }

    /** Tag van een lobby-item, of null. */
    public static String tagOf(OorlogSim plugin, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
    }
}
