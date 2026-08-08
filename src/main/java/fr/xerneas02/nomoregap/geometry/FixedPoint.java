package fr.xerneas02.nomoregap.geometry;

import fr.xerneas02.nomoregap.util.NoMoreGapLimits;

/** Fixed-point coordinate rounded to the nearest unit, with ties rounded away from zero. */
public record FixedPoint(int units) {
    public static final int MIN_UNITS = -NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
    /**
     * A composite may span several world cells.  Keeping the transform range tied
     * to the part limit prevents a valid high part from being rejected during
     * serialisation or placement.
     */
    public static final int MAX_UNITS = NoMoreGapLimits.FIXED_UNITS_PER_BLOCK
            * (NoMoreGapLimits.MAX_PARTS_PER_CELL + 1);
    public static final FixedPoint ZERO = new FixedPoint(0);
    public static final FixedPoint HALF_BLOCK = new FixedPoint(128);
    public static final FixedPoint FULL_BLOCK = new FixedPoint(256);

    public FixedPoint {
        if (units < MIN_UNITS || units > MAX_UNITS) throw new IllegalArgumentException("Fixed coordinate out of range: " + units);
    }

    public static FixedPoint fromDouble(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Coordinate must be finite");
        double scaled = value * NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        long rounded = (long) (scaled < 0 ? Math.ceil(scaled - 0.5) : Math.floor(scaled + 0.5));
        if (rounded < MIN_UNITS || rounded > MAX_UNITS) throw new IllegalArgumentException("Fixed coordinate out of range: " + value);
        return new FixedPoint((int) rounded);
    }

    public double asDouble() { return (double) units / NoMoreGapLimits.FIXED_UNITS_PER_BLOCK; }
    public FixedPoint add(FixedPoint other) { return new FixedPoint(Math.addExact(units, other.units)); }
    public FixedPoint subtract(FixedPoint other) { return new FixedPoint(Math.subtractExact(units, other.units)); }
}
