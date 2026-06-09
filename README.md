# SkyWars：SunGameCore 使用案例文档

SkyWars 是一个基于 SunGameCore 开发的空岛战争小游戏示例。本文档重点介绍 SkyWars 如何使用 SunGameCore 的各个能力，方便开发者参考它来编写自己的小游戏插件。

> 本文档不是 SkyWars 玩家玩法说明，而是开发者视角的接入说明。

---

## 1. 项目定位

SkyWars 自己负责具体玩法：

- 空岛战争规则
- 地图配置
- 箱子物资
- 保护期
- 击杀/淘汰
- 胜利判断
- 特殊物品，例如一次性秒人斧、手雷 TNT

SunGameCore 负责通用基础设施：

- 临时世界创建和销毁
- 匹配队列
- 对局模型
- 生命周期监听
- 保护监听
- 聊天隔离
- 边界检测
- 旁观者服务
- 游戏清理
- BossBar 管理
- 任务管理
- Loot 物品池
- PlaceholderAPI 基类
- 命令框架
- 常用工具类

---

## 2. 通过 JitPack 引入 SunGameCore

SunGameCore 已发布到 JitPack，SkyWars 可以直接通过 JitPack 引入库插件 API，不需要先把 SunGameCore 安装到本地 Maven 仓库。

JitPack 页面：

```text
https://jitpack.io/#CBer-SuXuan/SunGameCore/v1.0.3
```

### 2.1 Maven 示例

先添加 JitPack 仓库：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

再添加 SunGameCore 依赖：

```xml
<dependency>
    <groupId>com.github.cber-suxuan</groupId>
    <artifactId>SunGameCore</artifactId>
    <version>v1.0.3</version>
    <scope>provided</scope>
</dependency>
```

`scope` 使用 `provided`，因为运行时由服务器插件目录中的 SunGameCore 提供。不要把 SunGameCore 打包进 SkyWars jar。

### 2.2 Gradle 示例

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.cber-suxuan:SunGameCore:v1.0.3'
}
```

### 2.3 运行时仍然需要安装 SunGameCore 插件

JitPack 只解决开发和编译时依赖。服务器运行时仍然需要同时放入：

```text
plugins/SunGameCore.jar
plugins/SkyWars.jar
```

---

## 3. plugin.yml 依赖

SkyWars 在 `plugin.yml` 中声明：

```yaml
depend:
  - SunGameCore
softdepend:
  - PlaceholderAPI
