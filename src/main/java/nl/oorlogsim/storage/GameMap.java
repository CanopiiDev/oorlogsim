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
    private Region region;

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

    public Region region() { return region; }
    public void region(Region region) { this.region = region; }

    /** Beide spawns gezet. */
    public boolean hasSpawns() {
        return spawns.containsKey(TeamId.KD1) && spawns.containsKey(TeamId.KD2);
    }

    /** Ligt deze spawn binnen het gebied (of is er geen gebied)? */
    public boolean spawnInRegion(TeamId team) {
        StoredLocation s = spawns.get(team);
        if (s == null) return false;
        if (region == null) return true;
        return s.world().equals(region.world()) && region.containsXZ(s.x(), s.z())
                && s.y() >= region.minY() && s.y() <= region.maxY() + 1;
    }

    /** Klaar om te spelen: beide spawns, en (indien vereist) een gebied met beide spawns erin. */
    public boolean isReady(boolean requireRegion) {
        if (!hasSpawns()) return false;
        if (region == null) return !requireRegion;
        return spawnInRegion(TeamId.KD1) && spawnInRegion(TeamId.KD2);
    }
}
