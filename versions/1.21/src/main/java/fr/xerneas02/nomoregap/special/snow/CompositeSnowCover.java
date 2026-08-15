package fr.xerneas02.nomoregap.special.snow;

public record CompositeSnowCover(int layers) {
    public CompositeSnowCover {
        if (layers < 0 || layers > 8) throw new IllegalArgumentException("Snow layers must be 0..8");
    }
}
