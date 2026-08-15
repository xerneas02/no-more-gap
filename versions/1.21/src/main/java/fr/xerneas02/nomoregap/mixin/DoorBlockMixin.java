package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
abstract class DoorBlockMixin {
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$keepDoorTop(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                       Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random,
                                       CallbackInfoReturnable<BlockState> callback) {
        if (direction != Direction.DOWN || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || neighbor.getBlock() != ModBlocks.COMPOSITE) return;
        if (level.getBlockEntity(neighborPos) instanceof CompositeBlockEntity composite
                && composite.parts().view().stream().anyMatch(part -> part.state().getBlock() == (Object) this
                && part.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
            callback.setReturnValue(state);
        }
    }

    @Inject(method = "playerWillDestroy", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$breakCompositeDoorBase(Level level, BlockPos pos, BlockState state, Player player,
                                                   CallbackInfoReturnable<BlockState> callback) {
        if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || !(level.getBlockEntity(pos.below()) instanceof CompositeBlockEntity composite)
                || composite.parts().isEmpty()
                || composite.parts().view().getFirst().state().getBlock() != (Object) this) return;
        var lowerPos = pos.below();
        var lowerState = level.getBlockState(lowerPos);
        lowerState.getBlock().playerDestroy(level, player, lowerPos, lowerState, composite, player.getMainHandItem());
        level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        callback.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
