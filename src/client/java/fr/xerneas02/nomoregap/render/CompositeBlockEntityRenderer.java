package fr.xerneas02.nomoregap.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import fr.xerneas02.nomoregap.lava.LavaLoggingReactions;

/** Prototype renderer. Static parts should eventually be cached in chunk geometry. */
public final class CompositeBlockEntityRenderer implements BlockEntityRenderer<CompositeBlockEntity, CompositeBlockEntityRenderer.State> {
    private static BlockPos breakingAnchor;
    private static int breakingStage = -1;
    private final BlockModelResolver resolver;
    private final BlockDisplayContext displayContext = BlockDisplayContext.create();

    public CompositeBlockEntityRenderer(BlockEntityRendererProvider.Context context) { resolver = context.blockModelResolver(); }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public boolean shouldRender(CompositeBlockEntity entity, Vec3 camera) {
        if (!entity.getBlockPos().equals(breakingAnchor)
                && entity.parts().view().stream().allMatch(part -> isRenderedInChunk(entity, part))) return false;
        int distance = NoMoreGapConfig.renderDistanceBlocks(
                net.minecraft.client.Minecraft.getInstance().options.renderDistance().get());
        return Vec3.atCenterOf(entity.getBlockPos()).closerThan(camera, distance);
    }

    @Override public void extractRenderState(CompositeBlockEntity entity, State state, float tickProgress, Vec3 camera,
                                             ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        state.breakingIndex = -1;
        state.breakingStage = entity.getBlockPos().equals(breakingAnchor) ? breakingStage : -1;
        int breakingPartId = state.breakingStage < 0 || net.minecraft.client.Minecraft.getInstance().player == null ? -1
                : fr.xerneas02.nomoregap.interaction.PartRaycaster.raycast(entity, entity.getLevel(),
                        net.minecraft.client.Minecraft.getInstance().player, 6).map(hit -> hit.partId()).orElse(-1);
        if (state.revision != entity.revision()) rebuild(entity, state);
        if (breakingPartId >= 0) for (int i = 0; i < state.count; i++) {
            if (state.partIds[i] == breakingPartId) {
                state.breakingIndex = i;
                break;
            }
        }
        for (int i = 0; i < state.count; i++) {
            state.lights[i] = state.lightCoords;
            state.yScale[i] = verticalScale(entity, state.parts[i]);
        }
    }

