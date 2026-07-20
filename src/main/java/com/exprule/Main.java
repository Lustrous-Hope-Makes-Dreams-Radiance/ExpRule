package com.exprule;

import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.SculkCatalyst;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class Main extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // 注册监听器
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ExpRule has been enabled.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 判断死亡不掉落
        Boolean keepInv = player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY);
        if (keepInv != null && keepInv) {
            // 计算原版掉落经验（等级 * 7，最大上限 100）
            int droppedExp = Math.min(player.getLevel() * 7, 100);

            event.setKeepLevel(false);
            event.setNewLevel(0);
            event.setNewExp(0);

            if (droppedExp <= 0) {
                return;
            }

            // 获取玩家死亡位置
            Block deathBlock = player.getLocation().getBlock();

            // 寻找 8 格半径内的最近的幽匿催发体
            Block catalyst = getNearestCatalyst(deathBlock, 8);

            if (Objects.nonNull(catalyst)) {
                // 附近有催发体：拦截所有掉落的经验球
                event.setDroppedExp(0);

                // 最近的那个催发体吸收经验并触发幽匿蔓延
                try {
                    ((SculkCatalyst)catalyst.getState()).bloom(deathBlock, droppedExp);
                } catch (Exception e) {
                    // 记录死亡玩家、死亡位置和催发体位置
                    String playerName = player.getName();
                    String deathPos = deathBlock.getWorld().getName() + " " + deathBlock.getX() + "," + deathBlock.getY() + "," + deathBlock.getZ();
                    String catalystPos = catalyst.getWorld().getName() + " " + catalyst.getX() + "," + catalyst.getY() + "," + catalyst.getZ();
                    getLogger().warning("Player " + playerName + " died, nearest catalyst at " + catalystPos +
                            " triggered bloom exception, death location: " + deathPos + ", reason: " + e.getMessage());
                }
            } else {
                // 附近没有催发体：按原版逻辑正常掉出经验球
                event.setDroppedExp(droppedExp);
            }
        }
    }

    public Block getNearestCatalyst(Block center, int radius) {
        int radiusSq = radius * radius;

        Block nearest = null;
        int nearestDistSq = Integer.MAX_VALUE;

        for (int d = 0; d <= radius; d++) {
            int dx;
            int dy;
            int dz;
            //Y+
            dy = d;
            for (dx = -d; dx <= d; dx++) {
                for (dz = -d; dz <= d; dz++) {
                    int distSq = dx * dx + dz * dz + dy * dy;
                    if (distSq <= radiusSq && distSq < nearestDistSq) {
                        Block block = center.getRelative(dx, dy, dz);
                        if (block.getType() == Material.SCULK_CATALYST) {
                            nearest = block;
                            nearestDistSq = distSq;
                        }
                    }
                }
            }
            //Y-
            dy = -d;
            for (dx = -d; dx <= d; dx++) {
                for (dz = -d; dz <= d; dz++) {
                    int distSq = dx * dx + dz * dz + dy * dy;
                    if (distSq <= radiusSq && distSq < nearestDistSq) {
                        Block block = center.getRelative(dx, dy, dz);
                        if (block.getType() == Material.SCULK_CATALYST) {
                            nearest = block;
                            nearestDistSq = distSq;
                        }
                    }
                }
            }
            // X+
            dx = d;
            for (dy = -d + 1; dy <= d - 1; dy++) {
                for (dz = -d; dz <= d - 1; dz++) {
                    int distSq = dx * dx + dz * dz + dy * dy;
                    if (distSq <= radiusSq && distSq < nearestDistSq) {
                        Block block = center.getRelative(dx, dy, dz);
                        if (block.getType() == Material.SCULK_CATALYST) {
                            nearest = block;
                            nearestDistSq = distSq;
                        }
                    }
                }
            }
            // X-
            dx = -d;
            for (dy = -d + 1; dy <= d - 1; dy++) {
                for (dz = -d + 1; dz <= d; dz++) {
                    int distSq = dx * dx + dz * dz + dy * dy;
                    if (distSq <= radiusSq && distSq < nearestDistSq) {
                        Block block = center.getRelative(dx, dy, dz);
                        if (block.getType() == Material.SCULK_CATALYST) {
                            nearest = block;
                            nearestDistSq = distSq;
                        }
                    }
                }
            }
            // Z+
            dz = d;
            for (dy = -d + 1; dy <= d - 1; dy++) {
                for (dx = -d + 1; dx <= d; dx++) {
                    int distSq = dx * dx + dz * dz + dy * dy;
                    if (distSq <= radiusSq && distSq < nearestDistSq) {
                        Block block = center.getRelative(dx, dy, dz);
                        if (block.getType() == Material.SCULK_CATALYST) {
                            nearest = block;
                            nearestDistSq = distSq;
                        }
                    }
                }
            }
            // Z-
            dz = -d;
            for (dy = -d + 1; dy <= d - 1; dy++) {
                for (dx = -d; dx <= d - 1; dx++) {
                    int distSq = dx * dx + dz * dz + dy * dy;
                    if (distSq <= radiusSq && distSq < nearestDistSq) {
                        Block block = center.getRelative(dx, dy, dz);
                        if (block.getType() == Material.SCULK_CATALYST) {
                                nearest = block;
                                nearestDistSq = distSq;
                        }
                    }
                }
            }
            if (Objects.nonNull(nearest) && (d + 1) * (d + 1) > nearestDistSq)
                return nearest;
        }
        return nearest;
    }
}
