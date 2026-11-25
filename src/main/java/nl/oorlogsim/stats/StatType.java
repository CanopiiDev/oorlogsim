package nl.oorlogsim.stats;

import java.util.Locale;

public enum StatType {
    OS_KILLS, OS_WINS, OS_DAMAGE, OS_GAMES, KITPVP_KILLS, KITPVP_DEATHS, KITPVP_BEST_STREAK;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
