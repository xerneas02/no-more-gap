package fr.xerneas02.nomoregap.piston;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartFlags;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;

import java.util.HashSet;

/**
 * Detects powered composite piston parts and fires the resolver/controller
 * exactly once per power edge. Piston parts are regular parts; their world
 * position is the anchor plus the part transform.
 *
 * <p>When a piston part extends it mirrors vanilla: the body switches to
 * {@code EXTENDED=true} (shortened shape) and a {@code PistonHeadBlock} part is
 * added one block ahead, inside the same composite. Retraction removes the head
 * and restores the body for every piston (normal pistons simply do not pull).
 */
public final class CompositePistonTrigger {
    private static boolean firing;

    private CompositePistonTrigger() {
    }

    public static void tick(ServerLevel level, BlockPos anchor, CompositeBlockEntity composite) {
        if (level.isClientSide() || composite == null || firing) return;
        var pistonIds = new HashSet<Integer>();
        // Iterate a copy: fire() mutates the composite (replacePart / addPart ->
        // changed -> refreshProxies -> setBlock -> neighborChanged), which
        // would otherwise throw ConcurrentModificationException.
        for (var part : java.util.List.copyOf(composite.parts().view())) {
            if (!isPistonPart(part)) continue;
            pistonIds.add(part.id());
            var worldPos = partWorldPos(anchor, part.transform());
            boolean powered = isPowered(level, worldPos, part) || internallyPowered(composite, part);
            boolean extended = part.state().getValue(PistonBaseBlock.EXTENDED);
            if (composite.pistonPowerChanged(part.id(), powered, extended)) {
                if (powered) {
                    fire(level, anchor, composite, part);
                } else {
                    fireRetract(level, anchor, composite, part);
                }
            }
        }
        composite.retainPistonPower(pistonIds);
    }

    public static void fire(ServerLevel level, BlockPos anchor, CompositeBlockEntity composite, PartInstance part) {
        if (firing) return;
        firing = true;
        try {
            var direction = part.state().getValue(BlockStateProperties.FACING);
            boolean sticky = part.state().getBlock() == Blocks.STICKY_PISTON;
            var resolver = new CompositePistonResolver(level, anchor, part.id(), part, direction, true, sticky);
            var plan = resolver.resolve();
            // If the push is blocked (immovable block, depth limit, ...), the
            // piston must not extend at all — mirroring vanilla.
            if (plan.blocked) return;
            if (!CompositePistonController.apply(level, plan)) return;
            if (!(level.getBlockEntity(anchor) instanceof CompositeBlockEntity current)
                    || current.parts().find(part.id()).map(p -> !p.state().hasProperty(PistonBaseBlock.EXTENDED)).orElse(true)) {
                return;
            }
            // The piston part extends: shorten the body and add the head part
            // one block ahead, exactly like vanilla does with a moving piston.
            current.replacePart(part.id(),
                    current.parts().find(part.id()).orElseThrow().state().setValue(PistonBaseBlock.EXTENDED, true));
            addPistonHead(current, part, direction, sticky);
        } finally {
            firing = false;
        }
    }

    /** Fires a retraction: removes the head and restores the body for every piston. */
    public static void fireRetract(ServerLevel level, BlockPos anchor, CompositeBlockEntity composite, PartInstance part) {
        if (firing) return;
        firing = true;
        try {
            // If the piston never extended (e.g. its push was blocked), there
            // is nothing to retract and a sticky piston must not pull the block
            // in front of it into the head cell.
            var currentState = composite.parts().find(part.id()).map(p -> p.state()).orElse(null);
            if (currentState == null || !currentState.hasProperty(PistonBaseBlock.EXTENDED)
                    || !currentState.getValue(PistonBaseBlock.EXTENDED)) {
                return;
            }
            var direction = part.state().getValue(BlockStateProperties.FACING);
            boolean sticky = part.state().getBlock() == Blocks.STICKY_PISTON;
            var resolver = new CompositePistonResolver(level, anchor, part.id(), part, direction, false, sticky);
            var plan = resolver.resolve();
            // Remove the head BEFORE applying the retraction so the head cell
            // (and its proxy) is free for a pulled block.
            if (!(level.getBlockEntity(anchor) instanceof CompositeBlockEntity current)) return;
            var removedHead = removePistonHead(current, part, direction);
            if (!plan.blocked && !CompositePistonController.apply(level, plan)) {
                if (removedHead != null) {
                    var restored = current.addPart(removedHead.state(), removedHead.transform(), removedHead.flags());
                    current.setPistonHeadOwner(restored.id(), part.id());
                }
                return;
            }
            if (current.parts().find(part.id()).map(p -> !p.state().hasProperty(PistonBaseBlock.EXTENDED)).orElse(true)) {
                return;
            }
            current.replacePart(part.id(),
                    current.parts().find(part.id()).orElseThrow().state().setValue(PistonBaseBlock.EXTENDED, false));
        } finally {
            firing = false;
        }
    }

