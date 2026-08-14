package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.command.NoMoreGapDebugCommand;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.interaction.CompositePlacementHandler;
import fr.xerneas02.nomoregap.interaction.CreativeCompositeBreakingHandler;
import fr.xerneas02.nomoregap.interaction.CompositeInteractionHandler;
import fr.xerneas02.nomoregap.network.NoMoreGapNetworking;
import fr.xerneas02.nomoregap.registry.ModRegistries;
import fr.xerneas02.nomoregap.lava.LavaLoggingRules;
import fr.xerneas02.nomoregap.rule.CompositeRules;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoMoreGap implements ModInitializer {
    public static final String MOD_ID = "no_more_gap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override public void onInitialize() {
        NoMoreGapConfig.initialize();
        LavaLoggingRules.initialize();
        CompositeRules.initialize();
        ModRegistries.initialize();
        NoMoreGapNetworking.initialize();
        NoMoreGapDebugCommand.initialize();
        CompositePlacementHandler.initialize();
        CreativeCompositeBreakingHandler.initialize();
        CompositeInteractionHandler.initialize();
    }
}
