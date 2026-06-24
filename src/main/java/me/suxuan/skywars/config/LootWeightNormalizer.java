package me.suxuan.skywars.config;

import org.bukkit.configuration.ConfigurationSection;

public final class LootWeightNormalizer {
	private static final int SCALE = 100;

	private LootWeightNormalizer() {
	}

	public static void normalize(ConfigurationSection lootSection) {
		if (lootSection == null) return;
		normalizeTierContainer(lootSection.getConfigurationSection("tiers"));
		normalizeTierContainer(lootSection.getConfigurationSection("tables"));
	}

	private static void normalizeTierContainer(ConfigurationSection tierContainer) {
		if (tierContainer == null) return;
		for (String tierId : tierContainer.getKeys(false)) {
			ConfigurationSection tier = tierContainer.getConfigurationSection(tierId);
			if (tier == null) continue;
			normalizeEntries(tier.getConfigurationSection("entries"));
		}
	}

	private static void normalizeEntries(ConfigurationSection entries) {
		if (entries == null) return;
		for (String entryId : entries.getKeys(false)) {
			ConfigurationSection entry = entries.getConfigurationSection(entryId);
			if (entry == null || !entry.contains("weight")) continue;
			double weight = entry.getDouble("weight", 1.0D);
			entry.set("weight", Math.max(1, (int) Math.round(weight * SCALE)));
		}
	}
}
