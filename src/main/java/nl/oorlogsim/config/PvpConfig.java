package nl.oorlogsim.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Getypeerde weergave van pvp.yml (1.8 spam-PvP). */
public final class PvpConfig {
    public enum Mode { LEGACY, VANILLA }

    public final boolean enabled;
    /** Echte wereldnamen (aliassen 'maps' en 'kitpvp' al vertaald). */
    public final Set<String> worlds = new HashSet<>();
    public final double attackSpeed;
    public final int hitDelayTicks;

    public final boolean knockbackEnabled, ignoreResistance;
    public final double kbHorizontal, kbVertical, kbExtraHorizontal, kbExtraVertical, kbVerticalLimit, kbFriction;

    public final boolean weaponDamageEnabled;
    public final Map<Material, Double> weaponDamage = new EnumMap<>(Material.class);

    public final boolean disableSweep, disableShields, disableOffhand;
    public final Mode crits;
    public final double critMultiplier;

    public final Mode regenMode;
    public final int regenIntervalTicks, regenMinFood;
    public final double regenAmount;
    public final float regenExhaustion;

    public final int cooldownGoldenApple, cooldownEnchantedGoldenApple, cooldownEnderPearl;
    public final Mode goldenAppleMode, enchantedGoldenAppleMode;

    public PvpConfig(FileConfiguration c, PluginConfig main, Logger log) {
        enabled = c.getBoolean("enabled", true);
        for (String w : c.getStringList("worlds")) {
            if (w.equalsIgnoreCase("maps")) worlds.add(main.mapsWorld);
            else if (w.equalsIgnoreCase("kitpvp")) worlds.add(main.kitpvpWorld);
            else if (w.equalsIgnoreCase("lobby")) worlds.add(main.lobbyWorld);
            else worlds.add(w);
        }
        attackSpeed = c.getDouble("attack-speed", 1024.0);
        hitDelayTicks = Math.max(0, c.getInt("hit-delay-ticks", 20));

        knockbackEnabled = c.getBoolean("knockback.enabled", true);
        kbHorizontal = c.getDouble("knockback.horizontal", 0.4);
        kbVertical = c.getDouble("knockback.vertical", 0.4);
        kbExtraHorizontal = c.getDouble("knockback.extra-horizontal", 0.5);
        kbExtraVertical = c.getDouble("knockback.extra-vertical", 0.1);
        kbVerticalLimit = c.getDouble("knockback.vertical-limit", 0.4);
        kbFriction = Math.max(0.0001, c.getDouble("knockback.friction", 2.0));
        ignoreResistance = c.getBoolean("knockback.ignore-resistance", true);

        weaponDamageEnabled = c.getBoolean("weapon-damage.enabled", true);
        ConfigurationSection wd = c.getConfigurationSection("weapon-damage.values");
        if (wd != null) {
            for (String k : wd.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null) log.warning("pvp.yml: onbekend item '" + k + "'");
                else weaponDamage.put(m, wd.getDouble(k));
            }
        }

        disableSweep = c.getBoolean("disable-sweep", true);
        disableShields = c.getBoolean("disable-shields", true);
        disableOffhand = c.getBoolean("disable-offhand", true);
        crits = PluginConfig.enumOr(Mode.class, c.getString("crits"), Mode.LEGACY, log);
        critMultiplier = c.getDouble("crit-multiplier", 1.5);

        regenMode = PluginConfig.enumOr(Mode.class, c.getString("regeneration.mode"), Mode.LEGACY, log);
        regenIntervalTicks = Math.max(1, c.getInt("regeneration.interval-ticks", 80));
        regenAmount = c.getDouble("regeneration.amount", 1.0);
        regenMinFood = c.getInt("regeneration.min-food-level", 18);
        regenExhaustion = (float) c.getDouble("regeneration.exhaustion", 3.0);

        cooldownGoldenApple = Math.max(0, c.getInt("cooldowns.golden-apple", 0));
        cooldownEnchantedGoldenApple = Math.max(0, c.getInt("cooldowns.enchanted-golden-apple", 0));
        cooldownEnderPearl = Math.max(0, c.getInt("cooldowns.ender-pearl", 0));
        goldenAppleMode = PluginConfig.enumOr(Mode.class, c.getString("golden-apple.mode"), Mode.LEGACY, log);
        enchantedGoldenAppleMode = PluginConfig.enumOr(Mode.class, c.getString("golden-apple.enchanted-mode"), Mode.LEGACY, log);
    }

    public boolean appliesIn(String world) {
        return enabled && worlds.contains(world);
    }
}
