package com.exprule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// {@link PaperCatalystAccess} 的反射契约测试。
// <p>该类依赖 NMS（net.minecraft.*）反射，单元测试环境（只有 paper-api，没有 Paper 服务端）
// 无法加载这些类，因此只覆盖不依赖 NMS 的纯逻辑：{@code baseExperience} 与构造函数的失败降级。
class PaperCatalystAccessTest {
    private Player playerWith(int level, GameMode gameMode) {
        Player player = mock(Player.class);
        when(player.getLevel()).thenReturn(level);
        when(player.getGameMode()).thenReturn(gameMode);
        return player;
    }

    @Test
    @DisplayName("baseExperience: 等级 0 返回 0")
    void baseExperienceZeroLevel() {
        assertEquals(0, PaperCatalystAccess.baseExperience(playerWith(0, GameMode.SURVIVAL)));
    }

    @Test
    @DisplayName("baseExperience: 等级 10 返回 70（10*7）")
    void baseExperienceTenLevels() {
        assertEquals(70, PaperCatalystAccess.baseExperience(playerWith(10, GameMode.SURVIVAL)));
    }

    @Test
    @DisplayName("baseExperience: 等级 14 返回 98（未触顶）")
    void baseExperienceJustBelowCap() {
        assertEquals(98, PaperCatalystAccess.baseExperience(playerWith(14, GameMode.SURVIVAL)));
    }

    @Test
    @DisplayName("baseExperience: 等级 15 返回 100（105 被上限截断）")
    void baseExperienceCappedAt100() {
        assertEquals(100, PaperCatalystAccess.baseExperience(playerWith(15, GameMode.SURVIVAL)));
    }

    @Test
    @DisplayName("baseExperience: 等级 100 仍返回 100")
    void baseExperienceFarAboveCap() {
        assertEquals(100, PaperCatalystAccess.baseExperience(playerWith(100, GameMode.CREATIVE)));
    }

    @Test
    @DisplayName("baseExperience: 旁观者模式返回 0")
    void baseExperienceSpectatorReturnsZero() {
        assertEquals(0, PaperCatalystAccess.baseExperience(playerWith(50, GameMode.SPECTATOR)));
    }

    @Test
    @Disabled("需要真实 Paper 类路径：在 paperweight-userdev 下运行，或将真实 Paper jar 加入测试 classpath。" + "普通单元测试环境缺少 net.minecraft.*，构造函数必然抛 ClassNotFoundException。")
    @DisplayName("构造函数在 Paper 环境下反射解析全部成员且不抛异常")
    void constructorSucceedsOnPaperServer() {
        assertDoesNotThrow(PaperCatalystAccess::new);
    }
}
