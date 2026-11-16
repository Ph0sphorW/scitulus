package org.icarus.scitulus.command;

import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.icarus.scitulus.ItemStamper;
import org.icarus.scitulus.Scitulus;
import org.icarus.scitulus.TrimData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("removal")
public final class ScitulusCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "selftest", "help");

    private final Scitulus plugin;

    public ScitulusCommand(Scitulus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                this.plugin.reload();
                sender.sendMessage(Component.text("[Scitulus] Reloading.", NamedTextColor.GREEN));
            }
            case "list" -> printTables(sender);
            case "selftest" -> selfTest(sender);
            default -> {
                sender.sendMessage(Component.text("[Scitulus] 帮助", NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD));
                sender.sendMessage(Component.text("  /" + label + " reload    —— 重载", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /" + label + " list      —— 材料 / 模板对照表", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /" + label + " selftest  —— 自检", NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private void printTables(CommandSender sender) {
        sender.sendMessage(Component.text("── 锻造材料 ──", NamedTextColor.AQUA));
        for (TrimData.MaterialBonus bonus : TrimData.MATERIALS) {
            String mode = bonus.operation() == AttributeModifier.Operation.ADD_SCALAR ? "倍率" : "加算";
            sender.sendMessage(Component.text("  " + bonus.label() + " → " + bonus.attribute().getKey().getKey()
                            + "  " + bonus.displayValue() + "（" + mode + "）  * 4 = " + bonus.displayTotal(),
                    NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("── 盔甲纹饰模板（单件档每件累加，全套档额外追加）──", NamedTextColor.AQUA));
        for (TrimData.PatternDefinition def : TrimData.PATTERNS) {
            sender.sendMessage(Component.text("  " + def.label() + "（" + def.theme() + "，"
                    + triggerText(def.trigger()) + "）", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("    单件: " + describe(def.partial(), def.partialPotions())
                    + "   x4 + 额外: " + describe(def.full(), def.fullPotions()), NamedTextColor.DARK_GRAY));
        }
    }

    private void selfTest(CommandSender sender) {
        int pass = 0;
        List<String> failures = new ArrayList<>();

        for (TrimData.MaterialBonus bonus : TrimData.MATERIALS) {
            String label = bonus.label() + "(" + bonus.material().key().value() + ")";
            ItemStack item = trimmedChestplate(bonus.material(), TrimPattern.SENTRY);
            ItemStack stamped = ItemStamper.stamp(item);
            if (stamped == null) {
                failures.add(label + ": 未刻上修饰符");
                continue;
            }
            String problem = verify(stamped, bonus.itemKey(EquipmentSlotGroup.CHEST), bonus.attribute(), bonus.amount(),
                    bonus.operation(), EquipmentSlotGroup.CHEST);
            if (problem != null) {
                failures.add(label + ": " + problem);
                continue;
            }
            if (ItemStamper.stamp(stamped) != null) {
                failures.add(label + ": 重复刻写");
                continue;
            }
            pass++;
        }

        int patternPass = 0;
        int patternTotal = 0;
        for (TrimData.PatternDefinition def : TrimData.PATTERNS) {
            if (!def.stampable()) {
                continue;
            }
            patternTotal++;
            String label = def.label() + "(" + def.key().value() + ")";
            ItemStack result = ItemStamper.stamp(trimmedChestplate(TrimMaterial.REDSTONE, def.pattern()));
            if (result == null) {
                failures.add(label + ": 未刻上纹饰属性");
                continue;
            }
            String problem = null;
            for (int i = 0; i < def.partial().size() && problem == null; i++) {
                TrimData.TrimAttribute attr = def.partial().get(i);
                problem = verify(result, def.itemKey(i, EquipmentSlotGroup.CHEST), attr.attribute(), attr.amount(), attr.operation(),
                        EquipmentSlotGroup.CHEST);
            }
            if (problem != null) {
                failures.add(label + ": " + problem);
                continue;
            }
            if (ItemStamper.stamp(result) != null) {
                failures.add(label + ": 重复刻写");
                continue;
            }
            patternPass++;
        }

        boolean untouched = ItemStamper.stamp(new ItemStack(Material.DIAMOND_CHESTPLATE)) == null;

        ItemStack plain = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemStack trimmed = ItemStamper.stamp(trimmedChestplate(TrimMaterial.LAPIS, TrimPattern.SENTRY));
        double armorValue = attributeValue(plain, Attribute.ARMOR);
        double toughnessValue = attributeValue(plain, Attribute.ARMOR_TOUGHNESS);
        boolean preserved = trimmed != null
                && attributeValue(trimmed, Attribute.ARMOR) == armorValue
                && attributeValue(trimmed, Attribute.ARMOR_TOUGHNESS) == toughnessValue;

        ItemStack custom = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta customMeta = custom.getItemMeta();
        NamespacedKey customKey = NamespacedKey.fromString("selftest:custom");
        if (customKey != null) {
            customMeta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(customKey, 5.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
            custom.setItemMeta(customMeta);
        }
        ItemStack customStamped = ItemStamper.stamp(withTrim(custom, TrimMaterial.IRON, TrimPattern.SENTRY));
        boolean coexist = customStamped != null
                && attributeValue(customStamped, Attribute.MAX_HEALTH) == 5.0
                && attributeValue(customStamped, Attribute.ARMOR) == armorValue
                && attributeValue(customStamped, Attribute.ARMOR_TOUGHNESS) == toughnessValue + 1.0;

        boolean cleaned = false;
        ItemStack stamped = ItemStamper.stamp(trimmedChestplate(TrimMaterial.NETHERITE, TrimPattern.SENTRY));
        if (stamped != null) {
            ItemStack result = ItemStamper.stamp(withTrim(stamped, null, null));
            cleaned = result != null && countOurs(result) == 0;
        }

        TrimData.PatternDefinition bolt = TrimData.patternOf(TrimPattern.BOLT.key());
        double perPieceArmor = bolt == null ? 0.0 : bolt.partial().stream()
                .filter(attr -> attr.attribute() == Attribute.ARMOR)
                .mapToDouble(TrimData.TrimAttribute::amount).sum();
        double armorControl = 0.0;
        double armorScaled = 0.0;
        for (int i = 0; i < TrimData.FULL_SET_SIZE; i++) {
            armorControl += attributeValue(
                    ItemStamper.stamp(trimmedChestplate(TrimMaterial.DIAMOND, TrimPattern.SENTRY)),
                    Attribute.ARMOR);
            armorScaled += attributeValue(
                    ItemStamper.stamp(trimmedChestplate(TrimMaterial.DIAMOND, TrimPattern.BOLT)),
                    Attribute.ARMOR);
        }
        double expectedScale = perPieceArmor * TrimData.FULL_SET_SIZE;
        boolean scaled = Math.abs((armorScaled - armorControl) - expectedScale) < 1.0E-6;

        Material[] armorTypes = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
        Set<NamespacedKey> materialKeys = new HashSet<>();
        for (Material armorType : armorTypes) {
            ItemStack piece = ItemStamper.stamp(withTrim(new ItemStack(armorType),
                    TrimMaterial.AMETHYST, TrimPattern.SENTRY));
            ItemMeta pieceMeta = piece == null ? null : piece.getItemMeta();
            if (pieceMeta != null && pieceMeta.getAttributeModifiers() != null) {
                for (Map.Entry<Attribute, AttributeModifier> entry : pieceMeta.getAttributeModifiers().entries()) {
                    if (TrimData.NAMESPACE.equals(entry.getValue().getKey().getNamespace())) {
                        materialKeys.add(entry.getValue().getKey());
                    }
                }
            }
        }
        boolean slotDistinct = materialKeys.size() == TrimData.FULL_SET_SIZE;

        sender.sendMessage(Component.text("── Scitulus 自检 ──", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  材料刻写: " + pass + "/" + TrimData.MATERIALS.size()
                        + (failures.isEmpty() ? " ✓" : " ✗"),
                failures.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  纹饰单件档刻写: " + patternPass + "/" + patternTotal
                        + (patternPass == patternTotal ? " ✓" : " ✗"),
                patternPass == patternTotal ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  纹饰按件累加: " + (scaled ? "✓" : "✗")
                        + "（4 件镶铆共 +" + (armorScaled - armorControl) + "，应为 +" + expectedScale + "）",
                scaled ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  按槽位区分（同类护甲不互相覆盖）: " + (slotDistinct ? "✓" : "✗")
                        + "（4 件共 " + materialKeys.size() + " 个不同修饰符 id）",
                slotDistinct ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  无纹饰物品不动: " + (untouched ? "✓" : "✗"),
                untouched ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  原版属性保留: " + (preserved ? "✓" : "✗")
                        + "（护甲 " + armorValue + " / 韧性 " + toughnessValue + "）",
                preserved ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  自定义属性共存: " + (coexist ? "✓" : "✗")
                        + "（生命 " + (customStamped == null ? "?" : attributeValue(customStamped, Attribute.MAX_HEALTH))
                        + " / 韧性 " + (customStamped == null ? "?"
                        : attributeValue(customStamped, Attribute.ARMOR_TOUGHNESS)) + "）",
                coexist ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(Component.text("  拆纹饰清理: " + (cleaned ? "✓" : "✗"),
                cleaned ? NamedTextColor.GREEN : NamedTextColor.RED));
        for (String failure : failures) {
            sender.sendMessage(Component.text("  - " + failure, NamedTextColor.RED));
        }
    }

    private static ItemStack trimmedChestplate(TrimMaterial material, TrimPattern pattern) {
        return withTrim(new ItemStack(Material.DIAMOND_CHESTPLATE), material, pattern);
    }

    private static ItemStack withTrim(ItemStack item, TrimMaterial material, TrimPattern pattern) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(material == null || pattern == null ? null : new ArmorTrim(material, pattern));
            copy.setItemMeta(armorMeta);
        }
        return copy;
    }

    private static double attributeValue(ItemStack item, Attribute attribute) {
        ItemMeta meta = item.getItemMeta();
        Multimap<Attribute, AttributeModifier> patch = meta == null ? null : meta.getAttributeModifiers();
        Multimap<Attribute, AttributeModifier> source =
                patch == null || patch.isEmpty() ? item.getType().getDefaultAttributeModifiers() : patch;
        double total = 0.0;
        for (Map.Entry<Attribute, AttributeModifier> entry : source.entries()) {
            if (entry.getKey().equals(attribute)) {
                total += entry.getValue().getAmount();
            }
        }
        return total;
    }

    private static int countOurs(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getAttributeModifiers() == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<Attribute, AttributeModifier> entry : meta.getAttributeModifiers().entries()) {
            if (TrimData.NAMESPACE.equals(entry.getValue().getKey().getNamespace())) {
                count++;
            }
        }
        return count;
    }

    private static String verify(ItemStack item, NamespacedKey expectedKey, Attribute attribute, double amount,
                                 AttributeModifier.Operation operation, EquipmentSlotGroup slotGroup) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getAttributeModifiers() == null) {
            return "物品上没有属性修饰符";
        }
        for (Map.Entry<Attribute, AttributeModifier> entry : meta.getAttributeModifiers().entries()) {
            AttributeModifier modifier = entry.getValue();
            if (expectedKey != null && !expectedKey.equals(modifier.getKey())) {
                continue;
            }
            if (!TrimData.NAMESPACE.equals(modifier.getKey().getNamespace())) {
                continue;
            }
            if (!entry.getKey().equals(attribute)) {
                return "属性不符：期望 " + attribute.getKey().getKey() + "，得到 " + entry.getKey().getKey().getKey();
            }
            if (modifier.getAmount() != amount || modifier.getOperation() != operation) {
                return "数值不符：期望 " + amount + " " + operation + "，得到 "
                        + modifier.getAmount() + " " + modifier.getOperation();
            }
            if (modifier.getSlotGroup() != slotGroup) {
                return "槽位不符：期望 " + slotGroup + "，得到 " + modifier.getSlotGroup();
            }
            return null;
        }
        return "未找到 " + (expectedKey == null ? "scitulus 命名空间的修饰符" : expectedKey);
    }

    private static String describe(List<TrimData.TrimAttribute> trimAttributes, List<TrimData.PotionGain> potions) {
        StringBuilder builder = new StringBuilder();
        for (TrimData.TrimAttribute trimAttribute : trimAttributes) {
            if (!builder.isEmpty()) {
                builder.append("，");
            }
            String sign = trimAttribute.operation() == AttributeModifier.Operation.ADD_SCALAR ? "%" : "";
            double shown = trimAttribute.operation() == AttributeModifier.Operation.ADD_SCALAR ? trimAttribute.amount() * 100 : trimAttribute.amount();
            builder.append(trimAttribute.attribute().getKey().getKey()).append(' ')
                    .append(shown > 0 ? "+" : "").append(trim(shown)).append(sign);
        }
        for (TrimData.PotionGain potion : potions) {
            if (!builder.isEmpty()) {
                builder.append("，");
            }
            builder.append(potion.type().getKey().getKey()).append(' ')
                    .append(roman(potion.amplifier() + 1));
        }
        return builder.isEmpty() ? "—" : builder.toString();
    }

    private static String triggerText(TrimData.Trigger trigger) {
        return switch (trigger) {
            case ALWAYS -> "恒定";
            case IN_WATER -> "在水中";
            case SNEAKING -> "潜行时";
            case SPRINTING -> "疾跑时";
            case SATURATION_AT_MOST_10 -> "饱和度 ≤ 10";
            case FULL_SET_ONLY -> "穿戴全套";
            case COMBAT -> "战斗触发";
        };
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(value);
        };
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
