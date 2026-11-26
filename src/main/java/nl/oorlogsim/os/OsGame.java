package nl.oorlogsim.os;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.config.PluginConfig;
import nl.oorlogsim.config.TeamSettings;
import nl.oorlogsim.game.logic.*;
import nl.oorlogsim.stats.StatType;
import nl.oorlogsim.storage.GameMap;
import nl.oorlogsim.storage.KitType;
import nl.oorlogsim.storage.StoredLocation;
import nl.oorlogsim.util.Players;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * De Oorlog Simulatie: lobby (teams kiezen, stemmen), countdown, potje en einde.
 * Er loopt er altijd maximaal één tegelijk.
 */
public final class OsGame {
    public enum Reason { KILLED, LEFT }

    private final OorlogSim plugin;
    private final Random random = new Random();

    private volatile OsState state = OsState.IDLE;
    /** Gekozen teams in de lobby (voor het volgende potje). */
    private final Map<UUID, TeamId> chosen = new ConcurrentHashMap<>();
    private final MapVote votes = new MapVote();

    /** Deelnemers van het lopende potje (ook dode spectators). */
    private final Map<UUID, TeamId> participants = new ConcurrentHashMap<>();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private MatchStats stats = new MatchStats();
    private GameMap map;

    private BukkitTask countdownTask, timeLimitTask, endTask;
    private int remaining;
    private boolean shuttingDown;
    private int prepareId;

    public OsGame(OorlogSim plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ status

    public OsState state() { return state; }
    public boolean isRunning() { return state == OsState.RUNNING || state == OsState.ENDING; }
    public boolean isParticipant(UUID id) { return isRunning() && participants.containsKey(id); }
    public boolean isAlive(UUID id) { return state == OsState.RUNNING && alive.contains(id); }
    public Optional<TeamId> participantTeam(UUID id) { return isRunning() ? Optional.ofNullable(participants.get(id)) : Optional.empty(); }
    public Optional<TeamId> chosenTeam(UUID id) { return Optional.ofNullable(chosen.get(id)); }
    public MatchStats stats() { return stats; }
    public GameMap map() { return map; }
    public MapVote votes() { return votes; }

    /** Gebied van de map waarop nu gespeeld wordt (alleen tijdens RUNNING/ENDING). */
    public nl.oorlogsim.storage.Region activeRegion() {
        GameMap m = map;
        return isRunning() && m != null ? m.region() : null;
    }

    /** Team van een speler: tijdens potje het deelnemersteam, anders het gekozen lobby-team. */
    public Optional<TeamId> teamOf(UUID id) {
        Optional<TeamId> t = participantTeam(id);
        return t.isPresent() ? t : chosenTeam(id);
    }

    private PluginConfig cfg() { return plugin.cfg(); }

    private World lobbyWorld() { return Bukkit.getWorld(cfg().lobbyWorld); }

    private List<Player> lobbyPlayers() {
        World w = lobbyWorld();
        if (w == null) return List.of();
        return w.getPlayers().stream().filter(p -> !participants.containsKey(p.getUniqueId()) || !isRunning()).toList();
    }

    private List<Player> onlineParticipants() {
        List<Player> out = new ArrayList<>();
        for (UUID id : participants.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public Location lobbySpawn() {
        StoredLocation l = plugin.locations().lobby();
        Location loc = l == null ? null : l.toLocation();
        if (loc == null && lobbyWorld() != null) loc = lobbyWorld().getSpawnLocation();
        return loc;
    }

    // ------------------------------------------------------------------ lobby

    public void onEnterLobby(Player p) {
        if (participants.containsKey(p.getUniqueId()) && isRunning()) return;
        LobbyItems.prepare(plugin, p);
        TeamId team = chosen.get(p.getUniqueId());
        if (team != null) plugin.names().apply(p, cfg().team(team));
        Location spawn = lobbySpawn();
        if (spawn != null && plugin.locations().lobby() != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && p.getWorld().equals(spawn.getWorld())) p.teleport(spawn);
            });
        }
        plugin.messages().send(p, "lobby.welcome");
    }

