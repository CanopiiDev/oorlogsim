package nl.oorlogsim.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Locatie die opgeslagen kan worden zonder dat de wereld al geladen is. */
public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {

    public static StoredLocation of(Location loc) {
        return new StoredLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    /** null als de wereld (nog) niet bestaat. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void write(ConfigurationSection sec) {
        sec.set("world", world);
        sec.set("x", x);
        sec.set("y", y);
        sec.set("z", z);
        sec.set("yaw", yaw);
        sec.set("pitch", pitch);
    }

    public static StoredLocation read(ConfigurationSection sec) {
        if (sec == null || !sec.contains("world")) return null;
        return new StoredLocation(sec.getString("world"), sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    public boolean sameBlock(Location loc) {
        return loc.getWorld() != null && loc.getWorld().getName().equals(world)
                && loc.getBlockX() == (int) Math.floor(x) && loc.getBlockY() == (int) Math.floor(y) && loc.getBlockZ() == (int) Math.floor(z);
    }

    public String coords() {
        return Math.round(x) + " " + Math.round(y) + " " + Math.round(z);
    }
}
