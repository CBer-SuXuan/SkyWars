package me.suxuan.skywars.manager;

import me.suxuan.skywars.config.MapConfig;
import me.suxuan.skywars.config.PluginConfig;
import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.skywars.util.Text;
import me.suxuan.slimearena.api.ArenaManager;
import me.suxuan.sungame.api.MiniGameService;
import me.suxuan.sungame.api.boundary.BoundaryRule;
import me.suxuan.sungame.api.boundary.BoundaryWatcher;
import me.suxuan.sungame.api.cleanup.GameCleanupOptions;
import me.suxuan.sungame.api.cleanup.GameCleanupService;
import me.suxuan.sungame.api.queue.*;
import me.suxuan.sungame.api.session.GameState;
import me.suxuan.sungame.api.session.ManagedPlayerProvider;
import me.suxuan.sungame.api.spectator.SpectatorOptions;
import me.suxuan.sungame.api.spectator.SpectatorService;
import me.suxuan.sungame.api.task.GameTaskRegistry;
import me.suxuan.sungame.util.AudienceUtil;
import me.suxuan.sungame.util.GameItemUtil;
import me.suxuan.sungame.util.LocationUtil;
import me.suxuan.sungame.util.PlayerStateUtil;
import me.suxuan.sungame.util.TeleportTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class GameManager implements ManagedPlayerProvider<SkyWarsGame>, QueueCallbacks {
	public static final String ACTION_ONE_SHOT_AXE = "skywars:one_shot_axe";
	public static final String ACTION_GRENADE_TNT = "skywars:grenade_tnt";
	public static final String ACTION_SPECTATOR_LEAVE = "skywars:spectator_leave";

	private final JavaPlugin plugin;
	private final ArenaManager arenaManager;
	private final QueueManager queueManager;
	private final TeleportTracker teleportTracker;
	private final GameTaskRegistry taskRegistry;
	private final BoundaryWatcher<SkyWarsGame> boundaryWatcher;
	private final GameCleanupService<SkyWarsGame> cleanupService;
	private final SpectatorService<SkyWarsGame> spectatorService;
	private final GameBossBarManager bossBarManager;
	private final Map<String, SkyWarsGame> gamesByWorldName = new HashMap<>();
	private final Map<UUID, SkyWarsGame> playerGames = new HashMap<>();
	private final Random random = new Random();
	private PluginConfig config;

	public GameManager(@NotNull JavaPlugin plugin, @NotNull ArenaManager arenaManager, @NotNull MiniGameService miniGameService, @NotNull PluginConfig config) {
		this.plugin = plugin;
		this.arenaManager = arenaManager;
		this.config = config;
		this.teleportTracker = miniGameService.createTeleportTracker(plugin);
		this.taskRegistry = miniGameService.createTaskRegistry(plugin);
		this.boundaryWatcher = miniGameService.createBoundaryWatcher(plugin, taskRegistry);
		this.cleanupService = miniGameService.createCleanupService(plugin, taskRegistry);
		this.spectatorService = miniGameService.createSpectatorService(plugin);
		this.queueManager = miniGameService.createQueueManager(plugin, queueSettings(config), this);
		this.bossBarManager = new GameBossBarManager(this, miniGameService.createBossBarService(plugin));
	}

	private QueueSettings queueSettings(PluginConfig config) {
		return new QueueSettings("sw", config.queueTemplateWorld(), config.queueSpawn(), config.minPlayers(), config.maxPlayers(),
				config.longCountdownSeconds(), config.quickCountdownSeconds(), config.quickCountdownPercent());
	}

	public void updateConfig(@NotNull PluginConfig config) {
		this.config = config;
		queueManager.updateSettings(queueSettings(config));
	}

	public void joinQueue(@NotNull Player player) {
		if (playerGames.containsKey(player.getUniqueId())) return;
		queueManager.joinQueueResult(player).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> handleJoinQueueResult(player, result)));
	}

	private void handleJoinQueueResult(@NotNull Player player, @NotNull QueueJoinResult result) {
		if (!player.isOnline() || result.success()) return;
		if (result.status() == QueueJoinStatus.ALREADY_IN_QUEUE) return;
		player.sendMessage(switch (result.status()) {
			case PLAYER_OFFLINE -> Component.text("你已离线，无法加入匹配。", NamedTextColor.RED);
			case QUEUE_FULL -> Component.text("当前匹配队列已满。", NamedTextColor.RED);
			case QUEUE_CLOSED -> Component.text("当前匹配队列已关闭。", NamedTextColor.RED);
			case QUEUE_NOT_MANAGED -> Component.text("目标队列不可用。", NamedTextColor.RED);
			case CREATE_FAILED -> Component.text("创建匹配队列失败，请稍后再试。", NamedTextColor.RED);
			case ALREADY_IN_QUEUE -> Component.text("你已经在匹配队列中。", NamedTextColor.YELLOW);
			case SUCCESS -> Component.empty();
		});
	}

	public boolean leaveQueue(@NotNull Player player) {
		boolean left = queueManager.leaveQueue(player);
		if (left) {
			bossBarManager.clear(player);
			teleportToLobby(player);
		}
		return left;
	}

	public boolean leaveCurrentGameToQueue(@NotNull Player player) {
		SkyWarsGame game = playerGames.get(player.getUniqueId());
		if (game == null) return false;
		eliminate(player, "离开游戏", false);
		joinQueue(player);
		return true;
	}

	public void startQueueCountdown(QueueArena queue, boolean force) {
		queueManager.startCountdown(queue, force);
	}

	public void stopAll() {
		queueManager.stopAll();
		for (SkyWarsGame game : new ArrayList<>(gamesByWorldName.values())) endGame(game, true);
		bossBarManager.clearAll();
	}

	@Override
	public void onPlayerJoinedQueue(@NotNull Player player, @NotNull QueueArena queue) {
		bossBarManager.updateQueue(queue);
	}

	@Override
	public void onPlayerLeftQueue(@NotNull Player player, @NotNull QueueArena queue) {
		bossBarManager.clear(player);
		bossBarManager.updateQueue(queue);
	}

	@Override
	public void onCountdownCancelled(@NotNull QueueArena queue) {
		bossBarManager.updateQueue(queue);
	}

	@Override
	public void onQueueCreateFailed(@NotNull Throwable throwable) {
		plugin.getLogger().severe("创建 SkyWars queue 失败: " + throwable.getMessage());
	}

	@Override
	public void onCountdownTick(@NotNull QueueArena queue, int secondsLeft) {
		bossBarManager.updateQueue(queue);
		if (secondsLeft == 3 || secondsLeft == 2 || secondsLeft == 1 || secondsLeft % 5 == 0) {
			AudienceUtil.showQueueTitle(
					queue,
					Component.text(secondsLeft, NamedTextColor.YELLOW),
					Component.text("SkyWars 即将开始", NamedTextColor.AQUA),
					0,
					25,
					5
			);
		}
	}

	@Override
	public void onQueueReady(@NotNull QueueArena queue) {
		launchGameFromQueue(queue);
	}

	private void launchGameFromQueue(QueueArena queue) {
		MapConfig mapConfig = selectRandomMap();
		String gameId = "sw_game_" + mapConfig.id() + "_" + System.currentTimeMillis();
		arenaManager.createArenaAsync(mapConfig.templateWorld(), gameId).whenComplete((world, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
			if (throwable != null) {
				plugin.getLogger().severe("创建 SkyWars 游戏地图失败: " + throwable.getMessage());
				List<UUID> fallbackPlayers = new ArrayList<>(queue.players());
				Location fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
				queueManager.cleanupQueue(queue, false);
				for (UUID uuid : fallbackPlayers) {
					Player player = Bukkit.getPlayer(uuid);
					if (player == null) continue;
					player.sendMessage(Component.text("游戏地图创建失败，正在重新加入匹配。", NamedTextColor.RED));
					joinQueue(player);
				}
				taskRegistry.runLater(queue.id(), "discard-failed-queue-world", 60L, () -> arenaManager.discardArenaAsync(queue.world(), fallback));
				return;
			}
			configureSkyWarsWorld(world);
			SkyWarsGame game = new SkyWarsGame(gameId, world, mapConfig);
			gamesByWorldName.put(world.getName(), game);
			List<UUID> participants = new ArrayList<>(queue.players());
			if (participants.size() > mapConfig.spawns().size())
				participants = participants.subList(0, mapConfig.spawns().size());
			for (UUID uuid : participants) {
				game.players().add(uuid);
				game.alivePlayers().add(uuid);
				playerGames.put(uuid, game);
			}
			queueManager.cleanupQueue(queue, false);
			beginGame(game);
			taskRegistry.runLater(game.id(), "discard-queue-world", 60L, () -> arenaManager.discardArenaAsync(queue.world(), game.world().getSpawnLocation()));
		}));
	}

	@SuppressWarnings("removal")
	private void configureSkyWarsWorld(World world) {
		world.setGameRule(GameRule.FALL_DAMAGE, true);
		world.setGameRule(GameRule.FIRE_DAMAGE, true);
		world.setGameRule(GameRule.DROWNING_DAMAGE, true);
		world.setGameRule(GameRule.FREEZE_DAMAGE, true);
		world.setGameRule(GameRule.KEEP_INVENTORY, true);
	}

	private MapConfig selectRandomMap() {
		int total = config.maps().stream().mapToInt(MapConfig::weight).sum();
		int value = random.nextInt(Math.max(1, total));
		for (MapConfig map : config.maps()) {
			value -= map.weight();
			if (value < 0) return map;
		}
		return config.maps().getFirst();
	}

	private void beginGame(SkyWarsGame game) {
		game.state(GameState.RUNNING);
		game.timeLeftSeconds(game.mapConfig().gameSettings().gameTimeSeconds());
		game.protectionSecondsLeft(game.mapConfig().gameSettings().protectionSeconds());
		game.protectionActive(game.mapConfig().gameSettings().protectionSeconds() > 0);
		List<Location> spawns = new ArrayList<>(game.mapConfig().spawns());
		if (game.mapConfig().gameSettings().refillChestsOnStart()) fillChests(game, spawns);
		Collections.shuffle(spawns, random);
		int index = 0;
		for (UUID uuid : game.players()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null) continue;
			preparePlayer(player, game, spawns.get(index++ % spawns.size()));
		}
		broadcastGame(game, Text.mm("<gold>SkyWars 开始！<yellow>击败所有敌人成为最后存活者。"));
		bossBarManager.updateGame(game);
		startProtectionTimer(game);
		startGameTimer(game);
		startPositionChecks(game);
	}

	private void preparePlayer(Player player, SkyWarsGame game, Location relativeSpawn) {
		PlayerStateUtil.prepareSurvival(player);
		player.setGameMode(GameMode.SURVIVAL);
		teleportTracker.teleport(player, LocationUtil.withWorld(relativeSpawn, game.world()));
	}

	private void fillChests(SkyWarsGame game, List<Location> relativeSpawns) {
		World world = game.world();
		Set<String> scannedChunks = new HashSet<>();
		Map<String, Integer> filledByTier = new LinkedHashMap<>();
		int filled = 0;
		for (Location relativeSpawn : relativeSpawns) {
			filled += scanChestsAround(game, world, relativeSpawn, scannedChunks, filledByTier);
		}
		filled += scanChestsAround(game, world, game.mapConfig().spectatorSpawn(), scannedChunks, filledByTier);
		plugin.getLogger().info("SkyWars 地图 " + game.mapConfig().id() + " 已填充箱子数量: " + filled + " " + filledByTier);
	}

	private int currentChestScanRadius(World world) {
		return gameByWorld(world).map(game -> game.mapConfig().gameSettings().chestScanRadiusChunks()).orElse(config.gameSettings().chestScanRadiusChunks());
	}

	private int scanChestsAround(SkyWarsGame game, World world, Location relativeCenter, Set<String> scannedChunks, Map<String, Integer> filledByTier) {
		int radius = currentChestScanRadius(world);
		int centerChunkX = relativeCenter.getBlockX() >> 4;
		int centerChunkZ = relativeCenter.getBlockZ() >> 4;
		int filled = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int chunkX = centerChunkX + dx;
				int chunkZ = centerChunkZ + dz;
				String key = chunkX + "," + chunkZ;
				if (!scannedChunks.add(key)) continue;
				Chunk chunk = world.getChunkAt(chunkX, chunkZ);
				chunk.load(true);
				for (BlockState state : chunk.getTileEntities()) {
					if (state instanceof Container container) {
						String tierId = game.mapConfig().lootSettings().tierFor(state.getLocation());
						Inventory inventory = container.getInventory();
						game.mapConfig().lootSettings().lootRegistry().table(tierId).fill(inventory, random);
						filledByTier.merge(tierId, 1, Integer::sum);
						filled++;
					}
				}
			}
		}
		return filled;
	}

	private void startProtectionTimer(SkyWarsGame game) {
		if (!game.protectionActive()) return;
		taskRegistry.repeat(game.id(), "protection", 0L, 20L, () -> {
			if (game.state() != GameState.RUNNING) return;
			bossBarManager.updateGame(game);
			if (game.protectionSecondsLeft() <= 0) {
				game.protectionActive(false);
				broadcastGame(game, Component.text("保护期结束，PVP 已开启！", NamedTextColor.RED));
				taskRegistry.cancel(game.id(), "protection");
				bossBarManager.updateGame(game);
				return;
			}
			if (game.protectionSecondsLeft() <= 5 || game.protectionSecondsLeft() % 5 == 0) {
				AudienceUtil.showGameTitle(
						game,
						Component.text(game.protectionSecondsLeft(), NamedTextColor.YELLOW),
						Component.text("保护期", NamedTextColor.GREEN),
						0,
						25,
						5
				);
			}
			game.protectionSecondsLeft(game.protectionSecondsLeft() - 1);
		});
	}

	private void startGameTimer(SkyWarsGame game) {
		if (game.timeLeftSeconds() <= 0) return;
		taskRegistry.repeat(game.id(), "timer", 20L, 20L, () -> {
			if (game.state() != GameState.RUNNING) return;
			if (game.timeLeftSeconds() <= 0) {
				finishGame(game, null, "时间耗尽");
				return;
			}
			game.timeLeftSeconds(game.timeLeftSeconds() - 1);
			bossBarManager.updateGame(game);
		});
	}

	private void startPositionChecks(SkyWarsGame game) {
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
	}

	public void eliminate(Player player, String reason, boolean checkWin) {
		SkyWarsGame game = playerGames.get(player.getUniqueId());
		if (game == null || game.state() != GameState.RUNNING) return;
		if (!game.alivePlayers().remove(player.getUniqueId())) return;
		player.setGameMode(GameMode.ADVENTURE);
		spectatorService.makeSpectator(player, game, SpectatorOptions.of(LocationUtil.withWorld(game.mapConfig().spectatorSpawn(), game.world()), createLeaveItem()));
		broadcastGame(game, Component.text(player.getName() + " 被淘汰了：" + reason, NamedTextColor.RED));
		bossBarManager.updateGame(game);
		if (checkWin) checkWin(game);
	}

	private ItemStack createLeaveItem() {
		ItemStack item = new ItemStack(Material.RED_BED);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.displayName(Component.text("右键离开游戏", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
			item.setItemMeta(meta);
		}
		GameItemUtil.setActionKey(item, ACTION_SPECTATOR_LEAVE);
		return item;
	}

	public boolean isLeaveItem(ItemStack item) {
		return GameItemUtil.isActionItem(item, ACTION_SPECTATOR_LEAVE);
	}

	public void handleSpectatorLeave(Player player) {
		if (!isEliminated(player)) return;
		SkyWarsGame game = playerGames.remove(player.getUniqueId());
		if (game == null) return;
		spectatorService.showToGame(player, game);
		spectatorService.clear(player);
		game.players().remove(player.getUniqueId());
		teleportTracker.clear(player);
		bossBarManager.clear(player);
		PlayerStateUtil.prepareAdventure(player, Material.ENDER_PEARL);
		joinQueue(player);
		bossBarManager.updateGame(game);
	}

	public void handleQuit(Player player) {
		queueManager.removePlayer(player, true);
		SkyWarsGame game = playerGames.remove(player.getUniqueId());
		if (game == null) return;
		boolean wasAlive = game.alivePlayers().remove(player.getUniqueId());
		spectatorService.showToGame(player, game);
		spectatorService.clear(player);
		game.players().remove(player.getUniqueId());
		teleportTracker.clear(player);
		bossBarManager.clear(player);
		if (wasAlive) broadcastGame(game, Component.text(player.getName() + " 退出并被淘汰", NamedTextColor.RED));
		bossBarManager.updateGame(game);
		checkWin(game);
	}

	public void checkChangedWorld(Player player) {
		SkyWarsGame game = playerGames.get(player.getUniqueId());
		if (game == null || game.state() != GameState.RUNNING) return;
		if (!player.getWorld().equals(game.world()) && !teleportTracker.isPending(player, game.world()))
			eliminate(player, "离开竞技场", true);
	}

	private void checkWin(SkyWarsGame game) {
		if (game == null || game.state() != GameState.RUNNING) return;
		if (game.alivePlayers().size() <= 1) {
			UUID winner = game.alivePlayers().stream().findFirst().orElse(null);
			finishGame(game, winner, winner == null ? "无人存活" : "只剩最后一名玩家");
		}
	}

	private void finishGame(SkyWarsGame game, UUID winner, String reason) {
		if (game == null || game.state() != GameState.RUNNING) return;
		String winnerName = null;
		if (winner != null) {
			Player winnerPlayer = Bukkit.getPlayer(winner);
			winnerName = winnerPlayer == null ? winner.toString() : winnerPlayer.getName();
			broadcastGame(game, Text.mm("<gold>" + winnerName + " 获胜！<gray>原因：" + reason));
		} else {
			broadcastGame(game, Text.mm("<gray>游戏结束，无人获胜。原因：" + reason));
		}
		bossBarManager.showEnding(game, winnerName);
		endGame(game, false);
	}

	private void endGame(SkyWarsGame game, boolean immediate) {
		if (game == null || game.state() == GameState.ENDING) return;
		taskRegistry.cancel(game.id(), "protection");
		taskRegistry.cancel(game.id(), "timer");
		boundaryWatcher.stop(game);
		game.state(GameState.ENDING);
		long delay = immediate ? 1L : game.mapConfig().gameSettings().endingSeconds() * 20L;
		Location fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
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
					for (UUID uuid : new ArrayList<>(game.players())) playerGames.remove(uuid);
					spectatorService.clearAll(game);
					gamesByWorldName.remove(game.world().getName());
				}
		);
	}

	private void teleportToLobby(Player player) {
		World world = Bukkit.getWorld("world");
		if (world == null) world = Bukkit.getWorlds().getFirst();
		PlayerStateUtil.prepareAdventure(player, Material.ENDER_PEARL);
		player.setGameMode(player.isOp() ? GameMode.CREATIVE : GameMode.ADVENTURE);
		player.teleportAsync(world.getSpawnLocation());
	}

	private void broadcastGame(SkyWarsGame game, Component component) {
		AudienceUtil.broadcastGame(game, component);
		Bukkit.getConsoleSender().sendMessage(Component.text("[SkyWars/Game] ").append(component));
	}

	@Override
	public @NotNull JavaPlugin plugin() {
		return plugin;
	}

	@Override
	public @NotNull Optional<SkyWarsGame> gameOf(@NotNull Player player) {
		return Optional.ofNullable(playerGames.get(player.getUniqueId()));
	}

	@Override
	public @NotNull Optional<SkyWarsGame> gameByWorld(@NotNull World world) {
		return Optional.ofNullable(gamesByWorldName.get(world.getName()));
	}

	@Override
	public @NotNull Optional<QueueArena> queueOf(@NotNull Player player) {
		return queueManager.queueOf(player);
	}

	public List<QueueArena> queues() {
		return queueManager.queues();
	}

	public Collection<SkyWarsGame> games() {
		return List.copyOf(gamesByWorldName.values());
	}

	public int minPlayers() {
		return queueManager.minPlayers();
	}

	public int maxPlayers() {
		return queueManager.maxPlayers();
	}

	public int quickStartPlayers() {
		return queueManager.quickStartPlayers();
	}

	public PluginConfig config() {
		return config;
	}

	public boolean isProtectionActive(Player player) {
		return gameOf(player).map(SkyWarsGame::protectionActive).orElse(false);
	}

	public boolean canBreakBlocks(Player player) {
		return gameOf(player)
				.filter(game -> game.alivePlayers().contains(player.getUniqueId()))
				.map(game -> game.mapConfig().gameSettings().allowBlockBreak())
				.orElse(false);
	}

	public boolean isAliveInGame(Player player) {
		return gameOf(player)
				.map(game -> game.state() == GameState.RUNNING && game.alivePlayers().contains(player.getUniqueId()))
				.orElse(false);
	}
}
