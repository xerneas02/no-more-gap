package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.lava.LavaLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class LavaLoggedNeighborTickMixin {
    @Inject(method = "updateShape", at = @At("HEAD"))
    private void noMoreGap$scheduleLavaAfterNeighborChange(LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                                            Direction direction, BlockPos neighborPos, BlockState neighbor,
                                                            RandomSource random, CallbackInfoReturnable<BlockState> callback) {
        var state = (BlockState) (Object) this;
        if (state.hasProperty(LavaLogging.LAVA_LOGGED) && state.getValue(LavaLogging.LAVA_LOGGED)) {
            ticks.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
        }
    }
}
