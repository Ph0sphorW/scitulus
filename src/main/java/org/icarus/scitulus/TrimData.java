package org.icarus.scitulus;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"removal", "unstable"})
public final class TrimData {

    public static final String NAMESPACE = "scitulus";
    public static final int FULL_SET_SIZE = 4;

    public static final Key SENTRY = TrimPattern.SENTRY.key();
    public static final Key VEX = TrimPattern.VEX.key();
    public static final Key DUNE = TrimPattern.DUNE.key();
    public static final Key WARD = TrimPattern.WARD.key();
    public static final Key HOST = TrimPattern.HOST.key();

    public static final EquipmentSlotGroup ARMOR_SLOT = EquipmentSlotGroup.ARMOR;

    public static EquipmentSlotGroup slotGroupOf(Material material) {
        EquipmentSlot slot = material.getEquipmentSlot();
        return slot.getGroup();
    }

    private TrimData() {
    }

    public record TrimAttribute(Attribute attribute,
                                double amount,
                                AttributeModifier.Operation operation) {

        public static TrimAttribute add(Attribute attribute, double amount) {
            return new TrimAttribute(attribute, amount, AttributeModifier.Operation.ADD_NUMBER);
        }

        public static TrimAttribute scale(Attribute attribute, double amount) {
            return new TrimAttribute(attribute, amount, AttributeModifier.Operation.ADD_SCALAR);
        }
    }

    public static NamespacedKey key(String path) {
        NamespacedKey key = NamespacedKey.fromString(NAMESPACE + ":" + path);
        if (key == null) {
            throw new IllegalArgumentException("Illegal NamespacedKey path: " + path);
        }
        return key;
    }

    public record MaterialBonus(String id,
                                TrimMaterial material,
                                String label,
                                Attribute attribute,
                                double amount,
                                AttributeModifier.Operation operation) {

        public NamespacedKey itemKey() {
            return key("material/" + this.id);
        }

        public String displayValue() {
            return display(this.amount);
        }

        public String displayTotal() {
            return display(this.amount * FULL_SET_SIZE);
        }

        private String display(double value) {
            String sign = value > 0 ? "+" : "";
            if (this.operation == AttributeModifier.Operation.ADD_SCALAR) {
                return sign + Math.round(value * 100) + "%";
            }
            return sign + trimNumber(value);
        }
    }

