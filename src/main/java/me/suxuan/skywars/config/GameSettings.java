package me.suxuan.skywars.config;

import org.bukkit.configuration.ConfigurationSection;

public record GameSettings(
		int protectionSeconds,
		int gameTimeSeconds,
		int endingSeconds,
		double eliminateY,
		boolean allowBlockBreak,
		boolean refillChestsOnStart,
		int chestScanRadiusChunks,
		boolean grenadeTntEnabled,
		double grenadeTntPower,
		int grenadeTntFuseTicks,
		double grenadeTntVelocity,
		int grenadeTntCooldownTicks,
		boolean grenadeTntBreakBlocks
) {
	public static GameSettings defaults() {
		return new GameSettings(10, 900, 8, -20.0D, true, true, 3, true, 3.5D, 40, 1.25D, 60, false);
	}

	public static GameSettings fromSection(ConfigurationSection section) {
		return fromSection(section, defaults());
	}

	public static GameSettings fromSection(ConfigurationSection section, GameSettings fallback) {
		if (fallback == null) fallback = defaults();
		if (section == null) return fallback;
		ConfigurationSection grenadeTnt = section.getConfigurationSection("grenade-tnt");
		return new GameSettings(
				Math.max(0, section.getInt("protection-seconds", fallback.protectionSeconds())),
				Math.max(0, section.getInt("game-time-seconds", fallback.gameTimeSeconds())),
				Math.max(1, section.getInt("ending-seconds", fallback.endingSeconds())),
				section.getDouble("eliminate-y", fallback.eliminateY()),
				section.getBoolean("allow-block-break", fallback.allowBlockBreak()),
				section.getBoolean("refill-chests-on-start", fallback.refillChestsOnStart()),
				Math.max(0, section.getInt("chest-scan-radius-chunks", fallback.chestScanRadiusChunks())),
				grenadeTnt == null ? fallback.grenadeTntEnabled() : grenadeTnt.getBoolean("enabled", fallback.grenadeTntEnabled()),
				grenadeTnt == null ? fallback.grenadeTntPower() : Math.max(0.0D, grenadeTnt.getDouble("power", fallback.grenadeTntPower())),
				grenadeTnt == null ? fallback.grenadeTntFuseTicks() : Math.max(1, grenadeTnt.getInt("fuse-ticks", fallback.grenadeTntFuseTicks())),
				grenadeTnt == null ? fallback.grenadeTntVelocity() : Math.max(0.0D, grenadeTnt.getDouble("velocity", fallback.grenadeTntVelocity())),
				grenadeTnt == null ? fallback.grenadeTntCooldownTicks() : Math.max(0, grenadeTnt.getInt("cooldown-ticks", fallback.grenadeTntCooldownTicks())),
				grenadeTnt == null ? fallback.grenadeTntBreakBlocks() : grenadeTnt.getBoolean("break-blocks", fallback.grenadeTntBreakBlocks())
		);
	}
}
