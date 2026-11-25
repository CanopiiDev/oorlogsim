package nl.oorlogsim.game.logic;

import java.util.Optional;

/** Bepaalt wanneer een OS voorbij is en wie er wint. */
public final class MatchOutcome {
    private MatchOutcome() {}

    /** winner == null betekent gelijkspel. */
    public record Result(TeamId winner) {
        public static Result win(TeamId team) { return new Result(team); }
        public static Result draw() { return new Result(null); }
        public boolean isDraw() { return winner == null; }
    }

    public static Optional<Result> evaluate(int aliveKD1, int aliveKD2) {
        if (aliveKD1 <= 0 && aliveKD2 <= 0) return Optional.of(Result.draw());
        if (aliveKD1 <= 0) return Optional.of(Result.win(TeamId.KD2));
        if (aliveKD2 <= 0) return Optional.of(Result.win(TeamId.KD1));
        return Optional.empty();
    }

    public static Result onTimeout(int aliveKD1, int aliveKD2) {
        if (aliveKD1 > aliveKD2) return Result.win(TeamId.KD1);
        if (aliveKD2 > aliveKD1) return Result.win(TeamId.KD2);
        return Result.draw();
    }
}
