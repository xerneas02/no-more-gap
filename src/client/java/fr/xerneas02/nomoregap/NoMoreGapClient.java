package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import fr.xerneas02.nomoregap.render.CompositeBlockEntityRenderer;
import fr.xerneas02.nomoregap.render.CompositeChunkModel;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

public final class NoMoreGapClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, bakeContext) ->
                bakeContext.state().getBlock() == ModBlocks.COMPOSITE
                        || bakeContext.state().getBlock() == ModBlocks.COMPOSITE_PROXY
                        ? new CompositeChunkModel(model) : model));
        BlockEntityRendererRegistry.register(ModBlockEntities.COMPOSITE, CompositeBlockEntityRenderer::new);
    }
}
