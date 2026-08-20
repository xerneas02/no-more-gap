package fr.xerneas02.nomoregap.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.concurrent.atomic.LongAdder;

/** Supplies NeoForge's dispatcher with the complete bounds instead of the source cell only. */
public final class NeoForgeCompositeBlockEntityRenderer extends CompositeBlockEntityRenderer {
    private static final LongAdder SHOULD_RENDER_CALLS = new LongAdder();
    private static final LongAdder SHOULD_RENDER_TRUE = new LongAdder();
    private static final LongAdder SUBMIT_CALLS = new LongAdder();
    private static long nextLogAt;

    public NeoForgeCompositeBlockEntityRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override public boolean shouldRender(CompositeBlockEntity entity, Vec3 camera) {
        SHOULD_RENDER_CALLS.increment();
        boolean result = super.shouldRender(entity, camera);
        if (result) SHOULD_RENDER_TRUE.increment();
        logDiagnostics();
        return result;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        SUBMIT_CALLS.increment();
        super.submit(state, pose, collector, camera);
    }

    @Override public AABB getRenderBoundingBox(CompositeBlockEntity entity) {
        if (entity.getLevel() == null) return new AABB(entity.getBlockPos());
        var shape = entity.geometry(entity.getLevel(), CollisionContext.empty()).selection();
        return shape.isEmpty() ? new AABB(entity.getBlockPos()) : shape.bounds().move(entity.getBlockPos());
    }

    private static void logDiagnostics() {
        long now = System.currentTimeMillis();
        if (now < nextLogAt) return;
        nextLogAt = now + 5_000;
        NoMoreGap.LOGGER.info("NeoForge render diagnostics: ber{{checks={}, accepted={}, submits={}}}, {}",
                SHOULD_RENDER_CALLS.sumThenReset(), SHOULD_RENDER_TRUE.sumThenReset(), SUBMIT_CALLS.sumThenReset(),
                CompositeChunkModel.diagnostics());
    }
}
