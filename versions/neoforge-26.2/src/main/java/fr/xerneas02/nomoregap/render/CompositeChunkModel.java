package fr.xerneas02.nomoregap.render;

import fr.xerneas02.nomoregap.part.PartInstance;

/** NeoForge uses the block-entity renderer until its 26.2 model API stabilizes. */
public final class CompositeChunkModel {
    private CompositeChunkModel() {}
    // ponytail: BER-only rendering is slower; replace with NeoForge chunk-model emission if profiling requires it.
    public static boolean isChunkRendered(PartInstance part) { return false; }
}
