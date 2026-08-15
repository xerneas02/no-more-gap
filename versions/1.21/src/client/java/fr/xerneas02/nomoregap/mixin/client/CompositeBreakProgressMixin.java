package fr.xerneas02.nomoregap.mixin.client;

import fr.xerneas02.nomoregap.render.CompositeBlockEntityRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 26.2 moved block-break progress from LevelRenderer to ClientLevel. */
@Mixin(ClientLevel.class)
abstract class CompositeBreakProgressMixin {
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), require = 0)
    private void noMoreGap$trackCompositeBreak(int breakerId, BlockPos pos, int progress, CallbackInfo callback) {
        CompositeBlockEntityRenderer.trackBreakProgress(breakerId, pos, progress);
    }
}
