package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Keeps stateful vanilla parts reactive while they are stored in composites. */
public final class CompositePartUpdater {
    private CompositePartUpdater() {}

    public static void refreshAround(Level level, BlockPos pos) {
        refresh(level, pos);
        for (Direction direction : Direction.values()) refresh(level, pos.relative(direction));
    }

    private static void refresh(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) return;
        for (var part : java.util.List.copyOf(composite.parts().view())) {
            var updated = refreshRedstone(level, pos, part.state());
            updated = refreshConnections(level, pos, updated);
            if (updated != part.state()) {
                composite.replacePart(part.id(), updated);
                syncDoorTop(level, pos, updated);
            }
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            fr.xerneas02.nomoregap.lava.LavaLoggingReactions.tryReact(serverLevel, pos);
        }
    }

    private static BlockState refreshRedstone(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock)) return state;
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
        if (wasPowered != powered && state.hasProperty(BlockStateProperties.OPEN)) {
            state = state.setValue(BlockStateProperties.OPEN, powered);
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) state = state.setValue(BlockStateProperties.POWERED, powered);
        return state;
    }

    private static void syncDoorTop(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof DoorBlock)
                || state.getValue(DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) return;
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
                state = state.updateShape(level, level, pos, direction, neighborPos,
                        stateAt(level, neighborPos, state), RandomSource.create());
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

    private static BlockState stateAt(Level level, BlockPos pos, BlockState requested) {
        var state = level.getBlockState(pos);
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) return state;
        return composite.parts().view().stream()
                .map(part -> part.state())
                .filter(part -> part.getBlock().getClass() == requested.getBlock().getClass())
                .findFirst().orElse(state);
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
