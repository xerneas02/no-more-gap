package fr.xerneas02.nomoregap.geometry;

import net.minecraft.core.Direction;

public record SupportSurface(Kind kind, Direction normal, FixedPoint x, FixedPoint y, FixedPoint z) {
    public enum Kind { TOP, BOTTOM, SIDE, INNER }
}
