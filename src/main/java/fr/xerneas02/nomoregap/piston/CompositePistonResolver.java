package fr.xerneas02.nomoregap.piston;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.CompositeProxyBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.OverlapTester;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves which parts and which vanilla cells move when a composite-contained
 * piston fires. All vanilla piston limitations are preserved:
 *
 * <ul>
 *   <li>the 12-logical-block push limit (a part counts as one logical block);</li>
 *   <li>immovable blocks ({@code PushReaction.BLOCK}), breakable blocks
 *       ({@code DESTROY}) and {@code PUSH_ONLY}/{@code IGNORE} reactions;</li>
 *   <li>world height, void and border checks;</li>
 *   <li>normal vs sticky pistons (sticky pulls adjacent parts/blocks; normal
 *       pistons never pull);</li>
 *   <li>slime/honey branch propagation through adjacent movable blocks.</li>
 * </ul>
 *
 * <p>The resolver is a pure computation: it never mutates the world. Callers
 * validate first and only then apply {@link CompositePistonController}.
 */
public final class CompositePistonResolver {
    /** Matches vanilla {@code PistonStructureResolver#MAX_PUSH_DEPTH}. */
    public static final int MAX_PUSH_DEPTH = 12;

    private static final int UNIT = NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;

    private final Level level;
    private final BlockPos pistonAnchor;
    private final int pistonPartId;
    private final PartInstance pistonPart;
    private final Direction direction;
    private final boolean extending;
    private final boolean sticky;
    private final Direction moveDir;
    private final Direction pushDirection;
    private final List<CompositePistonMovePlan.PartMove> partMoves = new ArrayList<>();
    private final List<BlockPos> vanillaBlocksToPush = new ArrayList<>();
    private final List<BlockPos> blocksToDestroy = new ArrayList<>();
    private final List<Integer> partsToDestroy = new ArrayList<>();
    private final Set<BlockPos> vanillaSeen = new HashSet<>();
    private final Set<PartRef> partSeen = new HashSet<>();
    private final Set<BlockPos> blockedCells = new HashSet<>();
    /** Anchors of composites that are being moved as a whole unit by this piston. */
    private final Set<BlockPos> movingComposites = new HashSet<>();

    private record PartRef(BlockPos anchor, int partId) {
    }

    public CompositePistonResolver(Level level, BlockPos pistonAnchor, int pistonPartId,
                                   PartInstance pistonPart, Direction direction,
                                   boolean extending, boolean sticky) {
        this.level = level;
        this.pistonAnchor = pistonAnchor;
        this.pistonPartId = pistonPartId;
        this.pistonPart = pistonPart;
        this.direction = direction;
        this.extending = extending;
        this.sticky = sticky;
        // Extension pushes blocks forward (+direction); a sticky retraction
        // pulls the block in front of the head back toward the piston
        // (-direction).
        this.moveDir = extending ? direction : direction.getOpposite();
        this.pushDirection = direction;
    }

