package nl.oorlogsim.game.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Huidige killstreaks in KitPvP. */
public final class KillStreaks {
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public int kill(UUID player) {
        return streaks.merge(player, 1, Integer::sum);
    }

    public void reset(UUID player) {
        streaks.remove(player);
    }

    public int current(UUID player) {
        return streaks.getOrDefault(player, 0);
    }
}
