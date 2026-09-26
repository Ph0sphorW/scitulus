package org.icarus.scitulus.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.icarus.scitulus.ArmorSet;
import org.icarus.scitulus.Cooldowns;
import org.icarus.scitulus.PlayerEffects;
import org.icarus.scitulus.Scitulus;
import org.icarus.scitulus.TrimData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CombatListener implements Listener {

    // sentry
    private static final double SENTRY_DAMAGE_PARTIAL = 1.0;
    private static final double SENTRY_DAMAGE_FULL = 2.0;
    private static final int SENTRY_SPEED_TICKS = 60;       // 3s

    // vex
    private static final int VEX_COOLDOWN_PARTIAL = 100;    // 5s
    private static final int VEX_COOLDOWN_FULL = 70;        // 3.5s
    private static final String VEX_COOLDOWN_KEY = "vex_fang";

    // dune
    private static final double DUNE_DAMAGE_PER_BLOCK = 0.8;
    private static final int DUNE_FLIGHT_CAP_TICKS = 20;    // 上限 1s
    private static final double DUNE_FULL_MULTIPLIER = 1.6;
    private static final int DUNE_FLIGHT_EXPIRE_TICKS = 400; // 20s 后丢弃发射记录

    private record ArrowFlight(Location from, int tick, boolean full) {
    }

    private final Scitulus plugin;
    private final PlayerEffects effects;
    private final Cooldowns cooldowns;
    private final Map<UUID, ArrowFlight> flights = new HashMap<>();

    public CombatListener(Scitulus plugin, PlayerEffects effects, Cooldowns cooldowns) {
        this.plugin = plugin;
        this.effects = effects;
        this.cooldowns = cooldowns;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player player)) {
            return;
        }
        int count = this.effects.armorSet(player).count(TrimData.DUNE);
        if (count <= 0) {
            return;
        }
        boolean full = count >= TrimData.FULL_SET_SIZE;
        pruneFlights();
        this.flights.put(arrow.getUniqueId(),
                new ArrowFlight(arrow.getLocation().clone(), Bukkit.getCurrentTick(), full));
        if (full) {
            arrow.setPierceLevel(arrow.getPierceLevel() + 1);
        }
        debug(player.getName() + " 沙丘发射记录（全套=" + full + "）");
    }

    private void pruneFlights() {
        if (this.flights.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        this.flights.entrySet().removeIf(uuidArrowFlightEntry -> now - uuidArrowFlightEntry.getValue().tick() > DUNE_FLIGHT_EXPIRE_TICKS);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player shooter) {
            handleArrow(shooter, arrow, event);
            return;
        }
        if (damager instanceof Player attacker) {
            handleAxe(attacker, event);
        }
    }

    private void handleArrow(Player shooter, AbstractArrow arrow, EntityDamageByEntityEvent event) {
        ArmorSet set = this.effects.armorSet(shooter);

        int sentry = set.count(TrimData.SENTRY);
        if (sentry > 0 && arrow.isShotFromCrossbow()) {
            boolean full = sentry >= TrimData.FULL_SET_SIZE;
            event.setDamage(event.getDamage() + (full ? SENTRY_DAMAGE_FULL : SENTRY_DAMAGE_PARTIAL));
            if (full) {
                shooter.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SENTRY_SPEED_TICKS, 0,
                        true, false, true));
            }
            debug(shooter.getName() + " 哨兵弩伤害 +" + (full ? SENTRY_DAMAGE_FULL : SENTRY_DAMAGE_PARTIAL));
        }

        ArrowFlight arrowFlight = this.flights.get(arrow.getUniqueId());
        if (arrowFlight == null) {
            return;
        }
        int ticks = Bukkit.getCurrentTick() - arrowFlight.tick();
        double blocks = arrowFlight.from().distance(arrow.getLocation());
        if (ticks > DUNE_FLIGHT_CAP_TICKS) {
            blocks *= (double) DUNE_FLIGHT_CAP_TICKS / ticks;
        }
        double bonus = DUNE_DAMAGE_PER_BLOCK * blocks * (arrowFlight.full() ? DUNE_FULL_MULTIPLIER : 1.0);
        if (bonus > 0) {
            event.setDamage(event.getDamage() + bonus);
            debug(shooter.getName() + " 沙丘箭矢伤害 +" + String.format(Locale.ROOT, "%.2f", bonus)
                    + "（" + String.format(Locale.ROOT, "%.2f", blocks) + " 格）");
        }
    }

    private void handleAxe(Player attacker, EntityDamageByEntityEvent event) {
        ArmorSet set = this.effects.armorSet(attacker);
        int count = set.count(TrimData.VEX);
        if (count <= 0) {
            return;
        }
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!isAxe(hand.getType())) {
            return;
        }
        if (!this.cooldowns.ready(attacker, VEX_COOLDOWN_KEY)) {
            return;
        }
        boolean full = count >= TrimData.FULL_SET_SIZE;
        this.cooldowns.set(attacker, VEX_COOLDOWN_KEY, full ? VEX_COOLDOWN_FULL : VEX_COOLDOWN_PARTIAL);
        summonFangs(attacker, event.getEntity(), full ? 2 : 1);
        debug(attacker.getName() + " 恼鬼召唤 " + (full ? 2 : 1) + " 只尖牙");
    }

    private void summonFangs(Player owner, Entity victim, int count) {
        Location base = victim.getLocation();
        Vector direction = base.toVector().subtract(owner.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = owner.getLocation().getDirection().setY(0);
        }
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        for (int i = 0; i < count; i++) {
            Location location = base.clone().add(direction.clone().multiply(i * 0.7));
            victim.getWorld().spawn(location, EvokerFangs.class, fangs -> {
                fangs.setOwner(owner);
                fangs.setAttackDelay(8);
            });
        }
    }

    private static boolean isAxe(Material material) {
        return material.name().endsWith("_AXE");
    }

    private void debug(String message) {
        if (this.plugin.config().debug()) {
            this.plugin.getLogger().info("[debug] " + message);
        }
    }
}
