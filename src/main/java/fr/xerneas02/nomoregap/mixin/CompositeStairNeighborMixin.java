package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.interaction.CompositePartUpdater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Stair corners query multiple neighbours directly, not just updateShape's neighbour argument. */
@Mixin(StairBlock.class)
abstract class CompositeStairNeighborMixin {
    @Redirect(method = "getStairsShape", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState noMoreGap$stairsShapeNeighbour(BlockGetter level, BlockPos neighbourPos) {
        return CompositePartUpdater.stateAt(level, neighbourPos, Blocks.OAK_STAIRS.defaultBlockState());
    }

    @Redirect(method = "canTakeShape", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState noMoreGap$stairsCornerNeighbour(BlockGetter level, BlockPos neighbourPos) {
        return CompositePartUpdater.stateAt(level, neighbourPos, Blocks.OAK_STAIRS.defaultBlockState());
    }
}
