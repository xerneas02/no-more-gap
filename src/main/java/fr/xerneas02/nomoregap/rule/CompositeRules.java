package fr.xerneas02.nomoregap.rule;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class CompositeRules {
    public static final GameRule<Integer> MAX_PARTS = GameRuleBuilder.forInteger(NoMoreGapConfig.maxCompositeParts())
            .range(2, NoMoreGapLimits.MAX_PARTS_PER_CELL)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(NoMoreGap.id("max_composite_parts"));

    private CompositeRules() {}
    public static void initialize() {}
}