    public CompositePistonMovePlan resolve() {
        partMoves.clear();
        vanillaBlocksToPush.clear();
        blocksToDestroy.clear();
        partsToDestroy.clear();
        vanillaSeen.clear();
        partSeen.clear();
        blockedCells.clear();

        var pistonPos = partWorldPos(pistonAnchor, pistonPart.transform());
        BlockPos pistonDest = pistonPos.offset(direction.getStepX(), direction.getStepY(), direction.getStepZ());

        if (extending) {
            // Vanilla pushes the line starting from the cell the head will
            // occupy (pistonPos + direction).
            if (!pushBlockLine(pistonDest)) {
                return CompositePistonMovePlan.empty(pistonAnchor, pistonPartId, direction, extending, sticky);
            }
        } else if (sticky) {
            // A sticky retraction pulls the block directly in front of the head
            // (pistonPos + 2*direction) back toward the piston.
            var headCell = pistonDest;
            var pullFrom = headCell.offset(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            if (!pullBlock(pullFrom)) {
                return CompositePistonMovePlan.empty(pistonAnchor, pistonPartId, direction, extending, sticky);
            }
        }
        // Slime/honey branch propagation applies both when pushing and when a
        // sticky piston pulls: every collected cell that is sticky drags
        // adjacent movable cells with it.
        if (extending || sticky) {
            if (!addBranchingBlocks()) {
                return CompositePistonMovePlan.empty(pistonAnchor, pistonPartId, direction, extending, sticky);
            }
        }
        return new CompositePistonMovePlan(pistonAnchor, pistonPartId, direction, extending, sticky, false,
                partMoves, vanillaBlocksToPush, blocksToDestroy, partsToDestroy, pistonDest);
    }

    /**
     * Returns {@code false} when the move would fail (immovable block, depth
     * limit or blocked destination), leaving {@code this} untouched so callers
     * can abort. Walks the whole line like vanilla {@code addBlockLine}: each
     * cell in the push direction is checked; composite cells contribute every
     * part whose world box touches the cell.
     */
    private boolean pushBlockLine(BlockPos start) {
        // The piston head (offset on Y inside its composite) is face-to-face
        // with the cell in front AND the cell above it. Push both levels, each
        // with its own full horizontal line, so blocks stacked in front of the
        // head are pushed exactly like the bottom line.
        if (!pushLine(start)) return false;
        var above = start.offset(0, 1, 0);
        if (!level.getBlockState(above).isAir() && !vanillaSeen.contains(above)
                && partSeen.stream().noneMatch(ref -> ref.anchor().equals(above))) {
            if (!pushLine(above)) return false;
        }
        return true;
    }

    /** Pushes a full horizontal line of cells starting at {@code start}, like vanilla {@code addBlockLine}. */
    private boolean pushLine(BlockPos start) {
        var pos = start;
        for (int depth = 0; depth <= MAX_PUSH_DEPTH; depth++) {
            var cell = level.getBlockState(pos);
            if (cell.isAir()) {
                // Destination is free; line is valid.
                return true;
            }
            if (!pushable(cell, pos, moveDir)) return false;
            if (isCompositeAnchor(cell)) {
                var anchor = level.getBlockEntity(pos);
                if (anchor instanceof CompositeBlockEntity composite) {
                    if (composite.parts().isEmpty()) {
                        // Treat as air.
                        return true;
                    }
                    // Collect every part whose world cell touches this cell.
                    if (!collectPartsInCell(composite, pos)) return false;
                    if (totalMoves() > MAX_PUSH_DEPTH) return false;
                    pos = pos.relative(moveDir);
                    continue;
                }
                // Anchor with no entity: treat as immovable.
                return false;
            }
            if (isCompositeProxy(cell)) {
                var proxy = level.getBlockEntity(pos);
                if (proxy instanceof CompositeProxyBlockEntity compositeProxy) {
                    var anchorPos = compositeProxy.anchor();
                    var anchorState = level.getBlockState(anchorPos);
                    if (isCompositeAnchor(anchorState)
                            && level.getBlockEntity(anchorPos) instanceof CompositeBlockEntity composite) {
                        if (!collectPartsInCell(composite, pos)) return false;
                        if (totalMoves() > MAX_PUSH_DEPTH) return false;
                        pos = pos.relative(moveDir);
                        continue;
                    }
                }
                return false;
            }
            // Plain vanilla block.
            if (!pushVanilla(pos, cell)) return false;
            if (totalMoves() > MAX_PUSH_DEPTH) return false;
            pos = pos.relative(moveDir);
        }
        return false;
    }

    /** Collects every part of a composite whose world geometry occupies the given world cell. */
    private boolean collectPartsInCell(CompositeBlockEntity composite, BlockPos cell) {
        var anchor = composite.getBlockPos();
        // The piston's own composite is never moved as a whole unit: only the
        // parts that actually touch the cell shift inside it.
        if (anchor.equals(pistonAnchor)) {
            for (var part : composite.parts().view()) {
                var partRef = new PartRef(anchor, part.id());
                if (partSeen.contains(partRef)) continue;
                if (!partOccursInCell(composite, part, cell)) continue;
                if (!tryCollectPart(composite, part)) return false;
            }
            return true;
        }
        // Check whether any part actually occupies the cell before deciding to
        // move the whole composite.
        boolean touches = false;
        for (var part : composite.parts().view()) {
            if (partOccursInCell(composite, part, cell)) {
                touches = true;
                break;
            }
        }
        if (!touches) return true;
        // A composite moves as a whole unit: every part shifts together. If the
        // anchor composite was already collected, skip.
        if (!movingComposites.add(anchor)) return true;
        for (var part : composite.parts().view()) {
            if (!tryCollectPart(composite, part)) return false;
        }
        return true;
    }

    /** True when any collision box of the part, in world coordinates, intersects the cell. */
    private boolean partOccursInCell(CompositeBlockEntity composite, PartInstance part, BlockPos cell) {
        var shape = part.state().getCollisionShape(level, composite.getBlockPos(), CollisionContext.empty());
        var transformed = ShapeTransformer.transform(shape, part.transform());
        var anchor = composite.getBlockPos();
        for (var box : transformed.toAabbs()) {
            var world = box.move(anchor.getX(), anchor.getY(), anchor.getZ());
            if (world.intersects(cellBox(cell))) return true;
        }
        return false;
    }

    private int totalMoves() {
        return partMoves.size() + vanillaBlocksToPush.size();
    }

    /**
     * Vanilla {@code PistonStructureResolver.addBranchingBlocks}: after the
     * push line is resolved, every collected cell that is sticky (slime or
     * honey) drags adjacent (6 faces) movable cells, provided they can stick to
     * each other. Returns false when a branch is blocked.
     */
    private boolean addBranchingBlocks() {
        // Iterate until fixpoint: new sticky blocks may add more branches.
        var branches = new ArrayList<BlockPos>();
        branches.addAll(vanillaBlocksToPush);
        for (var move : partMoves) {
            branches.add(partWorldPos(move.fromAnchor(), move.toTransform()));
        }
        for (int i = 0; i < branches.size(); i++) {
            var pos = branches.get(i);
            var state = stateAtCell(pos);
            if (!isSticky(state)) continue;
            for (Direction dir : Direction.values()) {
                var neighbor = pos.relative(dir);
                if (vanillaSeen.contains(neighbor)) continue;
                if (partSeen.stream().anyMatch(ref -> ref.anchor().equals(neighbor))) continue;
                var neighborState = level.getBlockState(neighbor);
                if (neighborState.isAir()) continue;
                if (!canStickToEachOther(state, stateAtCell(neighbor))) continue;
                if (isCompositeAnchor(neighborState) || isCompositeProxy(neighborState)) {
                    if (isCompositeAnchor(neighborState)) {
                        if (level.getBlockEntity(neighbor) instanceof CompositeBlockEntity composite) {
                            if (!collectPartsInCell(composite, neighbor)) return false;
                        }
                    } else if (level.getBlockEntity(neighbor) instanceof CompositeProxyBlockEntity proxy
                            && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite) {
                        if (!collectPartsInCell(composite, neighbor)) return false;
                    }
                } else {
                    if (!pushable(neighborState, neighbor, moveDir)) return false;
                    if (!pushVanilla(neighbor, neighborState)) return false;
                }
                if (totalMoves() > MAX_PUSH_DEPTH) return false;
                if (isSticky(stateAtCell(neighbor))) branches.add(neighbor);
            }
        }
        return true;
    }

    /** Resolves the effective block state of a cell, unwrapping composite anchors/proxies. */
    private BlockState stateAtCell(BlockPos cell) {
        var state = level.getBlockState(cell);
        if (isCompositeAnchor(state)) {
            if (level.getBlockEntity(cell) instanceof CompositeBlockEntity composite
                    && !composite.parts().isEmpty()) {
                return composite.parts().view().getFirst().state();
            }
            return state;
        }
        if (isCompositeProxy(state)) {
            if (level.getBlockEntity(cell) instanceof CompositeProxyBlockEntity proxy
                    && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite
                    && !composite.parts().isEmpty()) {
                return composite.parts().view().getFirst().state();
            }
            return state;
        }
        return state;
    }

    /** Vanilla {@code PistonStructureResolver.isSticky}: slime and honey blocks. */
    private static boolean isSticky(BlockState state) {
        return state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK);
    }

