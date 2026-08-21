package fr.xerneas02.nomoregap.part;

/**
 * Flag bits stored in {@link PartInstance#flags()}. The upper bits are reserved
 * for internal markers; lower bits are free for future gameplay flags.
 */
public final class PartFlags {
    /** The part is a piston head, created when a composite piston extends. */
    public static final int PISTON_HEAD = 1 << 29;

    private PartFlags() {}
}
