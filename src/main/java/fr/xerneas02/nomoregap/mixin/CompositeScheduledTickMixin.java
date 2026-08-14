package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.interaction.CompositeUseContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScheduledTickAccess.class)
interface CompositeScheduledTickMixin {
    @Inject(method = "scheduleTick(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;I)V", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$schedulePartTick(BlockPos pos, Block block, int delay, CallbackInfo callback) {
        if ((Object) this instanceof Level level && CompositeUseContext.handleScheduleTick(level, pos, block, delay)) callback.cancel();
    }
}
