package org.icarus.scitulus;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"removal", "unstable"})
public final class ItemStamper {

    private record Key(Attribute attribute, NamespacedKey key, double amount,
                       AttributeModifier.Operation operation) {

        static Key of(Attribute attribute, AttributeModifier modifier) {
            return new Key(attribute, modifier.getKey(), modifier.getAmount(), modifier.getOperation());
        }

        boolean isOurs() {
            return TrimData.NAMESPACE.equals(this.key.getNamespace());
        }
    }

    private ItemStamper() {
    }

    public static ItemStack stamp(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        if (!(item.getItemMeta() instanceof ArmorMeta meta)) {
            return null;
        }

        TrimData.MaterialBonus material = null;
        TrimData.PatternDefinition pattern = null;
        if (meta.hasTrim()) {
            ArmorTrim trim = meta.getTrim();
            if (trim != null) {
                material = TrimData.materialOf(trim.getMaterial());
                pattern = TrimData.patternOf(trim.getPattern().key());
            }
        }
        boolean stamps = material != null || (pattern != null && pattern.stampable());

        Map<Key, EquipmentSlotGroup> current = collect(meta.getAttributeModifiers());
        if (!stamps && current.keySet().stream().noneMatch(Key::isOurs)) {
            return null;
        }

        Map<Key, EquipmentSlotGroup> desired = collect(item.getType().getDefaultAttributeModifiers());
        current.forEach(desired::putIfAbsent);
        desired.keySet().removeIf(Key::isOurs);

        EquipmentSlotGroup slot = TrimData.slotGroupOf(item.getType());
        if (material != null) {
            desired.put(new Key(material.attribute(), material.itemKey(), material.amount(), material.operation()),
                    slot);
        }
        if (pattern != null && pattern.stampable()) {
            List<TrimData.TrimAttribute> attrs = pattern.partial();
            for (int i = 0; i < attrs.size(); i++) {
                TrimData.TrimAttribute attr = attrs.get(i);
                desired.put(new Key(attr.attribute(), pattern.itemKey(i), attr.amount(), attr.operation()), slot);
            }
        }

        if (desired.keySet().equals(current.keySet())) {
            return null;
        }

        Multimap<Attribute, AttributeModifier> modifiers = LinkedHashMultimap.create();
        desired.forEach((entry, group) -> modifiers.put(entry.attribute(),
                new AttributeModifier(entry.key(), entry.amount(), entry.operation(), group)));

        ItemStack copy = item.clone();
        ItemMeta copyMeta = copy.getItemMeta();
        copyMeta.setAttributeModifiers(modifiers);
        copy.setItemMeta(copyMeta);
        return copy;
    }

    private static Map<Key, EquipmentSlotGroup> collect(Multimap<Attribute, AttributeModifier> modifiers) {
        Map<Key, EquipmentSlotGroup> entries = new LinkedHashMap<>();
        if (modifiers != null) {
            for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                entries.putIfAbsent(Key.of(entry.getKey(), entry.getValue()),
                        entry.getValue().getSlotGroup());
            }
        }
        return entries;
    }
}
