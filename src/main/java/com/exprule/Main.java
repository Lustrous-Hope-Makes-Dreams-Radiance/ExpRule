package com.exprule;

import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.SculkCatalyst;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

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

            // 寻找 8 格半径内的所有幽匿催发体
            List<Block> catalysts = getNearbyCatalysts(deathBlock, 8);

            if (!catalysts.isEmpty()) {
                // 附近有催发体：拦截所有掉落的经验球
                event.setDroppedExp(0);

                // 找出距离死亡位置最近的催发体
                Block nearest = null;
                double nearestDistSq = Double.MAX_VALUE;
                for (Block b : catalysts) {
                    double distSq = b.getLocation().distanceSquared(deathBlock.getLocation());
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = b;
                    }
                }

                // 只让最近的那个催发体吸收经验并触发幽匿蔓延
                if (nearest != null && nearest.getState() instanceof SculkCatalyst catalyst) {
                    try {
                        catalyst.bloom(deathBlock, droppedExp);
                    } catch (Exception e) {
                        // 记录死亡玩家、死亡位置和催发体位置
                        String playerName = player.getName();
                        String deathPos = deathBlock.getWorld().getName() + " " + deathBlock.getX() + "," + deathBlock.getY() + "," + deathBlock.getZ();
                        String catalystPos = nearest.getWorld().getName() + " " + nearest.getX() + "," + nearest.getY() + "," + nearest.getZ();
                        getLogger().warning("Player " + playerName + " died, nearest catalyst at " + catalystPos +
                                            " triggered bloom exception, death location: " + deathPos + ", reason: " + e.getMessage());
                    }
                }
            } else {
                // 附近没有催发体：按原版逻辑正常掉出经验球
                event.setDroppedExp(droppedExp);
            }
        }
    }

    // 收集以 center 为中心、半径 radius 格（球体）内的所有幽匿催发体方块。
    private List<Block> getNearbyCatalysts(Block center, int radius) {
        List<Block> found = new ArrayList<>();
        int radiusSq = radius * radius;

        World world = center.getWorld();
        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();
        int centerInWorldY = center.getY();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                // 安全检查：如果超出当前世界的高度边界，直接跳过这一层 Y 轴
                int targetY = centerInWorldY + y;
                if (targetY < minHeight || targetY >= maxHeight) {  
                    continue;
                }

                for (int z = -radius; z <= radius; z++) {
                    // 球面剪枝：利用纯数学计算，如果超出球形半径，直接跳过
                    if ((x * x + y * y + z * z) > radiusSq) {
                        continue;
                    }

                    Block block = center.getRelative(x, y, z);
                    if (block.getType() == Material.SCULK_CATALYST) {
                        found.add(block);
                    }
                }
            }
        }
        return found;
    }
}