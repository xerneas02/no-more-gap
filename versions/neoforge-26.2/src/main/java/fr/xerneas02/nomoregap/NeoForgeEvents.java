package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.command.NoMoreGapDebugCommand;
import fr.xerneas02.nomoregap.interaction.CompositeInteractionHandler;
import fr.xerneas02.nomoregap.interaction.CompositePlacementHandler;
import fr.xerneas02.nomoregap.interaction.CreativeCompositeBreakingHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Thin NeoForge adapters around loader-neutral gameplay handlers. */
public final class NeoForgeEvents {
    private NeoForgeEvents() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        NoMoreGapDebugCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        var result = CompositePlacementHandler.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (!result.consumesAction()) {
            result = CompositeInteractionHandler.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        }
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void attackBlock(PlayerInteractEvent.LeftClickBlock event) {
        var result = CreativeCompositeBreakingHandler.attackBlock(event.getEntity(), event.getLevel(), event.getHand(),
                event.getPos(), event.getFace());
        if (result.consumesAction()) event.setCanceled(true);
    }
}
