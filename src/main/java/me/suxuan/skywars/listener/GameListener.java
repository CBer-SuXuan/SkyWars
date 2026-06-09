package me.suxuan.skywars.listener;

import me.suxuan.skywars.config.GameSettings;
import me.suxuan.skywars.manager.GameManager;
import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.sungame.util.GameItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class GameListener implements Listener {
	private static final NamespacedKey GRENADE_TNT_ENTITY_KEY = new NamespacedKey("skywars", "grenade_tnt");
	private final GameManager gameManager;

	public GameListener(@NotNull GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@EventHandler
	public void onChangedWorld(PlayerChangedWorldEvent event) {
		gameManager.checkChangedWorld(event.getPlayer());
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
		if (tryThrowGrenadeTnt(event)) event.setCancelled(true);
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

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent event) {
		if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
		if (!isGrenadeTnt(tnt)) return;
		if (tnt.getSource() instanceof Player player) {
			Optional<SkyWarsGame> game = gameManager.gameOf(player);
			if (game.isPresent() && !game.get().mapConfig().gameSettings().grenadeTntBreakBlocks()) event.blockList().clear();
			return;
		}
		event.blockList().clear();
	}

	private boolean tryUseOneShotAxe(EntityDamageByEntityEvent event, Player attacker, Player victim, Optional<SkyWarsGame> victimGame, Optional<SkyWarsGame> attackerGame) {
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
		if (!gameManager.isAliveInGame(player) || gameManager.isProtectionActive(player)) return false;
		ItemStack item = event.getItem();
		if (!GameItemUtil.isActionItem(item, GameManager.ACTION_GRENADE_TNT)) return false;
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

	private boolean isGrenadeTnt(TNTPrimed tnt) {
		return tnt.getPersistentDataContainer().has(GRENADE_TNT_ENTITY_KEY, PersistentDataType.BYTE);
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