    /** Adds the head part in the cell one block ahead of the piston body. */
    private static void addPistonHead(CompositeBlockEntity composite, PartInstance pistonPart,
                                      Direction direction, boolean sticky) {
        var headTransform = pistonHeadTransform(pistonPart, direction);
        // If a head already exists (re-fire), do not duplicate it.
        for (var part : composite.parts().view()) {
            if ((part.flags() & PartFlags.PISTON_HEAD) != 0 && part.transform().equals(headTransform)) {
                if (!composite.hasPistonHeadOwner(part.id())) {
                    composite.setPistonHeadOwner(part.id(), pistonPart.id());
                }
                return;
            }
        }
        var headState = Blocks.PISTON_HEAD.defaultBlockState()
                .setValue(BlockStateProperties.FACING, direction)
                .setValue(BlockStateProperties.SHORT, false)
                .setValue(BlockStateProperties.PISTON_TYPE, sticky ? PistonType.STICKY : PistonType.DEFAULT);
        var head = composite.addPart(headState, headTransform, PartFlags.PISTON_HEAD);
        composite.setPistonHeadOwner(head.id(), pistonPart.id());
    }

    /** Removes only the head created by this piston part. */
    private static PartInstance removePistonHead(CompositeBlockEntity composite, PartInstance pistonPart, Direction direction) {
        var headTransform = pistonHeadTransform(pistonPart, direction);
        for (var part : java.util.List.copyOf(composite.parts().view())) {
            if ((part.flags() & PartFlags.PISTON_HEAD) != 0
                    && (composite.isPistonHeadOwnedBy(part.id(), pistonPart.id())
                    || (!composite.hasPistonHeadOwner(part.id()) && part.transform().equals(headTransform)))) {
                composite.removePart(part.id());
                return part;
            }
        }
        return null;
    }

    private static LocalTransform pistonHeadTransform(PartInstance pistonPart, Direction direction) {
        return new LocalTransform(
                pistonPart.transform().x().add(FixedPoint.fromDouble(direction.getStepX())),
                pistonPart.transform().y().add(FixedPoint.fromDouble(direction.getStepY())),
                pistonPart.transform().z().add(FixedPoint.fromDouble(direction.getStepZ())),
                pistonPart.transform().quarterTurns());
    }

    private static boolean isPistonPart(PartInstance part) {
        return part.state().getBlock() == Blocks.PISTON || part.state().getBlock() == Blocks.STICKY_PISTON;
    }

    private static boolean isPowered(ServerLevel level, BlockPos worldPos, PartInstance part) {
        var direction = part.state().getValue(BlockStateProperties.FACING);
        // A piston is powered when any neighbour except the head side is powered.
        for (Direction dir : Direction.values()) {
            if (dir == direction) continue;
            var neighbor = worldPos.relative(dir);
            if (level.hasSignal(neighbor, dir)) return true;
        }
        return false;
    }

    /**
     * A piston part is also powered when a powered part (button, lever, redstone
     * wire) lives in the same composite cell, next to the piston head direction.
     * Composite parts do not emit through the world grid, so this checks the
     * internal part list directly.
     */
    private static boolean internallyPowered(CompositeBlockEntity composite, PartInstance pistonPart) {
        var pistonPos = partWorldPos(composite.getBlockPos(), pistonPart.transform());
        var direction = pistonPart.state().getValue(BlockStateProperties.FACING);
        for (var other : composite.parts().view()) {
            if (other.id() == pistonPart.id()) continue;
            if ((other.flags() & PartFlags.PISTON_HEAD) != 0) continue;
            if (!other.state().hasProperty(BlockStateProperties.POWERED)) continue;
            if (!other.state().getValue(BlockStateProperties.POWERED)) continue;
            var otherPos = partWorldPos(composite.getBlockPos(), other.transform());
            // The powered part must be adjacent to the piston (not on the head side).
            if (otherPos.equals(pistonPos.relative(direction))) continue;
            if (otherPos.distManhattan(pistonPos) == 1) return true;
            // Same cell: a lever/button attached to the piston cell counts.
            if (otherPos.equals(pistonPos)) return true;
        }
        return false;
    }

    private static BlockPos partWorldPos(BlockPos anchor, fr.xerneas02.nomoregap.geometry.LocalTransform transform) {
        int unit = fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        return anchor.offset(
                Math.floorDiv(transform.x().units(), unit),
                Math.floorDiv(transform.y().units(), unit),
                Math.floorDiv(transform.z().units(), unit));
    }
}
