package org.icarus.scitulus;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.icarus.scitulus.command.ScitulusCommand;
import org.icarus.scitulus.listener.CombatListener;
import org.icarus.scitulus.listener.SmithingListener;
import org.icarus.scitulus.listener.WardListener;

public final class Scitulus extends JavaPlugin {

    private ScitulusConfig config;
    private Cooldowns cooldowns;
    private PlayerEffects effects;

    @Override
    public void onEnable() {
        this.config = ScitulusConfig.load(this);
        this.cooldowns = new Cooldowns();
        this.effects = new PlayerEffects(this);

        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(this.effects, this);
        manager.registerEvents(new CombatListener(this, this.effects, this.cooldowns), this);
        manager.registerEvents(new SmithingListener(this), this);
        manager.registerEvents(new WardListener(this.effects), this);

        PluginCommand command = getCommand("scitulus");
        if (command != null) {
            ScitulusCommand executor = new ScitulusCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (this.config.enabled()) {
            this.effects.start();
        }

        getLogger().info("Scitulus - Smithing is great again");
        getLogger().info("Made by Ph0sphorW with ❤");
    }

    @Override
    public void onDisable() {
        if (this.effects != null) {
            this.effects.stop();
        }
    }

    public void reload() {
        if (this.effects != null) {
            this.effects.stop();
        }
        reloadConfig();
        this.config = ScitulusConfig.load(this);
        if (this.config.enabled()) {
            this.effects.start();
        }
    }

    public ScitulusConfig config() {
        return this.config;
    }
}
