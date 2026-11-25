package nl.oorlogsim.display;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.oorlogsim.config.TeamSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Fallback zonder TAB: vanilla scoreboard-teams op het hoofd-scoreboard. */
public final class ScoreboardNameColorService implements NameColorService {

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private Team team(TeamSettings settings) {
        String name = "oorlogsim_" + settings.id().name().toLowerCase();
        Team t = board().getTeam(name);
        if (t == null) t = board().registerNewTeam(name);
        TextColor color = colorOf(settings.color());
        t.color(NamedTextColor.nearestTo(color));
        t.prefix(LegacyComponentSerializer.legacyAmpersand().deserialize(settings.tabPrefix()));
        return t;
    }

    static TextColor colorOf(String s) {
        if (s.startsWith("#")) {
            TextColor c = TextColor.fromHexString(s);
            if (c != null) return c;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(s.toLowerCase());
        return named == null ? NamedTextColor.WHITE : named;
    }

    @Override
    public void apply(Player player, TeamSettings settings) {
        team(settings).addEntry(player.getName());
    }

    @Override
    public void clear(Player player) {
        for (Team t : board().getTeams()) {
            if (t.getName().startsWith("oorlogsim_")) t.removeEntry(player.getName());
        }
    }

    @Override
    public void shutdown() {
        for (Team t : board().getTeams()) {
            if (t.getName().startsWith("oorlogsim_")) t.unregister();
        }
    }
}
