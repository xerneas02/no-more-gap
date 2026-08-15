package fr.xerneas02.nomoregap.geometry;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Experimental pre-1.0 surface contract. */
public final class SurfaceExtractor {
    private SurfaceExtractor() {}
    public static List<SupportSurface> none() { return List.of(); }

    public static Optional<SupportSurface> topAt(VoxelShape shape, double x, double z) {
        double highest = -1;
        for (var box : shape.toAabbs()) {
            if (x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ && box.maxY > highest) {
                highest = box.maxY;
            }
        }
        if (highest < 0) return Optional.empty();
        return Optional.of(new SupportSurface(SupportSurface.Kind.TOP, Direction.UP,
                FixedPoint.fromDouble(x), FixedPoint.fromDouble(highest), FixedPoint.fromDouble(z)));
    }

    public static Optional<SupportSurface> bottomAt(VoxelShape shape, double x, double z) {
        double lowest = Double.POSITIVE_INFINITY;
        for (var box : shape.toAabbs()) {
            if (x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ && box.minY < lowest) {
                lowest = box.minY;
            }
        }
        if (!Double.isFinite(lowest)) return Optional.empty();
        return Optional.of(new SupportSurface(SupportSurface.Kind.BOTTOM, Direction.DOWN,
                FixedPoint.fromDouble(x), FixedPoint.fromDouble(lowest), FixedPoint.fromDouble(z)));
    }
}
