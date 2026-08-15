package fr.xerneas02.nomoregap.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.lava.LavaLoggingReactions;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Stable renderer for the 1.21.x render-state pipeline. */
public final class CompositeBlockEntityRenderer
        implements BlockEntityRenderer<CompositeBlockEntity, CompositeBlockEntityRenderer.State> {
    public CompositeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(CompositeBlockEntity entity, State state, float tickProgress, Vec3 camera,
                                             ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        state.count = 0;
        for (var part : entity.parts().view()) {
            if (part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE
                    || part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY) continue;
            int i = state.count++;
            state.blocks[i] = part.state();
            state.x[i] = part.transform().xDouble();
            state.y[i] = part.transform().yDouble();
            state.z[i] = part.transform().zDouble();
            state.rotation[i] = part.transform().degrees();
            state.yScale[i] = verticalScale(entity, part);
            state.formedRock[i] = part.flags() == LavaLoggingReactions.FORMED_ROCK;
            if (state.count == NoMoreGapLimits.MAX_PARTS_PER_CELL) break;
        }
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.count; i++) {
            pose.pushPose();
            pose.translate(state.x[i], state.y[i], state.z[i]);
            pose.scale(1, state.yScale[i], 1);
            if (state.formedRock[i]) {
                pose.translate(0.5, 0.5, 0.5);
                pose.scale(0.996f, 0.996f, 0.996f);
                pose.translate(-0.5, -0.5, -0.5);
            }
            pose.rotateAround(Axis.YP.rotationDegrees(state.rotation[i]), 0.5f, 0, 0.5f);
            collector.submitBlock(pose, state.blocks[i], state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    @Override public boolean shouldRenderOffScreen() { return true; }

    private static float verticalScale(CompositeBlockEntity entity,
                                       fr.xerneas02.nomoregap.part.PartInstance part) {
        if (entity.getLevel() == null
                || !(part.state().getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                || part.state().getBlock() instanceof net.minecraft.world.level.block.WallBlock)) return 1;
        var above = entity.getBlockPos().above();
        var slab = entity.getLevel().getBlockState(above);
        boolean topSlab = isTopSlab(slab)
                || entity.getLevel().getBlockEntity(above) instanceof CompositeBlockEntity composite
                && composite.parts().view().stream().anyMatch(candidate -> isTopSlab(candidate.state()));
        if (!topSlab) return 1;
        var shape = part.state().getShape(entity.getLevel(), entity.getBlockPos(),
                net.minecraft.world.phys.shapes.CollisionContext.empty());
        if (shape.isEmpty()) return 1;
        double available = 1.5 - part.transform().yDouble() - 1.0 / NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        return (float) Math.clamp(available / shape.bounds().maxY, 0, 1);
    }

    private static boolean isTopSlab(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                && state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                == net.minecraft.world.level.block.state.properties.SlabType.TOP;
    }

    public static final class State extends BlockEntityRenderState {
        private final net.minecraft.world.level.block.state.BlockState[] blocks =
                new net.minecraft.world.level.block.state.BlockState[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] x = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] y = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] z = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final float[] yScale = new float[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final int[] rotation = new int[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final boolean[] formedRock = new boolean[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private int count;
    }

    public static void trackBreakProgress(int breakerId, BlockPos pos, int progress) {
        // ponytail: per-part crack overlays omitted; add a dedicated render pass if visual parity is required.
    }
}
