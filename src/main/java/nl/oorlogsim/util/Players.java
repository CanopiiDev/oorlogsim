package nl.oorlogsim.util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;

/** Kleine hulpfuncties rond spelers. */
public final class Players {
    private Players() {}

    public static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    /** Vol leven, eten, geen effecten/vuur/val. */
    public static void reset(Player p, GameMode mode) {
        p.setGameMode(mode);
        p.setHealth(maxHealth(p));
        p.setFoodLevel(20);
        p.setSaturation(5f);
        p.setExhaustion(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setLevel(0);
        p.setExp(0f);
        p.setAllowFlight(mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
    }

    /** De speler die de schade veroorzaakte (direct, pijl/trident, of TNT), of null. */
    public static Player attacker(EntityDamageEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player p) return p;
        Entity direct = event.getDamageSource().getDirectEntity();
        if (direct instanceof Player p) return p;
        if (direct instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (direct instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    /** Zou deze schade de speler doden (zonder totem in de hand)? */
    public static boolean isLethal(Player p, double finalDamage) {
        if (finalDamage < p.getHealth() + p.getAbsorptionAmount()) return false;
        return !holds(p, Material.TOTEM_OF_UNDYING);
    }

    private static boolean holds(Player p, Material m) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return main.getType() == m || off.getType() == m;
    }

    public static void teleport(Player p, Location loc) {
        if (loc != null) p.teleport(loc);
    }
}
