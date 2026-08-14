package fr.xerneas02.nomoregap.lava;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/** Vanilla-style reaction, retaining the original lava-logged block as a composite part. */
public final class LavaLoggingReactions {
    public static final int FORMED_ROCK = 1;

    private LavaLoggingReactions() {}

    public static void tryReact(ServerLevel level, BlockPos changedPos) {
        if (!level.getGameRules().get(LavaLoggingRules.DO_REACTIONS)) return;
        var changed = level.getBlockState(changedPos);
        if (isLavaLogged(changed)) {
            if (touchesWater(level, changedPos)) formObsidianLogged(level, changedPos, changed);
            return;
        }
        if (isLavaSource(changed) && touchesWaterLogged(level, changedPos)) {
            level.setBlock(changedPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (!isWater(changed)) return;
        for (Direction direction : Direction.values()) {
            var lavaPos = changedPos.relative(direction);
            var lava = level.getBlockState(lavaPos);
            if (isLavaLogged(lava)) {
                formObsidianLogged(level, lavaPos, lava);
                return;
            }
            if (isLavaSource(lava) && isWaterLogged(changed)) {
                level.setBlock(lavaPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
        }
    }

    private static boolean touchesWater(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isWater(level.getBlockState(pos.relative(direction)))) return true;
        }
        return false;
    }

    private static boolean touchesWaterLogged(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isWaterLogged(level.getBlockState(pos.relative(direction)))) return true;
        }
        return false;
    }

    private static boolean isLavaLogged(BlockState state) {
        return state.hasProperty(LavaLogging.LAVA_LOGGED) && state.getValue(LavaLogging.LAVA_LOGGED);
    }

    private static boolean isWaterLogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED)
                && !isLavaLogged(state);
    }

    private static boolean isWater(BlockState state) {
        return isWaterLogged(state) || state.getFluidState().is(Fluids.WATER);
    }

    private static boolean isLavaSource(BlockState state) {
        return state.getFluidState().is(Fluids.LAVA) && state.getFluidState().isSource();
    }

    private static void formObsidianLogged(ServerLevel level, BlockPos pos, BlockState lavaState) {
        var original = lavaState.setValue(LavaLogging.LAVA_LOGGED, false);
        if (!level.setBlock(pos, ModBlocks.COMPOSITE.defaultBlockState(), Block.UPDATE_ALL)) return;
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) {
            composite.addPart(original, LocalTransform.IDENTITY, 0);
            composite.addPart(Blocks.OBSIDIAN.defaultBlockState(), LocalTransform.IDENTITY, FORMED_ROCK);
            var player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, false);
            if (player != null) fr.xerneas02.nomoregap.advancement.ModAdvancements.grant(player, "obsidian_log");
        }
    }
}
