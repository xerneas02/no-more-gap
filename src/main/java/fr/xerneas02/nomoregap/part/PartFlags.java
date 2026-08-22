package fr.xerneas02.nomoregap.part;

/**
 * Flag bits stored in {@link PartInstance#flags()}. The upper bits are reserved
 * for internal markers; lower bits are free for future gameplay flags.
 */
public final class PartFlags {
    /** The part is a piston head, created when a composite piston extends. */
    public static final int PISTON_HEAD = 1 << 29;
    /** Temporarily rendered by the block-entity renderer during a piston move. */
    public static final int PISTON_MOVING = 1 << 28;

    private PartFlags() {}
}
