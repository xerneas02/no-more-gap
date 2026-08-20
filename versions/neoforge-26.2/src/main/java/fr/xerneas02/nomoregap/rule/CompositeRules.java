package fr.xerneas02.nomoregap.rule;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;

public final class CompositeRules {
    public static GameRule<Integer> MAX_PARTS;
    private CompositeRules() {}
    public static void initialize() {}
}
