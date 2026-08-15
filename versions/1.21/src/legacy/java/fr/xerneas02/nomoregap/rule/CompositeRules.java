package fr.xerneas02.nomoregap.rule;

import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

public final class CompositeRules {
    public static final GameRules.Key<GameRules.IntegerValue> MAX_PARTS = GameRuleRegistry.register(
            "no_more_gap:max_composite_parts", GameRules.Category.MISC,
            GameRuleFactory.createIntRule(NoMoreGapConfig.maxCompositeParts(), 2, NoMoreGapLimits.MAX_PARTS_PER_CELL));

    private CompositeRules() {}
    public static void initialize() {}
    public static int maxParts(net.minecraft.server.level.ServerLevel level) {
        return level.getGameRules().getInt(MAX_PARTS);
    }
}
