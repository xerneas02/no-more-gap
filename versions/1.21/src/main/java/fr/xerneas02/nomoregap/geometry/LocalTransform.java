package fr.xerneas02.nomoregap.geometry;

public record LocalTransform(FixedPoint x, FixedPoint y, FixedPoint z, int quarterTurns) {
    public static final LocalTransform IDENTITY = new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 0);

    public LocalTransform {
        if (x == null || y == null || z == null) throw new NullPointerException("Translations are required");
        if (quarterTurns < 0 || quarterTurns > 3) throw new IllegalArgumentException("quarterTurns must be 0..3");
    }

    public double xDouble() { return x.asDouble(); }
    public double yDouble() { return y.asDouble(); }
    public double zDouble() { return z.asDouble(); }
    public int degrees() { return quarterTurns * 90; }
}
