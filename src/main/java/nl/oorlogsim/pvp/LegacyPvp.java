package nl.oorlogsim.pvp;

import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.config.PvpConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Zet spelers in de 1.8-PvP-werelden op 1.8-instellingen (geen aanval-cooldown, hit delay)
 * en terug naar vanilla daarbuiten. Regelt ook de 1.8-regeneratie.
 */
public final class LegacyPvp {
    public static final double VANILLA_ATTACK_SPEED = 4.0;
    public static final int VANILLA_NO_DAMAGE_TICKS = 20;

    private final OorlogSim plugin;
    private BukkitTask regenTask;

    public LegacyPvp(OorlogSim plugin) {
        this.plugin = plugin;
        restartRegenTask();
    }

    private PvpConfig cfg() {
        return plugin.pvpCfg();
    }

    public boolean appliesTo(Player p) {
        return cfg().appliesIn(p.getWorld().getName());
    }

    public void update(Player p) {
        if (appliesTo(p)) apply(p);
        else remove(p);
    }

    public void apply(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(cfg().attackSpeed);
        p.setMaximumNoDamageTicks(cfg().hitDelayTicks);
    }

    public void remove(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(VANILLA_ATTACK_SPEED);
        p.setMaximumNoDamageTicks(VANILLA_NO_DAMAGE_TICKS);
    }

    public void restartRegenTask() {
        if (regenTask != null) regenTask.cancel();
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, this::regenTick, cfg().regenIntervalTicks, cfg().regenIntervalTicks);
    }

    private void regenTick() {
        if (cfg().regenMode != PvpConfig.Mode.LEGACY) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!appliesTo(p) || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            if (p.getFoodLevel() < cfg().regenMinFood) continue;
            double max = nl.oorlogsim.util.Players.maxHealth(p);
            if (p.getHealth() >= max) continue;
            p.setHealth(Math.min(max, p.getHealth() + cfg().regenAmount));
            p.setExhaustion(p.getExhaustion() + cfg().regenExhaustion);
        }
    }

    public void shutdown() {
        if (regenTask != null) regenTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) remove(p);
    }
}
