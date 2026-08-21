package fr.xerneas02.nomoregap.piston;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.interaction.CompositePartUpdater;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies a {@link CompositePistonMovePlan} to the world. The operation is
 * transactional with respect to parts: the complete plan is validated before
 * the first mutation, and every cell reservation is checked before anything is
 * written, so a blocked destination leaves the world untouched.
 */
public final class CompositePistonController {
    private CompositePistonController() {
    }

    public static boolean apply(Level level, CompositePistonMovePlan plan) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return false;
        if (plan.partMoves.isEmpty() && plan.vanillaBlocksToPush.isEmpty() && plan.blocksToDestroy.isEmpty()) {
            return true;
        }

        // ---- 1. Pre-validate every destination cell -------------------------
        // Parts that stay inside their own composite (toAnchor == fromAnchor)
        // never conflict at the cell level; their internal transform
        // destinations were already validated by the resolver. Only moves that
        // change anchor, and vanilla block moves, reserve cells here. Multiple
        // parts of the SAME composite moving together to the same anchor are
        // allowed (they form one composite at the destination).
        var destinationOccupants = new HashMap<BlockPos, Object>();
        for (var move : plan.partMoves) {
            if (move.toAnchor().equals(move.fromAnchor())) continue;
            var toCell = move.toAnchor();
            if (!server.isInsideBuildHeight(toCell)) return false;
            var occupant = destinationOccupants.get(toCell);
            if (occupant != null && !occupant.equals(move) && !occupant.equals(move.fromAnchor())) return false;
            destinationOccupants.put(toCell, move.fromAnchor());
        }
        for (var target : plan.vanillaBlocksToPush) {
            if (!server.isInsideBuildHeight(target)) return false;
            var occupant = destinationOccupants.get(target);
            if (occupant != null) return false;
            destinationOccupants.put(target, target);
        }
        for (var destroy : plan.blocksToDestroy) {
            destinationOccupants.remove(destroy);
        }

        // ---- 2. Snapshot of current anchors --------------------------------
        var currentParts = new HashMap<BlockPos, java.util.List<PartInstance>>();
        var currentIds = new HashMap<BlockPos, java.util.Set<Integer>>();
        for (var move : plan.partMoves) {
            var anchor = move.fromAnchor();
            if (!currentParts.containsKey(anchor)) {
                if (!(level.getBlockEntity(anchor) instanceof CompositeBlockEntity composite)) return false;
                currentParts.put(anchor, java.util.List.copyOf(composite.parts().view()));
                currentIds.put(anchor, new java.util.HashSet<>());
            }
            currentIds.get(anchor).add(move.partId());
        }

        // ---- 3. Verify that the destination composite can accept the parts --
        var destParts = new HashMap<BlockPos, java.util.List<PartInstance>>();
        for (var move : plan.partMoves) {
            var dest = move.toAnchor();
            if (destParts.containsKey(dest)) continue;
            var existing = currentParts.get(dest);
            if (existing == null) {
                if (level.getBlockEntity(dest) instanceof CompositeBlockEntity composite) {
                    existing = java.util.List.copyOf(composite.parts().view());
                }
            }
            destParts.put(dest, existing == null ? java.util.List.of() : existing);
        }
        for (var entry : destParts.entrySet()) {
            int count = entry.getValue().size();
            for (var move : plan.partMoves) {
                if (move.toAnchor().equals(entry.getKey())) count++;
            }
            if (count > fr.xerneas02.nomoregap.util.NoMoreGapLimits.MAX_PARTS_PER_CELL) return false;
        }

        // ---- 4. Reserve destination cells -----------------------------------
        var reservationOwners = new HashMap<BlockPos, Object>();
        var provisionalBlocks = new HashMap<BlockPos, Block>();
        for (var move : plan.partMoves) {
            if (move.toAnchor().equals(move.fromAnchor())) continue;
            var dest = move.toAnchor();
            var occupant = reservationOwners.get(dest);
            if (occupant != null && !occupant.equals(move.fromAnchor())) return false;
            reservationOwners.put(dest, move.fromAnchor());
        }
        for (var target : plan.vanillaBlocksToPush) {
            var occupant = reservationOwners.get(target);
            if (occupant != null) return false;
            reservationOwners.put(target, target);
            var state = level.getBlockState(target);
            if (!(state.getBlock() instanceof CompositeBlock)
                    && !(state.getBlock() instanceof fr.xerneas02.nomoregap.block.CompositeProxyBlock)) {
                provisionalBlocks.put(target, state.getBlock());
            }
        }
        for (var destroy : plan.blocksToDestroy) {
            reservationOwners.remove(destroy);
        }

