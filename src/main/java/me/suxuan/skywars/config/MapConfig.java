package me.suxuan.skywars.config;

import me.suxuan.sungame.api.loot.DistanceLootSelector;
import me.suxuan.sungame.api.loot.LootRegistry;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MapConfig(
		@NotNull String id,
		@NotNull String displayName,
		@NotNull String templateWorld,
		int weight,
		@NotNull Location spectatorSpawn,
		@NotNull List<Location> spawns,
		@NotNull GameSettings gameSettings,
		@NotNull DistanceLootSelector lootSettings
) {
	public MapConfig {
		weight = Math.max(1, weight);
		spawns = List.copyOf(spawns);
		if (spawns.isEmpty()) throw new IllegalArgumentException("地图 " + id + " 至少需要一个出生点");
	}

	public static MapConfig fromFile(@NotNull File file, @NotNull GameSettings defaultGameSettings, @NotNull LootRegistry globalLootSettings) {
		YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
		String fileName = file.getName();
		String id = fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
		String displayName = config.getString("display-name", id);
		String templateWorld = config.getString("template-world", id);
		int weight = config.getInt("weight", 1);
		Location spectatorSpawn = readLocation(Objects.requireNonNull(config.getConfigurationSection("spectator-spawn"), "地图 " + id + " 缺少 spectator-spawn 配置"));
		List<Location> spawns = new ArrayList<>();
		for (Map<?, ?> raw : config.getMapList("spawns")) {
			spawns.add(readLocation(raw));
		}
		if (spawns.isEmpty()) throw new IllegalStateException("地图 " + id + " 缺少 spawns 配置");
		GameSettings gameSettings = GameSettings.fromSection(config.getConfigurationSection("game"), defaultGameSettings);
		LootWeightNormalizer.normalize(config.getConfigurationSection("loot"));
		DistanceLootSelector lootSettings = DistanceLootSelector.fromSection(config.getConfigurationSection("loot"), spectatorSpawn, globalLootSettings);
		return new MapConfig(id, displayName, templateWorld, weight, spectatorSpawn, spawns, gameSettings, lootSettings);
	}

	private static Location readLocation(ConfigurationSection section) {
		return new Location(null, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
				(float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
	}

	private static Location readLocation(Map<?, ?> map) {
		return new Location(null, number(map, "x"), number(map, "y"), number(map, "z"),
				(float) number(map, "yaw"), (float) number(map, "pitch"));
	}

	private static double number(Map<?, ?> map, String key) {
		Object value = map.get(key);
		return value instanceof Number number ? number.doubleValue() : 0.0D;
	}

	@Override public Location spectatorSpawn() { return spectatorSpawn.clone(); }
	@Override public List<Location> spawns() { return spawns.stream().map(Location::clone).toList(); }
}
