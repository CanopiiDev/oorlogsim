package nl.oorlogsim;

import nl.oorlogsim.command.OsCommand;
import nl.oorlogsim.config.Messages;
import nl.oorlogsim.config.PluginConfig;
import nl.oorlogsim.config.PvpConfig;
import nl.oorlogsim.display.NameColorService;
import nl.oorlogsim.display.ScoreboardNameColorService;
import nl.oorlogsim.gui.MenuListener;
import nl.oorlogsim.inventory.SnapshotStore;
import nl.oorlogsim.kitpvp.KitPvpListener;
import nl.oorlogsim.kitpvp.KitPvpManager;
import nl.oorlogsim.os.OsChatListener;
import nl.oorlogsim.os.OsGame;
import nl.oorlogsim.os.OsListener;
import nl.oorlogsim.pvp.LegacyPvp;
import nl.oorlogsim.pvp.LegacyPvpListener;
import nl.oorlogsim.stats.LeaderboardManager;
import nl.oorlogsim.stats.StatsService;
import nl.oorlogsim.storage.KitStore;
import nl.oorlogsim.storage.LocationStore;
import nl.oorlogsim.storage.MapRegistry;
import nl.oorlogsim.util.CombatTracker;
import nl.oorlogsim.world.WorldListener;
import nl.oorlogsim.world.WorldRole;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class OorlogSim extends JavaPlugin {
    private PluginConfig cfg;
    private PvpConfig pvpCfg;
    private Messages messages;
    private MapRegistry maps;
    private KitStore kits;
    private LocationStore locations;
    private SnapshotStore snapshots;
    private NameColorService names;
    private StatsService stats;
    private LeaderboardManager leaderboards;
    private CombatTracker combat;
    private OsGame os;
    private KitPvpManager kitpvp;
    private LegacyPvp legacyPvp;
    private nl.oorlogsim.rollback.RollbackService rollback;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "pvp.yml").exists()) saveResource("pvp.yml", false);
        loadConfigs();
        messages = new Messages(this);
        maps = new MapRegistry(this);
        kits = new KitStore(this);
        locations = new LocationStore(this);
        snapshots = new SnapshotStore(this);
        combat = new CombatTracker();
        stats = new StatsService(this);
        rollback = new nl.oorlogsim.rollback.RollbackService(this);
        names = createNameService();
        legacyPvp = new LegacyPvp(this);
        os = new OsGame(this);
        kitpvp = new KitPvpManager(this);
        leaderboards = new LeaderboardManager(this);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new OsListener(this), this);
        pm.registerEvents(new OsChatListener(this), this);
        pm.registerEvents(new KitPvpListener(this), this);
        pm.registerEvents(new LegacyPvpListener(this), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new nl.oorlogsim.os.MapBorderListener(this), this);
        // Achtergebleven momentopnames (crash) terugzetten zodra alle werelden geladen zijn
        Bukkit.getScheduler().runTaskLater(this, rollback::restorePending, 40L);

        OsCommand command = new OsCommand(this);
        PluginCommand pc = getCommand("os");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        if (pm.getPlugin("PlaceholderAPI") != null) {
            new nl.oorlogsim.papi.OorlogSimExpansion(this).register();
            getLogger().info("PlaceholderAPI gevonden: %oorlogsim_...% placeholders geregistreerd.");
        }

        // Spelers die al online zijn (bijv. na /reload) goed zetten
        for (Player p : Bukkit.getOnlinePlayers()) legacyPvp.update(p);
        getLogger().info("OorlogSim staat aan. Werelden: lobby=" + cfg.lobbyWorld + ", maps=" + cfg.mapsWorld + ", kitpvp=" + cfg.kitpvpWorld);
    }

    private NameColorService createNameService() {
        if (Bukkit.getPluginManager().getPlugin("TAB") != null) {
            try {
                NameColorService s = new nl.oorlogsim.display.TabNameColorService();
                getLogger().info("TAB gevonden: teamkleuren via de TAB-API.");
                return s;
            } catch (Throwable t) {
                getLogger().warning("TAB gevonden maar de API werkt niet (" + t.getMessage() + "), fallback naar scoreboard-teams.");
            }
        }
        return new ScoreboardNameColorService();
    }

    private void loadConfigs() {
        reloadConfig();
        cfg = new PluginConfig(getConfig(), getLogger());
        YamlConfiguration pvp = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "pvp.yml"));
        var def = getResource("pvp.yml");
        if (def != null) pvp.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        pvpCfg = new PvpConfig(pvp, cfg, getLogger());
    }

    /** /os admin reload */
    public void reloadAll() {
        loadConfigs();
        messages.reload();
        legacyPvp.restartRegenTask();
        leaderboards.restartTask();
        leaderboards.refreshAll();
        for (Player p : Bukkit.getOnlinePlayers()) legacyPvp.update(p);
    }

    @Override
    public void onDisable() {
        if (os != null) os.shutdown();
        if (leaderboards != null) leaderboards.shutdown();
        if (legacyPvp != null) legacyPvp.shutdown();
        if (names != null) {
            for (Player p : Bukkit.getOnlinePlayers()) names.clear(p);
            names.shutdown();
        }
        if (stats != null) stats.saveSync();
    }

    public WorldRole role(World w) {
        if (w == null) return WorldRole.OTHER;
        String n = w.getName();
        if (n.equals(cfg.lobbyWorld)) return WorldRole.LOBBY;
        if (n.equals(cfg.mapsWorld)) return WorldRole.MAPS;
        if (n.equals(cfg.kitpvpWorld)) return WorldRole.KITPVP;
        return WorldRole.OTHER;
    }

    /** Damage als tekst volgens config (hartjes of HP). */
    public String formatDamage(double hp) {
        boolean hearts = cfg.damageDisplay == PluginConfig.DamageDisplay.HEARTS;
        double v = hearts ? hp / 2.0 : hp;
        String num = String.format(Locale.ROOT, "%.1f", v);
        return messages.raw(hearts ? "match.damage-hearts" : "match.damage-hp", "value", num);
    }

    public PluginConfig cfg() { return cfg; }
    public PvpConfig pvpCfg() { return pvpCfg; }
    public Messages messages() { return messages; }
    public MapRegistry maps() { return maps; }
    public KitStore kits() { return kits; }
    public LocationStore locations() { return locations; }
    public SnapshotStore snapshots() { return snapshots; }
    public NameColorService names() { return names; }
    public StatsService stats() { return stats; }
    public LeaderboardManager leaderboards() { return leaderboards; }
    public CombatTracker combat() { return combat; }
    public OsGame os() { return os; }
    public KitPvpManager kitpvp() { return kitpvp; }
    public LegacyPvp legacyPvp() { return legacyPvp; }
    public nl.oorlogsim.rollback.RollbackService rollback() { return rollback; }

    /** Maps die nu gestemd/gespeeld kunnen worden. */
    public java.util.List<nl.oorlogsim.storage.GameMap> playableMaps() {
        return maps.playable(cfg.requireRegion, rollback::isBusy);
    }
}
