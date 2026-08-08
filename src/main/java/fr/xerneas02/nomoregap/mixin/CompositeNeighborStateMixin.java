package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets vanilla connection logic see a compatible part instead of the composite shell. */
@Mixin({FenceBlock.class, WallBlock.class, StairBlock.class})
abstract class CompositeNeighborStateMixin {
    @ModifyVariable(method = "updateShape", at = @At("HEAD"), argsOnly = true, index = 7)
    private BlockState noMoreGap$unwrapCompositeNeighbor(BlockState neighbor, BlockState state, LevelReader level,
                                                           ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                                           BlockPos neighborPos, BlockState originalNeighbor,
                                                           RandomSource random) {
        if (!(level.getBlockEntity(neighborPos) instanceof CompositeBlockEntity composite)) return neighbor;
        return composite.parts().view().stream().map(part -> part.state())
                .filter(part -> compatible(state, part))
                .findFirst().orElse(neighbor);
    }

    private static boolean compatible(BlockState state, BlockState part) {
        if (state.getBlock() instanceof StairBlock) return part.getBlock() instanceof StairBlock;
        if (state.getBlock() instanceof FenceBlock) {
            return part.getBlock() instanceof FenceBlock || part.getBlock() instanceof FenceGateBlock
                    || part.getBlock() instanceof WallBlock;
        }
        return part.getBlock() instanceof WallBlock || part.getBlock() instanceof FenceBlock
                || part.getBlock() instanceof FenceGateBlock;
    }
}
