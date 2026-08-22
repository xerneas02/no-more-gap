package fr.xerneas02.nomoregap.piston;

import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of {@link CompositePistonResolver}. Records every part to
 * move across the whole piston activation, together with the vanilla blocks
 * that must be pushed or destroyed in the same operation, and the final
 * destination of the piston part itself.
 */
public final class CompositePistonMovePlan {
    public record PartMove(int partId, BlockPos fromAnchor, LocalTransform fromTransform,
                           BlockPos toAnchor, LocalTransform toTransform) {
    }
    public record PartRef(BlockPos anchor, int partId) {
    }

    public final BlockPos pistonAnchor;
    public final int pistonPartId;
    public final Direction direction;
    public final boolean extending;
    public final boolean sticky;
    public final boolean blocked;
    public final List<PartMove> partMoves;
    public final List<BlockPos> vanillaBlocksToPush;
    public final List<BlockPos> blocksToDestroy;
    public final List<PartRef> partsToDestroy;
    public final Map<BlockPos, Integer> toPushIndex;
    public final BlockPos pistonDestination;

    CompositePistonMovePlan(BlockPos pistonAnchor, int pistonPartId, Direction direction, boolean extending,
                            boolean sticky, List<PartMove> partMoves, List<BlockPos> vanillaBlocksToPush,
                            List<BlockPos> blocksToDestroy, BlockPos pistonDestination) {
        this(pistonAnchor, pistonPartId, direction, extending, sticky, false, partMoves, vanillaBlocksToPush,
                blocksToDestroy, List.of(), pistonDestination);
    }

    CompositePistonMovePlan(BlockPos pistonAnchor, int pistonPartId, Direction direction, boolean extending,
                            boolean sticky, boolean blocked, List<PartMove> partMoves,
                            List<BlockPos> vanillaBlocksToPush, List<BlockPos> blocksToDestroy,
                            BlockPos pistonDestination) {
        this(pistonAnchor, pistonPartId, direction, extending, sticky, blocked, partMoves, vanillaBlocksToPush,
                blocksToDestroy, List.of(), pistonDestination);
    }

    CompositePistonMovePlan(BlockPos pistonAnchor, int pistonPartId, Direction direction, boolean extending,
                            boolean sticky, boolean blocked, List<PartMove> partMoves,
                            List<BlockPos> vanillaBlocksToPush, List<BlockPos> blocksToDestroy,
                            List<PartRef> partsToDestroy, BlockPos pistonDestination) {
        this.pistonAnchor = pistonAnchor;
        this.pistonPartId = pistonPartId;
        this.direction = direction;
        this.extending = extending;
        this.sticky = sticky;
        this.blocked = blocked;
        this.partMoves = List.copyOf(partMoves);
        this.vanillaBlocksToPush = List.copyOf(vanillaBlocksToPush);
        this.blocksToDestroy = List.copyOf(blocksToDestroy);
        this.partsToDestroy = List.copyOf(partsToDestroy);
        this.pistonDestination = pistonDestination;
        var index = new HashMap<BlockPos, Integer>();
        for (int i = 0; i < this.vanillaBlocksToPush.size(); i++) {
            index.put(this.vanillaBlocksToPush.get(i), i);
        }
        this.toPushIndex = Collections.unmodifiableMap(index);
    }

    public boolean hasMoves() {
        return !partMoves.isEmpty() || !vanillaBlocksToPush.isEmpty();
    }

    public boolean isPartMoved(int partId) {
        return partMoves.stream().anyMatch(move -> move.partId() == partId);
    }

    public boolean isAtDestination(BlockPos pos) {
        return vanillaBlocksToPush.contains(pos);
    }

    /** An empty, blocked plan: the piston must not extend. */
    public static CompositePistonMovePlan empty(BlockPos pistonAnchor, int pistonPartId, Direction direction,
                                                boolean extending, boolean sticky) {
        return new CompositePistonMovePlan(pistonAnchor, pistonPartId, direction, extending, sticky, true,
                List.of(), List.of(), List.of(), List.of(),
                pistonAnchor.offset(direction.getStepX(), direction.getStepY(), direction.getStepZ()));
    }
}
