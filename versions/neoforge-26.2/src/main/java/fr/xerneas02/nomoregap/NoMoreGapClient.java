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

    public static void installMovingPiston(
            fr.xerneas02.nomoregap.network.payload.MovingPistonPayload payload) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var movingState = net.minecraft.world.level.block.Blocks.MOVING_PISTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.FACING, payload.direction())
                .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.PistonType.DEFAULT);
        level.setBlock(payload.pos(), movingState, net.minecraft.world.level.block.Block.UPDATE_MOVE_BY_PISTON
                | net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        level.setBlockEntity(net.minecraft.world.level.block.piston.MovingPistonBlock.newMovingBlockEntity(
                payload.pos(), movingState, payload.movedState(), payload.direction(), payload.extending(), false));
    }
}
