package org.icarus.scitulus.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.icarus.scitulus.PlayerEffects;
import org.icarus.scitulus.TrimData;

public final class WardListener implements Listener {

    private final PlayerEffects effects;

    public WardListener(PlayerEffects effects) {
        this.effects = effects;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PotionEffect added = event.getNewEffect();
        if (added == null || added.getType() != PotionEffectType.DARKNESS) {
            return;
        }
        if (this.effects.armorSet(player).isFullSet(TrimData.WARD)) {
            event.setCancelled(true);
        }
    }
}
