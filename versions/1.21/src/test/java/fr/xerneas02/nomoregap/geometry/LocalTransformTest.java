package fr.xerneas02.nomoregap.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalTransformTest {
    @Test void identityAndRotations() {
        assertEquals(0.0, LocalTransform.IDENTITY.xDouble());
        for (int turn = 0; turn < 4; turn++) {
            var transform = new LocalTransform(FixedPoint.HALF_BLOCK, FixedPoint.ZERO, FixedPoint.ZERO, turn);
            assertEquals(turn * 90, transform.degrees());
            assertEquals(0.5, transform.xDouble());
            assertEquals(transform, new LocalTransform(FixedPoint.HALF_BLOCK, FixedPoint.ZERO, FixedPoint.ZERO, turn));
        }
    }

    @Test void rejectsInvalidRotation() {
        assertThrows(IllegalArgumentException.class, () -> new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 4));
        assertThrows(IllegalArgumentException.class, () -> new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, -1));
    }
}
