package nl.oorlogsim.pvp;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import nl.oorlogsim.OorlogSim;
import nl.oorlogsim.config.PvpConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Alle 1.8-PvP-mechanieken, alleen actief in de werelden uit pvp.yml. */
public final class LegacyPvpListener implements Listener {
    private static final NamespacedKey BASE_ATTACK_DAMAGE = NamespacedKey.minecraft("base_attack_damage");
    private static final double VANILLA_CRIT = 1.5;

    private final OorlogSim plugin;

    public LegacyPvpListener(OorlogSim plugin) {
        this.plugin = plugin;
    }

    private PvpConfig cfg() {
        return plugin.pvpCfg();
    }

    private boolean active(Player p) {
        return cfg().appliesIn(p.getWorld().getName());
    }

    // ------------------------------------------------------------------ schade

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !active(attacker)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (cfg().disableSweep) e.setCancelled(true);
            return;
        }
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!e.isApplicable(EntityDamageEvent.DamageModifier.BASE)) return;

        double base = e.getDamage(EntityDamageEvent.DamageModifier.BASE);
        boolean vanillaCrit = e.isCritical();

        if (cfg().weaponDamageEnabled) {
            Material weapon = attacker.getInventory().getItemInMainHand().getType();
            Double wanted = cfg().weaponDamage.get(weapon);
            if (wanted != null) {
                double vanillaWeapon = 1.0 + itemAttackBonus(attacker);
                double delta = wanted - vanillaWeapon;
                base = Math.max(0, base + (vanillaCrit ? delta * VANILLA_CRIT : delta));
            }
        }

        if (cfg().crits == PvpConfig.Mode.LEGACY) {
            if (vanillaCrit) {
                base = base / VANILLA_CRIT * cfg().critMultiplier;
            } else if (legacyCritConditions(attacker)) {
                base = base * cfg().critMultiplier;
                attacker.getWorld().spawnParticle(org.bukkit.Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
            }
        }
        e.setDamage(EntityDamageEvent.DamageModifier.BASE, base);
    }

    /** Bonus van het wapen zelf (zonder kracht-effecten), zoals vanilla dat in de attribute zet. */
    private static double itemAttackBonus(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (a == null) return 0;
        double sum = 0;
        for (AttributeModifier m : a.getModifiers()) {
            if (BASE_ATTACK_DAMAGE.equals(m.getKey()) && m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) sum += m.getAmount();
        }
        return sum;
    }

    /** 1.8: crit bij vallen, ook tijdens sprinten. */
    private static boolean legacyCritConditions(Player p) {
        return p.getFallDistance() > 0 && !((org.bukkit.entity.Entity) p).isOnGround() && !p.isInWater() && !p.isClimbing()
                && !p.hasPotionEffect(PotionEffectType.BLINDNESS) && !p.isInsideVehicle() && p.isSprinting();
    }

    // ------------------------------------------------------------------ knockback

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getHitBy() instanceof Player attacker)) return;
        if (!cfg().knockbackEnabled || !active(victim)) return;
        EntityKnockbackEvent.Cause cause = e.getCause();
        if (cause == EntityKnockbackEvent.Cause.SWEEP_ATTACK) {
            if (cfg().disableSweep) e.setCancelled(true);
            return;
        }
        if (cause == EntityKnockbackEvent.Cause.ENTITY_ATTACK) {
            // Vanilla's extra sprint/enchant-knockback: zit al in onze berekening hieronder.
            e.setCancelled(true);
            return;
        }
        if (cause != EntityKnockbackEvent.Cause.DAMAGE) return;

        Vector current = victim.getVelocity();
        double dx = attacker.getLocation().getX() - victim.getLocation().getX();
        double dz = attacker.getLocation().getZ() - victim.getLocation().getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0E-4) {
            dx = Math.random() - Math.random();
            dz = Math.random() - Math.random();
            dist = Math.sqrt(dx * dx + dz * dz);
        }
        double f = cfg().kbFriction;
        Vector v = new Vector(current.getX() / f, current.getY() / f, current.getZ() / f);
        v.setX(v.getX() - dx / dist * cfg().kbHorizontal);
        v.setY(v.getY() + cfg().kbVertical);
        v.setZ(v.getZ() - dz / dist * cfg().kbHorizontal);
        if (v.getY() > cfg().kbVerticalLimit) v.setY(cfg().kbVerticalLimit);

        int bonus = attacker.isSprinting() ? 1 : 0;
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        bonus += hand.getEnchantmentLevel(Enchantment.KNOCKBACK);
        if (bonus > 0) {
            double yaw = Math.toRadians(attacker.getLocation().getYaw());
            v.add(new Vector(-Math.sin(yaw) * bonus * cfg().kbExtraHorizontal, cfg().kbExtraVertical, Math.cos(yaw) * bonus * cfg().kbExtraHorizontal));
        }
        if (!cfg().ignoreResistance) {
            AttributeInstance res = victim.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            double r = res == null ? 0 : Math.max(0, Math.min(1, res.getValue()));
            Vector diff = v.clone().subtract(current).multiply(1 - r);
            e.setKnockback(diff);
            return;
        }
        // De event-vector is het verschil dat bij de huidige snelheid wordt opgeteld.
        e.setKnockback(v.subtract(current));
    }

    // ------------------------------------------------------------------ schild & offhand

    @EventHandler(priority = EventPriority.LOW)
    public void onShield(PlayerInteractEvent e) {
        if (!cfg().disableShields || !active(e.getPlayer())) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.SHIELD) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (cfg().disableOffhand && active(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOffhandClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !cfg().disableOffhand || !active(p)) return;
        if (e.getClick() == ClickType.SWAP_OFFHAND
                || (e.getClickedInventory() instanceof PlayerInventory && e.getSlot() == 40)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ regeneratie

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p) || !active(p)) return;
        if (cfg().regenMode == PvpConfig.Mode.LEGACY && e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ golden apples & pearls

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!active(p)) return;
        Material type = e.getItem().getType();
        if (type == Material.GOLDEN_APPLE) {
            if (p.hasCooldown(Material.GOLDEN_APPLE)) {
                e.setCancelled(true);
                return;
            }
            boolean legacy = cfg().goldenAppleMode == PvpConfig.Mode.LEGACY;
            int cd = cfg().cooldownGoldenApple;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (legacy) {
                    p.removePotionEffect(PotionEffectType.REGENERATION);
                    p.removePotionEffect(PotionEffectType.ABSORPTION);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
                }
                if (cd > 0) p.setCooldown(Material.GOLDEN_APPLE, cd * 20);
            });
        } else if (type == Material.ENCHANTED_GOLDEN_APPLE) {
            if (p.hasCooldown(Material.ENCHANTED_GOLDEN_APPLE)) {
                e.setCancelled(true);
                return;
            }
            boolean legacy = cfg().enchantedGoldenAppleMode == PvpConfig.Mode.LEGACY;
            int cd = cfg().cooldownEnchantedGoldenApple;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (legacy) {
                    p.removePotionEffect(PotionEffectType.REGENERATION);
                    p.removePotionEffect(PotionEffectType.ABSORPTION);
                    p.removePotionEffect(PotionEffectType.RESISTANCE);
                    p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 4));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
                }
                if (cd > 0) p.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, cd * 20);
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player p) || !active(p)) return;
        int cd = cfg().cooldownEnderPearl;
        Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.ENDER_PEARL, cd * 20));
    }
}
