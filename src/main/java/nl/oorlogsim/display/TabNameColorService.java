package nl.oorlogsim.display;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import nl.oorlogsim.config.TeamSettings;
import org.bukkit.entity.Player;

/**
 * Zet via de TAB-API een tijdelijke prefix. clear() wist de tijdelijke waarde,
 * zodat TAB weer de normale (LuckPerms-)prefix toont.
 * Alleen laden als de TAB-plugin aanwezig is.
 */
public final class TabNameColorService implements NameColorService {

    private TabPlayer tab(Player p) {
        TabAPI api = TabAPI.getInstance();
        return api == null ? null : api.getPlayer(p.getUniqueId());
    }

    @Override
    public void apply(Player player, TeamSettings team) {
        TabPlayer tp = tab(player);
        if (tp == null) return;
        TabAPI api = TabAPI.getInstance();
        if (api.getTabListFormatManager() != null) api.getTabListFormatManager().setPrefix(tp, team.tabPrefix());
        if (api.getNameTagManager() != null) api.getNameTagManager().setPrefix(tp, team.nametagPrefix());
    }

    @Override
    public void clear(Player player) {
        TabPlayer tp = tab(player);
        if (tp == null) return;
        TabAPI api = TabAPI.getInstance();
        if (api.getTabListFormatManager() != null) api.getTabListFormatManager().setPrefix(tp, null);
        if (api.getNameTagManager() != null) api.getNameTagManager().setPrefix(tp, null);
    }
}
