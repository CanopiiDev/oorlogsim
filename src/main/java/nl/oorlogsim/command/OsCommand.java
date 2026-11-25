package nl.oorlogsim.command;

import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.config.Messages;
import nl.oorlogsim.game.logic.TeamId;
import nl.oorlogsim.gui.MapOverviewGui;
import nl.oorlogsim.stats.LeaderboardManager;
import nl.oorlogsim.stats.LeaderboardType;
import nl.oorlogsim.stats.StatType;
import nl.oorlogsim.storage.GameMap;
import nl.oorlogsim.storage.KitType;
import nl.oorlogsim.storage.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/** /os en /os admin ... */
public final class OsCommand implements TabExecutor {
    private static final String ADMIN = "oorlogsim.admin";
    private final OorlogSim plugin;

    public OsCommand(OorlogSim plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            msg().sendList(sender, "help");
            if (sender.hasPermission(ADMIN)) msg().sendList(sender, "help-admin");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "lobby" -> teleportToWorld(sender, "oorlogsim.lobby", plugin.cfg().lobbyWorld, true);
            case "kitpvp" -> teleportToWorld(sender, "oorlogsim.kitpvp", plugin.cfg().kitpvpWorld, false);
            case "stats" -> stats(sender, args);
            case "admin" -> {
                if (!sender.hasPermission(ADMIN)) {
                    msg().send(sender, "general.no-permission");
                    return true;
                }
                admin(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> msg().send(sender, "general.unknown-command");
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        msg().send(s, "general.players-only");
        return null;
    }

    private void teleportToWorld(CommandSender sender, String perm, String worldName, boolean lobby) {
        Player p = player(sender);
        if (p == null) return;
        if (!p.hasPermission(perm)) {
            msg().send(p, "general.no-permission");
            return;
        }
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            msg().send(p, "general.world-missing", "world", worldName);
            return;
        }
        Location target = lobby ? plugin.os().lobbySpawn() : plugin.kitpvp().spawnLocation();
        p.teleport(target != null ? target : w.getSpawnLocation());
    }

    private void stats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("oorlogsim.stats")) {
            msg().send(sender, "general.no-permission");
            return;
        }
        UUID id;
        String name;
        if (args.length >= 2) {
            name = args[1];
            Player online = Bukkit.getPlayerExact(name);
            Optional<UUID> found = online != null ? Optional.of(online.getUniqueId()) : plugin.stats().findByName(name);
            if (found.isEmpty()) {
                msg().send(sender, "stats.unknown-player", "player", msg().escape(name));
                return;
            }
            id = found.get();
            String stored = plugin.stats().nameOf(id);
            if (stored != null) name = stored;
        } else {
            Player p = player(sender);
            if (p == null) return;
            id = p.getUniqueId();
            name = p.getName();
        }
        List<String> kv = new ArrayList<>(List.of("player", msg().escape(name)));
        for (StatType t : StatType.values()) {
            double v = plugin.stats().get(id, t);
            kv.add(t.key());
            kv.add(t == StatType.OS_DAMAGE ? plugin.formatDamage(v) : String.valueOf((long) v));
        }
        String[] arr = kv.toArray(String[]::new);
        msg().send(sender, "stats.header", arr);
        msg().sendList(sender, "stats.lines", arr);
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String[] a) {
        if (a.length == 0) {
            msg().sendList(sender, "help-admin");
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "maps" -> {
                Player p = player(sender);
                if (p != null) MapOverviewGui.open(plugin, p);
            }
            case "map" -> map(sender, a);
            case "setspawn" -> setSpawn(sender, a);
            case "tp" -> tp(sender, a);
            case "setlobby" -> setLobby(sender);
            case "setbutton" -> setButton(sender);
            case "setkit" -> kit(sender, a, true);
            case "givekit" -> kit(sender, a, false);
            case "kitpvp" -> kitpvp(sender, a);
            case "setleaderboard" -> setLeaderboard(sender, a);
            case "removeleaderboard" -> {
                if (a.length < 2) { msg().sendList(sender, "help-admin"); return; }
                msg().send(sender, plugin.leaderboards().remove(a[1]) ? "admin.leaderboard-removed" : "admin.leaderboard-unknown", "id", a[1]);
            }
            case "leaderboards" -> {
                msg().send(sender, "admin.leaderboards-header");
                for (LeaderboardManager.Board b : plugin.leaderboards().list()) {
                    StoredLocation l = b.location();
                    msg().send(sender, "admin.leaderboards-line", "id", b.id(), "type", b.type().id(), "world", l.world(),
                            "x", String.valueOf(Math.round(l.x())), "y", String.valueOf(Math.round(l.y())), "z", String.valueOf(Math.round(l.z())));
                }
            }
            case "start" -> {
                if (plugin.os().forceStart(sender)) msg().send(sender, "admin.started");
            }
            case "stop" -> msg().send(sender, plugin.os().stop() ? "admin.stopped" : "admin.nothing-to-stop");
            case "reload" -> {
                plugin.reloadAll();
                msg().send(sender, "general.reloaded");
            }
            default -> msg().sendList(sender, "help-admin");
        }
    }

    private void map(CommandSender sender, String[] a) {
        if (a.length < 3) {
            msg().sendList(sender, "help-admin");
            return;
        }
        String action = a[1].toLowerCase(Locale.ROOT);
        String id = a[2];
        switch (action) {
            case "create" -> msg().send(sender, plugin.maps().create(id) ? "admin.map-created" : "admin.map-exists", "map", id);
            case "delete" -> msg().send(sender, plugin.maps().delete(id) ? "admin.map-deleted" : "admin.map-unknown", "map", id);
            case "enable", "disable" -> {
                Optional<GameMap> m = plugin.maps().get(id);
                if (m.isEmpty()) { msg().send(sender, "admin.map-unknown", "map", id); return; }
                boolean on = action.equals("enable");
                m.get().enabled(on);
                plugin.maps().save();
                msg().send(sender, on ? "admin.map-enabled" : "admin.map-disabled", "map", m.get().id());
            }
            case "rename" -> {
                Optional<GameMap> m = plugin.maps().get(id);
                if (m.isEmpty()) { msg().send(sender, "admin.map-unknown", "map", id); return; }
                if (a.length < 4) { msg().sendList(sender, "help-admin"); return; }
                String name = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                m.get().displayName(name);
                plugin.maps().save();
                msg().send(sender, "admin.map-renamed", "map", m.get().id(), "name", name);
            }
            default -> msg().sendList(sender, "help-admin");
        }
    }

    private void setSpawn(CommandSender sender, String[] a) {
        Player p = player(sender);
        if (p == null) return;
        if (a.length < 3) { msg().sendList(p, "help-admin"); return; }
        TeamId team = TeamId.parse(a[2]);
        if (team == null) { msg().send(p, "admin.team-unknown"); return; }
        if (!p.getWorld().getName().equals(plugin.cfg().mapsWorld)) {
            msg().send(p, "admin.map-wrong-world", "world", plugin.cfg().mapsWorld);
            return;
        }
        GameMap m = plugin.maps().getOrCreate(a[1]);
        m.spawn(team, StoredLocation.of(p.getLocation()));
        plugin.maps().save();
        msg().send(p, "admin.spawn-set", "team", plugin.cfg().team(team).display(), "map", m.id());
    }

    private void tp(CommandSender sender, String[] a) {
        Player p = player(sender);
        if (p == null) return;
        if (a.length < 3) { msg().sendList(p, "help-admin"); return; }
        Optional<GameMap> m = plugin.maps().get(a[1]);
        TeamId team = TeamId.parse(a[2]);
        if (m.isEmpty()) { msg().send(p, "admin.map-unknown", "map", a[1]); return; }
        if (team == null) { msg().send(p, "admin.team-unknown"); return; }
        StoredLocation sl = m.get().spawn(team);
        Location loc = sl == null ? null : sl.toLocation();
        if (loc == null) {
            msg().send(p, "general.not-set", "what", "Spawn " + team.name() + " van " + m.get().id());
            return;
        }
        p.teleport(loc);
        msg().send(p, "admin.teleported");
    }

    private void setLobby(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        if (!p.getWorld().getName().equals(plugin.cfg().lobbyWorld)) {
            msg().send(p, "admin.lobby-wrong-world", "world", plugin.cfg().lobbyWorld);
            return;
        }
        plugin.locations().lobby(StoredLocation.of(p.getLocation()));
        msg().send(p, "admin.lobby-set");
    }

    private void setButton(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        Block b = p.getTargetBlockExact(6);
        if (b == null || !Tag.BUTTONS.isTagged(b.getType())) {
            msg().send(p, "admin.button-not-a-button");
            return;
        }
        if (!b.getWorld().getName().equals(plugin.cfg().lobbyWorld)) {
            msg().send(p, "admin.lobby-wrong-world", "world", plugin.cfg().lobbyWorld);
            return;
        }
        plugin.locations().button(StoredLocation.of(b.getLocation()));
        msg().send(p, "admin.button-set", "x", String.valueOf(b.getX()), "y", String.valueOf(b.getY()), "z", String.valueOf(b.getZ()));
    }

    private void kit(CommandSender sender, String[] a, boolean set) {
        Player p = player(sender);
        if (p == null) return;
        KitType type = a.length < 2 ? null : KitType.parse(a[1]);
        if (type == null) { msg().send(p, "admin.kit-unknown"); return; }
        if (set) {
            plugin.kits().set(type, p);
            msg().send(p, "admin.kit-set", "kit", type.id());
        } else {
            plugin.kits().apply(type, p);
            msg().send(p, "admin.kit-given", "kit", type.id());
        }
    }

    private void kitpvp(CommandSender sender, String[] a) {
        String action = a.length < 2 ? "" : a[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "addspawn" -> {
                Player p = player(sender);
                if (p == null) return;
                if (!p.getWorld().getName().equals(plugin.cfg().kitpvpWorld)) {
                    msg().send(p, "admin.kitpvp-wrong-world", "world", plugin.cfg().kitpvpWorld);
                    return;
                }
                int n = plugin.locations().addKitpvpSpawn(StoredLocation.of(p.getLocation()));
                msg().send(p, "admin.kitpvp-spawn-added", "n", String.valueOf(n));
            }
            case "clearspawns" -> {
                plugin.locations().clearKitpvpSpawns();
                msg().send(sender, "admin.kitpvp-spawns-cleared");
            }
            case "spawns" -> {
                List<StoredLocation> spawns = plugin.locations().kitpvpSpawns();
                msg().send(sender, "admin.kitpvp-spawns-header", "n", String.valueOf(spawns.size()));
                for (int i = 0; i < spawns.size(); i++) {
                    StoredLocation l = spawns.get(i);
                    msg().send(sender, "admin.kitpvp-spawn-line", "n", String.valueOf(i + 1),
                            "x", String.valueOf(Math.round(l.x())), "y", String.valueOf(Math.round(l.y())), "z", String.valueOf(Math.round(l.z())));
                }
            }
            default -> msg().sendList(sender, "help-admin");
        }
    }

    private void setLeaderboard(CommandSender sender, String[] a) {
        Player p = player(sender);
        if (p == null) return;
        LeaderboardType type = a.length < 2 ? null : LeaderboardType.parse(a[1]);
        if (type == null) {
            msg().send(p, "admin.leaderboard-type-unknown", "types", LeaderboardType.ids());
            return;
        }
        String id = a.length >= 3 ? a[2] : plugin.leaderboards().freeId(type);
        plugin.leaderboards().create(id, type, p.getLocation().add(0, 2, 0));
        msg().send(p, "admin.leaderboard-set", "id", id, "type", type.id());
    }

    // ------------------------------------------------------------------ tab-completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            out.addAll(List.of("help", "lobby", "kitpvp", "stats"));
            if (admin) out.add("admin");
        } else if (args[0].equalsIgnoreCase("stats") && args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args[0].equalsIgnoreCase("admin") && admin) {
            String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
            List<String> mapIds = plugin.maps().all().stream().map(GameMap::id).collect(Collectors.toList());
            if (args.length == 2) {
                out.addAll(List.of("maps", "map", "setspawn", "tp", "setlobby", "setbutton", "setkit", "givekit", "kitpvp",
                        "setleaderboard", "removeleaderboard", "leaderboards", "start", "stop", "reload"));
            } else if (args.length == 3) {
                switch (sub) {
                    case "map" -> out.addAll(List.of("create", "delete", "enable", "disable", "rename"));
                    case "setspawn", "tp" -> out.addAll(mapIds);
                    case "setkit", "givekit" -> out.addAll(List.of("os", "kitpvp"));
                    case "kitpvp" -> out.addAll(List.of("addspawn", "clearspawns", "spawns"));
                    case "setleaderboard" -> Arrays.stream(LeaderboardType.values()).forEach(t -> out.add(t.id()));
                    case "removeleaderboard" -> plugin.leaderboards().list().forEach(b -> out.add(b.id()));
                    default -> { }
                }
            } else if (args.length == 4) {
                if (sub.equals("setspawn") || sub.equals("tp")) out.addAll(List.of("KD1", "KD2"));
                else if (sub.equals("map") && !args[2].equalsIgnoreCase("create")) out.addAll(mapIds);
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
