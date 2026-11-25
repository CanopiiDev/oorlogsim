package nl.oorlogsim.game.logic;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TeamAssignerTest {
    private static UUID id() { return UUID.randomUUID(); }

    @Test
    void smallestFillsSmallerTeamFirst() {
        Map<UUID, TeamId> chosen = new HashMap<>();
        for (int i = 0; i < 3; i++) chosen.put(id(), TeamId.KD1);
        chosen.put(id(), TeamId.KD2);
        List<UUID> free = List.of(id(), id());
        Map<UUID, TeamId> result = TeamAssigner.assign(chosen, free, TeamAssigner.Mode.SMALLEST, new Random(1));
        assertEquals(6, result.size());
        assertEquals(3, result.values().stream().filter(t -> t == TeamId.KD1).count());
        assertEquals(3, result.values().stream().filter(t -> t == TeamId.KD2).count());
        free.forEach(u -> assertEquals(TeamId.KD2, result.get(u)));
    }

    @Test
    void chosenTeamsAreNeverChanged() {
        Map<UUID, TeamId> chosen = new HashMap<>();
        UUID a = id(), b = id();
        chosen.put(a, TeamId.KD1);
        chosen.put(b, TeamId.KD1);
        for (TeamAssigner.Mode mode : TeamAssigner.Mode.values()) {
            Map<UUID, TeamId> r = TeamAssigner.assign(chosen, List.of(id()), mode, new Random(7));
            assertEquals(TeamId.KD1, r.get(a));
            assertEquals(TeamId.KD1, r.get(b));
            assertEquals(3, r.size());
        }
    }

    @Test
    void randomAssignsEveryUnassignedPlayer() {
        List<UUID> free = new ArrayList<>();
        for (int i = 0; i < 20; i++) free.add(id());
        Map<UUID, TeamId> r = TeamAssigner.assign(Map.of(), free, TeamAssigner.Mode.RANDOM, new Random(3));
        assertEquals(20, r.size());
        assertTrue(r.containsValue(TeamId.KD1));
        assertTrue(r.containsValue(TeamId.KD2));
    }

    @Test
    void otherTeam() {
        assertEquals(TeamId.KD2, TeamId.KD1.other());
        assertEquals(TeamId.KD1, TeamId.KD2.other());
    }
}
