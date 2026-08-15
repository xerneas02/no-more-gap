package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.lava.LavaLogging;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class LavaLoggedFluidStateMixin {
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$useLavaFluid(CallbackInfoReturnable<net.minecraft.world.level.material.FluidState> callback) {
        var state = (net.minecraft.world.level.block.state.BlockState) (Object) this;
        if (state.hasProperty(LavaLogging.LAVA_LOGGED) && state.getValue(LavaLogging.LAVA_LOGGED)) {
            callback.setReturnValue(Fluids.LAVA.getSource(false));
        }
    }
}