```

这保证 SkyWars 启动时，SunGameCore 已经启用并注册服务。

---

## 4. 启动时获取 SunGameCore 服务

入口类：

```text
src/main/java/me/suxuan/skywars/SkyWars.java
```

SkyWars 在 `onEnable()` 中获取两个服务：

```java
ArenaManager arenaManager = loadService(ArenaManager.class);
MiniGameService miniGameService = loadService(MiniGameService.class);
```

如果服务不存在，插件会禁用自身：

```java
if (arenaManager == null || miniGameService == null) {
    getLogger().severe("未能获取 SlimeArenaAPI 或 SunGameAPI 服务，插件将禁用。");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

这是一种推荐写法：小游戏插件不应该在缺少核心库时继续运行。

---

## 5. GameManager 作为核心适配层

核心类：

```text
src/main/java/me/suxuan/skywars/manager/GameManager.java
```

SkyWars 的 `GameManager` 同时承担三种角色：

```java
public final class GameManager implements ManagedPlayerProvider<SkyWarsGame>, QueueCallbacks
```

也就是：

1. 管理 SkyWars 对局。
2. 告诉 SunGameCore 玩家属于哪个游戏或队列。
3. 接收队列系统的回调。

---

## 6. 使用 QueueManager 实现匹配队列

SkyWars 通过 `MiniGameService` 创建队列管理器：

```java
this.queueManager = miniGameService.createQueueManager(plugin, queueSettings(config), this);
```

队列配置来自 `config.yml`：

```yaml
queue-template-world: queue
min-players: 2
max-players: 8
long-countdown-seconds: 120
quick-countdown-seconds: 10
quick-countdown-percent: 80
queue-spawn:
  x: 10.5
  y: 9
  z: -1.5
  yaw: -90
  pitch: 0.0
```

转换为：

```java
private QueueSettings queueSettings(PluginConfig config) {
    return new QueueSettings(
            "sw",
            config.queueTemplateWorld(),
            config.queueSpawn(),
            config.minPlayers(),
            config.maxPlayers(),
            config.longCountdownSeconds(),
            config.quickCountdownSeconds(),
            config.quickCountdownPercent()
    );
}
```

玩家加入队列：

```java
queueManager.joinQueueResult(player)
```

队列准备完成后，SunGameCore 回调：

```java
@Override
public void onQueueReady(@NotNull QueueArena queue) {
    launchGameFromQueue(queue);
}
```

---

## 7. 从 queue 创建正式游戏

方法：

```java
private void launchGameFromQueue(QueueArena queue)
```

流程：

1. 随机选择地图配置。
2. 使用 `ArenaManager` 创建正式游戏世界。
3. 创建 `SkyWarsGame` 对局对象。
4. 把 queue 玩家登记到游戏。
5. 清理 queue 管理关系。
6. 开始游戏。
7. 延迟销毁旧 queue 世界。

核心代码：

```java
arenaManager.createArenaAsync(mapConfig.templateWorld(), gameId)
        .whenComplete((world, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                // 创建失败时重新入队并延迟销毁 queue 世界
                return;
            }

            SkyWarsGame game = new SkyWarsGame(gameId, world, mapConfig);
            gamesByWorldName.put(world.getName(), game);

            for (UUID uuid : participants) {
                game.players().add(uuid);
                game.alivePlayers().add(uuid);
                playerGames.put(uuid, game);
            }

            queueManager.cleanupQueue(queue, false);
            beginGame(game);
        }));
```

这里体现了 SunGameCore 的典型用法：

- queue 世界只负责等待。
- 正式游戏世界从地图模板重新创建。
- 游戏结束后正式游戏世界会被销毁。

---

## 8. 使用 GameSession / BaseGameSession

SkyWars 的对局类：

```text
src/main/java/me/suxuan/skywars/model/SkyWarsGame.java
```

继承 SunGameCore 的：

```java
public final class SkyWarsGame extends BaseGameSession
```

`BaseGameSession` 已经提供：

- id
- world
- mapId
- players
- alivePlayers
- state

SkyWars 只需要补充自己的字段：

```java
private final MapConfig mapConfig;
private boolean protectionActive;
private int protectionSecondsLeft;
private int timeLeftSeconds;
```

这展示了推荐写法：小游戏自己的 `GameSession` 只保留业务字段。

---

## 9. 使用 ManagedPlayerProvider

SkyWars 的 `GameManager` 实现：

```java
@Override
public Optional<SkyWarsGame> gameOf(Player player) {
    return Optional.ofNullable(playerGames.get(player.getUniqueId()));
}

@Override
public Optional<SkyWarsGame> gameByWorld(World world) {
    return Optional.ofNullable(gamesByWorldName.get(world.getName()));
}

@Override
public Optional<QueueArena> queueOf(Player player) {
    return queueManager.queueOf(player);
}
```

这些方法被 SunGameCore 的通用监听器使用，用于判断：

- 玩家是否在游戏中
- 玩家是否在队列中
- 玩家是否已淘汰
- 某个世界属于哪个游戏

---

## 10. 使用通用生命周期监听器

SkyWars 注册：

```java
Bukkit.getPluginManager().registerEvents(
        new CommonLifecycleListener<>(gameManager, lifecycleCallbacks(), protectionPolicy),
        this
);
```

它负责处理：

- 玩家进服
- 自动加入队列
- 玩家退出
- 死亡淘汰
- 重生位置
- 基础交互限制

SkyWars 自己只需要提供回调：

```java
private LifecycleCallbacks<SkyWarsGame> lifecycleCallbacks() {
    return new LifecycleCallbacks<>() {
        @Override
        public boolean handleJoin(Player player) {
            gameManager.joinQueue(player);
            return true;
        }

        @Override
        public void handleQuit(Player player) {
            gameManager.handleQuit(player);
        }

        @Override
        public void eliminate(Player player, String reason) {
            gameManager.eliminate(player, reason, true);
        }
    };
}
```

---

## 11. 使用通用保护监听器

SkyWars 注册：

```java
Bukkit.getPluginManager().registerEvents(
        new CommonProtectionListener<>(gameManager, protectionPolicy),
        this
);
```

SkyWars 通过 `ProtectionPolicy` 自定义规则：

```java
@Override
public boolean cancelBlockBreak(Player player) {
    return gameManager.queueOf(player).isPresent()
            || gameManager.isEliminated(player)
            || !gameManager.canBreakBlocks(player);
}
```

这样 SunGameCore 负责监听事件，SkyWars 只负责表达策略。

---

## 12. 使用聊天隔离监听器

SkyWars 注册：

```java
Bukkit.getPluginManager().registerEvents(
        new CommonChatListener<>(gameManager, new ChatPolicy<SkyWarsGame>() {}),
        this
);
```

默认效果：

- 同一个游戏内玩家才能看到游戏聊天。
- 同一个 queue 内玩家才能看到 queue 聊天。
- 不同游戏、不同队列互相隔离。

---

## 13. 使用 GameTaskRegistry 管理任务

SkyWars 创建：

```java
this.taskRegistry = miniGameService.createTaskRegistry(plugin);
```

用于：

- 保护期倒计时
- 游戏总时间倒计时
- 延迟销毁 queue 世界
- 延迟销毁失败创建的 queue 世界

示例：

```java
taskRegistry.repeat(game.id(), "timer", 20L, 20L, () -> {
    if (game.state() != GameState.RUNNING) return;
    game.timeLeftSeconds(game.timeLeftSeconds() - 1);
});
```

优点是任务按游戏 ID 归类，结束时可以统一取消。

---

## 14. 使用 BoundaryWatcher 检测虚空和离开世界

SkyWars 创建：

```java
this.boundaryWatcher = miniGameService.createBoundaryWatcher(plugin, taskRegistry);
```

使用：

```java
boundaryWatcher.watch(
        game,
        List.of(
                BoundaryRule.outsideWorldIgnoringPendingTeleport(teleportTracker, "离开竞技场"),
                BoundaryRule.belowY(game.mapConfig().gameSettings().eliminateY(), "掉入虚空")
        ),
        (player, currentGame, reason) -> {
            eliminate(player, reason, true);
            checkWin(currentGame);
        },
        20L,
        5L
);
```

这里同时使用了：

- `BoundaryWatcher`
- `BoundaryRule`
- `TeleportTracker`

`TeleportTracker` 用于避免插件内部传送被误判为“离开竞技场”。

---

## 15. 使用 SpectatorService 处理淘汰玩家

SkyWars 创建：

```java
this.spectatorService = miniGameService.createSpectatorService(plugin);
```

淘汰玩家时：

```java
spectatorService.makeSpectator(
        player,
        game,
        SpectatorOptions.of(
                LocationUtil.withWorld(game.mapConfig().spectatorSpawn(), game.world()),
                createLeaveItem()
        )
);
```

它负责：

- 设置类旁观状态
- 传送到旁观点
- 给离开物品
- 处理玩家可见性

SkyWars 的离开物品使用 SunGameCore 的 `GameItemUtil` 标记：

```java
GameItemUtil.setActionKey(item, ACTION_SPECTATOR_LEAVE);
```

判断时：

```java
GameItemUtil.isActionItem(item, ACTION_SPECTATOR_LEAVE);
```

---

## 16. 使用 GameBossBarService 显示状态

SkyWars 创建：

```java
this.bossBarManager = new GameBossBarManager(
        this,
        miniGameService.createBossBarService(plugin)
);
```

`GameBossBarManager` 是 SkyWars 自己的展示层，底层 BossBar 管理由 SunGameCore 完成。

示例：

```java
bossBars.showQueue(queue, name, progress, BossBar.Color.BLUE);
bossBars.showGame(game, name, progress, BossBar.Color.RED);
bossBars.clear(player);
bossBars.clearAll();
```

SkyWars 用它显示：

- 匹配人数
- 保护期剩余时间
- 游戏剩余时间
- 存活人数
- 结算胜者

---

## 17. 使用 GameCleanupService 清理游戏

SkyWars 创建：

```java
this.cleanupService = miniGameService.createCleanupService(plugin, taskRegistry);
```

游戏结束时：

```java
cleanupService.cleanup(
        game,
        GameCleanupOptions.discardWorld(delay, 100L, fallback),
        (player, currentGame) -> {
            playerGames.remove(player.getUniqueId());
            teleportTracker.clear(player);
            bossBarManager.clear(player);
            spectatorService.clear(player);
            PlayerStateUtil.prepareAdventure(player, Material.ENDER_PEARL);
            joinQueue(player);
        },
        () -> spectatorService.showAll(game),
        () -> {
            spectatorService.clearAll(game);
            gamesByWorldName.remove(game.world().getName());
        }
);
```

它负责：

- 延迟清理玩家
- 延迟卸载游戏世界
- 取消该游戏的任务
- 执行收尾回调

---

## 18. 使用 Loot 系统填充箱子

SkyWars 的全局物品池在：

```text
src/main/resources/config.yml
```

地图配置可以使用距离规则决定箱子等级：

```yaml
loot:
  center:
    x: 0.5
    z: 0.5
  tiers:
    - id: center
      max-distance: 18.0
    - id: advanced
      max-distance: 36.0
    - id: normal
      max-distance: -1
```

SkyWars 加载时使用：

```java
LootRegistry.fromSection(config.getConfigurationSection("loot"));
DistanceLootSelector.fromSection(mapLootSection, spectatorSpawn, registry);
```

填充时：

```java
String tierId = game.mapConfig().lootSettings().tierFor(state.getLocation());
game.mapConfig().lootSettings().lootRegistry().table(tierId).fill(inventory, random);
```

这样箱子物资完全由 SunGameCore 的 Loot API 生成。

---

## 19. 使用 ItemStackConfigUtil 和 GameItemUtil 做特殊物品

SkyWars 的一次性秒人斧和手雷 TNT 都来自配置：

```yaml
one_shot_axe:
  material: DIAMOND_AXE
  name: "<dark_red><bold>一次性秒人斧"
  action-key: skywars:one_shot_axe
```

```yaml
grenade_tnt:
  material: TNT
  name: "<red><bold>手雷 TNT"
  action-key: skywars:grenade_tnt
```

`ItemStackConfigUtil` 读取配置时会自动写入 PDC 标记。

SkyWars 监听器中判断：

```java
GameItemUtil.isActionItem(item, GameManager.ACTION_ONE_SHOT_AXE)
GameItemUtil.isActionItem(item, GameManager.ACTION_GRENADE_TNT)
```

这展示了推荐做法：

> 特殊物品不要靠名称或 Lore 判断，而应该靠 PDC action-key 判断。

---

## 20. 使用 MiniCommandExecutor 编写命令

SkyWars 命令类：

```text
src/main/java/me/suxuan/skywars/command/SkyWarsCommand.java
```

使用 SunGameCore 的命令框架：

```java
MiniCommandExecutor.builder()
        .usage("<yellow>用法: /<label> <join|leave|forcestart|stop|reload|status>")
        .unknownMessage("<red>未知子命令")
        .noPermissionMessage("<red>你没有权限")
        .playerOnlyMessage("<red>只有玩家可以执行该命令")
        .register(joinCommand())
        .register(leaveCommand())
        .register(forceStartCommand())
        .register(stopCommand())
        .register(reloadCommand())
        .register(statusCommand())
        .build();
```

这样不用手写复杂的 `args[0]` 分发逻辑。

---

## 21. 使用 PlaceholderAPI 基类

SkyWars 的 Expansion：

```text
src/main/java/me/suxuan/skywars/hook/SkyWarsExpansion.java
```

继承：

```java
BaseMiniGameExpansion<SkyWarsGame>
```

并通过 `PlaceholderValueProvider` 提供：

- 当前游戏
- 当前队列
- 最小人数
- 最大人数
- 自定义占位符

SkyWars 自定义了：

```text
%skywars_role%
%skywars_role_name%
%skywars_map_name%
%skywars_state_name%
%skywars_time_left_formatted%
%skywars_protection_left_formatted%
```

---

## 22. SkyWars 自己保留的玩法逻辑

虽然 SunGameCore 提供了大量基础设施，但 SkyWars 仍然保留玩法逻辑：

- 选择随机地图
- 扫描地图箱子
- 保护期逻辑
- 游戏时间逻辑
- 胜利判断
- 一次性秒人斧
- 手雷 TNT
- 雪球/鸡蛋造成伤害

这是推荐分工：

| 类型 | 放在哪里 |
| --- | --- |
| 通用基础设施 | SunGameCore |
| 具体小游戏规则 | SkyWars |

---

## 23. 开发自己的小游戏时可以照抄的流程

1. 在 `plugin.yml` 中 depend SunGameCore。
2. 在 `onEnable()` 获取 `ArenaManager` 和 `MiniGameService`。
3. 创建 `QueueManager`。
4. 创建 `GameTaskRegistry`。
5. 创建 `BoundaryWatcher`、`GameCleanupService`、`SpectatorService`、`GameBossBarService`。
6. 编写自己的 `GameSession`，推荐继承 `BaseGameSession`。
7. 实现 `ManagedPlayerProvider`。
8. 注册 `CommonLifecycleListener`、`CommonProtectionListener`、`CommonChatListener`。
9. 在 `onQueueReady` 中创建正式游戏世界和对局。
10. 游戏结束时调用 `GameCleanupService` 清理。

---

## 24. 构建

由于 SkyWars 通过 JitPack 引入 SunGameCore，正常情况下不需要先在本地执行 `mvn install` 安装 SunGameCore。

在 SkyWars 项目根目录执行：

```bash
mvn clean package
```

Maven 会从 JitPack 下载 SunGameCore API 依赖。

构建产物：

```text
target/SkyWars-1.0.jar
```

运行服务器时仍然需要把 SunGameCore 插件 jar 和 SkyWars 插件 jar 都放入 `plugins` 目录。
