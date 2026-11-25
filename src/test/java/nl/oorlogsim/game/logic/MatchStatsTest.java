package nl.oorlogsim.game.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchStatsTest {
    @Test
    void overkillIsCappedAtRemainingHealth() {
        MatchStats s = new MatchStats();
        UUID a = UUID.randomUUID(), v = UUID.randomUUID();
        s.recordDamage(a, v, 5.0, 2.0);
        assertEquals(2.0, s.damage(a), 1e-9);
        s.recordDamage(a, v, 3.0, 20.0);
        assertEquals(5.0, s.damage(a), 1e-9);
    }

    @Test
    void negativeOrZeroDamageIsIgnored() {
        MatchStats s = new MatchStats();
        UUID a = UUID.randomUUID();
        s.recordDamage(a, UUID.randomUUID(), -1, 20);
        s.recordDamage(a, UUID.randomUUID(), 4, 0);
        assertEquals(0.0, s.damage(a), 1e-9);
    }

    @Test
    void killsAndTopOrderingTieBrokenByDamage() {
        MatchStats s = new MatchStats();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        s.recordKill(a); s.recordKill(a);
        s.recordKill(b); s.recordKill(b);
        s.recordKill(c);
        s.recordDamage(b, c, 10, 20);
        s.recordDamage(a, c, 4, 20);
        assertEquals(2, s.kills(a));
        assertEquals(0, s.kills(UUID.randomUUID()));
        assertEquals(List.of(b, a, c), s.topKills(3));
        assertEquals(List.of(b, a), s.topKills(2));
    }

    @Test
    void topKillsOnlyListsPlayersWithKills() {
        MatchStats s = new MatchStats();
        UUID a = UUID.randomUUID();
        s.recordDamage(a, UUID.randomUUID(), 3, 20);
        assertTrue(s.topKills(3).isEmpty());
    }
}
