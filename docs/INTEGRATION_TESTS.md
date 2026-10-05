# ExpRule 测试说明

本文档分两部分：

1. `mvn test` 执行的单元测试，逐项说明场景和断言。
2. 必须在真实 Paper 服务端执行的手动集成测试。

单元测试使用 MockBukkit、JUnit 5 和 Mockito。测试 classpath 只有 `paper-api`，没有 `net.minecraft.*`，因此不能验证 `PaperCatalystAccess` 的真实 NMS 反射、幽匿蔓延、粒子、声音或成就。

## 单元测试边界

`MainTest` 每个用例都会调用 `MockBukkit.mock()` 与 `MockBukkit.load(Main.class)`，结束后调用 `MockBukkit.unmock()`。由于没有 NMS，插件 `onEnable` 创建 `PaperCatalystAccess` 时会失败并捕获异常，加载完成后 `catalystAccess` 为 `null`。

需要模拟反射可用或反射失败的用例，会把 Mockito mock 注入 `Main.catalystAccess`。这只验证 `Main` 的调用和异常处理，不执行 NMS。

`PlayerDeathEvent` 使用真实构造器。`DamageSource` 使用 mock，因为 `DamageSource.builder(DamageType)` 依赖服务端实现。默认伤害源的 `getCausingEntity()` 返回 `null`。

## MainTest

### 玩家死亡

| 测试 | 场景 | 断言 |
| --- | --- | --- |
| `keepInventoryFalseLeavesEventUntouched` | `KEEP_INVENTORY=false`，等级 20；初始 `droppedExp=42`、`newExp=11`、`newLevel=33` | 三个字段保持 42、11、33 |
| `keepInventoryTrueZeroesLevelAndExp` | `KEEP_INVENTORY=true`，等级 5；初始 `newExp=11`、`newLevel=33` | `newExp=0`、`newLevel=0` |
| `keepInventoryTrueCapsDroppedExpAt100` | `catalystAccess=null`，`KEEP_INVENTORY=true`，等级 15 | `droppedExp=100`（15×7=105 被截断）、`newLevel=0` |
| `zeroLevelDropsZeroExp` | `KEEP_INVENTORY=true`，等级 0；初始 `droppedExp=99` | `droppedExp=0` |
| `nullCatalystAccessFallsBackToBaseExperience` | `catalystAccess=null`，`KEEP_INVENTORY=true`，等级 10 | 不抛异常，`droppedExp=70` |
| `nullCatalystAccessSpectatorFallsBackToZero` | `catalystAccess=null`，`KEEP_INVENTORY=true`，等级 30，旁观者模式 | `droppedExp=0` |
| `deathExperienceOverridesBaseExperience` | 注入 mock；等级 10，伤害源的击杀者是 mock 实体，`deathExperience` 返回 13 | 以玩家和击杀者调用一次 `deathExperience`；`droppedExp=13`、`newExp=0` |
| `deathExperienceFailureKeepsBaseExperience` | 注入 mock；等级 10，无击杀者，`deathExperience` 抛出 `ReflectiveOperationException` | 不向外抛异常；`droppedExp` 保留基础值 70，`newLevel=0` |

`keepInventoryTrueZeroesLevelAndExp` 当前没有断言 `keepLevel=false` 或该等级对应的 `droppedExp`。

### 实体死亡游戏事件

| 测试 | 场景 | 断言 |
| --- | --- | --- |
| `entityDieIgnoresNonEntityDieEvent` | 注入 mock；事件是 `BLOCK_DESTROY`，实体是玩家，`KEEP_INVENTORY=true` | 不调用 `addCharge` |
| `entityDieIgnoresNonPlayerEntity` | 注入 mock；事件是 `ENTITY_DIE`，实体不是玩家 | 不调用 `addCharge` |
| `entityDieIgnoresWhenKeepInventoryFalse` | 注入 mock；事件是 `ENTITY_DIE`，实体是玩家，`KEEP_INVENTORY=false`，半径 8 | 不调用 `addCharge` |
| `entityDieCallsAddChargeForPlayerWithKeepInventory` | 注入 mock；事件是 `ENTITY_DIE`，实体是玩家，`KEEP_INVENTORY=true`，半径 8 | 以该玩家和半径 8 调用一次 `addCharge` |
| `entityDieWithNullCatalystAccessDoesNotThrow` | `catalystAccess=null`；事件是 `ENTITY_DIE`，实体是玩家，`KEEP_INVENTORY=true`，半径 8 | 不抛异常 |
| `entityDieSwallowsAddChargeFailure` | 注入 mock；`addCharge` 抛出 `ReflectiveOperationException`，其余条件满足且半径为 8 | 不向外抛异常，并以该玩家和半径 8 调用一次 `addCharge` |

