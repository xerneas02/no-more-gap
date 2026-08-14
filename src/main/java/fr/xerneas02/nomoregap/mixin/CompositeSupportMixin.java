package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.geometry.CompositeFaceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets any vanilla block use an occupied composite face when its normal support check fails. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class CompositeSupportMixin {
    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
    private void noMoreGap$allowCompositeSupport(LevelReader level, BlockPos pos,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) return;
        for (Direction direction : Direction.values()) {
            var supportPos = pos.relative(direction);
            if (CompositeFaceSupport.isComposite(level, supportPos)
                    && CompositeFaceSupport.supports(level, supportPos, direction.getOpposite())) {
                callback.setReturnValue(true);
                return;
            }
        }
    }
}
