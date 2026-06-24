package me.suxuan.skywars.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.suxuan.skywars.manager.GameManager;
import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.sungame.api.queue.QueueArena;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class SkyWarsChatListener implements Listener {
	private final GameManager gameManager;

	public SkyWarsChatListener(@NotNull GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChat(AsyncChatEvent event) {
		Player player = event.getPlayer();
		Optional<SkyWarsGame> game = gameManager.gameOf(player);
		if (game.isPresent()) {
			SkyWarsGame skyWarsGame = game.get();
			boolean alive = skyWarsGame.alivePlayers().contains(player.getUniqueId());
			event.renderer(renderer(prefixForGame(alive), alive ? NamedTextColor.WHITE : NamedTextColor.GRAY, alive ? NamedTextColor.WHITE : NamedTextColor.GRAY));
			return;
		}
		Optional<QueueArena> queue = gameManager.queueOf(player);
		queue.ifPresent(queueArena -> event.renderer(renderer(Component.text("[等待中] ", NamedTextColor.YELLOW))));
	}

	private Component prefixForGame(boolean alive) {
		if (alive) return Component.text("[存活] ", NamedTextColor.GREEN);
		return Component.text("[已淘汰] ", NamedTextColor.GRAY);
	}

	private ChatRenderer renderer(Component prefix) {
		return renderer(prefix, NamedTextColor.WHITE, NamedTextColor.WHITE);
	}

	private ChatRenderer renderer(Component prefix, NamedTextColor nameColor, NamedTextColor messageColor) {
		return (source, sourceDisplayName, message, viewer) -> prefix
				.append(sourceDisplayName.color(nameColor))
				.append(Component.text(": ", NamedTextColor.DARK_GRAY))
				.append(message.color(messageColor));
	}

}
