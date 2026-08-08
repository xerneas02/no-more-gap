package fr.xerneas02.nomoregap.geometry;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Resolves the actual outer shape of a vanilla or composite support cell. */
public final class CompositeFaceSupport {
    private CompositeFaceSupport() {}

    public static Boolean supports(LevelReader level, BlockPos supportPos, Direction face) {
        return surfaceBox(level, supportPos, face) != null;
    }

    public static AABB surfaceBox(LevelReader level, BlockPos supportPos, Direction face) {
        CompositeBlockEntity composite = null;
        BlockPos anchor = supportPos;
        if (level.getBlockEntity(supportPos) instanceof CompositeBlockEntity direct) composite = direct;
        else if (level.getBlockEntity(supportPos) instanceof CompositeProxyBlockEntity proxy
                && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity resolved) {
            composite = resolved;
            anchor = proxy.anchor();
        }
        if (composite == null) {
            var state = level.getBlockState(supportPos);
            if (state.isAir()) return null;
            return state.getShape(level, supportPos, CollisionContext.empty()).toAabbs().stream()
                    .map(box -> box.move(supportPos))
                    .max((left, right) -> Double.compare(faceValue(left, face), faceValue(right, face))).orElse(null);
        }
        int x = supportPos.getX() - anchor.getX(), y = supportPos.getY() - anchor.getY(), z = supportPos.getZ() - anchor.getZ();
        var compositeAnchor = anchor;
        return composite.geometry(level, CollisionContext.empty()).selection().toAabbs().stream()
                .filter(box -> box.maxX > x && box.minX < x + 1 && box.maxY > y && box.minY < y + 1 && box.maxZ > z && box.minZ < z + 1)
                .map(box -> box.move(compositeAnchor))
                .max((left, right) -> Double.compare(faceValue(left, face), faceValue(right, face))).orElse(null);
    }

    private static double faceValue(AABB box, Direction face) {
        return switch (face) {
            case EAST -> box.maxX;
            case WEST -> -box.minX;
            case SOUTH -> box.maxZ;
            case NORTH -> -box.minZ;
            default -> Double.NEGATIVE_INFINITY;
        };
    }
}
