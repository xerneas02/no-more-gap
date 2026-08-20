package fr.xerneas02.nomoregap.render;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Supplies NeoForge's dispatcher with the complete bounds instead of the source cell only. */
public final class NeoForgeCompositeBlockEntityRenderer extends CompositeBlockEntityRenderer {
    public NeoForgeCompositeBlockEntityRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override public AABB getRenderBoundingBox(CompositeBlockEntity entity) {
        if (entity.getLevel() == null) return new AABB(entity.getBlockPos());
        var shape = entity.geometry(entity.getLevel(), CollisionContext.empty()).selection();
        return shape.isEmpty() ? new AABB(entity.getBlockPos()) : shape.bounds().move(entity.getBlockPos());
    }
}
