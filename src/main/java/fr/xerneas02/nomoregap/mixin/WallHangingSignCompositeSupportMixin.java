package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.geometry.CompositeFaceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallHangingSignBlock.class)
abstract class WallHangingSignCompositeSupportMixin {
    @Inject(method = "canAttachTo", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$allowCompositeFace(LevelReader level, BlockState state, BlockPos supportPos, Direction face,
                                               CallbackInfoReturnable<Boolean> callback) {
        Boolean supported = CompositeFaceSupport.supports(level, supportPos, face);
        if (supported != null) callback.setReturnValue(supported);
    }
}
