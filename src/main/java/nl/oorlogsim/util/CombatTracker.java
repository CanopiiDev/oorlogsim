package nl.oorlogsim.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Onthoudt wie een speler het laatst raakte, voor kill-toekenning. */
public final class CombatTracker {
    private record Hit(UUID attacker, long time) {}

    private final Map<UUID, Hit> lastHit = new ConcurrentHashMap<>();

    public void hit(UUID victim, UUID attacker) {
        lastHit.put(victim, new Hit(attacker, System.currentTimeMillis()));
    }

    /** Laatste aanvaller binnen het aantal seconden, of null. */
    public UUID lastAttacker(UUID victim, int withinSeconds) {
        Hit h = lastHit.get(victim);
        if (h == null || System.currentTimeMillis() - h.time > withinSeconds * 1000L) return null;
        return h.attacker;
    }

    public void clear(UUID victim) {
        lastHit.remove(victim);
    }

    public void clearAll() {
        lastHit.clear();
    }
}
