package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import fr.xerneas02.nomoregap.render.CompositeChunkModel;
import fr.xerneas02.nomoregap.render.NeoForgeCompositeBlockEntityRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = NoMoreGap.MOD_ID, value = Dist.CLIENT)
public final class NoMoreGapClient {
    private NoMoreGapClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.COMPOSITE, NeoForgeCompositeBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void modifyModels(ModelEvent.ModifyBakingResult event) {
        event.getBakingResult().blockStateModels().replaceAll((state, model) ->
                state.getBlock() == ModBlocks.COMPOSITE || state.getBlock() == ModBlocks.COMPOSITE_PROXY
                        ? new CompositeChunkModel(model) : model);
    }
}
