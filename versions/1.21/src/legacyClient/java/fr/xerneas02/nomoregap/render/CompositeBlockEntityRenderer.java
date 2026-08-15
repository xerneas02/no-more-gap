package fr.xerneas02.nomoregap.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.lava.LavaLoggingReactions;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;

/** Renderer used before the render-state pipeline introduced in 1.21.9. */
public final class CompositeBlockEntityRenderer implements BlockEntityRenderer<CompositeBlockEntity> {
    public CompositeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    public void render(CompositeBlockEntity entity, float tickProgress, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        renderParts(entity, pose, buffers, light);
    }

    public void render(CompositeBlockEntity entity, float tickProgress, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay,
                       net.minecraft.world.phys.Vec3 camera) {
        renderParts(entity, pose, buffers, light);
    }

    private static void renderParts(CompositeBlockEntity entity, PoseStack pose,
                                    MultiBufferSource buffers, int light) {
        for (var part : entity.parts().view()) {
            if (part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE
                    || part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY) continue;
            pose.pushPose();
            pose.translate(part.transform().xDouble(), part.transform().yDouble(), part.transform().zDouble());
            pose.scale(1, verticalScale(entity, part), 1);
            if (part.flags() == LavaLoggingReactions.FORMED_ROCK) {
                pose.translate(0.5, 0.5, 0.5);
                pose.scale(0.996f, 0.996f, 0.996f);
                pose.translate(-0.5, -0.5, -0.5);
            }
            pose.rotateAround(Axis.YP.rotationDegrees(part.transform().degrees()), 0.5f, 0, 0.5f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    part.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    public boolean shouldRenderOffScreen() { return true; }
    public boolean shouldRenderOffScreen(CompositeBlockEntity entity) { return true; }

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

    public static void trackBreakProgress(int breakerId, BlockPos pos, int progress) {
        // ponytail: per-part crack overlays omitted; add a dedicated render pass if visual parity is required.
    }
}
