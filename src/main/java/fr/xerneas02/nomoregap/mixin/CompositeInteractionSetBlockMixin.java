package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.interaction.CompositeUseContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class CompositeInteractionSetBlockMixin {
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$writeClickedPart(BlockPos pos, BlockState state, int flags,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (CompositeUseContext.handleSetBlock((Level) (Object) this, pos, state)) callback.setReturnValue(true);
    }
}
