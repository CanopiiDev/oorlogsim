package nl.oorlogsim.os;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.game.logic.TeamId;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;

/**
 * Teamchat tijdens een OS: berichten van deelnemers gaan alleen naar het eigen team.
 * EssentialsChat (of een andere chatplugin) blijft de opmaak doen; wij beperken alleen de ontvangers
 * en zetten de teamtag ervoor.
 */
public final class OsChatListener implements Listener {
    private final OorlogSim plugin;

    public OsChatListener(OorlogSim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        OsGame os = plugin.os();
        if (!os.isRunning()) return;
        Player sender = e.getPlayer();
        Optional<TeamId> team = os.participantTeam(sender.getUniqueId());
        if (team.isPresent()) {
            TeamId t = team.get();
            e.viewers().removeIf(a -> a instanceof Player p && os.participantTeam(p.getUniqueId()).map(x -> x != t).orElse(plugin.cfg().isolateChat));
            Component tag = plugin.messages().get("match.team-chat-prefix", "team", plugin.cfg().team(t).display());
            ChatRenderer original = e.renderer();
            e.renderer((source, displayName, message, viewer) -> tag.append(original.render(source, displayName, message, viewer)));
        } else if (plugin.cfg().isolateChat) {
            e.viewers().removeIf((Audience a) -> a instanceof Player p && os.isParticipant(p.getUniqueId()));
        }
    }
}