        // ---- 5. Apply part moves -------------------------------------------
        // Apply composite moves furthest-first so a composite arrives in a cell
        // that was just vacated by the next composite in the line. Moves that
        // stay inside their own composite are independent and can run in any
        // order.
        var orderedMoves = new ArrayList<>(plan.partMoves);
        orderedMoves.sort((a, b) -> {
            boolean aWhole = !a.toAnchor().equals(a.fromAnchor());
            boolean bWhole = !b.toAnchor().equals(b.fromAnchor());
            if (aWhole != bWhole) return aWhole ? -1 : 1;
            int da = directionStep(plan.direction, a.fromAnchor());
            int db = directionStep(plan.direction, b.fromAnchor());
            return Integer.compare(db, da);
        });
        for (var move : orderedMoves) {
            var fromAnchor = move.fromAnchor();
            var toAnchor = move.toAnchor();
            if (!(level.getBlockEntity(fromAnchor) instanceof CompositeBlockEntity fromComposite)) continue;
            if (toAnchor.equals(fromAnchor)) {
                // The part stays in the same composite: just update transform.
                fromComposite.replaceTransform(move.partId(), move.toTransform());
                continue;
            }
            var part = fromComposite.parts().find(move.partId()).orElse(null);
            if (part == null) return false;
            // Remove from source.
            fromComposite.removePart(move.partId());
            if (fromComposite.parts().isEmpty()) {
                level.removeBlock(fromAnchor, false);
            }
            // Add to destination.
            CompositeBlockEntity toComposite;
            if (level.getBlockEntity(toAnchor) instanceof CompositeBlockEntity existing) {
                toComposite = existing;
            } else if (level.getBlockState(toAnchor).isAir()) {
                level.setBlock(toAnchor, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE.defaultBlockState(),
                        Block.UPDATE_ALL);
                if (!(level.getBlockEntity(toAnchor) instanceof CompositeBlockEntity created)) return false;
                toComposite = created;
            } else {
                return false;
            }
            toComposite.addPart(part.state(), move.toTransform(), part.flags());
        }

        // ---- 6. Apply vanilla block moves (furthest first) -----------------
        // The plan stores the SOURCE positions of blocks to move (like vanilla
        // PistonStructureResolver.getToPush()). Each block moves one cell in the
        // movement direction: forward when extending, backward when a sticky
        // piston retracts.
        var moveDir = plan.extending ? plan.direction : plan.direction.getOpposite();
        var vanillaSources = new ArrayList<>(plan.vanillaBlocksToPush);
        // Read every source state BEFORE writing anything, so a block that is
        // about to be overwritten is still intact when we snapshot it.
        var sourceStates = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var pos : vanillaSources) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CompositeBlock)
                    && !(state.getBlock() instanceof fr.xerneas02.nomoregap.block.CompositeProxyBlock)) {
                sourceStates.put(pos, state);
            }
        }
        // Move the furthest block first so it lands in air; the nearer blocks
        // then move into the now-vacated cells.
        vanillaSources.sort((a, b) -> {
            int da = directionStep(moveDir, a);
            int db = directionStep(moveDir, b);
            return Integer.compare(db, da);
        });
        for (var source : vanillaSources) {
            var state = sourceStates.get(source);
            if (state == null) continue;
            var target = source.offset(moveDir.getStepX(), moveDir.getStepY(), moveDir.getStepZ());
            // The target must currently be empty (or an already-moved block cell).
            level.setBlock(target, state, Block.UPDATE_ALL);
            level.removeBlock(source, false);
        }

        // ---- 7. Destroy blocks ----------------------------------------------
        for (var destroy : plan.blocksToDestroy) {
            level.destroyBlock(destroy, true);
        }

        // ---- 7b. Destroy parts (PushReaction.DESTROY) -----------------------
        if (!plan.partsToDestroy.isEmpty()) {
            var affectedAnchors = new java.util.HashSet<BlockPos>();
            for (var id : plan.partsToDestroy) {
                for (var move : plan.partMoves) {
                    affectedAnchors.add(move.fromAnchor());
                }
                for (var anchor : affectedAnchors) {
                    if (level.getBlockEntity(anchor) instanceof CompositeBlockEntity composite) {
                        composite.removePart(id);
                    }
                }
            }
        }

        // ---- 8. Notify neighbours -------------------------------------------
        for (var anchor : currentParts.keySet()) {
            if (level.getBlockEntity(anchor) instanceof CompositeBlockEntity composite
                    && !composite.parts().isEmpty()) {
                composite.refreshProxies();
            }
        }
        for (var dest : destParts.keySet()) {
            if (level.getBlockEntity(dest) instanceof CompositeBlockEntity composite) {
                composite.refreshProxies();
            }
        }
        for (var anchor : currentParts.keySet()) {
            CompositePartUpdater.refreshAround(level, anchor);
        }
        for (var dest : destParts.keySet()) {
            CompositePartUpdater.refreshAround(level, dest);
        }
        level.updateNeighborsAt(plan.pistonAnchor, level.getBlockState(plan.pistonAnchor).getBlock());
        return true;
    }

    private static int directionStep(Direction direction, BlockPos pos) {
        return pos.getX() * direction.getStepX() + pos.getY() * direction.getStepY()
                + pos.getZ() * direction.getStepZ();
    }
}
