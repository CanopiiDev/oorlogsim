package nl.oorlogsim.config;

import nl.oorlogsim.game.logic.TeamId;
import org.bukkit.Material;

/** Instellingen van één kingdom uit config.yml. */
public record TeamSettings(TeamId id, String name, String color, Material joinItem, String tabPrefix, String nametagPrefix) {

    /** MiniMessage-tekst van de teamnaam in teamkleur, bijv. {@code <color:blue>Merovia</color>}. */
    public String display() {
        return "<color:" + color + ">" + name + "</color>";
    }
}
