package nl.oorlogsim.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.game.logic.TeamId;
import nl.oorlogsim.stats.StatType;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Optional;

/** %oorlogsim_...% placeholders. Alleen geladen als PlaceholderAPI aanwezig is. */
public final class OorlogSimExpansion extends PlaceholderExpansion {
    private final OorlogSim plugin;

    public OorlogSimExpansion(OorlogSim plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "oorlogsim"; }
    @Override public String getAuthor() { return "OorlogSim"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.equals("state")) return plugin.os().state().name().toLowerCase(Locale.ROOT);
        if (player == null) return "";
        Optional<TeamId> team = plugin.os().teamOf(player.getUniqueId());
        switch (p) {
            case "team":
                return team.map(t -> plugin.cfg().team(t).name()).orElse("");
            case "teamcolor":
                return team.map(t -> {
                    String prefix = plugin.cfg().team(t).nametagPrefix();
                    return prefix == null ? "" : prefix;
                }).orElse("");
            case "kills":
                return String.valueOf(plugin.os().stats().kills(player.getUniqueId()));
            case "damage":
                return plugin.formatDamage(plugin.os().stats().damage(player.getUniqueId()));
            default:
                break;
        }
        if (p.startsWith("stat_")) {
            try {
                StatType t = StatType.valueOf(p.substring(5).toUpperCase(Locale.ROOT));
                double v = plugin.stats().get(player.getUniqueId(), t);
                return t == StatType.OS_DAMAGE ? plugin.formatDamage(v) : String.valueOf((long) v);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
