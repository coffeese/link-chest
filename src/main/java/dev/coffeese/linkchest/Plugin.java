package dev.coffeese.linkchest;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

public class Plugin extends JavaPlugin {

    private Logger logger;

    @Override
    public void onLoad() {
        logger = getLogger();
        logger.info("onLoad");
    }

    @Override
    public void onEnable() {
        logger.info("onEnable");

        Containers containers = new Containers(this.getDataFolder(), logger);
        if (containers.init()) {
            this.getServer().getPluginManager().registerEvents(new ChestListener(this, containers, logger), this);
        } else {
            throw new RuntimeException("Plugin initialize failed...");
        }
    }

    @Override
    public void onDisable() {
        logger.info("onDisable");
    }
}
