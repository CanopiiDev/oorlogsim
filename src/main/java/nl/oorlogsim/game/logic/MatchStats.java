package nl.oorlogsim.game.logic;

import java.util.*;

/** Kills en toegebrachte damage per speler tijdens één potje. */
public final class MatchStats {
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Double> damage = new HashMap<>();

    /** Telt alleen de schade die het slachtoffer echt kwijtraakt (overkill telt niet). */
    public void recordDamage(UUID attacker, UUID victim, double finalDamage, double victimHealthBefore) {
        double dealt = Math.min(finalDamage, victimHealthBefore);
        if (dealt <= 0) return;
        damage.merge(attacker, dealt, Double::sum);
    }

    public void recordKill(UUID killer) {
        kills.merge(killer, 1, Integer::sum);
    }

    public int kills(UUID id) {
        return kills.getOrDefault(id, 0);
    }

    public double damage(UUID id) {
        return damage.getOrDefault(id, 0.0);
    }

    /** Spelers met minstens één kill, meeste kills eerst; gelijk → meeste damage eerst. */
    public List<UUID> topKills(int n) {
        return kills.keySet().stream()
                .sorted(Comparator.comparingInt(this::kills).reversed()
                        .thenComparing(Comparator.comparingDouble(this::damage).reversed()))
                .limit(n)
                .toList();
    }
}