    /** Vanilla {@code PistonStructureResolver.canStickToEachOther}. */
    private static boolean canStickToEachOther(BlockState a, BlockState b) {
        // Honey and slime do not stick to each other.
        if (a.is(Blocks.HONEY_BLOCK) && b.is(Blocks.SLIME_BLOCK)) return false;
        if (a.is(Blocks.SLIME_BLOCK) && b.is(Blocks.HONEY_BLOCK)) return false;
        // Sticky if either side is sticky.
        return isSticky(a) || isSticky(b);
    }

    /** Pulls the block/part in the cell in front of the head back toward the piston. */
    private boolean pullBlock(BlockPos pos) {
        if (!pullCell(pos)) return false;
        // Mirror the push: the head is offset on Y, so the cell above the one
        // in front of the head is also pulled.
        var above = pos.offset(0, 1, 0);
        if (!level.getBlockState(above).isAir() && !vanillaSeen.contains(above)
                && partSeen.stream().noneMatch(ref -> ref.anchor().equals(above))) {
            return pullCell(above);
        }
        return true;
    }

    /** Pulls the single block/composite at a cell. Immovable or non-movable blocks are simply ignored (not pulled, and not blocking). */
    private boolean pullCell(BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (isCompositeAnchor(state)) {
            if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) {
                if (composite.parts().isEmpty()) return true;
                if (!collectPartsInCell(composite, pos)) return false;
                return true;
            }
            return false;
        }
        if (isCompositeProxy(state)) {
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity compositeProxy) {
                var anchorPos = compositeProxy.anchor();
                if (level.getBlockEntity(anchorPos) instanceof CompositeBlockEntity composite) {
                    if (!collectPartsInCell(composite, pos)) return false;
                    return true;
                }
            }
            return false;
        }
        // A retraction must never fail because of an immovable block: obsidian,
        // bedrock or any non-movable block in front of the head is simply left
        // in place (ignored) while the sticky piston still retracts its head.
        if (!pushable(state, pos, moveDir)) return true;
        if (!pushVanilla(pos, state)) return true;
        return true;
    }

    private boolean tryCollectPart(CompositeBlockEntity composite, PartInstance part) {
        var ref = new PartRef(composite.getBlockPos(), part.id());
        if (partSeen.contains(ref)) return true;
        // The piston part itself never moves; it stays in its anchor.
        if (part.id() == pistonPartId && composite.getBlockPos().equals(pistonAnchor)) return true;
        // Piston head parts are internal to the extending piston; they are
        // never pushed as separate blocks.
        if ((part.flags() & fr.xerneas02.nomoregap.part.PartFlags.PISTON_HEAD) != 0) return true;
        // Apply the vanilla push reaction of the part's block.
        var reaction = part.state().getPistonPushReaction();
        if (reaction == PushReaction.BLOCK) return false;
        if (reaction == PushReaction.DESTROY) {
            // Vanilla destroys the block and continues the line.
            partSeen.add(ref);
            partsToDestroy.add(part.id());
            return true;
        }
        if (reaction == PushReaction.IGNORE || reaction == PushReaction.PUSH_ONLY) return false;
        // Vanilla also rejects unbreakable blocks (destroy speed -1), such as
        // bedrock, barriers and the reinforced deepslate.
        if (part.state().getDestroySpeed(level, composite.getBlockPos()) == -1.0F) return false;

        // Move the part one block in the movement direction. When the whole
        // composite is being moved (movingComposites), the part changes anchor
        // and keeps its local transform: the entire composite shifts one cell.
        // Otherwise the part just moves inside its own composite and only its
        // local transform changes.
        var fromAnchor = composite.getBlockPos();
        // The piston's own composite never moves as a whole unit: only the
        // parts in front of the head shift inside it. Any OTHER composite whose
        // cell is touched moves entirely to a new anchor.
        boolean wholeComposite = movingComposites.contains(fromAnchor) && !fromAnchor.equals(pistonAnchor);
        var target = wholeComposite
                ? part.transform()
                : new LocalTransform(
                        part.transform().x().add(FixedPoint.fromDouble(moveDir.getStepX())),
                        part.transform().y().add(FixedPoint.fromDouble(moveDir.getStepY())),
                        part.transform().z().add(FixedPoint.fromDouble(moveDir.getStepZ())),
                        part.transform().quarterTurns());
        var toAnchor = wholeComposite
                ? fromAnchor.offset(moveDir.getStepX(), moveDir.getStepY(), moveDir.getStepZ())
                : fromAnchor;
        // The destination cell must be free (or part of the moving line).
        var targetCell = partWorldPos(toAnchor, target);
        if (!destinationFree(targetCell)) return false;
        partSeen.add(ref);
        partMoves.add(new CompositePistonMovePlan.PartMove(part.id(), fromAnchor, part.transform(), toAnchor, target));
        return true;
    }

    /**
     * A destination cell is free when it is air, when it is a plain vanilla
     * block that is part of this push line, or when it is a composite/proxy
     * cell: the push line will either move that composite away or it is a
     * destination of another part of the same line. The controller
     * re-validates every destination atomically before mutating anything.
     */
    private boolean destinationFree(BlockPos cell) {
        var state = level.getBlockState(cell);
        if (state.isAir()) return true;
        if (isCompositeProxy(state) || isCompositeAnchor(state)) return true;
        // Plain vanilla block: it can only be displaced if it is part of the
        // vanilla push list of this same piston.
        return vanillaSeen.contains(cell);
    }

    /** Records a plain vanilla block at {@code pos} to be moved by this piston; returns false if it cannot move. */
    private boolean pushVanilla(BlockPos pos, BlockState state) {
        if (vanillaSeen.contains(pos)) return true;
        var reaction = state.getPistonPushReaction();
        if (reaction == PushReaction.BLOCK) return false;
        if (reaction == PushReaction.DESTROY) {
            blocksToDestroy.add(pos);
            vanillaSeen.add(pos);
            return true;
        }
        if (reaction == PushReaction.IGNORE || reaction == PushReaction.PUSH_ONLY) return false;
        // Vanilla also rejects unbreakable blocks (destroy speed -1): bedrock,
        // obsidian, barriers and the reinforced deepslate must never be moved,
        // even by a retracting sticky piston.
        if (state.getDestroySpeed(level, pos) == -1.0F) return false;
        // Blocks carrying a block entity are not movable by vanilla pistons.
        if (state.hasBlockEntity()) return false;
        vanillaSeen.add(pos);
        vanillaBlocksToPush.add(pos);
        return true;
    }
    private boolean pushable(BlockState state, BlockPos pos, Direction moveDir) {
        if (isCompositeAnchor(state) || isCompositeProxy(state)) return true;
        // allowDestroy=true: vanilla destroys blocks with PushReaction.DESTROY
        // (tall grass, torches, ...) instead of blocking the piston.
        return PistonBaseBlock.isPushable(state, level, pos, pushDirection, true, null);
    }

    private boolean isCompositeAnchor(BlockState state) {
        return state.getBlock() instanceof CompositeBlock;
    }

    private boolean isCompositeProxy(BlockState state) {
        return state.getBlock() instanceof CompositeProxyBlock;
    }

    private BlockPos partWorldPos(BlockPos anchor, LocalTransform transform) {
        return anchor.offset(
                Math.floorDiv(transform.x().units(), UNIT),
                Math.floorDiv(transform.y().units(), UNIT),
                Math.floorDiv(transform.z().units(), UNIT));
    }

    private net.minecraft.world.phys.AABB partWorldBox(CompositeBlockEntity composite, PartInstance part) {
        var shape = ShapeTransformer.transform(
                part.state().getShape(level, composite.getBlockPos(), CollisionContext.empty()),
                part.transform());
        var anchor = composite.getBlockPos();
        var box = shape.bounds();
        return box.move(anchor.getX(), anchor.getY(), anchor.getZ());
    }

    private net.minecraft.world.phys.AABB cellBox(BlockPos pos) {
        return new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
}
