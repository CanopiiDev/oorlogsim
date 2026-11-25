package nl.oorlogsim.storage;

import nl.oorlogsim.game.logic.TeamId;

import java.util.EnumMap;
import java.util.Map;

/** Een OS-arena: een gebied in de maps-wereld met een spawn per kingdom. */
public final class GameMap {
    private final String id;
    private String displayName;
    private boolean enabled = true;
    private final EnumMap<TeamId, StoredLocation> spawns = new EnumMap<>(TeamId.class);

    public GameMap(String id) {
        this.id = id;
        this.displayName = id;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void displayName(String name) { this.displayName = name; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean enabled) { this.enabled = enabled; }
    public StoredLocation spawn(TeamId team) { return spawns.get(team); }
    public void spawn(TeamId team, StoredLocation loc) { spawns.put(team, loc); }
    public Map<TeamId, StoredLocation> spawns() { return spawns; }

    /** Beide spawns gezet. */
    public boolean isReady() {
        return spawns.containsKey(TeamId.KD1) && spawns.containsKey(TeamId.KD2);
    }
}
