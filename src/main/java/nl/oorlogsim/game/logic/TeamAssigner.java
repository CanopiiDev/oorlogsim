package nl.oorlogsim.game.logic;

import java.util.*;

/** Deelt spelers zonder team in bij de start van een OS. */
public final class TeamAssigner {
    public enum Mode { SMALLEST, RANDOM }

    private TeamAssigner() {}

    public static Map<UUID, TeamId> assign(Map<UUID, TeamId> chosen, Collection<UUID> unassigned, Mode mode, Random rnd) {
        Map<UUID, TeamId> result = new LinkedHashMap<>(chosen);
        List<UUID> free = new ArrayList<>(unassigned);
        free.removeIf(result::containsKey);
        Collections.shuffle(free, rnd);
        for (UUID id : free) {
            TeamId team;
            if (mode == Mode.RANDOM) {
                team = rnd.nextBoolean() ? TeamId.KD1 : TeamId.KD2;
            } else {
                long kd1 = result.values().stream().filter(t -> t == TeamId.KD1).count();
                long kd2 = result.size() - kd1;
                team = kd1 < kd2 ? TeamId.KD1 : kd2 < kd1 ? TeamId.KD2 : (rnd.nextBoolean() ? TeamId.KD1 : TeamId.KD2);
            }
            result.put(id, team);
        }
        return result;
    }
}
