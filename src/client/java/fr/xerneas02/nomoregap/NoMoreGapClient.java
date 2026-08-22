package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import fr.xerneas02.nomoregap.render.CompositeBlockEntityRenderer;
import fr.xerneas02.nomoregap.render.CompositeChunkModel;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class NoMoreGapClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, bakeContext) ->
                bakeContext.state().getBlock() == ModBlocks.COMPOSITE
                        || bakeContext.state().getBlock() == ModBlocks.COMPOSITE_PROXY
                        ? new CompositeChunkModel(model) : model));
        BlockEntityRendererRegistry.register(ModBlockEntities.COMPOSITE, CompositeBlockEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(
                fr.xerneas02.nomoregap.network.payload.MovingPistonPayload.TYPE,
                (payload, context) -> installMovingPiston(context.client(), payload));
    }

    private static void installMovingPiston(net.minecraft.client.Minecraft minecraft,
                                            fr.xerneas02.nomoregap.network.payload.MovingPistonPayload payload) {
        var level = minecraft.level;
        if (level == null) {
            NoMoreGap.LOGGER.warn("Received moving piston {} without a client level", payload.pos());
            return;
        }
        var movingState = net.minecraft.world.level.block.Blocks.MOVING_PISTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.FACING, payload.direction())
                .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.PistonType.DEFAULT);
        level.setBlock(payload.pos(), movingState,
                net.minecraft.world.level.block.Block.UPDATE_MOVE_BY_PISTON
                        | net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        level.setBlockEntity(net.minecraft.world.level.block.piston.MovingPistonBlock.newMovingBlockEntity(
                payload.pos(), movingState, payload.movedState(), payload.direction(), payload.extending(), false));
        NoMoreGap.LOGGER.debug("Installed client moving piston at {} for {}", payload.pos(), payload.movedState());
    }
}
