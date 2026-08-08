package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Translates the state write performed by a vanilla block interaction into a part update. */
public final class CompositeUseContext {
    private static final ThreadLocal<Entry> ACTIVE = new ThreadLocal<>();

    private CompositeUseContext() {}

    public static void begin(Level level, BlockPos pos, CompositeBlockEntity composite, int partId) {
        ACTIVE.set(new Entry(level, pos, composite, partId));
    }

    public static void end() { ACTIVE.remove(); }

    public static void run(Level level, BlockPos pos, CompositeBlockEntity composite, int partId, Runnable action) {
        begin(level, pos, composite, partId);
        try { action.run(); } finally { end(); }
    }

    public static boolean handleSetBlock(Level level, BlockPos pos, BlockState state) {
        var entry = ACTIVE.get();
        if (entry == null || entry.level != level || !entry.pos.equals(pos)) return false;
        ACTIVE.remove(); // CompositeBlockEntity updates its own shell state through Level#setBlock.
        if (state.isAir()) entry.composite.removePart(entry.partId);
        else entry.composite.replacePart(entry.partId, state);
        level.updateNeighborsAt(pos, entry.composite.getBlockState().getBlock());
        return true;
    }

    private record Entry(Level level, BlockPos pos, CompositeBlockEntity composite, int partId) {}
}
