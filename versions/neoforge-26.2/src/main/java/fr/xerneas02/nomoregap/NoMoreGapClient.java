package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import fr.xerneas02.nomoregap.render.NeoForgeCompositeBlockEntityRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NoMoreGap.MOD_ID, value = Dist.CLIENT)
public final class NoMoreGapClient {
    private NoMoreGapClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.COMPOSITE, NeoForgeCompositeBlockEntityRenderer::new);
    }
}
