package nl.oorlogsim.storage;

/** Rechthoekig gebied (blokcoördinaten, randen inbegrepen) in één wereld. */
public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Maakt een gebied uit twee willekeurige hoeken. */
    public static Region of(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Region(world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /** Ligt dit blok in het gebied? */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Ligt deze positie (bijv. van een speler) binnen de randen van het gebied, ongeacht de hoogte? */
    public boolean containsXZ(double x, double z) {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    /** Zijde van de (vierkante) world border die het hele gebied omvat. */
    public double borderSize() {
        return Math.max(maxX - minX + 1, maxZ - minZ + 1);
    }

    public String describe() {
        return minX + " " + minY + " " + minZ + " → " + maxX + " " + maxY + " " + maxZ;
    }
}
