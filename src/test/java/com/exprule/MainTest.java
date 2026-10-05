package com.exprule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.bukkit.GameEvent;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

// MockBukkit 单元测试，覆盖 {@link Main} 的事件处理逻辑。
// <p>说明：测试环境没有 NMS，{@code new PaperCatalystAccess()} 在 {@code onEnable} 中必然抛
// {@link ReflectiveOperationException} 并被捕获，因此加载后 {@code catalystAccess} 恒为 {@code null}，
// 这正好用于验证“回退到基础经验”的路径。需要验证 {@code addCharge} 调用与否的场景，则通过反射把
// 一个 Mockito mock 注入到 {@code Main.catalystAccess} 字段（注入的是本项目自身的类，不涉及 NMS）。
class MainTest {
    private ServerMock server;
    private Main plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerDeathEvent newDeathEvent(Player player, int droppedExp, int newExp, int newTotalExp, int newLevel) {
        DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getCausingEntity()).thenReturn(null);
        return new PlayerDeathEvent(player, damageSource, new ArrayList<>(), droppedExp, newExp, newTotalExp, newLevel, (net.kyori.adventure.text.Component) null, true);
    }

    private void setKeepInventory(Player player, boolean value) {
        World world = player.getWorld();
        world.setGameRule(GameRule.KEEP_INVENTORY, value);
    }

    private void injectCatalystAccess(PaperCatalystAccess access) throws Exception {
        Field field = Main.class.getDeclaredField("catalystAccess");
        field.setAccessible(true);
        field.set(plugin, access);
    }

    @Test
    @DisplayName("KEEP_INVENTORY=false 时 onPlayerDeath 不修改任何经验字段")
    void keepInventoryFalseLeavesEventUntouched() {
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, false);
        player.setLevel(20);
        PlayerDeathEvent event = newDeathEvent(player, 42, 11, 22, 33);
        plugin.onPlayerDeath(event);
        assertEquals(42, event.getDroppedExp(), "droppedExp 不应被修改");
        assertEquals(11, event.getNewExp(), "newExp 不应被修改");
        assertEquals(33, event.getNewLevel(), "newLevel 不应被修改");
    }

    @Test
    @DisplayName("KEEP_INVENTORY=true 时 onPlayerDeath 将 newLevel 和 newExp 置为 0")
    void keepInventoryTrueZeroesLevelAndExp() {
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        player.setLevel(5);
        PlayerDeathEvent event = newDeathEvent(player, 0, 11, 22, 33);
        plugin.onPlayerDeath(event);
        assertEquals(0, event.getNewExp(), "newExp 应被置为 0");
        assertEquals(0, event.getNewLevel(), "newLevel 应被置为 0");
    }

    @Test
    @DisplayName("KEEP_INVENTORY=true 且等级 15 时基础经验被上限截断为 100")
    void keepInventoryTrueCapsDroppedExpAt100() throws Exception {
        injectCatalystAccess(null);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        player.setLevel(15); // 157 = 105，截断为 100
        PlayerDeathEvent event = newDeathEvent(player, 0, 9, 9, 9);
        plugin.onPlayerDeath(event);
        assertEquals(100, event.getDroppedExp(), "超过 100 的基础经验应被截断");
        assertEquals(0, event.getNewLevel(), "newLevel 应被置为 0");
    }

    @Test
    @DisplayName("玩家等级为 0 时 droppedExp 最终为 0")
    void zeroLevelDropsZeroExp() {
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        player.setLevel(0);
        PlayerDeathEvent event = newDeathEvent(player, 99, 0, 0, 0);
        plugin.onPlayerDeath(event);
        assertEquals(0, event.getDroppedExp(), "0 级玩家掉落的经验应为 0");
    }

    @Test
    @DisplayName("catalystAccess 为 null 时 onPlayerDeath 正常执行并回退到基础经验 min(level*7,100)")
    void nullCatalystAccessFallsBackToBaseExperience() throws Exception {
        // 加载后 catalystAccess 本就为 null（无 NMS），这里显式确认并验证回退值。
        injectCatalystAccess(null);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        player.setLevel(10); // 107 = 70
        PlayerDeathEvent event = newDeathEvent(player, 0, 0, 0, 0);
        assertDoesNotThrow(() -> plugin.onPlayerDeath(event));
        assertEquals(70, event.getDroppedExp(), "应回退到基础经验 70");
    }

    @Test
    @DisplayName("deathExperience 成功时 droppedExp 使用其返回值，而不是基础经验")
    void deathExperienceOverridesBaseExperience() throws Exception {
        PaperCatalystAccess access = mock(PaperCatalystAccess.class);
        injectCatalystAccess(access);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        player.setLevel(10); // 基础经验 70，被 deathExperience 覆盖
        Entity attacker = mock(Entity.class);
        DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getCausingEntity()).thenReturn(attacker);
        PlayerDeathEvent event = new PlayerDeathEvent(player, damageSource, new ArrayList<>(), 0, 4, 4, 4, (net.kyori.adventure.text.Component) null, true);
        when(access.deathExperience(player, attacker)).thenReturn(13);
        plugin.onPlayerDeath(event);
        assertEquals(13, event.getDroppedExp(), "应使用 deathExperience 的返回值");
        assertEquals(0, event.getNewExp(), "newExp 仍应被置为 0");
        verify(access, times(1)).deathExperience(player, attacker);
    }

    @Test
    @DisplayName("deathExperience 抛异常时保留此前写入的基础经验")
    void deathExperienceFailureKeepsBaseExperience() throws Exception {
        PaperCatalystAccess access = mock(PaperCatalystAccess.class);
        injectCatalystAccess(access);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        player.setLevel(10);
        PlayerDeathEvent event = newDeathEvent(player, 0, 0, 0, 0);
        when(access.deathExperience(player, null)).thenThrow(new ReflectiveOperationException("nms"));
        assertDoesNotThrow(() -> plugin.onPlayerDeath(event));
        assertEquals(70, event.getDroppedExp(), "反射失败后应保留基础经验 70");
        assertEquals(0, event.getNewLevel(), "等级仍应被清空");
    }

    @Test
    @DisplayName("catalystAccess 为 null 时旁观者模式回退经验为 0")
    void nullCatalystAccessSpectatorFallsBackToZero() throws Exception {
        injectCatalystAccess(null);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        player.setLevel(30);
        player.setGameMode(GameMode.SPECTATOR);
        PlayerDeathEvent event = newDeathEvent(player, 0, 0, 0, 0);
        plugin.onPlayerDeath(event);
        assertEquals(0, event.getDroppedExp(), "旁观者模式基础经验应为 0");
    }

    @Test
    @DisplayName("onEntityDie: 事件类型不是 ENTITY_DIE 时提前返回，不调用 addCharge")
    void entityDieIgnoresNonEntityDieEvent() throws Exception {
        PaperCatalystAccess access = mock(PaperCatalystAccess.class);
        injectCatalystAccess(access);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        GenericGameEvent event = mock(GenericGameEvent.class);
        when(event.getEvent()).thenReturn(GameEvent.BLOCK_DESTROY);
        when(event.getEntity()).thenReturn(player);
        plugin.onEntityDie(event);
        verify(access, never()).addCharge(any(), anyInt());
    }

    @Test
    @DisplayName("onEntityDie: 实体不是玩家时提前返回，不调用 addCharge")
    void entityDieIgnoresNonPlayerEntity() throws Exception {
        PaperCatalystAccess access = mock(PaperCatalystAccess.class);
        injectCatalystAccess(access);
        Entity notPlayer = mock(Entity.class);
        GenericGameEvent event = mock(GenericGameEvent.class);
        when(event.getEvent()).thenReturn(GameEvent.ENTITY_DIE);
        when(event.getEntity()).thenReturn(notPlayer);
        plugin.onEntityDie(event);
        verify(access, never()).addCharge(any(), anyInt());
    }

    @Test
    @DisplayName("onEntityDie: KEEP_INVENTORY=false 时提前返回，不调用 addCharge")
    void entityDieIgnoresWhenKeepInventoryFalse() throws Exception {
        PaperCatalystAccess access = mock(PaperCatalystAccess.class);
        injectCatalystAccess(access);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, false);
        GenericGameEvent event = mock(GenericGameEvent.class);
        when(event.getEvent()).thenReturn(GameEvent.ENTITY_DIE);
        when(event.getEntity()).thenReturn(player);
        when(event.getRadius()).thenReturn(8);
        plugin.onEntityDie(event);
        verify(access, never()).addCharge(any(), anyInt());
    }

    @Test
    @DisplayName("onEntityDie: ENTITY_DIE + 玩家 + KEEP_INVENTORY=true 时调用一次 addCharge（正向对照）")
    void entityDieCallsAddChargeForPlayerWithKeepInventory() throws Exception {
        PaperCatalystAccess access = mock(PaperCatalystAccess.class);
        injectCatalystAccess(access);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        GenericGameEvent event = mock(GenericGameEvent.class);
        when(event.getEvent()).thenReturn(GameEvent.ENTITY_DIE);
        when(event.getEntity()).thenReturn(player);
        when(event.getRadius()).thenReturn(8);
        plugin.onEntityDie(event);
        verify(access, times(1)).addCharge(player, 8);
    }

    @Test
    @DisplayName("onEntityDie: catalystAccess 为 null 时不抛异常")
    void entityDieWithNullCatalystAccessDoesNotThrow() throws Exception {
        injectCatalystAccess(null);
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        GenericGameEvent event = mock(GenericGameEvent.class);
        when(event.getEvent()).thenReturn(GameEvent.ENTITY_DIE);
        when(event.getEntity()).thenReturn(player);
        when(event.getRadius()).thenReturn(8);
        assertDoesNotThrow(() -> plugin.onEntityDie(event));
    }

    @Test
    @DisplayName("onEntityDie: addCharge 抛异常时不向外传播")
    void entityDieSwallowsAddChargeFailure() throws Exception {
        PaperCatalystAccess access = mock(PaperCatalystAccess.class);
        injectCatalystAccess(access);
        doThrow(new ReflectiveOperationException("nms")).when(access).addCharge(any(), anyInt());
        PlayerMock player = server.addPlayer();
        setKeepInventory(player, true);
        GenericGameEvent event = mock(GenericGameEvent.class);
        when(event.getEvent()).thenReturn(GameEvent.ENTITY_DIE);
        when(event.getEntity()).thenReturn(player);
        when(event.getRadius()).thenReturn(8);
        assertDoesNotThrow(() -> plugin.onEntityDie(event));
        verify(access, times(1)).addCharge(player, 8);
    }
}
