package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Keeps stateful vanilla parts reactive while they are stored in composites. */
public final class CompositePartUpdater {
    private CompositePartUpdater() {}

    public static void refreshAround(Level level, BlockPos pos) {
        for (int y = -2; y <= 2; y++) refresh(level, pos.offset(0, y, 0));
        for (Direction direction : Direction.values()) refresh(level, pos.relative(direction));
    }

    private static void refresh(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) return;
        composite.beginUpdate();
        try {
            for (var part : java.util.List.copyOf(composite.parts().view())) {
                try {
                    if (part.state().getBlock() instanceof TntBlock && level.hasNeighborSignal(pos)) {
                        CompositeUseContext.run(level, pos, composite, part.id(), () ->
                                part.state().handleNeighborChanged(level, pos,
                                        net.minecraft.world.level.block.Blocks.AIR, null, false));
                        continue;
                    }
                    var partPos = partPos(pos, part.transform());
                    var updated = refreshRedstone(level, partPos, part.state());
                    updated = refreshConnections(level, partPos, updated);
                    if (updated != part.state()) {
                        composite.replacePart(part.id(), updated);
                        syncDoorTop(level, pos, updated);
                    }
                } catch (RuntimeException exception) {
                    quarantine(level, pos, composite, part, exception);
                }
            }
        } finally {
            composite.endUpdate();
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            fr.xerneas02.nomoregap.lava.LavaLoggingReactions.tryReact(serverLevel, pos);
        }
    }

    private static void quarantine(Level level, BlockPos anchor, CompositeBlockEntity composite,
                                   fr.xerneas02.nomoregap.part.PartInstance part, RuntimeException exception) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) throw exception;
        composite.removePart(part.id());
        net.minecraft.world.level.block.Block.popResource(server, partPos(anchor, part.transform()),
                new net.minecraft.world.item.ItemStack(part.state().getBlock()));
        fr.xerneas02.nomoregap.NoMoreGap.LOGGER.warn("Removed crashing composite part {} at {}", part.id(), anchor, exception);
    }

    private static BlockState refreshRedstone(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CopperBulbBlock) {
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(BlockStateProperties.POWERED) != powered) {
                state = state.setValue(BlockStateProperties.POWERED, powered);
                if (powered) state = state.setValue(BlockStateProperties.LIT, !state.getValue(BlockStateProperties.LIT));
            }
            return state;
        }
        if (state.getBlock() instanceof RedstoneLampBlock) {
            return state.setValue(BlockStateProperties.LIT, level.hasNeighborSignal(pos));
        }
        if (!(state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock)) return state;
        if (state.getBlock() instanceof DoorBlock
                && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) return state;
        boolean powered = state.getBlock() instanceof DoorBlock
                ? level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above()) || level.hasNeighborSignal(pos.above(2))
                : level.hasNeighborSignal(pos);
        boolean wasPowered = state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
        if (wasPowered != powered && state.hasProperty(BlockStateProperties.OPEN)) {
            state = state.setValue(BlockStateProperties.OPEN, powered);
            if (!level.isClientSide()) {
                var sound = state.getBlock() instanceof DoorBlock door
                        ? (powered ? door.type().doorOpen() : door.type().doorClose())
                        : state.getBlock() instanceof TrapDoorBlock
                        ? (powered ? net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN : net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE)
                        : (powered ? net.minecraft.sounds.SoundEvents.FENCE_GATE_OPEN : net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE);
                level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
            }
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) state = state.setValue(BlockStateProperties.POWERED, powered);
        return state;
    }

    private static void syncDoorTop(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof DoorBlock)
                || state.getValue(DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) return;
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) {
            for (var part : java.util.List.copyOf(composite.parts().view())) {
                if (part.state().getBlock() == state.getBlock()
                        && part.state().getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                    composite.replacePart(part.id(), part.state().setValue(DoorBlock.OPEN, state.getValue(DoorBlock.OPEN))
                            .setValue(DoorBlock.POWERED, state.getValue(DoorBlock.POWERED)));
                }
            }
        }
        var topPos = pos.above();
        var top = level.getBlockState(topPos);
        if (top.getBlock() == state.getBlock() && top.hasProperty(DoorBlock.OPEN)) {
            level.setBlock(topPos, top.setValue(DoorBlock.OPEN, state.getValue(DoorBlock.OPEN))
                    .setValue(DoorBlock.POWERED, state.getValue(DoorBlock.POWERED)), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    private static BlockState refreshConnections(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof FenceBlock) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                var property = connectionProperty(direction);
                if (state.hasProperty(property)) state = state.setValue(property, connectsTo(level, pos, direction, state));
            }
            return state;
        }
        if (state.getBlock() instanceof WallBlock) {
            boolean connected = false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                boolean connects = connectsTo(level, pos, direction, state);
                connected |= connects;
                var property = wallProperty(direction);
                if (state.hasProperty(property)) state = state.setValue(property, connects ? WallSide.TALL : WallSide.NONE);
            }
            if (state.hasProperty(BlockStateProperties.UP)) state = state.setValue(BlockStateProperties.UP, !connected);
            return state;
        }
        if (state.hasProperty(BlockStateProperties.STAIRS_SHAPE)) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                var neighborPos = pos.relative(direction);
                state = state.updateShape(direction, stateAt(level, neighborPos, state), level, pos, neighborPos);
            }
        } else if (state.getBlock() instanceof CrossCollisionBlock) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                var neighborPos = pos.relative(direction);
                state = state.updateShape(direction, stateAt(level, neighborPos, state), level, pos, neighborPos);
            }
        }
        return state;
    }

    private static boolean connectsTo(Level level, BlockPos pos, Direction direction, BlockState state) {
        var neighbor = stateAt(level, pos.relative(direction), state);
        return neighbor.getBlock() instanceof FenceBlock || neighbor.getBlock() instanceof FenceGateBlock
                || neighbor.getBlock() instanceof WallBlock
                || net.minecraft.world.level.block.Block.isFaceFull(
                neighbor.getCollisionShape(level, pos.relative(direction), CollisionContext.empty()), direction.getOpposite());
    }

    public static BlockState stateAt(BlockGetter level, BlockPos pos, BlockState requested) {
        var state = level.getBlockState(pos);
        CompositeBlockEntity composite = null;
        BlockPos anchor = pos;
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity direct) composite = direct;
        else if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy
                && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity resolved) {
            composite = resolved;
            anchor = proxy.anchor();
        }
        if (composite == null) return state;
        var compositeAnchor = anchor;
        var parts = composite.parts().view().stream()
                .filter(part -> occupiesCell(level, compositeAnchor, pos, part)).toList();
        return parts.stream().map(part -> part.state()).filter(part -> part.getBlock() == requested.getBlock()).findFirst()
                .or(() -> parts.stream().map(part -> part.state()).filter(part -> compatible(requested, part)).findFirst())
                .orElse(state);
    }

    private static BlockPos partPos(BlockPos anchor, fr.xerneas02.nomoregap.geometry.LocalTransform transform) {
        return anchor.offset((int) Math.floor(transform.xDouble()), (int) Math.floor(transform.yDouble()),
                (int) Math.floor(transform.zDouble()));
    }

    private static boolean occupiesCell(BlockGetter level, BlockPos anchor, BlockPos cell,
                                        fr.xerneas02.nomoregap.part.PartInstance part) {
        int x = cell.getX() - anchor.getX(), y = cell.getY() - anchor.getY(), z = cell.getZ() - anchor.getZ();
        return ShapeTransformer.transform(part.state().getShape(level, anchor, CollisionContext.empty()), part.transform())
                .toAabbs().stream().anyMatch(box -> box.maxX > x && box.minX < x + 1
                        && box.maxY > y && box.minY < y + 1 && box.maxZ > z && box.minZ < z + 1);
    }

    public static boolean compatible(BlockState state, BlockState neighbor) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
            return neighbor.getBlock() instanceof net.minecraft.world.level.block.StairBlock;
        }
        if (state.getBlock() instanceof FenceBlock) {
            return neighbor.getBlock() instanceof FenceBlock || neighbor.getBlock() instanceof FenceGateBlock
                    || neighbor.getBlock() instanceof WallBlock;
        }
        if (state.getBlock() instanceof CrossCollisionBlock) return neighbor.getBlock() instanceof CrossCollisionBlock;
        return neighbor.getBlock() instanceof WallBlock || neighbor.getBlock() instanceof FenceBlock
                || neighbor.getBlock() instanceof FenceGateBlock;
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty connectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH;
            case EAST -> BlockStateProperties.EAST;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            default -> throw new IllegalArgumentException("Horizontal direction required");
        };
    }

    private static net.minecraft.world.level.block.state.properties.EnumProperty<WallSide> wallProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH_WALL;
            case EAST -> BlockStateProperties.EAST_WALL;
            case SOUTH -> BlockStateProperties.SOUTH_WALL;
            case WEST -> BlockStateProperties.WEST_WALL;
            default -> throw new IllegalArgumentException("Horizontal direction required");
        };
    }
}
