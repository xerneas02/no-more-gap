package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import fr.xerneas02.nomoregap.render.CompositeBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

public final class NoMoreGapClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRendererRegistry.register(ModBlockEntities.COMPOSITE, CompositeBlockEntityRenderer::new);
    }
}
