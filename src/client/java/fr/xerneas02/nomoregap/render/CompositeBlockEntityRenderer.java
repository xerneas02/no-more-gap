package fr.xerneas02.nomoregap.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import fr.xerneas02.nomoregap.lava.LavaLoggingReactions;

/** Prototype renderer. Static parts should eventually be cached in chunk geometry. */
public final class CompositeBlockEntityRenderer implements BlockEntityRenderer<CompositeBlockEntity, CompositeBlockEntityRenderer.State> {
    private final BlockModelResolver resolver;
    private final BlockDisplayContext displayContext = BlockDisplayContext.create();

    public CompositeBlockEntityRenderer(BlockEntityRendererProvider.Context context) { resolver = context.blockModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 256; }

    @Override public void extractRenderState(CompositeBlockEntity entity, State state, float tickProgress, Vec3 camera,
                                             ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        for (int i = 0; i < state.count; i++) state.models[i].clear();
        state.count = 0;
        state.breakingIndex = -1;
        int breakingPartId = crumbling == null || net.minecraft.client.Minecraft.getInstance().player == null ? -1
                : fr.xerneas02.nomoregap.interaction.PartRaycaster.raycast(entity, entity.getLevel(),
                        net.minecraft.client.Minecraft.getInstance().player, 6).map(hit -> hit.partId()).orElse(-1);
        for (var part : entity.parts().view()) {
            if (part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE) continue;
            int i = state.count++;
            if (part.id() == breakingPartId) state.breakingIndex = i;
            resolver.update(state.models[i], part.state(), displayContext);
            state.breakModels[i] = net.minecraft.client.Minecraft.getInstance().getModelManager()
                    .getBlockStateModelSet().get(part.state());
            state.x[i] = part.transform().xDouble();
            state.y[i] = part.transform().yDouble();
            state.snow[i] = part.state().getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock;
            state.formedRock[i] = part.flags() == LavaLoggingReactions.FORMED_ROCK
                    && entity.parts().view().stream().anyMatch(other -> other.id() != part.id()
                    && fr.xerneas02.nomoregap.geometry.OverlapTester.overlaps(
                    other.state().getShape(entity.getLevel(), entity.getBlockPos(), net.minecraft.world.phys.shapes.CollisionContext.empty()),
                    part.state().getShape(entity.getLevel(), entity.getBlockPos(), net.minecraft.world.phys.shapes.CollisionContext.empty()),
                    part.transform()));
            state.z[i] = part.transform().zDouble();
            state.rotation[i] = part.transform().degrees();
            var samplePos = entity.getBlockPos().offset(
                    (int) Math.floor(part.transform().xDouble() + 0.5),
                    (int) Math.floor(part.transform().yDouble() + 0.5),
                    (int) Math.floor(part.transform().zDouble() + 0.5));
            state.lights[i] = entity.getLevel() == null ? state.lightCoords : LevelRenderer.getLightCoords(
                    LevelRenderer.BrightnessGetter.DEFAULT, entity.getLevel(), part.state(), samplePos);
            if (state.count == NoMoreGapLimits.MAX_PARTS_PER_CELL) break;
        }
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.count; i++) {
            pose.pushPose();
            pose.translate(state.x[i], state.y[i], state.z[i]);
            if (state.snow[i]) {
                pose.translate(0.5, 0, 0.5);
                pose.scale(0.996f, 1, 0.996f);
                pose.translate(-0.5, 0, -0.5);
            }
            if (state.formedRock[i]) {
                pose.translate(0.5, 0.5, 0.5);
                pose.scale(0.996f, 0.996f, 0.996f);
                pose.translate(-0.5, -0.5, -0.5);
            }
            pose.rotateAround(Axis.YP.rotationDegrees(state.rotation[i]), 0.5f, 0, 0.5f);
            state.models[i].submit(pose, collector, state.lights[i], OverlayTexture.NO_OVERLAY, 0);
            if (i == state.breakingIndex && state.breakProgress != null) {
                collector.submitBreakingBlockModel(pose, state.breakModels[i], state.blockPos.asLong(),
                        state.breakProgress.progress());
            }
            pose.popPose();
        }
    }

    public static final class State extends BlockEntityRenderState {
        private final BlockModelRenderState[] models = new BlockModelRenderState[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final BlockStateModel[] breakModels = new BlockStateModel[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] x = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] y = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] z = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final int[] rotation = new int[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final int[] lights = new int[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final boolean[] snow = new boolean[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final boolean[] formedRock = new boolean[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private int count;
        private int breakingIndex = -1;

        private State() {
            java.util.Arrays.setAll(models, ignored -> new BlockModelRenderState());
        }
    }
}
