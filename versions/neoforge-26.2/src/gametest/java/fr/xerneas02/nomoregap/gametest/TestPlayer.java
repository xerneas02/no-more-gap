package fr.xerneas02.nomoregap.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * A lightweight, controllable mock player used to drive the port's real
 * interaction handlers inside game tests. It never joins the world, so tests
 * control its position and view direction precisely.
 */
public final class TestPlayer extends Player {
    private final GameType gameType;
    private boolean sneaking;

    public TestPlayer(Level level, GameProfile profile, GameType gameType) {
        super(level, profile);
        this.gameType = gameType;
    }

    @Override
    public GameType gameMode() {
        return gameType;
    }

    @Override
    public boolean isSecondaryUseActive() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }
}
