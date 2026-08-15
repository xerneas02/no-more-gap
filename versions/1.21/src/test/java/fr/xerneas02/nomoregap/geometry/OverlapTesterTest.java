package fr.xerneas02.nomoregap.geometry;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.shapes.Shapes;

import static org.junit.jupiter.api.Assertions.*;

class OverlapTesterTest {
    private static OverlapTester.Box box(double min, double max) { return new OverlapTester.Box(min, min, min, max, max, max); }

    @Test void distinguishesSeparationContactAndOverlap() {
        assertFalse(OverlapTester.overlaps(box(0, 0.4), box(0.6, 1)));
        assertFalse(OverlapTester.overlaps(box(0, 0.5), box(0.5, 1)));
        assertTrue(OverlapTester.overlaps(box(0, 0.6), box(0.5, 1)));
    }

    @Test void appliesToleranceAndHandlesEmptyBox() {
        assertFalse(OverlapTester.overlaps(box(0, 0), box(0, 1)));
        assertFalse(OverlapTester.overlaps(box(0, 0.5 + OverlapTester.EPSILON / 2), box(0.5, 1)));
        assertFalse(OverlapTester.overlaps(box(0, 0.5 + OverlapTester.EPSILON), box(0.5, 1)));
        assertTrue(OverlapTester.overlaps(box(0, 0.5 + OverlapTester.EPSILON * 2), box(0.5, 1)));
        assertThrows(IllegalArgumentException.class, () -> new OverlapTester.Box(1, 0, 0, 0, 1, 1));
    }

    @Test void checksTransformedVoxelShape() {
        var lowerSlab = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 8, 16);
        var torch = net.minecraft.world.level.block.Block.box(7, 0, 7, 9, 10, 9);
        assertFalse(OverlapTester.overlaps(lowerSlab, torch,
                new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0)));
        assertTrue(OverlapTester.overlaps(lowerSlab, torch, LocalTransform.IDENTITY));
        assertTrue(OverlapTester.staysInsideCell(lowerSlab,
                new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0)));
        assertFalse(OverlapTester.staysInsideCell(Shapes.block(),
                new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0)));
    }
}
