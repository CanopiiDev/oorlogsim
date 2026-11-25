package nl.oorlogsim.game.logic;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MatchOutcomeTest {
    @Test
    void bothEmptyIsDraw() {
        assertEquals(Optional.of(MatchOutcome.Result.draw()), MatchOutcome.evaluate(0, 0));
    }

    @Test
    void emptyTeamLoses() {
        assertEquals(Optional.of(MatchOutcome.Result.win(TeamId.KD2)), MatchOutcome.evaluate(0, 3));
        assertEquals(Optional.of(MatchOutcome.Result.win(TeamId.KD1)), MatchOutcome.evaluate(1, 0));
    }

    @Test
    void bothAliveContinues() {
        assertTrue(MatchOutcome.evaluate(2, 2).isEmpty());
    }

    @Test
    void timeoutMostAliveWins() {
        assertEquals(MatchOutcome.Result.win(TeamId.KD1), MatchOutcome.onTimeout(2, 1));
        assertEquals(MatchOutcome.Result.draw(), MatchOutcome.onTimeout(2, 2));
    }
}
