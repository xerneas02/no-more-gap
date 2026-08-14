package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Translates the state write performed by a vanilla block interaction into a part update. */
public final class CompositeUseContext {
    private static final ThreadLocal<Entry> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Entry> LAST_WRITE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> INTERNAL_WRITE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> REFRESHING = ThreadLocal.withInitial(() -> false);

    private CompositeUseContext() {}

    public static void begin(Level level, BlockPos pos, CompositeBlockEntity composite, int partId) {
        ACTIVE.set(new Entry(level, pos, composite, partId));
    }

    public static void end() {
        ACTIVE.remove();
        LAST_WRITE.remove();
    }

    public static void run(Level level, BlockPos pos, CompositeBlockEntity composite, int partId, Runnable action) {
        begin(level, pos, composite, partId);
        try { action.run(); } finally { end(); }
    }

    public static boolean handleSetBlock(Level level, BlockPos pos, BlockState state) {
        var entry = ACTIVE.get();
        if (INTERNAL_WRITE.get() || entry == null || entry.level != level || !entry.pos.equals(pos)) return false;
        LAST_WRITE.set(entry);
        INTERNAL_WRITE.set(true);
        try {
            if (state.isAir()) entry.composite.removePart(entry.partId);
            else entry.composite.replacePart(entry.partId, state);
        } finally {
            INTERNAL_WRITE.set(false);
        }
        level.updateNeighborsAt(pos, entry.composite.getBlockState().getBlock());
        if (!REFRESHING.get()) {
            REFRESHING.set(true);
            try {
                CompositePartUpdater.refreshAround(level, pos);
            } finally {
                REFRESHING.set(false);
            }
        }
        return true;
    }

    /** Keeps vanilla button delays after their state write has been redirected to a part. */
    public static boolean handleScheduleTick(Level level, BlockPos pos, Block block, int delay) {
        var entry = ACTIVE.get();
        if (entry == null || entry.level != level || !entry.pos.equals(pos)) entry = LAST_WRITE.get();
        if (entry == null || entry.level != level || !entry.pos.equals(pos)
                || entry.composite.parts().find(entry.partId()).isEmpty()) return false;
        var part = entry.composite.parts().find(entry.partId()).orElseThrow();
        if (part.state().getBlock() != block
                || !(block instanceof net.minecraft.world.level.block.ButtonBlock)) return false;
        entry.composite.schedulePart(entry.partId(), level.getGameTime() + delay);
        level.scheduleTick(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE, delay);
        return true;
    }

    private record Entry(Level level, BlockPos pos, CompositeBlockEntity composite, int partId) {}
}
