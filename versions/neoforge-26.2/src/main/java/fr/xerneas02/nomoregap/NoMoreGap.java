package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.network.NoMoreGapNetworking;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(NoMoreGap.MOD_ID)
public final class NoMoreGap {
    public static final String MOD_ID = "no_more_gap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    public NoMoreGap(IEventBus modBus, ModContainer container) {
        NoMoreGapConfig.initialize();
        modBus.register(NeoForgeRegistries.class);
        NoMoreGapNetworking.register(modBus);
        NeoForge.EVENT_BUS.register(NeoForgeEvents.class);
        if (net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()) {
            NeoForgeGameTestSetup.setup(modBus, container);
        }
    }
}