    public void onLeaveLobby(Player p) {
        if (participants.containsKey(p.getUniqueId()) && isRunning()) return;
        chosen.remove(p.getUniqueId());
        votes.remove(p.getUniqueId());
        plugin.names().clear(p);
        checkCountdownStillPossible();
    }

    public void chooseTeam(Player p, TeamId team) {
        TeamSettings ts = cfg().team(team);
        if (chosen.get(p.getUniqueId()) == team) {
            plugin.messages().send(p, "lobby.already-in-team", "team", ts.display());
            return;
        }
        chosen.put(p.getUniqueId(), team);
        plugin.names().apply(p, ts);
        plugin.messages().send(p, "lobby.joined-team", "team", ts.display());
    }

    public void vote(Player p, GameMap m) {
        votes.vote(p.getUniqueId(), m.id());
        plugin.messages().send(p, "lobby.voted", "map", m.displayName());
    }

    // ------------------------------------------------------------------ countdown

    public void pressButton(Player p) {
        if (isRunning()) {
            plugin.messages().send(p, "lobby.match-running");
            return;
        }
        if (state == OsState.COUNTDOWN || state == OsState.PREPARING) {
            plugin.messages().send(p, "lobby.countdown-already");
            return;
        }
        startCountdown(p, p.getName());
    }

    public boolean forceStart(CommandSender sender) {
        if (state != OsState.IDLE) {
            plugin.messages().send(sender, state == OsState.COUNTDOWN || state == OsState.PREPARING ? "lobby.countdown-already" : "lobby.match-running");
            return false;
        }
        return startCountdown(sender, sender.getName());
    }

    private int minTotal() {
        return cfg().minPlayersPerTeam * 2;
    }

