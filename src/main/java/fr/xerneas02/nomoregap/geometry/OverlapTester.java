package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OverlapTester {
    public static final double EPSILON = 1.0e-7;

    private OverlapTester() {}

    public static boolean overlaps(Box a, Box b) {
        return overlap(a.minX, a.maxX, b.minX, b.maxX)
                && overlap(a.minY, a.maxY, b.minY, b.maxY)
                && overlap(a.minZ, a.maxZ, b.minZ, b.maxZ);
    }

    public static boolean overlaps(VoxelShape occupied, VoxelShape part, LocalTransform transform) {
        return Shapes.joinIsNotEmpty(occupied, ShapeTransformer.transform(part, transform), BooleanOp.AND);
    }

    public static boolean staysInsideCell(VoxelShape shape, LocalTransform transform) {
        for (var box : ShapeTransformer.transform(shape, transform).toAabbs()) {
            if (box.minX < -EPSILON || box.minY < -EPSILON || box.minZ < -EPSILON
                    || box.maxX > 1 + EPSILON || box.maxY > 1 + EPSILON || box.maxZ > 1 + EPSILON) return false;
        }
        return true;
    }

    private static boolean overlap(double aMin, double aMax, double bMin, double bMax) {
        return Math.min(aMax, bMax) - Math.max(aMin, bMin) > EPSILON;
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Invalid box");
        }
    }
}
