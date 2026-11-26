package nl.oorlogsim.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionTest {
    private final Region r = Region.of("os_maps", 10, 0, 20, -10, 100, -20);

    @Test
    void normalisesCorners() {
        assertEquals(-10, r.minX());
        assertEquals(10, r.maxX());
        assertEquals(-20, r.minZ());
        assertEquals(20, r.maxZ());
        assertEquals(0, r.minY());
        assertEquals(100, r.maxY());
    }

    @Test
    void blockContainsIsInclusive() {
        assertTrue(r.contains(-10, 0, -20));
        assertTrue(r.contains(10, 100, 20));
        assertFalse(r.contains(11, 50, 0));
        assertFalse(r.contains(0, -1, 0));
        assertFalse(r.contains(0, 101, 0));
    }

    @Test
    void containsXZCoversWholeEdgeBlocks() {
        assertTrue(r.containsXZ(-10.0, -20.0));
        assertTrue(r.containsXZ(10.99, 20.99));
        assertFalse(r.containsXZ(11.0, 0));
        assertFalse(r.containsXZ(-10.01, 0));
        assertFalse(r.containsXZ(0, 21.0));
    }

    @Test
    void borderFitsAroundRectangle() {
        // breedte 21 (x -10..10), diepte 41 (z -20..20)
        assertEquals(0.5, r.centerX(), 1e-9);
        assertEquals(0.5, r.centerZ(), 1e-9);
        assertEquals(41.0, r.borderSize(), 1e-9);
    }
}
