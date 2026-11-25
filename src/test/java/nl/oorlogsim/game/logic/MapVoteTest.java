package nl.oorlogsim.game.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MapVoteTest {
    @Test
    void mostVotesWins() {
        MapVote v = new MapVote();
        v.vote(UUID.randomUUID(), "map1");
        v.vote(UUID.randomUUID(), "map2");
        v.vote(UUID.randomUUID(), "map2");
        assertEquals(Optional.of("map2"), v.winner(List.of("map1", "map2", "map3"), new Random(0)));
        assertEquals(2, v.count("map2"));
    }

    @Test
    void revotingMovesTheVote() {
        MapVote v = new MapVote();
        UUID p = UUID.randomUUID();
        v.vote(p, "map1");
        v.vote(p, "map2");
        assertEquals(0, v.count("map1"));
        assertEquals(1, v.count("map2"));
        v.remove(p);
        assertEquals(0, v.count("map2"));
    }

    @Test
    void votesForRemovedMapAreIgnored() {
        MapVote v = new MapVote();
        v.vote(UUID.randomUUID(), "gone");
        v.vote(UUID.randomUUID(), "gone");
        v.vote(UUID.randomUUID(), "map1");
        assertEquals(Optional.of("map1"), v.winner(List.of("map1", "map2"), new Random(0)));
    }

    @Test
    void noVotesPicksACandidate() {
        MapVote v = new MapVote();
        String w = v.winner(List.of("a", "b"), new Random(5)).orElseThrow();
        assertTrue(w.equals("a") || w.equals("b"));
        assertTrue(v.winner(List.of(), new Random(5)).isEmpty());
    }

    @Test
    void namesAreCaseInsensitive() {
        MapVote v = new MapVote();
        v.vote(UUID.randomUUID(), "Map1");
        assertEquals(1, v.count("map1"));
        assertEquals(Optional.of("map1"), v.winner(List.of("map1", "map2"), new Random(0)));
    }
}
