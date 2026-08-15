package fr.xerneas02.nomoregap.lava;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class LavaLoggingRules {
    public static final GameRule<Boolean> DO_REACTIONS = GameRuleBuilder.forBoolean(NoMoreGapConfig.lavaLoggingReactions())
            .category(GameRuleCategory.MISC)
            .buildAndRegister(NoMoreGap.id("do_lava_logging_reactions"));

    private LavaLoggingRules() {}
    public static void initialize() {}
    public static boolean enabled(net.minecraft.server.level.ServerLevel level) {
        return level.getGameRules().get(DO_REACTIONS);
    }
}
