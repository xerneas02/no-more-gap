package fr.xerneas02.nomoregap.special.fluid;

public record CompositeFluidContent(String fluidId, int fixedVolume) {
    public static final CompositeFluidContent EMPTY = new CompositeFluidContent("minecraft:empty", 0);
}
