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
    private int initialPlayerCount;
    private boolean firstRefillDone;
    private boolean secondRefillDone;
    private boolean doomStarted;
    private int doomElapsedSeconds;
    private int secondsSinceLastDoomBombardment;
    private int doomBombSequence;

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

    public int initialPlayerCount() {
        return initialPlayerCount;
    }

    public void initialPlayerCount(int initialPlayerCount) {
        this.initialPlayerCount = Math.max(0, initialPlayerCount);
    }

    public boolean firstRefillDone() {
        return firstRefillDone;
    }

    public void firstRefillDone(boolean firstRefillDone) {
        this.firstRefillDone = firstRefillDone;
    }

    public boolean secondRefillDone() {
        return secondRefillDone;
    }

    public void secondRefillDone(boolean secondRefillDone) {
        this.secondRefillDone = secondRefillDone;
    }

    public boolean doomStarted() {
        return doomStarted;
    }

    public void doomStarted(boolean doomStarted) {
        this.doomStarted = doomStarted;
    }

    public int doomElapsedSeconds() {
        return doomElapsedSeconds;
    }

    public void doomElapsedSeconds(int doomElapsedSeconds) {
        this.doomElapsedSeconds = Math.max(0, doomElapsedSeconds);
    }

    public int secondsSinceLastDoomBombardment() {
        return secondsSinceLastDoomBombardment;
    }

    public void secondsSinceLastDoomBombardment(int secondsSinceLastDoomBombardment) {
        this.secondsSinceLastDoomBombardment = Math.max(0, secondsSinceLastDoomBombardment);
    }

    public int nextDoomBombSequence() {
        return ++doomBombSequence;
    }
}
