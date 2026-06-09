package me.suxuan.skywars.model;

import me.suxuan.skywars.config.MapConfig;
import me.suxuan.sungame.api.session.BaseGameSession;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public final class SkyWarsGame extends BaseGameSession {
	private final MapConfig mapConfig;
	private boolean protectionActive;
	private int protectionSecondsLeft;
	private int timeLeftSeconds;

	public SkyWarsGame(@NotNull String id, @NotNull World world, @NotNull MapConfig mapConfig) {
		super(id, world, mapConfig.id());
		this.mapConfig = mapConfig;
	}

	public MapConfig mapConfig() {
		return mapConfig;
	}

	public boolean protectionActive() {
		return protectionActive;
	}

	public void protectionActive(boolean protectionActive) {
		this.protectionActive = protectionActive;
	}

	public int protectionSecondsLeft() {
		return protectionSecondsLeft;
	}

	public void protectionSecondsLeft(int protectionSecondsLeft) {
		this.protectionSecondsLeft = protectionSecondsLeft;
	}

	public int timeLeftSeconds() {
		return timeLeftSeconds;
	}

	public void timeLeftSeconds(int timeLeftSeconds) {
		this.timeLeftSeconds = timeLeftSeconds;
	}
}
