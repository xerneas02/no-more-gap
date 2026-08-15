package fr.xerneas02.nomoregap.mixin.client;

import fr.xerneas02.nomoregap.render.CompositeBlockEntityRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the composite renderer the local break stage; proxy blocks have no model of their own. */
@Mixin(LevelRenderer.class)
abstract class CompositeBreakAnimationMixin {
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), require = 0)
    private void noMoreGap$trackCompositeBreak(int breakerId, BlockPos pos, int progress, CallbackInfo callback) {
        CompositeBlockEntityRenderer.trackBreakProgress(breakerId, pos, progress);
    }
}
