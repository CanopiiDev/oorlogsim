package nl.oorlogsim.storage;

import java.util.Locale;

public enum KitType {
    OS, KITPVP;

    public static KitType parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
