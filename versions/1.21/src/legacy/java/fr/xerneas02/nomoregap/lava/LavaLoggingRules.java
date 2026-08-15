package fr.xerneas02.nomoregap.lava;

import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

public final class LavaLoggingRules {
    public static final GameRules.Key<GameRules.BooleanValue> DO_REACTIONS = GameRuleRegistry.register(
            "no_more_gap:do_lava_logging_reactions", GameRules.Category.MISC,
            GameRuleFactory.createBooleanRule(NoMoreGapConfig.lavaLoggingReactions()));

    private LavaLoggingRules() {}
    public static void initialize() {}
    public static boolean enabled(net.minecraft.server.level.ServerLevel level) {
        return level.getGameRules().getBoolean(DO_REACTIONS);
    }
}
