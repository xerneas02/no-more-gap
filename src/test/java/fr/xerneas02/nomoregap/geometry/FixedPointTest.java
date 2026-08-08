package fr.xerneas02.nomoregap.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedPointTest {
    @Test void constantsAndConversion() {
        assertEquals(0, FixedPoint.ZERO.units());
        assertEquals(0.5, FixedPoint.HALF_BLOCK.asDouble());
        assertEquals(1.0, FixedPoint.FULL_BLOCK.asDouble());
        assertEquals(1, FixedPoint.fromDouble(1.0 / 256).units());
        assertEquals(-1, FixedPoint.fromDouble(-1.0 / 256).units());
        assertEquals(1, FixedPoint.fromDouble(0.5 / 256).units());
        assertEquals(-1, FixedPoint.fromDouble(-0.5 / 256).units());
    }

    @Test void validatesRangeAndArithmetic() {
        assertEquals(new FixedPoint(2), new FixedPoint(1).add(new FixedPoint(1)));
        assertEquals(new FixedPoint(-1), FixedPoint.ZERO.subtract(new FixedPoint(1)));
        assertDoesNotThrow(() -> new FixedPoint(FixedPoint.MIN_UNITS));
        assertDoesNotThrow(() -> new FixedPoint(FixedPoint.MAX_UNITS));
        assertEquals(FixedPoint.MIN_UNITS, FixedPoint.fromDouble(-1).units());
        assertThrows(IllegalArgumentException.class, () -> new FixedPoint(FixedPoint.MAX_UNITS + 1));
        assertThrows(IllegalArgumentException.class, () -> FixedPoint.FULL_BLOCK.add(FixedPoint.FULL_BLOCK).add(new FixedPoint(1)));
        assertThrows(IllegalArgumentException.class, () -> FixedPoint.fromDouble(Double.NaN));
    }
}
