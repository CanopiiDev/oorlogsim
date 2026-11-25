package nl.oorlogsim.game.logic;

/** De twee kingdoms. KD1 = standaard Merovia, KD2 = standaard Eldoria (instelbaar). */
public enum TeamId {
    KD1, KD2;

    public TeamId other() {
        return this == KD1 ? KD2 : KD1;
    }

    public static TeamId parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
