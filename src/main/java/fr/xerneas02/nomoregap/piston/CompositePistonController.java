package fr.xerneas02.nomoregap.piston;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.interaction.CompositePartUpdater;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Applies a {@link CompositePistonMovePlan} to the world. The operation is
 * pre-validates its complete plan before changing the world, so a blocked
 * destination leaves the world untouched.
 */
public final class CompositePistonController {
    private CompositePistonController() {
    }

    public static boolean apply(Level level, CompositePistonMovePlan plan) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return false;
        if (plan.partMoves.isEmpty() && plan.vanillaBlocksToPush.isEmpty()
                && plan.blocksToDestroy.isEmpty() && plan.partsToDestroy.isEmpty()) {
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
        for (var entry : currentIds.entrySet()) {
            var source = currentParts.get(entry.getKey());
            for (var id : entry.getValue()) {
                if (source.stream().noneMatch(part -> part.id() == id)) return false;
            }
        }
        // The resolver may traverse proxies, but a composite anchor cannot be
        // moved into one: it has no composite entity to receive the parts.
        // Reject this before the first removal, preserving the all-or-nothing
        // contract for plans whose world state changed since resolution.
        for (var move : plan.partMoves) {
            if (move.toAnchor().equals(move.fromAnchor())) continue;
            var destination = level.getBlockState(move.toAnchor());
            if (destination.isAir() || level.getBlockEntity(move.toAnchor()) instanceof CompositeBlockEntity
                    || currentParts.containsKey(move.toAnchor())) continue;
            return false;
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

        // Destroy first: neighbour updates from moved blocks must not remove a
        // fragile block before destroyBlock gets a chance to create its drops.
        for (var destroy : plan.blocksToDestroy) {
            level.destroyBlock(destroy, true);
        }

        moveCompositeParts(level, plan);
        moveVanillaBlocks(server, plan);

        // ---- 7b. Destroy parts (PushReaction.DESTROY) -----------------------
        for (var part : plan.partsToDestroy) {
            if (level.getBlockEntity(part.anchor()) instanceof CompositeBlockEntity composite) {
                composite.removePart(part.partId());
            }
        }

        notifyMovedComposites(level, plan, currentParts.keySet(), destParts.keySet());
        return true;
    }

    private static void moveCompositeParts(Level level, CompositePistonMovePlan plan) {
        var moveDirection = plan.extending ? plan.direction : plan.direction.getOpposite();
        var orderedMoves = new ArrayList<>(plan.partMoves);
        var movedParts = new HashMap<CompositePistonMovePlan.PartRef, CompositePistonMovePlan.PartRef>();
        var movedHeadOwners = new HashMap<CompositePistonMovePlan.PartRef, Integer>();
        orderedMoves.sort((a, b) -> {
            boolean aWhole = !a.toAnchor().equals(a.fromAnchor());
            boolean bWhole = !b.toAnchor().equals(b.fromAnchor());
            if (aWhole != bWhole) return aWhole ? -1 : 1;
            return Integer.compare(directionStep(plan.direction, b.fromAnchor()),
                    directionStep(plan.direction, a.fromAnchor()));
        });
        for (var move : orderedMoves) {
            var sourceRef = new CompositePistonMovePlan.PartRef(move.fromAnchor(), move.partId());
            if (!(level.getBlockEntity(move.fromAnchor()) instanceof CompositeBlockEntity source)) {
                throw new IllegalStateException("Piston plan source disappeared after validation");
            }
            if (move.toAnchor().equals(move.fromAnchor())) {
                source.beginUpdate();
                try {
                    source.replaceTransform(move.partId(), move.toTransform());
                    source.startPistonMovement(move.partId(), moveDirection);
                } finally {
                    source.endUpdate();
                }
                movedParts.put(sourceRef, sourceRef);
                continue;
            }
            var part = source.parts().find(move.partId()).orElseThrow(
                    () -> new IllegalStateException("Piston part disappeared after validation"));
            int pistonHeadOwner = source.pistonHeadOwner(move.partId());
            source.removePart(move.partId());
            if (source.parts().isEmpty()) level.removeBlock(move.fromAnchor(), false);

            var destination = destinationComposite(level, move.toAnchor());
            destination.beginUpdate();
            PartInstance movedPart;
            try {
                movedPart = destination.addPart(part.state(), move.toTransform(), part.flags());
                destination.startPistonMovement(movedPart.id(), moveDirection);
            } finally {
                destination.endUpdate();
            }
            movedParts.put(sourceRef, new CompositePistonMovePlan.PartRef(move.toAnchor(), movedPart.id()));
            if (pistonHeadOwner >= 0) movedHeadOwners.put(sourceRef, pistonHeadOwner);
        }
        movedHeadOwners.forEach((oldHead, oldPistonId) -> {
            var movedHead = movedParts.get(oldHead);
            var movedPiston = movedParts.get(new CompositePistonMovePlan.PartRef(oldHead.anchor(), oldPistonId));
            if (movedHead != null && movedPiston != null && movedHead.anchor().equals(movedPiston.anchor())
                    && level.getBlockEntity(movedHead.anchor()) instanceof CompositeBlockEntity composite) {
                composite.setPistonHeadOwner(movedHead.partId(), movedPiston.partId());
            }
        });
    }

    private static CompositeBlockEntity destinationComposite(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) return composite;
        if (!level.getBlockState(pos).isAir()) {
            throw new IllegalStateException("Piston destination changed after validation");
        }
        level.setBlock(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) return composite;
        throw new IllegalStateException("Could not create piston move destination");
    }

