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
        bossBars.showQueue(queue, queueName(queue), queueProgress(queue), queueColor(queue));
    }

    private Component queueName(@NotNull QueueArena queue) {
        int players = queue.players().size();
        String count = players + "/" + gameManager.maxPlayers();
        if (queue.countdownLeft() >= 0) {
            String mode = queue.quickCountdown() ? "快速开始" : "等待更多玩家";
            return Component.text("空岛战争 | " + mode + " | 倒计时 " + queue.countdownLeft() + " 秒 | " + count,
                    queue.quickCountdown() ? NamedTextColor.GREEN : NamedTextColor.GOLD);
        }
        if (players < gameManager.minPlayers()) {
            return Component.text("空岛战争 | 未达到最少人数 | " + count + " | 至少需要 " + gameManager.minPlayers() + " 人", NamedTextColor.RED);
        }
        if (players < gameManager.quickStartPlayers()) {
            return Component.text("空岛战争 | 等待更多玩家 | " + count + " | 快速开始需要 " + gameManager.quickStartPlayers() + " 人", NamedTextColor.AQUA);
        }
        return Component.text("空岛战争 | 准备开始 | " + count, NamedTextColor.GREEN);
    }

    private float queueProgress(@NotNull QueueArena queue) {
        if (queue.countdownLeft() >= 0) {
            int total = queue.quickCountdown() ? gameManager.config().quickCountdownSeconds() : gameManager.config().longCountdownSeconds();
            return clamp(queue.countdownLeft() / (float) Math.max(1, total));
        }
        if (queue.players().size() < gameManager.minPlayers()) {
            return clamp(queue.players().size() / (float) Math.max(1, gameManager.minPlayers()));
        }
        return clamp(queue.players().size() / (float) Math.max(1, gameManager.quickStartPlayers()));
    }

    private BossBar.Color queueColor(@NotNull QueueArena queue) {
        if (queue.countdownLeft() >= 0) return queue.quickCountdown() ? BossBar.Color.GREEN : BossBar.Color.YELLOW;
        if (queue.players().size() < gameManager.minPlayers()) return BossBar.Color.RED;
        if (queue.players().size() < gameManager.quickStartPlayers()) return BossBar.Color.BLUE;
        return BossBar.Color.GREEN;
    }

    public void updateGame(@NotNull SkyWarsGame game) {
        String prefix = game.protectionActive() ? "保护期 " + game.protectionSecondsLeft() + "秒" : "剩余 " + game.timeLeftSeconds() + "秒";
        Component name = Component.text(prefix + " | 存活 " + game.alivePlayers().size() + "/" + game.initialPlayerCount(), NamedTextColor.GOLD);
        float progress = game.protectionActive()
                ? clamp(game.protectionSecondsLeft() / (float) Math.max(1, game.mapConfig().gameSettings().protectionSeconds()))
                : clamp(game.timeLeftSeconds() / (float) Math.max(1, game.mapConfig().gameSettings().gameTimeSeconds()));
        bossBars.showGame(game, name, progress, game.protectionActive() ? BossBar.Color.YELLOW : BossBar.Color.RED);
    }

    private float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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
