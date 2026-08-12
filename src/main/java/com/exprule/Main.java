package com.exprule;

import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.SculkCatalyst;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Main extends JavaPlugin implements Listener {
    private static short[] findingTable;
    static class PosCompressor{
        private static final int BITS = 5;
        private static final int MASK = (1 << BITS) - 1; // 低 5 位掩码（0x1F）
        private static final int OFFSET = 8;

        public static short compress(int x, int y, int z) {
            int va = x + OFFSET;
            int vb = y + OFFSET;
            int vc = z + OFFSET;
            return (short) ((va << (BITS * 2)) | (vb << BITS) | vc);
        }

        public static int getX(short pos) {
            return ((pos >>> (BITS * 2)) & MASK) - OFFSET;
        }

        public static int getY(short pos) {
            return ((pos >>> BITS) & MASK) - OFFSET;
        }

        public static int getZ(short pos) {
            return (pos & MASK) - OFFSET;
        }
    }

    @Override
    public void onEnable() {
        // 注册监听器
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ExpRule has been enabled.");

        List<int[]> posList = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -8; dz <= 8; dz++){
                    if (dx * dx + dy * dy + dz * dz <= 64) {
                        posList.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        findingTable = new short[posList.size()];
        System.out.println(posList.size());
        int index = 0;
        for (int[] ints : posList.stream().sorted(Comparator.comparingInt((pos) -> pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2])).toList()) {
            findingTable[index++] = PosCompressor.compress(ints[0], ints[1], ints[2]);
        }
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
            Location deathLocation = player.getLocation().clone();
            Block deathBlock = deathLocation.getBlock();

            // 寻找 8 格半径内的最近的幽匿催发体
            Block catalyst = getNearestCatalyst(deathBlock);

            if (Objects.nonNull(catalyst)) {
                // 附近有催发体：拦截所有掉落的经验球
                event.setDroppedExp(0);

                // 下一游戏刻再结算，让同一次爆炸先完成方块破坏。
                getServer().getScheduler().runTaskLater(this,
                        () -> settleExperience(catalyst, deathBlock, deathLocation, droppedExp, player.getName()),
                        1L);
            } else {
                // 附近没有催发体：按原版逻辑正常掉出经验球
                event.setDroppedExp(droppedExp);
            }
        }
    }

    private void settleExperience(Block catalyst, Block deathBlock, Location deathLocation,
                                  int droppedExp, String playerName) {
        if (catalyst.getType() != Material.SCULK_CATALYST) {
            spawnExperience(deathLocation, droppedExp);
            return;
        }

        try {
            ((SculkCatalyst)catalyst.getState()).bloom(deathBlock, droppedExp);
        } catch (Exception e) {
            // 催发失败时不能吞掉玩家经验。
            spawnExperience(deathLocation, droppedExp);

            String deathPos = deathBlock.getWorld().getName() + " " + deathBlock.getX() + "," + deathBlock.getY() + "," + deathBlock.getZ();
            String catalystPos = catalyst.getWorld().getName() + " " + catalyst.getX() + "," + catalyst.getY() + "," + catalyst.getZ();
            getLogger().warning("玩家 " + playerName + " 死亡，位于 " + catalystPos
                    + " 的最近幽匿催发体触发催发时发生异常；死亡位置：" + deathPos
                    + "；原因：" + e.getMessage());
        }
    }

    private void spawnExperience(Location location, int experience) {
        location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(experience));
    }

    public Block getNearestCatalyst(Block center) {
        for (short i : findingTable) {
            int x = PosCompressor.getX(i);
            int y = PosCompressor.getY(i);
            int z = PosCompressor.getZ(i);
            Block block = center.getRelative(x, y, z);
            if (block.getType() == Material.SCULK_CATALYST) {
                return block;
            }
        }
        return null;
    }
}

