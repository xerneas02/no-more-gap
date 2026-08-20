package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

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

    /** Shapes that touch exactly on a face, an edge or a corner never overlap. */
    @ParameterizedTest
    @MethodSource("touchingPairs")
    void touchingShapesDoNotOverlap(OverlapTester.Box a, OverlapTester.Box b) {
        assertFalse(OverlapTester.overlaps(a, b), "Touching boxes must not overlap: " + a + " vs " + b);
        assertFalse(OverlapTester.overlaps(b, a), "Overlap must be symmetric");
    }

    static Stream<OverlapTester.Box[]> touchingPairs() {
        return Stream.of(
                // face contact along X
                new OverlapTester.Box[]{box(0, 0.5), box(0.5, 1)},
                // face contact along Y
                new OverlapTester.Box[]{new OverlapTester.Box(0, 0, 0, 1, 0.5, 1), new OverlapTester.Box(0, 0.5, 0, 1, 1, 1)},
                // edge contact
                new OverlapTester.Box[]{new OverlapTester.Box(0, 0, 0, 0.5, 0.5, 0.5), new OverlapTester.Box(0.5, 0.5, 0, 1, 1, 0.5)},
                // corner contact
                new OverlapTester.Box[]{new OverlapTester.Box(0, 0, 0, 0.5, 0.5, 0.5), new OverlapTester.Box(0.5, 0.5, 0.5, 1, 1, 1)}
        );
    }

    @Test
    void rotatedPartsTouchingExactlyDoNotOverlap() {
        var lowerSlab = Block.box(0, 0, 0, 16, 8, 16);
        var torch = Block.box(7, 0, 7, 9, 10, 9);
        // The torch rests exactly on the slab top in every rotation.
        for (int rotation = 0; rotation < 4; rotation++) {
            var transform = new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, rotation);
            assertFalse(OverlapTester.overlaps(lowerSlab, torch, transform),
                    "rotation " + rotation + " must not overlap when touching exactly");
        }
    }

    @Test
    void staysInsideCellRejectsTransformsOutsideTheCell() {
        var slab = Block.box(0, 0, 0, 16, 8, 16);
        assertTrue(OverlapTester.staysInsideCell(slab, LocalTransform.IDENTITY));
        // A slab pushed fully outside the cell along X.
        assertFalse(OverlapTester.staysInsideCell(slab, new LocalTransform(FixedPoint.FULL_BLOCK, FixedPoint.ZERO, FixedPoint.ZERO, 0)));
        // A slab hanging over the cell boundary.
        assertFalse(OverlapTester.staysInsideCell(slab, new LocalTransform(FixedPoint.HALF_BLOCK, FixedPoint.ZERO, FixedPoint.ZERO, 0)));
        // Above the ceiling.
        assertFalse(OverlapTester.staysInsideCell(slab, new LocalTransform(FixedPoint.ZERO, FixedPoint.FULL_BLOCK, FixedPoint.ZERO, 0)));
    }

    @Test
    void overlapInAnyAxisIsDetected() {
        var half = box(0, 0.5);
        assertTrue(OverlapTester.overlaps(half, new OverlapTester.Box(0.25, 0, 0, 0.75, 1, 1)));
        assertTrue(OverlapTester.overlaps(half, new OverlapTester.Box(0, 0.25, 0, 1, 0.75, 1)));
        assertTrue(OverlapTester.overlaps(half, new OverlapTester.Box(0, 0, 0.25, 1, 1, 0.75)));
    }
}
