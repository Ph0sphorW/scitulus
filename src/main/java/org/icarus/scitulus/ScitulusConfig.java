package org.icarus.scitulus;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record ScitulusConfig(boolean enabled, int refreshIntervalTicks, boolean stampItemAttributes, boolean debug) {

    public static ScitulusConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        return new ScitulusConfig(
                config.getBoolean("enabled", true),
                Math.max(1, config.getInt("refresh-interval-ticks", 5)),
                config.getBoolean("stamp-item-attributes", true),
                config.getBoolean("debug", false));
    }
}