    public static final List<MaterialBonus> MATERIALS = List.of(
            new MaterialBonus("iron", TrimMaterial.IRON, "铁锭",
                    Attribute.ARMOR_TOUGHNESS, 1.0, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("copper", TrimMaterial.COPPER, "铜锭",
                    Attribute.BLOCK_INTERACTION_RANGE, 0.5, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("gold", TrimMaterial.GOLD, "金锭",
                    Attribute.BLOCK_BREAK_SPEED, 0.10, AttributeModifier.Operation.ADD_SCALAR),
            new MaterialBonus("redstone", TrimMaterial.REDSTONE, "红石粉",
                    Attribute.ATTACK_DAMAGE, 0.5, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("lapis", TrimMaterial.LAPIS, "青金石",
                    Attribute.MOVEMENT_SPEED, 0.03, AttributeModifier.Operation.ADD_SCALAR),
            new MaterialBonus("emerald", TrimMaterial.EMERALD, "绿宝石",
                    Attribute.SAFE_FALL_DISTANCE, 1.0, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("diamond", TrimMaterial.DIAMOND, "钻石",
                    Attribute.ATTACK_SPEED, 0.08, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("quartz", TrimMaterial.QUARTZ, "下界石英",
                    Attribute.ARMOR, 0.5, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("amethyst", TrimMaterial.AMETHYST, "紫水晶",
                    Attribute.MAX_HEALTH, 1.0, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("netherite", TrimMaterial.NETHERITE, "下界合金锭",
                    Attribute.KNOCKBACK_RESISTANCE, 0.08, AttributeModifier.Operation.ADD_NUMBER),
            new MaterialBonus("resin", TrimMaterial.RESIN, "树脂砖",
                    Attribute.FALL_DAMAGE_MULTIPLIER, -0.10, AttributeModifier.Operation.ADD_SCALAR)
    );

    private static final Map<Key, MaterialBonus> MATERIALS_BY_KEY = byKey(
            MATERIALS, bonus -> bonus.material().key());

    public static MaterialBonus materialOf(TrimMaterial material) {
        return material == null ? null : MATERIALS_BY_KEY.get(material.key());
    }

    public enum Trigger {
        ALWAYS,
        IN_WATER,
        SNEAKING,
        SPRINTING,
        SATURATION_AT_MOST_10,
        FULL_SET_ONLY,
        COMBAT
    }

    public record PotionGain(PotionEffectType type, int amplifier, int duration) {
    }

    public record PatternDefinition(TrimPattern pattern,
                                    String label,
                                    String theme,
                                    Trigger trigger,
                                    List<TrimAttribute> partial,
                                    List<TrimAttribute> full,
                                    List<PotionGain> partialPotions,
                                    List<PotionGain> fullPotions) {

        public Key key() {
            return this.pattern.key();
        }

        public boolean stampable() {
            return this.trigger == Trigger.ALWAYS && !this.partial.isEmpty();
        }

        public NamespacedKey itemKey(int index) {
            return TrimData.key("pattern/" + key().value() + "/" + index);
        }
    }

    private static PatternDefinition attributes(TrimPattern pattern,
                                                String label,
                                                String theme,
                                                Trigger trigger,
                                                List<TrimAttribute> partial,
                                                List<TrimAttribute> full) {
        return new PatternDefinition(pattern,
                label,
                theme,
                trigger,
                partial,
                full,
                List.of(),
                List.of());
    }

    private static PatternDefinition potions(TrimPattern pattern,
                                             String label,
                                             String theme,
                                             Trigger trigger,
                                             List<PotionGain> partial,
                                             List<PotionGain> full) {
        return new PatternDefinition(pattern,
                label,
                theme,
                trigger,
                List.of(),
                List.of(),
                partial,
                full);
    }

    private static PatternDefinition all(TrimPattern pattern,
                                         String label,
                                         String theme,
                                         Trigger trigger,
                                         List<TrimAttribute> partial,
                                         List<TrimAttribute> full,
                                         List<PotionGain> partialPotions,
                                         List<PotionGain> fullPotions) {
        return new PatternDefinition(pattern,
                label,
                theme,
                trigger,
                partial,
                full,
                partialPotions,
                fullPotions);
    }

    private static final int REFRESH = 40;

    public static final List<PatternDefinition> PATTERNS = List.of(
            attributes(TrimPattern.SENTRY, "哨兵", "掠夺者前哨站", Trigger.COMBAT, List.of(), List.of()),
            attributes(TrimPattern.VEX, "恼鬼", "林地府邸", Trigger.COMBAT, List.of(), List.of()),
            potions(TrimPattern.WILD, "荒野", "丛林神庙", Trigger.SATURATION_AT_MOST_10,
                    List.of(new PotionGain(PotionEffectType.STRENGTH, 0, 30),
                            new PotionGain(PotionEffectType.HASTE, 0, 30)),
                    List.of(new PotionGain(PotionEffectType.STRENGTH, 1, 100),
                            new PotionGain(PotionEffectType.HASTE, 1, 100))),
            potions(TrimPattern.COAST, "海岸", "沉船", Trigger.IN_WATER,
                    List.of(new PotionGain(PotionEffectType.STRENGTH, 0, 15),
                            new PotionGain(PotionEffectType.RESISTANCE, 0, 15)),
                    List.of(new PotionGain(PotionEffectType.STRENGTH, 1, 60),
                            new PotionGain(PotionEffectType.RESISTANCE, 1, 60))),
            attributes(TrimPattern.DUNE, "沙丘", "沙漠神殿", Trigger.COMBAT, List.of(), List.of()),
            attributes(TrimPattern.SNOUT, "猪鼻", "堡垒遗迹", Trigger.ALWAYS,
                    List.of(TrimAttribute.add(Attribute.ARMOR, 0.5),
                            TrimAttribute.scale(Attribute.BURNING_TIME, -0.10)),
                    List.of(TrimAttribute.add(Attribute.ARMOR, 1.0),
                            TrimAttribute.scale(Attribute.BURNING_TIME, -0.20))),
            attributes(TrimPattern.RIB, "肋骨", "下界要塞", Trigger.ALWAYS,
                    List.of(TrimAttribute.add(Attribute.KNOCKBACK_RESISTANCE, 0.05),
                            TrimAttribute.scale(Attribute.BURNING_TIME, -0.15)),
                    List.of(TrimAttribute.add(Attribute.KNOCKBACK_RESISTANCE, 0.10),
                            TrimAttribute.scale(Attribute.BURNING_TIME, -0.30))),
            attributes(TrimPattern.EYE, "眼眸", "要塞", Trigger.ALWAYS,
                    List.of(TrimAttribute.scale(Attribute.GRAVITY, -0.05),
                            TrimAttribute.add(Attribute.ENTITY_INTERACTION_RANGE, 0.5)),
                    List.of(TrimAttribute.scale(Attribute.GRAVITY, -0.10),
                            TrimAttribute.add(Attribute.ENTITY_INTERACTION_RANGE, 1.0))),
            attributes(TrimPattern.SPIRE, "尖塔", "末地城", Trigger.ALWAYS,
                    List.of(TrimAttribute.add(Attribute.MAX_HEALTH, 1.0),
                            TrimAttribute.scale(Attribute.FALL_DAMAGE_MULTIPLIER, -0.10)),
                    List.of(TrimAttribute.add(Attribute.MAX_HEALTH, 2.0),
                            TrimAttribute.scale(Attribute.FALL_DAMAGE_MULTIPLIER, -0.20))),
            attributes(TrimPattern.WARD, "监守", "远古城市", Trigger.FULL_SET_ONLY, List.of(), List.of()),
            attributes(TrimPattern.SILENCE, "幽静", "远古城市", Trigger.SNEAKING,
                    List.of(TrimAttribute.scale(Attribute.SNEAKING_SPEED, 0.10),
                            TrimAttribute.add(Attribute.ATTACK_SPEED, 0.05)),
                    List.of(TrimAttribute.scale(Attribute.SNEAKING_SPEED, 0.20),
                            TrimAttribute.add(Attribute.ATTACK_SPEED, 0.10))),
            attributes(TrimPattern.WAYFINDER, "向导", "古迹废墟", Trigger.SPRINTING,
                    List.of(TrimAttribute.scale(Attribute.MOVEMENT_SPEED, 0.20)),
                    List.of(TrimAttribute.scale(Attribute.MOVEMENT_SPEED, 0.20))),
            attributes(TrimPattern.SHAPER, "塑造", "古迹废墟", Trigger.ALWAYS,
                    List.of(TrimAttribute.add(Attribute.BLOCK_INTERACTION_RANGE, 0.5),
                            TrimAttribute.scale(Attribute.BLOCK_BREAK_SPEED, 0.10)),
                    List.of(TrimAttribute.add(Attribute.BLOCK_INTERACTION_RANGE, 1.0),
                            TrimAttribute.scale(Attribute.BLOCK_BREAK_SPEED, 0.20))),
            attributes(TrimPattern.HOST, "雇主", "古迹废墟", Trigger.ALWAYS,
                    List.of(TrimAttribute.add(Attribute.STEP_HEIGHT, 0.5)),
                    List.of(TrimAttribute.add(Attribute.STEP_HEIGHT, 1.0))),
            all(TrimPattern.RAISER, "牧民", "古迹废墟", Trigger.ALWAYS,
                    List.of(TrimAttribute.add(Attribute.SAFE_FALL_DISTANCE, 1.0)),
                    List.of(TrimAttribute.add(Attribute.SAFE_FALL_DISTANCE, 2.0)),
                    List.of(new PotionGain(PotionEffectType.HASTE, 0, REFRESH)),
                    List.of(new PotionGain(PotionEffectType.HASTE, 1, REFRESH))),
            attributes(TrimPattern.TIDE, "潮汐", "远古守卫者", Trigger.IN_WATER,
                    List.of(TrimAttribute.scale(Attribute.WATER_MOVEMENT_EFFICIENCY, 0.10),
                            TrimAttribute.add(Attribute.OXYGEN_BONUS, 1.0)),
                    List.of(TrimAttribute.scale(Attribute.WATER_MOVEMENT_EFFICIENCY, 0.20),
                            TrimAttribute.add(Attribute.OXYGEN_BONUS, 2.0))),
            attributes(TrimPattern.BOLT, "镶铆", "试炼密室", Trigger.ALWAYS,
                    List.of(TrimAttribute.add(Attribute.ARMOR, 0.5)),
                    List.of(TrimAttribute.add(Attribute.ARMOR, 1.0),
                            TrimAttribute.add(Attribute.ARMOR_TOUGHNESS, 1.0))),
            attributes(TrimPattern.FLOW, "涡流", "试炼密室", Trigger.ALWAYS,
                    List.of(TrimAttribute.scale(Attribute.FALL_DAMAGE_MULTIPLIER, -0.15)),
                    List.of(TrimAttribute.scale(Attribute.FALL_DAMAGE_MULTIPLIER, -0.30)))
    );

    private static final Map<Key, PatternDefinition> PATTERNS_BY_KEY = byKey(
            PATTERNS, PatternDefinition::key);

    public static PatternDefinition patternOf(Key key) {
        return PATTERNS_BY_KEY.get(key);
    }

    private static <T, K> Map<K, T> byKey(List<T> list, java.util.function.Function<T, K> keyer) {
        Map<K, T> map = new LinkedHashMap<>();
        for (T element : list) {
            map.put(keyer.apply(element), element);
        }
        return Map.copyOf(map);
    }

    private static String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
