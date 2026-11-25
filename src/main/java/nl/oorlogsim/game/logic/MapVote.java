package nl.oorlogsim.game.logic;

import java.util.*;

/** Stemmen op maps in de lobby. Mapnamen zijn hoofdletterongevoelig. */
public final class MapVote {
    private final Map<UUID, String> votes = new HashMap<>();

    private static String key(String map) {
        return map.toLowerCase(Locale.ROOT);
    }

    public void vote(UUID player, String map) {
        votes.put(player, key(map));
    }

    public void remove(UUID player) {
        votes.remove(player);
    }

    public void clear() {
        votes.clear();
    }

    public Optional<String> voteOf(UUID player) {
        return Optional.ofNullable(votes.get(player));
    }

    public int count(String map) {
        String k = key(map);
        return (int) votes.values().stream().filter(k::equals).count();
    }

    /** Kandidaat met de meeste stemmen; gelijk of geen stemmen → willekeurig onder de koplopers. */
    public Optional<String> winner(Collection<String> candidates, Random rnd) {
        if (candidates.isEmpty()) return Optional.empty();
        int best = -1;
        List<String> leaders = new ArrayList<>();
        for (String c : candidates) {
            int n = count(c);
            if (n > best) {
                best = n;
                leaders.clear();
            }
            if (n == best) leaders.add(c);
        }
        return Optional.of(leaders.get(rnd.nextInt(leaders.size())));
    }
}
