package fr.xerneas02.nomoregap.mixin.client;

import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server removes a composite part, not its proxy cell, so vanilla client prediction is invalid here. */
@Mixin(MultiPlayerGameMode.class)
abstract class CompositeProxyPredictionMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$keepProxyUntilServerReply(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity) {
            callback.setReturnValue(true);
        }
    }
}
