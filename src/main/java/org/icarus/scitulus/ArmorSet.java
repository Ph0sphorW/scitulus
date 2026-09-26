package org.icarus.scitulus;

import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("removal")
public final class ArmorSet {

    public static final List<EquipmentSlot> SLOTS =
            List.of(EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET);

    public record ArmorPiece(EquipmentSlot slot, Key material, Key pattern) {
    }

    private final List<ArmorPiece> armorPieces;
    private final Map<Key, Integer> patternCounts;
    private final Map<Key, Integer> materialCounts;

    private ArmorSet(List<ArmorPiece> armorPieces) {
        this.armorPieces = List.copyOf(armorPieces);
        Map<Key, Integer> patterns = new HashMap<>();
        Map<Key, Integer> materials = new HashMap<>();
        for (ArmorPiece armorPiece : this.armorPieces) {
            patterns.merge(armorPiece.pattern(), 1, Integer::sum);
            materials.merge(armorPiece.material(), 1, Integer::sum);
        }
        this.patternCounts = Map.copyOf(patterns);
        this.materialCounts = Map.copyOf(materials);
    }

    public static ArmorSet of(Player player) {
        List<ArmorPiece> found = new ArrayList<>(SLOTS.size());
        for (EquipmentSlot slot : SLOTS) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item.getType().isAir()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof ArmorMeta armorMeta) || !armorMeta.hasTrim()) {
                continue;
            }
            ArmorTrim trim = armorMeta.getTrim();
            if (trim == null) {
                continue;
            }
            found.add(new ArmorPiece(slot, trim.getMaterial().key(), trim.getPattern().key()));
        }
        return new ArmorSet(found);
    }

    public int count(Key pattern) {
        return this.patternCounts.getOrDefault(pattern, 0);
    }

    public int materialCount(Key material) {
        return this.materialCounts.getOrDefault(material, 0);
    }

    public boolean isFullSet(Key pattern) {
        return count(pattern) >= TrimData.FULL_SET_SIZE;
    }

    public boolean isEmpty() {
        return this.armorPieces.isEmpty();
    }
}
