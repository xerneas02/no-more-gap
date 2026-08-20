package fr.xerneas02.nomoregap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.impl.MutableTestFramework;

/**
 * Boots the NeoForge test framework so the {@code @ForEachTest}/{@code @GameTest}
 * methods in the game test source set are discovered and registered with the
 * vanilla GameTest server. Only active on game test server runs
 * ({@link net.neoforged.neoforge.gametest.GameTestHooks#isGametestEnabled()}),
 * never in normal gameplay.
 */
final class NeoForgeGameTestSetup {
    private NeoForgeGameTestSetup() {}

    static void setup(IEventBus modBus, ModContainer container) {
        MutableTestFramework framework = FrameworkConfiguration.builder(NoMoreGap.id("tests"))
                .build()
                .create();
        framework.init(modBus, container);

        NeoForge.EVENT_BUS.addListener((final RegisterCommandsEvent event) -> {
            final var node = Commands.literal("tests");
            framework.registerCommands(node);
            event.getDispatcher().register(node);
        });
    }
}
