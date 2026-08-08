package fr.xerneas02.nomoregap.lava;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class LavaLoggingRules {
    public static final GameRule<Boolean> DO_REACTIONS = GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(NoMoreGap.id("do_lava_logging_reactions"));

    private LavaLoggingRules() {}
    public static void initialize() {}
}