    private static void moveVanillaBlocks(net.minecraft.server.level.ServerLevel level,
                                          CompositePistonMovePlan plan) {
        var moveDirection = plan.extending ? plan.direction : plan.direction.getOpposite();
        var sources = new ArrayList<>(plan.vanillaBlocksToPush);
        var sourceStates = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var pos : sources) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CompositeBlock)
                    && !(state.getBlock() instanceof fr.xerneas02.nomoregap.block.CompositeProxyBlock)) {
                sourceStates.put(pos, state);
            }
        }
        sources.sort((a, b) -> Integer.compare(directionStep(moveDirection, b), directionStep(moveDirection, a)));
        for (var source : sources) {
            var movedState = sourceStates.get(source);
            if (movedState == null) continue;
            var target = source.relative(moveDirection);
            var movingState = Blocks.MOVING_PISTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.FACING, plan.direction)
                    .setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.TYPE,
                            net.minecraft.world.level.block.state.properties.PistonType.DEFAULT);
            level.setBlock(target, movingState, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_CLIENTS);
            var movingEntity = net.minecraft.world.level.block.piston.MovingPistonBlock.newMovingBlockEntity(
                    target, movingState, movedState, plan.direction, plan.extending, false);
            level.setBlockEntity(movingEntity);
            movingEntity.setChanged();
            level.sendBlockUpdated(target, movingState, movingState, Block.UPDATE_CLIENTS);
            fr.xerneas02.nomoregap.network.MovingPistonNetworking.send(
                    level, target, movedState, plan.direction, plan.extending);
            level.removeBlock(source, false);
        }
    }

    private static void notifyMovedComposites(Level level, CompositePistonMovePlan plan,
                                               java.util.Set<BlockPos> sources, java.util.Set<BlockPos> destinations) {
        for (var pos : sources) {
            if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite && !composite.parts().isEmpty()) {
                composite.refreshProxies();
            }
        }
        for (var pos : destinations) {
            if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) composite.refreshProxies();
        }
        sources.forEach(pos -> CompositePartUpdater.refreshAround(level, pos));
        destinations.forEach(pos -> CompositePartUpdater.refreshAround(level, pos));
        level.updateNeighborsAt(plan.pistonAnchor, level.getBlockState(plan.pistonAnchor).getBlock());
    }

    private static int directionStep(Direction direction, BlockPos pos) {
        return pos.getX() * direction.getStepX() + pos.getY() * direction.getStepY()
                + pos.getZ() * direction.getStepZ();
    }
}
