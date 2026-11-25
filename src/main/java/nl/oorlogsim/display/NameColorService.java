package nl.oorlogsim.display;

import nl.oorlogsim.config.TeamSettings;
import org.bukkit.entity.Player;

/** Kleurt de naam van een speler in TAB-lijst en boven het hoofd. */
public interface NameColorService {
    void apply(Player player, TeamSettings team);

    void clear(Player player);

    default void shutdown() {}
}
