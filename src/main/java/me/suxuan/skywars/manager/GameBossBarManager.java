package me.suxuan.skywars.manager;

import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.sungame.api.bossbar.GameBossBarService;
import me.suxuan.sungame.api.queue.QueueArena;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class GameBossBarManager {
	private final GameManager gameManager;
	private final GameBossBarService<SkyWarsGame> bossBars;

	public GameBossBarManager(@NotNull GameManager gameManager, @NotNull GameBossBarService<SkyWarsGame> bossBars) {
		this.gameManager = gameManager;
		this.bossBars = bossBars;
	}

	public void updateQueue(@NotNull QueueArena queue) {
		Component name = Component.text("SkyWars 匹配中 " + queue.players().size() + "/" + gameManager.maxPlayers(), NamedTextColor.AQUA);
		float progress = queue.players().size() / (float) Math.max(1, gameManager.maxPlayers());
		bossBars.showQueue(queue, name, progress, BossBar.Color.BLUE);
	}

	public void updateGame(@NotNull SkyWarsGame game) {
		String prefix = game.protectionActive() ? "保护期 " + game.protectionSecondsLeft() + "秒" : "剩余 " + game.timeLeftSeconds() + "秒";
		Component name = Component.text(prefix + " | 存活 " + game.alivePlayers().size() + "/" + game.players().size(), NamedTextColor.GOLD);
		float progress = game.protectionActive()
				? game.protectionSecondsLeft() / (float) Math.max(1, game.mapConfig().gameSettings().protectionSeconds())
				: game.timeLeftSeconds() / (float) Math.max(1, game.mapConfig().gameSettings().gameTimeSeconds());
		bossBars.showGame(game, name, progress, game.protectionActive() ? BossBar.Color.YELLOW : BossBar.Color.RED);
	}

	public void showEnding(@NotNull SkyWarsGame game, String winnerName) {
		Component name = winnerName == null
				? Component.text("SkyWars 结束：无人获胜", NamedTextColor.GRAY)
				: Component.text("SkyWars 结束：" + winnerName + " 获胜", NamedTextColor.GOLD);
		bossBars.showGame(game, name, 1.0F, BossBar.Color.GREEN);
	}

	public void clear(@NotNull Player player) {
		bossBars.clear(player);
	}

	public void clearAll() {
		bossBars.clearAll();
	}
}
