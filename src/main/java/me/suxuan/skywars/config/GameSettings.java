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
		boolean grenadeTntBreakBlocks,
		boolean refillEnabled,
		int firstRefillAfterSeconds,
		int secondRefillAfterSeconds,
		int secondRefillEnderPearlsPerChest,
		boolean doomEnabled,
		int doomStartAfterSeconds,
		int doomShrinkSeconds,
		double doomStartRadius,
		double doomEndRadius,
		double doomOutsideDamagePerSecond,
		double doomOutsidePullStrength,
		boolean doomBombardmentEnabled,
		int doomBombardmentIntervalSeconds,
		int doomBombardmentWarningTicks,
		double doomBombardmentRadiusAroundPlayer,
		double doomBombardmentExplosionPower,
		boolean doomBombardmentBreakBlocks,
		boolean doomBombardmentFire
) {
	public static GameSettings defaults() {
		return new GameSettings(10, 900, 8, -20.0D, true, true, 3,
				true, 3.5D, 40, 1.25D, 60, false,
				true, 180, 360, 1,
				true, 480, 90, 120.0D, 18.0D, 2.0D, 0.35D,
				true, 8, 30, 5.0D, 2.5D, true, true);
	}

	public static GameSettings fromSection(ConfigurationSection section) {
		return fromSection(section, defaults());
	}

	public static GameSettings fromSection(ConfigurationSection section, GameSettings fallback) {
		if (fallback == null) fallback = defaults();
		if (section == null) return fallback;
		ConfigurationSection grenadeTnt = section.getConfigurationSection("grenade-tnt");
		ConfigurationSection refill = section.getConfigurationSection("refill");
		ConfigurationSection doom = section.getConfigurationSection("doom");
		ConfigurationSection bombardment = doom == null ? null : doom.getConfigurationSection("bombardment");
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
				grenadeTnt == null ? fallback.grenadeTntBreakBlocks() : grenadeTnt.getBoolean("break-blocks", fallback.grenadeTntBreakBlocks()),
				refill == null ? fallback.refillEnabled() : refill.getBoolean("enabled", fallback.refillEnabled()),
				refill == null ? fallback.firstRefillAfterSeconds() : Math.max(0, refill.getInt("first-after-seconds", fallback.firstRefillAfterSeconds())),
				refill == null ? fallback.secondRefillAfterSeconds() : Math.max(0, refill.getInt("second-after-seconds", fallback.secondRefillAfterSeconds())),
				refill == null ? fallback.secondRefillEnderPearlsPerChest() : Math.max(0, refill.getInt("second-refill-ender-pearls-per-chest", fallback.secondRefillEnderPearlsPerChest())),
				doom == null ? fallback.doomEnabled() : doom.getBoolean("enabled", fallback.doomEnabled()),
				doom == null ? fallback.doomStartAfterSeconds() : Math.max(0, doom.getInt("start-after-seconds", fallback.doomStartAfterSeconds())),
				doom == null ? fallback.doomShrinkSeconds() : Math.max(1, doom.getInt("shrink-seconds", fallback.doomShrinkSeconds())),
				doom == null ? fallback.doomStartRadius() : Math.max(1.0D, doom.getDouble("start-radius", fallback.doomStartRadius())),
				doom == null ? fallback.doomEndRadius() : Math.max(1.0D, doom.getDouble("end-radius", fallback.doomEndRadius())),
				doom == null ? fallback.doomOutsideDamagePerSecond() : Math.max(0.0D, doom.getDouble("outside-damage-per-second", fallback.doomOutsideDamagePerSecond())),
				doom == null ? fallback.doomOutsidePullStrength() : Math.max(0.0D, doom.getDouble("outside-pull-strength", fallback.doomOutsidePullStrength())),
				bombardment == null ? fallback.doomBombardmentEnabled() : bombardment.getBoolean("enabled", fallback.doomBombardmentEnabled()),
				bombardment == null ? fallback.doomBombardmentIntervalSeconds() : Math.max(1, bombardment.getInt("interval-seconds", fallback.doomBombardmentIntervalSeconds())),
				bombardment == null ? fallback.doomBombardmentWarningTicks() : Math.max(1, bombardment.getInt("warning-ticks", fallback.doomBombardmentWarningTicks())),
				bombardment == null ? fallback.doomBombardmentRadiusAroundPlayer() : Math.max(0.0D, bombardment.getDouble("radius-around-player", fallback.doomBombardmentRadiusAroundPlayer())),
				bombardment == null ? fallback.doomBombardmentExplosionPower() : Math.max(0.0D, bombardment.getDouble("explosion-power", fallback.doomBombardmentExplosionPower())),
				bombardment == null ? fallback.doomBombardmentBreakBlocks() : bombardment.getBoolean("break-blocks", fallback.doomBombardmentBreakBlocks()),
				bombardment == null ? fallback.doomBombardmentFire() : bombardment.getBoolean("fire", fallback.doomBombardmentFire())
		);
	}
}
