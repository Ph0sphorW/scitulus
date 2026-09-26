package org.icarus.scitulus.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.icarus.scitulus.ItemStamper;
import org.icarus.scitulus.Scitulus;

public final class SmithingListener implements Listener {

    private final Scitulus plugin;

    public SmithingListener(Scitulus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        ItemStack stamped = ItemStamper.stamp(result);
        if (stamped != null) {
            event.setResult(stamped);
            if (this.plugin.config().debug()) {
                this.plugin.getLogger().info("[debug] 锻造预览已刻上纹饰材料属性: " + stamped.getType());
            }
        }
    }
}
