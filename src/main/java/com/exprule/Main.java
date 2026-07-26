package com.exprule;

import org.bukkit.GameRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class Main extends JavaPlugin implements Listener {
    public static List<String> excepts;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ExpRule has been enabled.");
        excepts = getConfig().getStringList("excepts");
    }

    @Override
    public void onLoad() {
        excepts = getConfig().getStringList("excepts");
    }

    @EventHandler
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            double finalDamage = event.getFinalDamage();
            if (player.getHealth() <= finalDamage) {
                if (Boolean.FALSE.equals(player.getWorld().getGameRuleValue(GameRules.KEEP_INVENTORY))) return;
                player.getWorld().setGameRule(GameRules.KEEP_INVENTORY, false);
                player.getServer().getScheduler().runTaskLater(this, () -> player.getWorld().setGameRule(GameRules.KEEP_INVENTORY, true), 1);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (excepts.contains(event.getEntity().getWorld().getKey().asString())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
    }
}

