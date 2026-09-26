package org.icarus.scitulus;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerEffects implements Listener {

    private static final double HOST_ABSORPTION_PER_PIECE = 1.0;

    private static final double HOST_ABSORPTION_FULL_EXTRA = 2.0;

    private final Scitulus plugin;
    private final Map<UUID, Map<NamespacedKey, TrimData.TrimAttribute>> applied = new HashMap<>();
    private final Map<UUID, ArmorSet> snapshots = new HashMap<>();
    private final Set<UUID> stale = new HashSet<>();

    private BukkitTask task;
    private int elapsed;

    public PlayerEffects(Scitulus plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long period = Math.max(1, this.plugin.config().refreshIntervalTicks());
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, period);
        for (Player player : Bukkit.getOnlinePlayers()) {
            invalidate(player);
        }
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.elapsed = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            release(player);
        }
        this.applied.clear();
        this.snapshots.clear();
        this.stale.clear();
    }

    private void release(Player player) {
        Map<NamespacedKey, TrimData.TrimAttribute> current = this.applied.remove(player.getUniqueId());
        if (current == null) {
            return;
        }
        for (Map.Entry<NamespacedKey, TrimData.TrimAttribute> entry : current.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getValue().attribute());
            if (instance != null) {
                instance.removeModifier(entry.getKey());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.applied.remove(id);
        this.snapshots.remove(id);
        this.stale.remove(id);
    }

    @EventHandler
    public void onArmorChange(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event) {
        invalidate(event.getPlayer());
    }

    public void invalidate(Player player) {
        UUID id = player.getUniqueId();
        this.stale.add(id);
        this.snapshots.remove(id);
    }

    public ArmorSet armorSet(Player player) {
        UUID id = player.getUniqueId();
        if (this.stale.remove(id)) {
            this.snapshots.remove(id);
        }
        return this.snapshots.computeIfAbsent(id, key -> ArmorSet.of(player));
    }

    private void tick() {
        int period = Math.max(1, this.plugin.config().refreshIntervalTicks());
        this.elapsed += period;
        boolean rescanAll = this.elapsed >= 20;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (rescanAll) {
                invalidate(player);
            }
            refresh(player);
        }
        if (rescanAll) {
            this.elapsed = 0;
        }
    }

    public void refresh(Player player) {
        ArmorSet set = armorSet(player);

        if (this.plugin.config().stampItemAttributes()) {
            stampArmor(player);
        }

        Map<NamespacedKey, TrimData.TrimAttribute> desired = new HashMap<>();
        collectPatternAttributes(player, set, desired);
        collectAbsorptionCap(player, set, desired);
        applyAttributeModifiers(player, desired);

        applyPotionEffects(player, set);
        applyAbsorption(player, set);

        if (set.isFullSet(TrimData.WARD)) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    private void stampArmor(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (EquipmentSlot slot : ArmorSet.SLOTS) {
            ItemStack current = inventory.getItem(slot);
            ItemStack updated = ItemStamper.stamp(current);
            if (updated != null) {
                inventory.setItem(slot, updated);
            }
        }
    }

    private void collectPatternAttributes(Player player, ArmorSet set,
                                          Map<NamespacedKey, TrimData.TrimAttribute> out) {
        boolean stamping = this.plugin.config().stampItemAttributes();
        for (TrimData.PatternDefinition def : TrimData.PATTERNS) {
            int count = set.count(def.key());
            if (count <= 0) {
                continue;
            }
            if (def.trigger() == TrimData.Trigger.COMBAT || def.trigger() == TrimData.Trigger.FULL_SET_ONLY) {
                continue;
            }
            if (!triggered(player, def.trigger())) {
                continue;
            }

            if (!def.stampable() || !stamping) {
                for (int i = 0; i < def.partial().size(); i++) {
                    TrimData.TrimAttribute attr = def.partial().get(i);
                    out.put(TrimData.key("pattern/" + def.key().value() + "/" + i),
                            new TrimData.TrimAttribute(attr.attribute(), attr.amount() * count, attr.operation()));
                }
            }
            
            if (count >= TrimData.FULL_SET_SIZE) {
                for (int i = 0; i < def.full().size(); i++) {
                    out.put(TrimData.key("patternfull/" + def.key().value() + "/" + i), def.full().get(i));
                }
            }
        }
    }

    private static double absorptionTarget(Player player, ArmorSet set) {
        int count = set.count(TrimData.HOST);
        if (count <= 0) {
            return 0.0;
        }
        double total = count * HOST_ABSORPTION_PER_PIECE;
        if (count >= TrimData.FULL_SET_SIZE) {
            total += HOST_ABSORPTION_FULL_EXTRA;
        }
        PotionEffect absorption = player.getPotionEffect(PotionEffectType.ABSORPTION);
        double vanilla = absorption == null ? 0.0 : 4.0 * (absorption.getAmplifier() + 1);
        return Math.max(total, vanilla);
    }

    private void collectAbsorptionCap(Player player, ArmorSet set, Map<NamespacedKey, TrimData.TrimAttribute> out) {
        double target = absorptionTarget(player, set);
        if (target <= 0.0) {
            return;
        }
        out.put(TrimData.key("pattern/host/absorption"),
                TrimData.TrimAttribute.add(Attribute.MAX_ABSORPTION, target));
    }

    private static boolean triggered(Player player, TrimData.Trigger trigger) {
        return switch (trigger) {
            case ALWAYS -> true;
            case IN_WATER -> player.isInWater();
            case SNEAKING -> player.isSneaking();
            case SPRINTING -> player.isSprinting();
            case SATURATION_AT_MOST_10 -> player.getSaturation() <= 10.0F;
            case FULL_SET_ONLY, COMBAT -> false;
        };
    }

    private void applyAttributeModifiers(Player player, Map<NamespacedKey, TrimData.TrimAttribute> desired) {
        Map<NamespacedKey, TrimData.TrimAttribute> current =
                this.applied.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>());
        int before = current.size();

        current.entrySet().removeIf(entry -> {
            TrimData.TrimAttribute want = desired.get(entry.getKey());
            if (want != null && want.equals(entry.getValue())) {
                return false;
            }
            AttributeInstance instance = player.getAttribute(entry.getValue().attribute());
            if (instance != null) {
                instance.removeModifier(entry.getKey());
            }
            return true;
        });

        for (Map.Entry<NamespacedKey, TrimData.TrimAttribute> entry : desired.entrySet()) {
            if (current.containsKey(entry.getKey())) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(entry.getValue().attribute());
            if (instance == null) {
                continue;
            }
            instance.addTransientModifier(new AttributeModifier(entry.getKey(), entry.getValue().amount(),
                    entry.getValue().operation(), TrimData.ARMOR_SLOT));
            current.put(entry.getKey(), entry.getValue());
        }

        if (before != current.size() && this.plugin.config().debug()) {
            this.plugin.getLogger().info("[debug] " + player.getName()
                    + " 模板属性修饰符 " + before + " -> " + current.size());
        }
    }

    private void applyPotionEffects(Player player, ArmorSet set) {
        for (TrimData.PatternDefinition def : TrimData.PATTERNS) {
            int count = set.count(def.key());
            if (count <= 0) {
                continue;
            }
            if (!triggered(player, def.trigger())) {
                continue;
            }
            boolean full = count >= TrimData.FULL_SET_SIZE;
            for (TrimData.PotionGain gain : (full ? def.fullPotions() : def.partialPotions())) {
                keepEffect(player, gain);
            }
        }
    }

    private static void keepEffect(Player player, TrimData.PotionGain gain) {
        PotionEffect existing = player.getPotionEffect(gain.type());
        if (existing != null
                && existing.getAmplifier() == gain.amplifier()
                && existing.getDuration() > gain.duration() / 2) {
            return;
        }
        player.addPotionEffect(new PotionEffect(gain.type(), gain.duration(), gain.amplifier(), true, false, true));
    }

    private void applyAbsorption(Player player, ArmorSet set) {
        double target = absorptionTarget(player, set);
        if (target > 0.0 && player.getAbsorptionAmount() < target) {
            player.setAbsorptionAmount(target);
        }
    }
}
