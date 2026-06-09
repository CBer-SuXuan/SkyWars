package me.suxuan.skywars.hook;

import me.suxuan.skywars.manager.GameManager;
import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.sungame.api.placeholder.BaseMiniGameExpansion;
import me.suxuan.sungame.api.placeholder.PlaceholderValueProvider;
import me.suxuan.sungame.api.queue.QueueArena;
import me.suxuan.sungame.api.session.GameState;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SkyWarsExpansion extends BaseMiniGameExpansion<SkyWarsGame> {
	public SkyWarsExpansion(@NotNull GameManager gameManager) {
		super(new Provider(gameManager));
	}

	@Override public @NotNull String getIdentifier() { return "skywars"; }
	@Override public @NotNull String getAuthor() { return "SuXuan_Dev"; }
	@Override public @NotNull String getVersion() { return "1.0"; }

	private static final class Provider implements PlaceholderValueProvider<SkyWarsGame> {
		private final GameManager gameManager;

		private Provider(@NotNull GameManager gameManager) {
			this.gameManager = gameManager;
		}

		@Override public SkyWarsGame gameOf(@NotNull Player player) { return gameManager.gameOf(player).orElse(null); }
		@Override public QueueArena queueOf(@NotNull Player player) { return gameManager.queueOf(player).orElse(null); }
		@Override public int minPlayers() { return gameManager.minPlayers(); }
		@Override public int maxPlayers() { return gameManager.maxPlayers(); }

		@Override
		public String resolveCustom(@NotNull Player player, SkyWarsGame game, QueueArena queue, @NotNull String key) {
			boolean inGame = game != null;
			boolean inQueue = queue != null;
			boolean alive = inGame && game.alivePlayers().contains(player.getUniqueId());
			boolean eliminated = inGame && !alive;
			return switch (key) {
				case "role" -> role(inGame, inQueue, alive, eliminated);
				case "role_name", "alive_status" -> roleName(inGame, inQueue, alive, eliminated);
				case "tab_prefix" -> tabPrefix(inGame, inQueue, alive, eliminated, false);
				case "tab_prefix_legacy" -> tabPrefix(inGame, inQueue, alive, eliminated, true);
				case "tab_name" -> tabName(player, inGame, inQueue, alive, eliminated, false);
				case "tab_name_legacy" -> tabName(player, inGame, inQueue, alive, eliminated, true);
				case "tab_suffix" -> tabSuffix(player, inGame, false);
				case "tab_suffix_legacy" -> tabSuffix(player, inGame, true);
				case "alive_players" -> game != null ? String.valueOf(game.alivePlayers().size()) : "0";
				case "eliminated_players", "spectators" -> game != null ? String.valueOf(Math.max(0, game.players().size() - game.alivePlayers().size())) : "0";
				case "total_players" -> game != null ? String.valueOf(game.players().size()) : queue != null ? String.valueOf(queue.players().size()) : "0";
				case "map_id" -> game != null ? game.mapConfig().id() : queue != null ? "queue" : "";
				case "map_name" -> game != null ? game.mapConfig().displayName() : queue != null ? "等待大厅" : "";
				case "state" -> game != null ? game.state().name() : queue != null ? queue.state().name() : "NONE";
				case "state_name" -> stateName(game, queue);
				case "time_left" -> game != null ? String.valueOf(Math.max(0, game.timeLeftSeconds())) : "0";
				case "time_left_formatted", "time" -> game != null ? formatSeconds(game.timeLeftSeconds()) : "00:00";
				case "protection" -> game != null ? String.valueOf(game.protectionActive()) : "false";
				case "protection_left" -> game != null ? String.valueOf(Math.max(0, game.protectionSecondsLeft())) : "0";
				case "protection_left_formatted" -> game != null ? formatSeconds(game.protectionSecondsLeft()) : "00:00";
				case "health" -> String.valueOf((int) Math.ceil(player.getHealth()));
				case "health_heart" -> String.format("%.1f", player.getHealth() / 2.0D);
				default -> "";
			};
		}

		private String role(boolean inGame, boolean inQueue, boolean alive, boolean eliminated) {
			if (alive) return "alive";
			if (eliminated) return "spectator";
			if (inQueue) return "queue";
			return inGame ? "game" : "none";
		}

		private String roleName(boolean inGame, boolean inQueue, boolean alive, boolean eliminated) {
			if (alive) return "存活";
			if (eliminated) return "已淘汰";
			if (inQueue) return "等待中";
			return inGame ? "游戏中" : "无";
		}

		private String tabPrefix(boolean inGame, boolean inQueue, boolean alive, boolean eliminated, boolean legacy) {
			if (legacy) {
				if (alive) return "§a[存活] ";
				if (eliminated) return "§7[已淘汰] ";
				if (inQueue) return "§e[等待中] ";
				return "";
			}
			if (alive) return "<green>[存活] ";
			if (eliminated) return "<gray>[已淘汰] ";
			if (inQueue) return "<yellow>[等待中] ";
			return "";
		}

		private String tabName(Player player, boolean inGame, boolean inQueue, boolean alive, boolean eliminated, boolean legacy) {
			String name = player.getName();
			if (legacy) {
				if (alive) return "§a" + name;
				if (eliminated) return "§7" + name;
				if (inQueue) return "§e" + name;
				return "§f" + name;
			}
			if (alive) return "<green>" + name;
			if (eliminated) return "<gray>" + name;
			if (inQueue) return "<yellow>" + name;
			return "<white>" + name;
		}

		private String tabSuffix(Player player, boolean inGame, boolean legacy) {
			if (!inGame) return "";
			String health = String.format("%.1f", player.getHealth() / 2.0D);
			return legacy ? " §7" + health + "❤" : " <gray>" + health + "❤";
		}

		private String stateName(SkyWarsGame game, QueueArena queue) {
			if (game != null) {
				GameState state = game.state();
				return switch (state) {
					case WAITING -> "等待中";
					case STARTING -> "开始中";
					case RUNNING -> "游戏中";
					case ENDING -> "结算中";
				};
			}
			if (queue != null) return "匹配中";
			return "无";
		}

		private String formatSeconds(int rawSeconds) {
			int seconds = Math.max(0, rawSeconds);
			return String.format("%02d:%02d", seconds / 60, seconds % 60);
		}
	}
}
