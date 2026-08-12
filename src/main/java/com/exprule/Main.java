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
        private static final int MASK = (1 << BITS) - 1; // 0x1F
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

            // 保留等级和经验条（keepInventory=true 的核心行为）
            event.setKeepLevel(true);
            event.setNewLevel(player.getLevel());
            event.setNewExp(player.getExp());

            if (droppedExp <= 0) {
                return;
            }

            // 获取玩家死亡位置
            Location deathLocation = player.getLocation().clone();
            Block deathBlock = deathLocation.getBlock();

            // 寻找 8 格半径内的最近的幽匿催发体
            Block catalyst = getNearestCatalyst(deathBlock);

            if (Objects.nonNull(catalyst)) {
                // 记录催发体位置（而非 Block 快照），以便后续重新获取最新状态
                Location catalystLocation = catalyst.getLocation();

                // 同 tick 结束前结算，确保在方块破坏阶段前完成 bloom
                getServer().getScheduler().runTask(this, () ->
                        settleExperience(catalystLocation, deathBlock, deathLocation, droppedExp));
            } else {
                // 附近没有催发体：按原版逻辑正常掉出经验球
                spawnExperience(deathLocation, droppedExp);
            }
        }
    }

    private void settleExperience(Location catalystLocation, Block deathBlock,
                                  Location deathLocation, int droppedExp) {
        // 关键：重新从世界获取当前方块状态，正确检测催发体是否被同爆炸破坏
        Block catalyst = catalystLocation.getWorld().getBlockAt(catalystLocation);

        if (catalyst.getType() == Material.SCULK_CATALYST) {
            // 催发体存活：触发 bloom（播放声音 + 生成蔓延光标，后续 tick 自动蔓延幽匿块）
            try {
                ((SculkCatalyst) catalyst.getState()).bloom(deathBlock, droppedExp);
            } catch (Exception e) {
                // 催发失败兜底：掉落经验
                spawnExperience(deathLocation, droppedExp);
                getLogger().warning("Catalyst bloom failed at " + catalystLocation +
                        ", experience dropped as fallback. Reason: " + e.getMessage());
            }
        } else {
            // 催发体已被破坏（如 TNT 同爆炸）：按原版逻辑掉落经验
            spawnExperience(deathLocation, droppedExp);
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

