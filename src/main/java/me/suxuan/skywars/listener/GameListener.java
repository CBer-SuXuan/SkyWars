package me.suxuan.skywars.listener;

import me.suxuan.skywars.config.GameSettings;
import me.suxuan.skywars.manager.GameManager;
import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.sungame.util.GameItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class GameListener implements Listener {
	private static final NamespacedKey GRENADE_TNT_ENTITY_KEY = new NamespacedKey("skywars", "grenade_tnt");
	private static final int ENCHANTING_ITEM_SLOT = 0;
	private static final int ENCHANTING_LAPIS_SLOT = 1;
	private final GameManager gameManager;

	public GameListener(@NotNull GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
	public void onDeath(PlayerDeathEvent event) {
		Player player = event.getPlayer();
		Optional<SkyWarsGame> game = gameManager.gameOf(player);
		if (game.isEmpty() || !game.get().world().equals(player.getWorld())) return;
		if (!game.get().alivePlayers().contains(player.getUniqueId())) return;
		List<ItemStack> drops = new ArrayList<>();
		for (ItemStack item : player.getInventory().getStorageContents()) {
			if (item != null && !item.getType().isAir()) drops.add(item.clone());
		}
		for (ItemStack item : player.getInventory().getArmorContents()) {
			if (item != null && !item.getType().isAir()) drops.add(item.clone());
		}
		ItemStack offhand = player.getInventory().getItemInOffHand();
		if (!offhand.getType().isAir()) drops.add(offhand.clone());
		org.bukkit.Location deathLocation = player.getLocation().clone();
		boolean playerKill = player.getKiller() != null
				&& gameManager.gameOf(player.getKiller()).filter(killerGame -> killerGame == game.get()).isPresent()
				&& game.get().alivePlayers().contains(player.getKiller().getUniqueId());
		org.bukkit.Bukkit.getScheduler().runTask(gameManager.plugin(), () -> {
			if (playerKill) deathLocation.getWorld().strikeLightningEffect(deathLocation);
			for (ItemStack item : drops) deathLocation.getWorld().dropItemNaturally(deathLocation, item);
		});
	}

	@EventHandler
	public void onChangedWorld(PlayerChangedWorldEvent event) {
		gameManager.checkChangedWorld(event.getPlayer());
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		if (gameManager.gameOf(player).isEmpty()) return;
		Material type = event.getBlock().getType();
		if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return;
		event.setCancelled(true);
		player.sendActionBar(Component.text("空岛战争中不能破坏箱子", NamedTextColor.RED));
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		if (gameManager.isEliminated(player)) {
			event.setCancelled(true);
			if (gameManager.isLeaveItem(event.getItem())) gameManager.handleSpectatorLeave(player);
			return;
		}
		if (gameManager.queueOf(player).isPresent()) {
			event.setCancelled(true);
			return;
		}
		if (tryBlockProtectedTntPlacement(event) || tryThrowGrenadeTnt(event) || tryShootFireCharge(event) || tryPlaceSlimePlatform(event)) event.setCancelled(true);
	}

	@EventHandler
	public void onCraft(CraftItemEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (gameManager.queueOf(player).isEmpty() && gameManager.gameOf(player).isEmpty()) return;
		ItemStack result = event.getRecipe().getResult();
		if (result.getType() != Material.CHEST && result.getType() != Material.TRAPPED_CHEST) return;
		event.setCancelled(true);
		player.sendActionBar(Component.text("空岛战争中不能合成箱子", NamedTextColor.RED));
	}

	@EventHandler
	public void onEnchantingOpen(InventoryOpenEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		if (!isUsableEnchantingTable(player, event.getInventory())) return;
		fillEnchantingLapis(event.getInventory());
	}

	@EventHandler
	public void onEnchantingClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		Inventory inventory = event.getView().getTopInventory();
		if (!isUsableEnchantingTable(player, inventory)) return;
		if (event.getRawSlot() == ENCHANTING_LAPIS_SLOT) event.setCancelled(true);
		org.bukkit.Bukkit.getScheduler().runTask(gameManager.plugin(), () -> fillEnchantingLapis(inventory));
	}

	@EventHandler
	public void onEnchantingDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		Inventory inventory = event.getView().getTopInventory();
		if (!isUsableEnchantingTable(player, inventory)) return;
		if (event.getRawSlots().contains(ENCHANTING_LAPIS_SLOT)) event.setCancelled(true);
		org.bukkit.Bukkit.getScheduler().runTask(gameManager.plugin(), () -> fillEnchantingLapis(inventory));
	}

	@EventHandler
	public void onEnchantingClose(InventoryCloseEvent event) {
		Inventory inventory = event.getInventory();
		if (inventory.getType() != InventoryType.ENCHANTING) return;
		clearEnchantingLapis(inventory);
	}

	private boolean isUsableEnchantingTable(Player player, Inventory inventory) {
		return inventory.getType() == InventoryType.ENCHANTING && gameManager.isAliveInGame(player);
	}

	private void fillEnchantingLapis(Inventory inventory) {
		if (inventory.getType() != InventoryType.ENCHANTING) return;
		inventory.setItem(ENCHANTING_LAPIS_SLOT, new ItemStack(Material.LAPIS_LAZULI, 64));
	}

	private void clearEnchantingLapis(Inventory inventory) {
		if (inventory.getType() != InventoryType.ENCHANTING) return;
		ItemStack item = inventory.getItem(ENCHANTING_LAPIS_SLOT);
		if (item != null && item.getType() == Material.LAPIS_LAZULI) inventory.setItem(ENCHANTING_LAPIS_SLOT, null);
	}

	@EventHandler
	public void onProjectileHit(ProjectileHitEvent event) {
		if (!(event.getHitEntity() instanceof Player victim)) return;
		if (!(event.getEntity() instanceof Egg || event.getEntity() instanceof Snowball)) return;
		if (!(event.getEntity().getShooter() instanceof Player attacker)) return;
		if (attacker.equals(victim)) return;
		Optional<SkyWarsGame> victimGame = gameManager.gameOf(victim);
		Optional<SkyWarsGame> attackerGame = gameManager.gameOf(attacker);
		if (victimGame.isEmpty() || attackerGame.isEmpty() || victimGame.get() != attackerGame.get()) return;
		if (!victimGame.get().alivePlayers().contains(victim.getUniqueId()) || !attackerGame.get().alivePlayers().contains(attacker.getUniqueId())) return;
		if (gameManager.isProtectionActive(victim) || gameManager.isProtectionActive(attacker)) return;
		victim.damage(1.0D, attacker);
	}

	@EventHandler
	public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;
		Player attacker = unwrapPlayer(event.getDamager());
		if (attacker == null) return;
		Optional<SkyWarsGame> victimGame = gameManager.gameOf(victim);
		Optional<SkyWarsGame> attackerGame = gameManager.gameOf(attacker);
		if (attackerGame.isPresent() && gameManager.isEliminated(attacker)) {
			event.setCancelled(true);
			return;
		}
		if (victimGame.isPresent() && attackerGame.isPresent() && victimGame.get() != attackerGame.get()) {
			event.setCancelled(true);
			return;
		}
		if (gameManager.isProtectionActive(victim) || gameManager.isProtectionActive(attacker)) {
			event.setCancelled(true);
			return;
		}
		if (tryUseOneShotAxe(event, attacker, victim, victimGame, attackerGame)) return;
	}

	@EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
		if (!isGrenadeTnt(tnt)) return;
		if (!(tnt.getSource() instanceof Player player)) {
			event.blockList().clear();
			return;
		}
		Optional<SkyWarsGame> game = gameManager.gameOf(player);
		if (game.isEmpty() || !game.get().mapConfig().gameSettings().grenadeTntBreakBlocks()) {
			event.blockList().clear();
			return;
		}
		restoreExplodedBlocks(event, game.get());
	}

	private void restoreExplodedBlocks(EntityExplodeEvent event, SkyWarsGame game) {
		if (!event.blockList().isEmpty()) return;
		int radius = (int) Math.ceil(game.mapConfig().gameSettings().grenadeTntPower()) + 1;
		List<Block> blocks = new ArrayList<>();
		org.bukkit.Location center = event.getLocation();
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					Block block = center.clone().add(x, y, z).getBlock();
					if (block.getType().isAir() || block.isLiquid()) continue;
					if (block.getLocation().distanceSquared(center) > radius * radius) continue;
					blocks.add(block);
				}
			}
		}
		Collections.shuffle(blocks);
		event.blockList().addAll(blocks);
	}

	private boolean tryUseOneShotAxe(EntityDamageByEntityEvent event, Player attacker, Player victim, Optional<SkyWarsGame> victimGame, Optional<SkyWarsGame> attackerGame) {
		if (!(event.getDamager() instanceof Player)) return false;
		if (victimGame.isEmpty() || attackerGame.isEmpty() || victimGame.get() != attackerGame.get()) return false;
		SkyWarsGame game = victimGame.get();
		if (game.state() != me.suxuan.sungame.api.session.GameState.RUNNING) return false;
		if (!game.alivePlayers().contains(victim.getUniqueId()) || !game.alivePlayers().contains(attacker.getUniqueId())) return false;
		ItemStack item = attacker.getInventory().getItemInMainHand();
		if (!GameItemUtil.isActionItem(item, GameManager.ACTION_ONE_SHOT_AXE)) return false;
		event.setCancelled(true);
		gameManager.eliminate(victim, "被一次性秒人斧击杀", true);
		consumeOneMainHandItem(attacker);
		return true;
	}

	private boolean tryBlockProtectedTntPlacement(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return false;
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
		Player player = event.getPlayer();
		if (!gameManager.isAliveInGame(player) || !gameManager.isProtectionActive(player)) return false;
		ItemStack item = event.getItem();
		if (item == null || item.getType() != Material.TNT) return false;
		sendProtectionActionBar(player);
		return true;
	}

	private boolean tryThrowGrenadeTnt(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return false;
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;
		Player player = event.getPlayer();
		Optional<SkyWarsGame> gameOptional = gameManager.gameOf(player);
		if (gameOptional.isEmpty()) return false;
		SkyWarsGame game = gameOptional.get();
		GameSettings settings = game.mapConfig().gameSettings();
		if (!settings.grenadeTntEnabled()) return false;
		if (!gameManager.isAliveInGame(player)) return false;
		ItemStack item = event.getItem();
		if (!isThrowableTnt(item)) return false;
		if (gameManager.isProtectionActive(player)) {
			sendProtectionActionBar(player);
			return true;
		}
		if (player.hasCooldown(Material.TNT)) return true;
		consumeOneMainHandItem(player);
		TNTPrimed tnt = player.getWorld().spawn(player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.6D)), TNTPrimed.class);
		tnt.setSource(player);
		tnt.setFuseTicks(settings.grenadeTntFuseTicks());
		tnt.setVelocity(player.getLocation().getDirection().multiply(settings.grenadeTntVelocity()));
		tnt.setYield((float) settings.grenadeTntPower());
		tnt.setIsIncendiary(false);
		tnt.getPersistentDataContainer().set(GRENADE_TNT_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
		if (settings.grenadeTntCooldownTicks() > 0) player.setCooldown(Material.TNT, settings.grenadeTntCooldownTicks());
		return true;
	}

	private boolean tryShootFireCharge(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return false;
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;
		Player player = event.getPlayer();
		if (!gameManager.isAliveInGame(player)) return false;
		ItemStack item = event.getItem();
		if (!isShootableFireCharge(item)) return false;
		if (gameManager.isProtectionActive(player)) {
			sendProtectionActionBar(player);
			return true;
		}
		if (player.hasCooldown(Material.FIRE_CHARGE)) return true;
		consumeOneMainHandItem(player);
		SmallFireball fireball = player.launchProjectile(SmallFireball.class);
		fireball.setVelocity(player.getLocation().getDirection().multiply(1.6D));
		fireball.setIsIncendiary(false);
		fireball.setYield(1.5F);
		player.setCooldown(Material.FIRE_CHARGE, 30);
		return true;
	}

	private boolean tryPlaceSlimePlatform(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return false;
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;
		Player player = event.getPlayer();
		Optional<SkyWarsGame> gameOptional = gameManager.gameOf(player);
		if (gameOptional.isEmpty()) return false;
		if (!gameManager.isAliveInGame(player)) return false;
		ItemStack item = event.getItem();
		if (!isSlimePlatformItem(item)) return false;
		if (player.hasCooldown(Material.SLIME_BLOCK)) return true;
		List<Block> placedBlocks = placeSlimePlatform(player.getLocation(), gameOptional.get());
		if (placedBlocks.isEmpty()) return true;
		consumeOneMainHandItem(player);
		player.setCooldown(Material.SLIME_BLOCK, 100);
		org.bukkit.Bukkit.getScheduler().runTaskLater(gameManager.plugin(), () -> removeSlimePlatform(placedBlocks), 160L);
		return true;
	}

	private List<Block> placeSlimePlatform(Location playerLocation, SkyWarsGame game) {
		Location center = playerLocation.clone();
		center.setY(playerLocation.getBlockY() - 4);
		if (!center.getWorld().equals(game.world())) return List.of();
		List<Block> placedBlocks = new ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				Block block = center.clone().add(x, 0, z).getBlock();
				if (!block.getWorld().equals(game.world())) continue;
				if (!block.getType().isAir()) continue;
				block.setType(Material.SLIME_BLOCK, false);
				placedBlocks.add(block);
			}
		}
		return placedBlocks;
	}

	private void removeSlimePlatform(List<Block> placedBlocks) {
		for (Block block : placedBlocks) {
			if (block.getType() == Material.SLIME_BLOCK) block.setType(Material.AIR, false);
		}
	}

	private boolean isThrowableTnt(ItemStack item) {
		return item != null && item.getType() == Material.TNT
				&& (GameItemUtil.actionKey(item) == null || GameItemUtil.isActionItem(item, GameManager.ACTION_GRENADE_TNT));
	}

	private boolean isShootableFireCharge(ItemStack item) {
		return item != null && item.getType() == Material.FIRE_CHARGE
				&& (GameItemUtil.actionKey(item) == null || GameItemUtil.isActionItem(item, GameManager.ACTION_FIRE_CHARGE));
	}

	private boolean isSlimePlatformItem(ItemStack item) {
		return item != null && item.getType() == Material.SLIME_BLOCK && GameItemUtil.isActionItem(item, GameManager.ACTION_SLIME_PLATFORM);
	}

	private boolean isGrenadeTnt(TNTPrimed tnt) {
		return tnt.getPersistentDataContainer().has(GRENADE_TNT_ENTITY_KEY, PersistentDataType.BYTE);
	}

	private void sendProtectionActionBar(Player player) {
		player.sendActionBar(Component.text("保护期内不能使用该道具", NamedTextColor.RED));
	}

	private void consumeOneMainHandItem(Player player) {
		ItemStack item = player.getInventory().getItemInMainHand();
		if (item.getAmount() <= 1) {
			player.getInventory().setItemInMainHand(null);
			return;
		}
		item.setAmount(item.getAmount() - 1);
		player.getInventory().setItemInMainHand(item);
	}

	private Player unwrapPlayer(org.bukkit.entity.Entity entity) {
		if (entity instanceof Player player) return player;
		if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) return player;
		return null;
	}
}