这些测试不检查日志内容，也不检查幽匿催发体是否实际获得 charge。

## PaperCatalystAccessTest

`baseExperience` 使用 Mockito 模拟 `Player.getLevel()` 和 `Player.getGameMode()`，不启动 MockBukkit。

| 测试 | 输入 | 断言 |
| --- | --- | --- |
| `baseExperienceZeroLevel` | 生存模式，等级 0 | 返回 0 |
| `baseExperienceTenLevels` | 生存模式，等级 10 | 返回 70 |
| `baseExperienceJustBelowCap` | 生存模式，等级 14 | 返回 98 |
| `baseExperienceCappedAt100` | 生存模式，等级 15 | 返回 100 |
| `baseExperienceFarAboveCap` | 创造模式，等级 100 | 返回 100 |
| `baseExperienceSpectatorReturnsZero` | 旁观者模式，等级 50 | 返回 0 |

`constructorSucceedsOnPaperServer` 用 `@Disabled` 禁用。它原本断言 `new PaperCatalystAccess()` 不抛异常，但当前测试环境缺少 `net.minecraft.*`，构造器必然抛出 `ClassNotFoundException`。启用它需要 paperweight-userdev 或把真实 Paper 服务端 JAR 加入测试 classpath。

当前没有直接测试 `deathExperience` 和 `addCharge` 的 NMS 分支，例如经验是否已被消耗、监听器距离排序、charge 注入和成就授予。

## 手动集成测试

以下检查必须在真实 Paper 服务端上执行。

### 基础掉落（无幽匿催发体）

| 步骤 | 期望 |
| --- | --- |
| 远离幽匿催发体，`keepInventory=true`，等级 10，`/kill @s` | 掉落经验 = min(10×7,100) = **70**；等级清零 |
| 等级 0 死亡 | 掉落 **0**，无经验球 |
| 等级 30 死亡 | 掉落 **100**（被上限截断） |
| 旁观者模式死亡 | 掉落 **0** |
| `keepInventory=false` 死亡 | 行为与原版完全一致（插件不干预） |

### 幽匿催发体蔓延

| 步骤 | 期望 |
| --- | --- |
| 幽匿催发体 8 格内死亡，等级 10 | 幽匿催发体 bloom（粒子+声音）；`/data get block <幽匿催发体> cursors` 出现 charge ≈ 70；周围泥土逐渐变为幽匿方块 |
| 多个幽匿催发体（不同距离）死亡 | **最近**的那个获得 charge 并蔓延 |
| 幽匿催发体超过 8 格时死亡 | 正常掉经验球，无蔓延 |
| 玩家悬空或在幽匿催发体正下方死亡 | 记录实际行为（当前实现不校验原版“正下方实心方块”前置条件，可能仍蔓延，需确认是否为预期） |
| 同时观察经验球与 charge | **重点**：确认掉落经验与 charge 是否同时为 70（当前代码为“并存”设计，需产品确认） |

### 附魔与伤害源修正

| 步骤 | 期望 |
| --- | --- |
| 击杀者持经验修补或抢夺相关附魔时死亡 | charge 经 `processMobExperience` 修正，与原版 mob 死亡规则一致 |
| 无明确击杀者（摔死或淹死） | 不崩溃，charge 正常 |

### 成就

| 步骤 | 期望 |
| --- | --- |
| 幽匿催发体旁死亡并成功注入 charge | 玩家获得 “It Spreads”（蔓延）进度 |

## 降级行为（真实反射失败）

模拟 NMS 反射不可用，例如在不支持的 Paper 版本或非 Paper 的 Spigot 上运行：

1. 将插件放入不支持的服务端，或临时改 `PaperCatalystAccess` 构造器抛异常后重新打包。
2. 启动服务器，检查 `logs/latest.log`：
   - 应出现 `Cannot access Paper death experience handling; base death experience will still drop.`（SEVERE）。
   - 插件不应被禁用，`ExpRule has been enabled.` 仍出现。
3. `keepInventory=true`、等级 10、幽匿催发体旁死亡：
   - 经验球仍掉落 **70**（基础经验回退）。
   - 幽匿催发体不获得 charge、不蔓延。
   - 日志出现 `Cannot calculate Paper death experience ...` 或 `Cannot add Paper sculk charge ...`（SEVERE），服务器不崩溃。

单元测试只验证对应异常不会从事件方法向外传播，以及基础 `droppedExp` 得以保留；日志文本和插件是否保持启用仍以本节为准。