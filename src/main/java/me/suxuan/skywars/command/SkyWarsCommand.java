package me.suxuan.skywars.command;

import me.suxuan.skywars.config.PluginConfig;
import me.suxuan.skywars.manager.GameManager;
import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.skywars.util.Text;
import me.suxuan.sungame.api.command.MiniCommand;
import me.suxuan.sungame.api.command.MiniCommandExecutor;
import me.suxuan.sungame.api.queue.QueueArena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SkyWarsCommand implements CommandExecutor, TabCompleter {
	private final GameManager gameManager;
	private final ConfigReloader configReloader;
	private final MiniCommandExecutor delegate;

	public SkyWarsCommand(@NotNull GameManager gameManager, @NotNull ConfigReloader configReloader) {
		this.gameManager = gameManager;
		this.configReloader = configReloader;
		this.delegate = MiniCommandExecutor.builder()
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
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		return delegate.onCommand(sender, command, label, args);
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		return delegate.onTabComplete(sender, command, label, args);
	}

	private MiniCommand joinCommand() {
		return MiniCommand.builder("join")
				.playerOnly(true)
				.executor(context -> {
					Player player = context.playerOrNull();
					if (player == null) return;
					gameManager.joinQueue(player);
					context.reply("<green>已尝试加入 SkyWars 匹配");
				})
				.build();
	}

	private MiniCommand leaveCommand() {
		return MiniCommand.builder("leave")
				.permission("skywars.admin")
				.playerOnly(true)
				.executor(context -> {
					Player player = context.playerOrNull();
					if (player == null) return;
					if (gameManager.leaveQueue(player)) context.reply("<green>已离开当前匹配队列");
					else if (gameManager.leaveCurrentGameToQueue(player))
						context.reply("<green>已离开当前游戏并返回匹配");
					else context.reply("<red>你当前不在匹配队列或游戏中");
				})
				.build();
	}

	private MiniCommand forceStartCommand() {
		return MiniCommand.builder("forcestart")
				.permission("skywars.admin")
				.executor(context -> {
					QueueArena queue = null;
					if (context.playerOrNull() != null)
						queue = gameManager.queueOf(context.playerOrNull()).orElse(null);
					if (queue == null && !gameManager.queues().isEmpty()) queue = gameManager.queues().getFirst();
					if (queue == null) context.reply("<red>没有可开始的 queue");
					else {
						gameManager.startQueueCountdown(queue, true);
						context.reply("<green>已强制开始 queue 倒计时");
					}
				})
				.build();
	}

	private MiniCommand stopCommand() {
		return MiniCommand.builder("stop")
				.permission("skywars.admin")
				.executor(context -> {
					gameManager.stopAll();
					context.reply("<green>已停止所有 queue 和游戏");
				})
				.build();
	}

	private MiniCommand reloadCommand() {
		return MiniCommand.builder("reload")
				.permission("skywars.admin")
				.executor(context -> {
					PluginConfig config = configReloader.reload();
					gameManager.updateConfig(config);
					context.reply("<green>配置已重载，地图数量: " + config.maps().size());
				})
				.build();
	}

	private MiniCommand statusCommand() {
		return MiniCommand.builder("status")
				.permission("skywars.admin")
				.executor(context -> sendStatus(context.sender()))
				.build();
	}

	private void sendStatus(CommandSender sender) {
		sender.sendMessage(Text.mm("<aqua>Queue 数量: " + gameManager.queues().size()));
		for (QueueArena queue : gameManager.queues()) {
			sender.sendMessage(Text.mm("<gray>- " + queue.id() + " " + queue.state() + " " + queue.players().size() + "人"));
		}
		sender.sendMessage(Text.mm("<aqua>游戏数量: " + gameManager.games().size()));
		for (SkyWarsGame game : gameManager.games()) {
			sender.sendMessage(Text.mm("<gray>- " + game.id() + " 地图=" + game.mapConfig().id() + " " + game.state() + " " + game.players().size() + "人 存活=" + game.alivePlayers().size() + " 保护=" + game.protectionActive()));
		}
	}

	@FunctionalInterface
	public interface ConfigReloader {
		PluginConfig reload();
	}
}
