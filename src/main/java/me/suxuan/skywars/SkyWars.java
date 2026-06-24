package me.suxuan.skywars;

import me.suxuan.skywars.command.SkyWarsCommand;
import me.suxuan.skywars.config.PluginConfig;
import me.suxuan.skywars.hook.SkyWarsExpansion;
import me.suxuan.skywars.listener.GameListener;
import me.suxuan.skywars.listener.SkyWarsChatListener;
import me.suxuan.skywars.manager.GameManager;
import me.suxuan.skywars.model.SkyWarsGame;
import me.suxuan.slimearena.api.ArenaManager;
import me.suxuan.sungame.api.MiniGameService;
import me.suxuan.sungame.api.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class SkyWars extends JavaPlugin {
    private PluginConfig pluginConfig;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ArenaManager arenaManager = loadService(ArenaManager.class);
        MiniGameService miniGameService = loadService(MiniGameService.class);
        if (arenaManager == null || miniGameService == null) {
            getLogger().severe("未能获取 SunGameCore 服务，插件将禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            this.pluginConfig = new PluginConfig(this);
        } catch (Exception exception) {
            getLogger().severe("读取 SkyWars 配置失败: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.gameManager = new GameManager(this, arenaManager, miniGameService, pluginConfig);
        ProtectionPolicy protectionPolicy = protectionPolicy();
        Bukkit.getPluginManager().registerEvents(new CommonLifecycleListener<>(gameManager, lifecycleCallbacks(), protectionPolicy, lifecyclePolicy()), this);
        Bukkit.getPluginManager().registerEvents(new CommonProtectionListener<>(gameManager, protectionPolicy), this);
        Bukkit.getPluginManager().registerEvents(new CommonChatListener<>(gameManager, new ChatPolicy<>() {
        }), this);
        Bukkit.getPluginManager().registerEvents(new SkyWarsChatListener(gameManager), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(gameManager), this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SkyWarsExpansion(gameManager).register();
            getLogger().info("已注册 PlaceholderAPI 占位符: %skywars_...%");
        }
        PluginCommand command = getCommand("sw");
        if (command != null) {
            SkyWarsCommand executor = new SkyWarsCommand(gameManager, this::reloadPluginConfig);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getLogger().info("SkyWars 已启用。地图数量: " + pluginConfig.maps().size());
    }

    private LifecycleCallbacks<SkyWarsGame> lifecycleCallbacks() {
        return new LifecycleCallbacks<>() {
            @Override
            public boolean handleJoin(@NotNull Player player) {
                if (player.isOp() || !gameManager.config().autoJoinNonOp()) {
                    World world = Bukkit.getWorld("world");
                    if (world == null) world = Bukkit.getWorlds().getFirst();
                    player.setGameMode(player.isOp() ? GameMode.CREATIVE : GameMode.ADVENTURE);
                    player.teleportAsync(world.getSpawnLocation());
                    return true;
                }
                gameManager.joinQueue(player);
                return true;
            }

            @Override
            public void joinQueue(@NotNull Player player) {
                gameManager.joinQueue(player);
            }

            @Override
            public void handleQuit(@NotNull Player player) {
                gameManager.handleQuit(player);
            }

            @Override
            public void eliminate(@NotNull Player player, @NotNull String reason) {
                gameManager.eliminate(player, reason, true);
            }


            @Override
            public Location respawnLocation(@NotNull Player player, @NotNull SkyWarsGame game) {
                Bukkit.getScheduler().runTaskLater(SkyWars.this, () -> {
                    if (player.isOnline()) gameManager.giveSpectatorLeaveItem(player);
                }, 2L);
                return me.suxuan.sungame.util.LocationUtil.withWorld(game.mapConfig().spectatorSpawn(), game.world());
            }

        };
    }

    private LifecyclePolicy<SkyWarsGame> lifecyclePolicy() {
        return new LifecyclePolicy<>() {
            @Override
            public boolean hideJoinMessage(@NotNull Player player) { return true; }

            @Override
            public boolean hideQuitMessage(@NotNull Player player) { return true; }

            @Override
            public boolean clearInventoryOnJoin(@NotNull Player player) { return true; }

            @Override
            public boolean resetPlayerOnJoin(@NotNull Player player) { return true; }

            @Override
            public boolean cancelFoodChange(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelNaturalRegain(@NotNull Player player, @NotNull EntityRegainHealthEvent.RegainReason reason) {
                if (reason != EntityRegainHealthEvent.RegainReason.SATIATED
                        && reason != EntityRegainHealthEvent.RegainReason.REGEN) return false;
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelPortal(@NotNull Player player) { return gameManager.queueOf(player).isPresent() || gameManager.gameOf(player).isPresent(); }

            @Override
            public boolean cancelTeleport(@NotNull Player player, @NotNull PlayerTeleportEvent.TeleportCause cause) {
                if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                    return !gameManager.isAliveInGame(player) || gameManager.isProtectionActive(player);
                }
                return cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                        || cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                        || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL;
            }

            @Override
            public boolean clearDeathDrops(@NotNull Player player, @NotNull SkyWarsGame game) { return true; }

            @Override
            public boolean clearDroppedExp(@NotNull Player player, @NotNull SkyWarsGame game) { return true; }

            @Override
            public boolean hideDeathMessage(@NotNull Player player, @NotNull SkyWarsGame game) { return true; }

            @Override
            public boolean autoRespawn(@NotNull Player player, @NotNull SkyWarsGame game) { return true; }

            @Override
            public boolean prepareSpectatorLikeOnRespawn(@NotNull Player player, @NotNull SkyWarsGame game) { return true; }
        };
    }

    private ProtectionPolicy protectionPolicy() {
        return new ProtectionPolicy() {
            @Override
            public boolean cancelBucket(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelInventoryOpen(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelInventoryClick(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelInventoryDrag(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelBlockPlace(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelBlockBreak(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player) || !gameManager.canBreakBlocks(player);
            }

            @Override
            public boolean cancelItemDrop(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelItemPickup(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelSwapHandItems(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }

            @Override
            public boolean cancelEntityInteract(@NotNull Player player) {
                return gameManager.queueOf(player).isPresent() || gameManager.isEliminated(player);
            }
        };
    }

    private <T> T loadService(Class<T> serviceClass) {
        RegisteredServiceProvider<T> registration = getServer().getServicesManager().getRegistration(serviceClass);
        return registration == null ? null : registration.getProvider();
    }

    public PluginConfig reloadPluginConfig() {
        this.pluginConfig = new PluginConfig(this);
        if (gameManager != null) gameManager.updateConfig(pluginConfig);
        return pluginConfig;
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.stopAll();
    }
}