    private boolean startCountdown(CommandSender who, String name) {
        if (lobbyPlayers().size() < minTotal()) {
            plugin.messages().send(who, "lobby.not-enough-players", "min", String.valueOf(minTotal()));
            return false;
        }
        if (plugin.playableMaps().isEmpty()) {
            plugin.messages().send(who, "lobby.no-maps");
            return false;
        }
        state = OsState.COUNTDOWN;
        remaining = cfg().countdownSeconds;
        broadcastLobby("lobby.countdown-started", "player", plugin.messages().escape(name), "seconds", String.valueOf(remaining));
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdown, 20L, 20L);
        return true;
    }

    private void tickCountdown() {
        if (state != OsState.COUNTDOWN) return;
        if (!checkCountdownStillPossible()) return;
        remaining--;
        if (remaining <= 0) {
            cancel(countdownTask);
            countdownTask = null;
            tryStart();
            return;
        }
        if (cfg().announceAt.contains(remaining) || remaining <= 5) {
            String s = String.valueOf(remaining);
            for (Player p : lobbyPlayers()) {
                plugin.messages().send(p, "lobby.countdown", "seconds", s);
                if (cfg().titles && remaining <= 5) {
                    p.showTitle(Title.title(plugin.messages().get("lobby.countdown-title", "seconds", s), Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                }
                play(p, cfg().soundCountdown);
            }
        }
    }

    /** Breekt de countdown af als er niet genoeg spelers meer zijn. */
    private boolean checkCountdownStillPossible() {
        if (state != OsState.COUNTDOWN) return true;
        if (lobbyPlayers().size() >= minTotal()) return true;
        cancelCountdown("lobby.countdown-cancelled");
        return false;
    }

    private void cancelCountdown(String messageKey) {
        cancel(countdownTask);
        countdownTask = null;
        state = OsState.IDLE;
        broadcastLobby(messageKey);
    }

    /** Teams voor de huidige lobby-spelers, of null als een team te klein is. */
    private Map<UUID, TeamId> assignTeams() {
        Map<UUID, TeamId> picked = new HashMap<>();
        List<UUID> free = new ArrayList<>();
        for (Player p : lobbyPlayers()) {
            TeamId t = chosen.get(p.getUniqueId());
            if (t != null) picked.put(p.getUniqueId(), t);
            else free.add(p.getUniqueId());
        }
        Map<UUID, TeamId> teams = TeamAssigner.assign(picked, free, cfg().autoAssign, random);
        long kd1 = teams.values().stream().filter(t -> t == TeamId.KD1).count();
        long kd2 = teams.size() - kd1;
        return kd1 < cfg().minPlayersPerTeam || kd2 < cfg().minPlayersPerTeam ? null : teams;
    }

    private void tryStart() {
        if (assignTeams() == null) {
            cancelCountdown("lobby.countdown-cancelled");
            return;
        }
        List<String> ids = plugin.playableMaps().stream().map(GameMap::id).toList();
        Optional<String> winner = cfg().mapSelection == PluginConfig.MapSelection.VOTE
                ? votes.winner(ids, random)
                : (ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(random.nextInt(ids.size()))));
        Optional<GameMap> chosenMap = winner.flatMap(plugin.maps()::get);
        if (chosenMap.isEmpty()) {
            cancelCountdown("lobby.no-maps");
            return;
        }
        GameMap m = chosenMap.get();
        boolean rollback = cfg().rollbackEnabled && plugin.rollback().available() && m.region() != null;
        if (!rollback) {
            if (cfg().rollbackEnabled && m.region() != null) {
                plugin.getLogger().warning("Rollback staat aan maar WorldEdit/FAWE ontbreekt: map " + m.id() + " wordt niet teruggezet.");
            }
            launch(m);
            return;
        }
        state = OsState.PREPARING;
        int id = ++prepareId;
        broadcastLobby("lobby.preparing", "map", m.displayName());
        plugin.rollback().snapshot(m).whenComplete((ok, error) -> {
            if (state != OsState.PREPARING || id != prepareId) { // gestopt tijdens het klaarzetten
                if (error == null) plugin.rollback().discard(m);
                return;
            }
            if (error != null) {
                state = OsState.IDLE;
                broadcastLobby("lobby.prepare-failed");
                return;
            }
            launch(m);
        });
    }

    /** Teams opnieuw bepalen (er kan iemand weg zijn) en het potje starten. */
    private void launch(GameMap m) {
        Map<UUID, TeamId> teams = assignTeams();
        Location spawn1 = m.spawn(TeamId.KD1) == null ? null : m.spawn(TeamId.KD1).toLocation();
        Location spawn2 = m.spawn(TeamId.KD2) == null ? null : m.spawn(TeamId.KD2).toLocation();
        if (teams == null || spawn1 == null || spawn2 == null) {
            if (state == OsState.PREPARING) plugin.rollback().discard(m);
            cancelCountdown(teams == null ? "lobby.countdown-cancelled" : "lobby.no-maps");
            return;
        }
        start(m, teams, spawn1, spawn2);
    }

    private void start(GameMap m, Map<UUID, TeamId> teams, Location spawn1, Location spawn2) {
        map = m;
        stats = new MatchStats();
        participants.clear();
        alive.clear();
        names.clear();
        plugin.combat().clearAll();
        state = OsState.RUNNING;
        TeamSettings t1 = cfg().team(TeamId.KD1), t2 = cfg().team(TeamId.KD2);
        for (Map.Entry<UUID, TeamId> e : teams.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            participants.put(p.getUniqueId(), e.getValue());
            alive.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
            chosen.remove(p.getUniqueId());
            votes.remove(p.getUniqueId());
        }
        for (Player p : onlineParticipants()) {
            TeamId team = participants.get(p.getUniqueId());
            plugin.names().apply(p, cfg().team(team));
            p.teleport(team == TeamId.KD1 ? spawn1 : spawn2);
            Players.reset(p, GameMode.SURVIVAL);
            plugin.kits().apply(KitType.OS, p);
            plugin.legacyPvp().update(p);
            plugin.stats().add(p.getUniqueId(), p.getName(), StatType.OS_GAMES, 1);
            applyBorder(p);
            plugin.messages().send(p, "match.started", "map", m.displayName());
            if (cfg().titles) {
                p.showTitle(Title.title(plugin.messages().get("match.start-title"),
                        plugin.messages().get("match.start-subtitle", "team1", t1.display(), "team2", t2.display())));
            }
            play(p, cfg().soundStart);
        }
        if (cfg().maxDurationSeconds > 0) {
            timeLimitTask = Bukkit.getScheduler().runTaskLater(plugin, this::onTimeLimit, cfg().maxDurationSeconds * 20L);
        }
        checkOutcome();
    }

    // ------------------------------------------------------------------ in het potje

    public void recordDamage(Player attacker, Player victim, double finalDamage) {
        if (state != OsState.RUNNING) return;
        stats.recordDamage(attacker.getUniqueId(), victim.getUniqueId(), finalDamage, victim.getHealth() + victim.getAbsorptionAmount());
        double dealt = Math.min(finalDamage, victim.getHealth() + victim.getAbsorptionAmount());
        if (dealt > 0) plugin.stats().add(attacker.getUniqueId(), attacker.getName(), StatType.OS_DAMAGE, dealt);
        plugin.combat().hit(victim.getUniqueId(), attacker.getUniqueId());
    }

    /** Speler is dood (of weg). killer mag null zijn. */
    public void eliminate(Player p, UUID killer, Reason reason) {
        if (state != OsState.RUNNING || !alive.remove(p.getUniqueId())) return;
        plugin.combat().clear(p.getUniqueId());
        TeamSettings vt = cfg().team(participants.get(p.getUniqueId()));
        String victim = "<color:" + vt.color() + ">" + plugin.messages().escape(p.getName()) + "</color>";
        if (killer != null && participants.containsKey(killer) && !killer.equals(p.getUniqueId())) {
            stats.recordKill(killer);
            String killerName = names.getOrDefault(killer, "?");
            plugin.stats().add(killer, killerName, StatType.OS_KILLS, 1);
            TeamSettings kt = cfg().team(participants.get(killer));
            String k = "<color:" + kt.color() + ">" + plugin.messages().escape(killerName) + "</color>";
            broadcastMatch("match.killed-by", "victim", victim, "killer", k);
        } else {
            broadcastMatch(reason == Reason.LEFT ? "match.left" : "match.died", "victim", victim);
        }
        if (reason == Reason.KILLED) {
            p.getInventory().clear();
            Players.reset(p, GameMode.SPECTATOR);
            plugin.messages().send(p, "match.you-are-spectator");
        } else {
            participants.remove(p.getUniqueId());
            plugin.names().clear(p);
            clearBorder(p);
        }
        checkOutcome();
    }

    /** Uitloggen of de maps-wereld verlaten tijdens het potje. */
    public void onParticipantGone(Player p) {
        if (!participants.containsKey(p.getUniqueId())) return;
        if (state == OsState.RUNNING && alive.contains(p.getUniqueId())) {
            UUID killer = plugin.combat().lastAttacker(p.getUniqueId(), cfg().combatTagSeconds);
            eliminate(p, killer, Reason.LEFT);
        } else {
            participants.remove(p.getUniqueId());
            plugin.names().clear(p);
            clearBorder(p);
        }
    }

    private void checkOutcome() {
        if (state != OsState.RUNNING) return;
        int a1 = 0, a2 = 0;
        for (UUID id : alive) {
            if (participants.get(id) == TeamId.KD1) a1++;
            else a2++;
        }
        MatchOutcome.evaluate(a1, a2).ifPresent(this::end);
    }

    private void onTimeLimit() {
        if (state != OsState.RUNNING) return;
        int a1 = 0, a2 = 0;
        for (UUID id : alive) {
            if (participants.get(id) == TeamId.KD1) a1++;
            else a2++;
        }
        broadcastMatch("match.timeout");
        end(MatchOutcome.onTimeout(a1, a2));
    }

    private void end(MatchOutcome.Result result) {
        state = OsState.ENDING;
        cancel(timeLimitTask);
        timeLimitTask = null;
        List<Player> online = onlineParticipants();
        if (result.isDraw()) {
            broadcastMatch("match.draw");
        } else {
            TeamSettings ts = cfg().team(result.winner());
            broadcastMatch("match.win", "team", ts.display());
            for (Map.Entry<UUID, TeamId> e : participants.entrySet()) {
                if (e.getValue() == result.winner()) {
                    plugin.stats().add(e.getKey(), names.getOrDefault(e.getKey(), "?"), StatType.OS_WINS, 1);
                }
            }
        }
        for (Player p : online) {
            if (cfg().titles) {
                Component title = result.isDraw() ? plugin.messages().get("match.draw-title")
                        : plugin.messages().get("match.win-title", "team", cfg().team(result.winner()).display());
                Component sub = result.isDraw() ? Component.empty() : plugin.messages().get("match.win-subtitle");
                p.showTitle(Title.title(title, sub));
            }
            play(p, cfg().soundWin);
            sendStats(p);
        }
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::returnToLobby, Math.max(1, cfg().endDelaySeconds * 20L));
    }

    private void sendStats(Player p) {
        var msg = plugin.messages();
        UUID id = p.getUniqueId();
        msg.sendList(p, "match.stats", "kills", String.valueOf(stats.kills(id)), "damage", plugin.formatDamage(stats.damage(id)));
        if (cfg().showTop) {
            List<UUID> top = stats.topKills(cfg().topSize);
            if (!top.isEmpty()) {
                msg.send(p, "match.top-header");
                for (int i = 0; i < top.size(); i++) {
                    UUID u = top.get(i);
                    msg.send(p, "match.top-line", "rank", String.valueOf(i + 1),
                            "player", msg.escape(names.getOrDefault(u, "?")), "kills", String.valueOf(stats.kills(u)));
                }
            }
        }
        msg.send(p, "match.stats-footer");
    }

    private void returnToLobby() {
        cancel(endTask);
        endTask = null;
        List<Player> online = onlineParticipants();
        GameMap played = map;
        participants.clear();
        alive.clear();
        names.clear();
        plugin.combat().clearAll();
        state = OsState.IDLE;
        map = null;
        Location lobby = lobbySpawn();
        for (Player p : online) {
            plugin.names().clear(p);
            clearBorder(p);
            if (lobby != null && p.getWorld().getName().equals(cfg().mapsWorld)) {
                p.teleport(lobby);
            } else if (p.getWorld().getName().equals(cfg().lobbyWorld)) {
                onEnterLobby(p);
            }
        }
        // Bij afsluiten blijft de momentopname staan en wordt hij bij de volgende start teruggezet.
        if (played != null && !shuttingDown && plugin.rollback().hasSnapshot(played.id())) {
            plugin.rollback().restore(played, ok -> { });
        }
    }

    private void applyBorder(Player p) {
        nl.oorlogsim.storage.Region r = map == null ? null : map.region();
        if (r == null || !cfg().borderEnabled || !cfg().borderVisual) return;
        org.bukkit.WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(r.centerX(), r.centerZ());
        border.setSize(r.borderSize());
        border.setWarningDistance(0);
        p.setWorldBorder(border);
    }

    private static void clearBorder(Player p) {
        if (p.getWorldBorder() != null) p.setWorldBorder(null);
    }

    /** Admin-stop: countdown of potje afbreken zonder winnaar. */
    public boolean stop() {
        switch (state) {
            case COUNTDOWN, PREPARING -> {
                cancelCountdown("match.stopped");
                return true;
            }
            case RUNNING -> {
                state = OsState.ENDING;
                cancel(timeLimitTask);
                timeLimitTask = null;
                broadcastMatch("match.stopped");
                returnToLobby();
                return true;
            }
            case ENDING -> {
                returnToLobby();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Bij uitschakelen van de plugin: iedereen netjes terug. */
    public void shutdown() {
        shuttingDown = true;
        cancel(countdownTask);
        if (isRunning()) {
            state = OsState.ENDING;
            cancel(timeLimitTask);
            returnToLobby();
        }
        state = OsState.IDLE;
    }

    // ------------------------------------------------------------------ helpers

    private void broadcastLobby(String key, String... kv) {
        for (Player p : lobbyPlayers()) plugin.messages().send(p, key, kv);
    }

    private void broadcastMatch(String key, String... kv) {
        for (Player p : onlineParticipants()) plugin.messages().send(p, key, kv);
    }

    private static void play(Player p, Sound s) {
        if (s != null) p.playSound(p.getLocation(), s, 1f, 1f);
    }

    private static void cancel(BukkitTask t) {
        if (t != null) t.cancel();
    }
}
