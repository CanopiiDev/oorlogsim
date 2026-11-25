package nl.oorlogsim.stats;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum LeaderboardType {
    TOP_KILLS("top-kills", StatType.OS_KILLS),
    TOP_WINS("top-wins", StatType.OS_WINS),
    TOP_DAMAGE("top-damage", StatType.OS_DAMAGE),
    KITPVP_KILLS("kitpvp-kills", StatType.KITPVP_KILLS),
    KITPVP_STREAK("kitpvp-streak", StatType.KITPVP_BEST_STREAK);

    private final String id;
    private final StatType stat;

    LeaderboardType(String id, StatType stat) {
        this.id = id;
        this.stat = stat;
    }

    public String id() { return id; }
    public StatType stat() { return stat; }

    public static LeaderboardType parse(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT);
        for (LeaderboardType t : values()) if (t.id.equals(k)) return t;
        return null;
    }

    public static String ids() {
        return Arrays.stream(values()).map(LeaderboardType::id).collect(Collectors.joining(", "));
    }
}
