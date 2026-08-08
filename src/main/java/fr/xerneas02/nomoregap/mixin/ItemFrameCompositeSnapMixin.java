package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.geometry.CompositeFaceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aligns item frames with the real support shape, including vanilla thin blocks. */
@Mixin(HangingEntity.class)
abstract class ItemFrameCompositeSnapMixin {
    private static final double FRAME_GAP = 1.0 / 32.0;

    @Inject(method = "recalculateBoundingBox", at = @At("TAIL"))
    private void noMoreGap$snapToSupportFace(CallbackInfo callback) {
        var frame = (HangingEntity) (Object) this;
        if (!(frame instanceof ItemFrame) || frame.getDirection().getAxis().isVertical()) return;
        Direction face = frame.getDirection();
        BlockPos supportPos = frame.getPos().relative(face.getOpposite());
        var surface = CompositeFaceSupport.surfaceBox(frame.level(), supportPos, face);
        if (surface == null) return;

        double x = (surface.minX + surface.maxX) * 0.5, y = (surface.minY + surface.maxY) * 0.5, z = (surface.minZ + surface.maxZ) * 0.5;
        switch (face) {
            case EAST -> x = surface.maxX + FRAME_GAP;
            case WEST -> x = surface.minX - FRAME_GAP;
            case SOUTH -> z = surface.maxZ + FRAME_GAP;
            case NORTH -> z = surface.minZ - FRAME_GAP;
            default -> { return; }
        }
        var current = frame.getBoundingBox().getCenter();
        frame.setPosRaw(x, y, z);
        frame.setBoundingBox(frame.getBoundingBox().move(x - current.x, y - current.y, z - current.z));
    }
}
