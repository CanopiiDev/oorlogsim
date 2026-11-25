package nl.oorlogsim.game.logic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KillStreakTest {
    @Test
    void streakCountsUpAndResets() {
        KillStreaks k = new KillStreaks();
        UUID p = UUID.randomUUID();
        assertEquals(0, k.current(p));
        assertEquals(1, k.kill(p));
        assertEquals(2, k.kill(p));
        k.reset(p);
        assertEquals(0, k.current(p));
        assertEquals(1, k.kill(p));
    }
}
