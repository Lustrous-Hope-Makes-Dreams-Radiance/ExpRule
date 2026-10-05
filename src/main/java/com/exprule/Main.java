package com.exprule;

import org.bukkit.GameRule;
import org.bukkit.GameEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class Main extends JavaPlugin implements Listener {
    private PaperCatalystAccess catalystAccess;

    @Override
    public void onEnable() {
        try {
            catalystAccess = new PaperCatalystAccess();
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Cannot access Paper death experience handling; base death experience will still drop.", e);
        }

        // 注册监听器
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ExpRule has been enabled.");

    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 判断死亡不掉落
        Boolean keepInv = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);
        if (keepInv != null && keepInv) {
            event.setKeepLevel(false);
            event.setNewLevel(0);
            event.setNewExp(0);
            int droppedExp = PaperCatalystAccess.baseExperience(player);
            if (catalystAccess != null) {
                try {
                    droppedExp = catalystAccess.deathExperience(player, event.getDamageSource().getCausingEntity());
                } catch (ReflectiveOperationException | RuntimeException e) {
                    getLogger().log(Level.SEVERE, "Cannot calculate Paper death experience for " + player.getName()
                    + "; base death experience will still drop.", e);
                }
            }
            event.setDroppedExp(droppedExp);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDie(GenericGameEvent event) {
        if (event.getEvent() != GameEvent.ENTITY_DIE || !(event.getEntity() instanceof Player player)
                || !Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY))
                || catalystAccess == null) {
            return;
        }
        try {
            catalystAccess.addCharge(player, event.getRadius());
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Cannot add Paper sculk charge for " + player.getName()
                    + "; death experience will still drop.", e);
        }
    }
}

