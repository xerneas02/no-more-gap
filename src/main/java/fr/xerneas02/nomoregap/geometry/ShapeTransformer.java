package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Prototype transformer for axis-aligned boxes, quarter turns around Y, and translations. */
public final class ShapeTransformer {
    private ShapeTransformer() {}

    public static VoxelShape transform(VoxelShape shape, LocalTransform transform) {
        VoxelShape result = Shapes.empty();
        for (var box : shape.toAabbs()) {
            double minX = box.minX, minZ = box.minZ, maxX = box.maxX, maxZ = box.maxZ;
            for (int turn = 0; turn < transform.quarterTurns(); turn++) {
                double nextMinX = 1 - maxZ, nextMinZ = minX, nextMaxX = 1 - minZ, nextMaxZ = maxX;
                minX = nextMinX; minZ = nextMinZ; maxX = nextMaxX; maxZ = nextMaxZ;
            }
            result = Shapes.or(result, Shapes.box(minX, box.minY, minZ, maxX, box.maxY, maxZ));
        }
        return result.move(transform.xDouble(), transform.yDouble(), transform.zDouble());
    }
}
