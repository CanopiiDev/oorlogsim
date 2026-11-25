package nl.oorlogsim.config;

import nl.oorlogsim.game.logic.TeamAssigner;
import nl.oorlogsim.game.logic.TeamId;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Getypeerde weergave van config.yml. Wordt opnieuw aangemaakt bij /os admin reload. */
public final class PluginConfig {
    public enum MapSelection { VOTE, RANDOM }
    public enum DamageDisplay { HEARTS, HP }

    public final String lobbyWorld, mapsWorld, kitpvpWorld;
    public final Map<TeamId, TeamSettings> teams = new EnumMap<>(TeamId.class);

    public final int slotKD1, slotKD2, voteSlot;
    public final boolean voteItemEnabled, lobbyClearInventory, lobbyInvulnerable;
    public final Material voteMaterial;

    public final int countdownSeconds, minPlayersPerTeam, combatTagSeconds, topSize, endDelaySeconds, maxDurationSeconds;
    public final TeamAssigner.Mode autoAssign;
    public final MapSelection mapSelection;
    public final boolean friendlyFire, osBlockBreak, osBlockPlace, osHunger, showTop, isolateChat, titles;
    public final DamageDisplay damageDisplay;
    public final Sound soundCountdown, soundStart, soundWin;
    public final Set<Integer> announceAt = new HashSet<>();

    public final boolean kitpvpHealOnKill, kitpvpBlockBreak, kitpvpBlockPlace, kitpvpHunger;
    public final int kitpvpAnnounceStreakFrom;

    public final int leaderboardSize, leaderboardRefreshSeconds, leaderboardLineWidth;
    public final boolean protectInventories;

    public PluginConfig(FileConfiguration c, Logger log) {
        lobbyWorld = c.getString("worlds.lobby", "os_lobby");
        mapsWorld = c.getString("worlds.maps", "os_maps");
        kitpvpWorld = c.getString("worlds.kitpvp", "kitpvp");

        for (TeamId id : TeamId.values()) {
            String p = "teams." + id.name() + ".";
            boolean kd1 = id == TeamId.KD1;
            teams.put(id, new TeamSettings(id,
                    c.getString(p + "name", kd1 ? "Merovia" : "Eldoria"),
                    c.getString(p + "color", kd1 ? "blue" : "green"),
                    material(c.getString(p + "join-item"), kd1 ? Material.BLUE_WOOL : Material.GREEN_WOOL, log),
                    c.getString(p + "tab-prefix", kd1 ? "&9[Merovia] " : "&a[Eldoria] "),
                    c.getString(p + "nametag-prefix", kd1 ? "&9" : "&a")));
        }

        slotKD1 = slot(c.getInt("lobby.items.KD1-slot", 2));
        slotKD2 = slot(c.getInt("lobby.items.KD2-slot", 6));
        voteItemEnabled = c.getBoolean("lobby.items.vote.enabled", true);
        voteSlot = slot(c.getInt("lobby.items.vote.slot", 4));
        voteMaterial = material(c.getString("lobby.items.vote.material"), Material.PAPER, log);
        lobbyClearInventory = c.getBoolean("lobby.clear-inventory", true);
        lobbyInvulnerable = c.getBoolean("lobby.invulnerable", true);

        countdownSeconds = Math.max(1, c.getInt("os.countdown-seconds", 30));
        minPlayersPerTeam = Math.max(1, c.getInt("os.min-players-per-team", 1));
        autoAssign = enumOr(TeamAssigner.Mode.class, c.getString("os.auto-assign"), TeamAssigner.Mode.SMALLEST, log);
        mapSelection = enumOr(MapSelection.class, c.getString("os.map-selection"), MapSelection.VOTE, log);
        friendlyFire = c.getBoolean("os.friendly-fire", false);
        osBlockBreak = c.getBoolean("os.allow-block-break", false);
        osBlockPlace = c.getBoolean("os.allow-block-place", false);
        osHunger = c.getBoolean("os.hunger", true);
        combatTagSeconds = Math.max(0, c.getInt("os.combat-tag-seconds", 10));
        damageDisplay = enumOr(DamageDisplay.class, c.getString("os.damage-display"), DamageDisplay.HEARTS, log);
        showTop = c.getBoolean("os.show-top", true);
        topSize = Math.max(1, c.getInt("os.top-size", 3));
        endDelaySeconds = Math.max(0, c.getInt("os.end-delay-seconds", 5));
        maxDurationSeconds = Math.max(0, c.getInt("os.max-duration-seconds", 0));
        isolateChat = c.getBoolean("os.isolate-chat", true);
        titles = c.getBoolean("os.titles", true);
        soundCountdown = sound(c.getString("os.sounds.countdown"), log);
        soundStart = sound(c.getString("os.sounds.start"), log);
        soundWin = sound(c.getString("os.sounds.win"), log);
        announceAt.addAll(c.getIntegerList("os.announce-at"));

        kitpvpHealOnKill = c.getBoolean("kitpvp.heal-on-kill", false);
        kitpvpBlockBreak = c.getBoolean("kitpvp.allow-block-break", false);
        kitpvpBlockPlace = c.getBoolean("kitpvp.allow-block-place", false);
        kitpvpHunger = c.getBoolean("kitpvp.hunger", true);
        kitpvpAnnounceStreakFrom = Math.max(0, c.getInt("kitpvp.announce-streak-from", 5));

        leaderboardSize = Math.max(1, c.getInt("leaderboards.size", 10));
        leaderboardRefreshSeconds = Math.max(5, c.getInt("leaderboards.refresh-seconds", 60));
        leaderboardLineWidth = Math.max(50, c.getInt("leaderboards.line-width", 200));
        protectInventories = c.getBoolean("protect-inventories", true);
    }

    public TeamSettings team(TeamId id) {
        return teams.get(id);
    }

    private static int slot(int s) {
        return Math.max(0, Math.min(8, s));
    }

    static Material material(String name, Material def, Logger log) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            log.warning("Onbekend item '" + name + "', gebruik " + def);
            return def;
        }
        return m;
    }

    /** Accepteert Bukkit-namen (BLOCK_NOTE_BLOCK_HAT) of keys (block.note_block.hat). */
    static Sound sound(String name, Logger log) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("none")) return null;
        if (name.contains(".") || name.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            Sound s = key == null ? null : Registry.SOUNDS.get(key);
            if (s == null) log.warning("Onbekend geluid '" + name + "'");
            return s;
        }
        try {
            Object value = Sound.class.getField(name.trim().toUpperCase(Locale.ROOT)).get(null);
            if (value instanceof Sound s) return s;
        } catch (ReflectiveOperationException ignored) {
        }
        log.warning("Onbekend geluid '" + name + "'");
        return null;
    }

    static <E extends Enum<E>> E enumOr(Class<E> type, String value, E def, Logger log) {
        if (value == null) return def;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Onbekende waarde '" + value + "' voor " + type.getSimpleName() + ", gebruik " + def);
            return def;
        }
    }
}