    private void rebuild(CompositeBlockEntity entity, State state) {
        for (int i = 0; i < state.count; i++) state.models[i].clear();
        state.count = 0;
        for (var part : entity.parts().view()) {
            if (part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE
                    || part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY) continue;
            int i = state.count++;
            state.partIds[i] = part.id();
            state.parts[i] = part;
            state.chunkRendered[i] = isRenderedInChunk(entity, part);
            state.blockStates[i] = part.state();
            resolver.update(state.models[i], part.state(), displayContext);
            state.breakModels[i] = net.minecraft.client.Minecraft.getInstance().getModelManager()
                    .getBlockStateModelSet().get(part.state());
            state.x[i] = part.transform().xDouble();
            state.y[i] = part.transform().yDouble();
            state.z[i] = part.transform().zDouble();
            state.rotation[i] = part.transform().degrees();
            var partShape = fr.xerneas02.nomoregap.geometry.ShapeTransformer.transform(
                    part.state().getShape(entity.getLevel(), entity.getBlockPos(),
                            net.minecraft.world.phys.shapes.CollisionContext.empty()), part.transform());
            state.partBounds[i] = partShape.isEmpty() ? null : partShape.bounds().move(
                    entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ());
            state.formedRock[i] = part.flags() == LavaLoggingReactions.FORMED_ROCK
                    && entity.parts().view().stream().anyMatch(other -> other.id() != part.id()
                    && fr.xerneas02.nomoregap.geometry.OverlapTester.overlaps(
                    other.state().getShape(entity.getLevel(), entity.getBlockPos(), net.minecraft.world.phys.shapes.CollisionContext.empty()),
                    part.state().getShape(entity.getLevel(), entity.getBlockPos(), net.minecraft.world.phys.shapes.CollisionContext.empty()),
                    part.transform()));
            if (state.count == NoMoreGapLimits.MAX_PARTS_PER_CELL) break;
        }
        var shape = entity.geometry(entity.getLevel(), net.minecraft.world.phys.shapes.CollisionContext.empty()).selection();
        state.bounds = shape.isEmpty() ? new AABB(entity.getBlockPos()) : shape.bounds().move(
                entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ());
        state.revision = entity.revision();
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.bounds != null && camera.cullFrustum != null && !camera.cullFrustum.isVisible(state.bounds)) return;
        for (int i = 0; i < state.count; i++) {
            if (state.partBounds[i] != null && camera.cullFrustum != null
                    && !camera.cullFrustum.isVisible(state.partBounds[i])) continue;
            pose.pushPose();
            pose.translate(state.x[i], state.y[i], state.z[i]);
            pose.scale(1, state.yScale[i], 1);
            if (state.formedRock[i]) {
                pose.translate(0.5, 0.5, 0.5);
                pose.scale(0.996f, 0.996f, 0.996f);
                pose.translate(-0.5, -0.5, -0.5);
            }
            pose.rotateAround(Axis.YP.rotationDegrees(state.rotation[i]), 0.5f, 0, 0.5f);
            if (!state.chunkRendered[i]) {
                state.models[i].submit(pose, collector, state.lights[i], OverlayTexture.NO_OVERLAY, 0);
            }
            if (i == state.breakingIndex && state.breakingStage >= 0) {
                submitBreakingModel(collector, pose, state.breakModels[i], state.blockPos.asLong(), state.breakingStage);
            }
            pose.popPose();
        }
    }

    private static void submitBreakingModel(SubmitNodeCollector collector, PoseStack pose, BlockStateModel model,
                                            long pos, int stage) {
        try {
            for (var method : collector.getClass().getMethods()) {
                if (!method.getName().equals("submitBreakingBlockModel")) continue;
                var parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 4 && parameterTypes[1].isInstance(model)) {
                    method.invoke(collector, pose, model, pos, stage);
                    return;
                }
                if (parameterTypes.length != 3 || !java.util.List.class.isAssignableFrom(parameterTypes[1])) continue;
                var parts = new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
                model.collectParts(net.minecraft.util.RandomSource.create(pos), parts);
                method.invoke(collector, pose, parts, stage);
                return;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
    }

    private static boolean isRenderedInChunk(CompositeBlockEntity entity,
                                             fr.xerneas02.nomoregap.part.PartInstance part) {
        if (!CompositeChunkModel.isChunkRendered(part) || entity.getLevel() == null) return false;
        int unit = NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        var owner = entity.getBlockPos().offset(
                Math.floorDiv(part.transform().x().units(), unit),
                Math.floorDiv(part.transform().y().units(), unit),
                Math.floorDiv(part.transform().z().units(), unit));
        var ownerEntity = entity.getLevel().getBlockEntity(owner);
        return ownerEntity == entity
                || ownerEntity instanceof fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity proxy
                && proxy.anchor().equals(entity.getBlockPos());
    }

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
        private final BlockModelRenderState[] models = new BlockModelRenderState[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final BlockStateModel[] breakModels = new BlockStateModel[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final net.minecraft.world.level.block.state.BlockState[] blockStates = new net.minecraft.world.level.block.state.BlockState[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final fr.xerneas02.nomoregap.part.PartInstance[] parts = new fr.xerneas02.nomoregap.part.PartInstance[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final AABB[] partBounds = new AABB[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final int[] partIds = new int[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] x = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] y = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final double[] z = new double[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final float[] yScale = new float[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final int[] rotation = new int[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final int[] lights = new int[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final boolean[] formedRock = new boolean[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private final boolean[] chunkRendered = new boolean[NoMoreGapLimits.MAX_PARTS_PER_CELL];
        private AABB bounds;
        private long revision = Long.MIN_VALUE;
        private int count;
        private int breakingIndex = -1;
        private int breakingStage = -1;

        private State() {
            java.util.Arrays.setAll(models, ignored -> new BlockModelRenderState());
        }
    }

    public static void trackBreakProgress(int breakerId, BlockPos pos, int progress) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || breakerId != minecraft.player.getId()) return;
        if (progress < 0 || progress >= 10) {
            breakingAnchor = null;
            breakingStage = -1;
            return;
        }
        if (minecraft.level == null) return;
        if (minecraft.level.getBlockEntity(pos) instanceof fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity proxy) {
            breakingAnchor = proxy.anchor();
        } else if (minecraft.level.getBlockEntity(pos) instanceof CompositeBlockEntity) {
            breakingAnchor = pos.immutable();
        } else {
            return;
        }
        breakingStage = progress;
    }
}
