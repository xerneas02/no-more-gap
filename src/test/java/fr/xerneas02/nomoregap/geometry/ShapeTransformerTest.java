package fr.xerneas02.nomoregap.geometry;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Invariant tests for {@link ShapeTransformer}: quarter-turn composition,
 * translation and box rotation semantics.
 */
class ShapeTransformerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final VoxelShape PANEL = Block.box(0, 0, 0, 16, 16, 3); // thin door-like panel
    private static final VoxelShape SLAB = Block.box(0, 0, 0, 16, 8, 16);

    /** Four quarter turns must return to the original geometry. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void fourQuarterTurnsRestoreTheOriginal(int start) {
        var transform = new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, start);
        var rotated = ShapeTransformer.transform(PANEL, transform);
        rotated = ShapeTransformer.transform(rotated, transform);
        rotated = ShapeTransformer.transform(rotated, transform);
        rotated = ShapeTransformer.transform(rotated, transform);
        assertSameBounds(rotated, PANEL, "rotating by 4x" + start + " quarter turns must restore the shape");
    }

    @Test
    void twoHalfTurnsRestoreTheOriginal() {
        var half = new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 2);
        assertSameBounds(ShapeTransformer.transform(ShapeTransformer.transform(PANEL, half), half), PANEL);
    }

    @Test
    void rotatingThenInverseRotatingKeepsBounds() {
        var forward = new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 1);
        var inverse = new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 3);
        var result = ShapeTransformer.transform(ShapeTransformer.transform(SLAB, forward), inverse);
        assertSameBounds(result, SLAB);
    }

    /** A panel at the north edge must end up at the east edge after one quarter turn. */
    @Test
    void quarterTurnRotatesPanelAroundTheCellCenter() {
        var turned = ShapeTransformer.transform(PANEL, new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 1));
        assertEquals(1, turned.toAabbs().size());
        var box = turned.toAabbs().getFirst();
        assertEquals(0.8125, box.minX, 1.0e-7);
        assertEquals(0.0, box.minY, 1.0e-7);
        assertEquals(0.0, box.minZ, 1.0e-7);
        assertEquals(1.0, box.maxX, 1.0e-7);
        assertEquals(1.0, box.maxY, 1.0e-7);
        assertEquals(1.0, box.maxZ, 1.0e-7);
    }

    @Test
    void translationMovesTheShape() {
        var moved = ShapeTransformer.transform(SLAB, new LocalTransform(FixedPoint.FULL_BLOCK, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0));
        var box = moved.toAabbs().getFirst();
        assertEquals(1.0, box.minX, 1.0e-7);
        assertEquals(0.5, box.minY, 1.0e-7);
        assertEquals(1.0, box.maxY, 1.0e-7);
    }

    /** Translations can push geometry outside the anchor cell; that is legal for composites. */
    @Test
    void supportsOffsetsOutsideTheCell() {
        var moved = ShapeTransformer.transform(SLAB,
                new LocalTransform(new FixedPoint(384), new FixedPoint(512), new FixedPoint(-256), 0));
        var box = moved.toAabbs().getFirst();
        assertEquals(1.5, box.minX, 1.0e-7);
        assertEquals(2.0, box.minY, 1.0e-7);
        assertEquals(-1.0, box.minZ, 1.0e-7);
    }

    @Test
    void identityLeavesTheShapeUntouched() {
        assertSameBounds(ShapeTransformer.transform(SLAB, LocalTransform.IDENTITY), SLAB);
    }

    @Test
    void emptyShapeStaysEmpty() {
        assertTrue(ShapeTransformer.transform(Shapes.empty(), LocalTransform.IDENTITY).isEmpty());
        assertTrue(ShapeTransformer.transform(Shapes.empty(),
                new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 2)).isEmpty());
    }

    private static void assertSameBounds(VoxelShape actual, VoxelShape expected) {
        assertSameBounds(actual, expected, "shapes must have identical bounds");
    }

    private static void assertSameBounds(VoxelShape actual, VoxelShape expected, String message) {
        assertEquals(expected.toAabbs().size(), actual.toAabbs().size(), message);
        var expectedBoxes = expected.toAabbs().stream().sorted(
                java.util.Comparator.comparingDouble(b -> b.minX + b.minY * 2 + b.minZ * 4)).toList();
        var actualBoxes = actual.toAabbs().stream().sorted(
                java.util.Comparator.comparingDouble(b -> b.minX + b.minY * 2 + b.minZ * 4)).toList();
        for (int i = 0; i < expectedBoxes.size(); i++) {
            assertEquals(expectedBoxes.get(i).minX, actualBoxes.get(i).minX, 1.0e-7, message);
            assertEquals(expectedBoxes.get(i).minY, actualBoxes.get(i).minY, 1.0e-7, message);
            assertEquals(expectedBoxes.get(i).minZ, actualBoxes.get(i).minZ, 1.0e-7, message);
            assertEquals(expectedBoxes.get(i).maxX, actualBoxes.get(i).maxX, 1.0e-7, message);
            assertEquals(expectedBoxes.get(i).maxY, actualBoxes.get(i).maxY, 1.0e-7, message);
            assertEquals(expectedBoxes.get(i).maxZ, actualBoxes.get(i).maxZ, 1.0e-7, message);
        }
    }
}
