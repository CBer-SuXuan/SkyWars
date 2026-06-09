package me.suxuan.skywars.config;

import me.suxuan.sungame.api.loot.LootRegistry;
import me.suxuan.sungame.util.config.LocationConfigUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PluginConfig {
	private final JavaPlugin plugin;
	private String queueTemplateWorld;
	private int minPlayers;
	private int maxPlayers;
	private int longCountdownSeconds;
	private int quickCountdownSeconds;
	private int quickCountdownPercent;
	private boolean autoJoinNonOp;
	private Location queueSpawn;
	private GameSettings gameSettings;
	private LootRegistry lootSettings;
	private List<MapConfig> maps;

	public PluginConfig(@NotNull JavaPlugin plugin) {
		this.plugin = plugin;
		reload();
	}

	public void reload() {
		plugin.reloadConfig();
		ensureDefaultMapConfig();
		FileConfiguration config = plugin.getConfig();
		queueTemplateWorld = config.getString("queue-template-world", "queue");
		minPlayers = config.getInt("min-players", 2);
		maxPlayers = config.getInt("max-players", 12);
		longCountdownSeconds = config.getInt("long-countdown-seconds", 60);
		quickCountdownSeconds = config.getInt("quick-countdown-seconds", 10);
		quickCountdownPercent = Math.clamp(config.getInt("quick-countdown-percent", 80), 1, 100);
		autoJoinNonOp = config.getBoolean("auto-join-non-op", true);
		if (minPlayers < 1) minPlayers = 1;
		if (maxPlayers < 1) maxPlayers = 1;
		if (minPlayers > maxPlayers) minPlayers = maxPlayers;
		ConfigurationSection queueSpawnSection = config.getConfigurationSection("queue-spawn");
		queueSpawn = queueSpawnSection == null ? new Location(null, 0.5, 80.0, 0.5, 0.0F, 0.0F) : LocationConfigUtil.readRelative(queueSpawnSection);
		gameSettings = GameSettings.fromSection(config.getConfigurationSection("game"));
		lootSettings = LootRegistry.fromSection(config.getConfigurationSection("loot"));
		maps = loadMaps();
		if (maps.isEmpty()) throw new IllegalStateException("没有可用地图配置，请检查 plugins/SkyWars/map/*.yml");
	}

	private void ensureDefaultMapConfig() {
		File mapDir = new File(plugin.getDataFolder(), "map");
		if (!mapDir.exists() && !mapDir.mkdirs())
			throw new IllegalStateException("无法创建地图配置目录: " + mapDir.getAbsolutePath());
		String[] files = mapDir.list();
		if (files == null || files.length == 0) plugin.saveResource("map/skywars_default.yml", false);
	}

	private List<MapConfig> loadMaps() {
		File mapDir = new File(plugin.getDataFolder(), "map");
		File[] files = mapDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
		List<MapConfig> result = new ArrayList<>();
		if (files == null) return result;
		for (File file : files) {
			try {
				MapConfig map = MapConfig.fromFile(file, gameSettings, lootSettings);
				if (map.spawns().size() < maxPlayers) {
					plugin.getLogger().warning("地图 " + map.id() + " 出生点数量少于 max-players，满员时只会使用前 " + map.spawns().size() + " 人");
				}
				result.add(map);
			} catch (Exception exception) {
				plugin.getLogger().severe("加载地图配置失败 " + file.getName() + ": " + exception.getMessage());
			}
		}
		result.sort(Comparator.comparing(MapConfig::id));
		return List.copyOf(result);
	}


	public String queueTemplateWorld() {
		return queueTemplateWorld;
	}

	public int minPlayers() {
		return minPlayers;
	}

	public int maxPlayers() {
		return maxPlayers;
	}

	public int longCountdownSeconds() {
		return longCountdownSeconds;
	}

	public int quickCountdownSeconds() {
		return quickCountdownSeconds;
	}

	public int quickCountdownPercent() {
		return quickCountdownPercent;
	}

	public int quickStartPlayers() {
		return Math.max(minPlayers, (int) Math.ceil(maxPlayers * (quickCountdownPercent / 100.0D)));
	}

	public boolean autoJoinNonOp() {
		return autoJoinNonOp;
	}

	public Location queueSpawn() {
		return queueSpawn.clone();
	}

	public GameSettings gameSettings() {
		return gameSettings;
	}

	public LootRegistry lootSettings() {
		return lootSettings;
	}

	public List<MapConfig> maps() {
		return maps;
	}
}
