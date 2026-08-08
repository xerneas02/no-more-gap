package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.lava.LavaLoggingReactions;
import fr.xerneas02.nomoregap.interaction.CompositePartUpdater;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LavaLoggingReactionMixin {
    @Inject(method = "neighborChanged", at = @At("TAIL"))
    private void noMoreGap$refreshCompositeParts(BlockPos pos, Block changedBlock, Orientation orientation,
                                                  org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        if ((Object) this instanceof ServerLevel level) CompositePartUpdater.refreshAround(level, pos);
    }

    @Inject(method = "setBlock", at = @At("TAIL"))
    private void noMoreGap$reactLavaAndWater(BlockPos pos, BlockState state, int flags,
                                              CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && (Object) this instanceof ServerLevel level) {
            LavaLoggingReactions.tryReact(level, pos);
            CompositePartUpdater.refreshAround(level, pos);
        }
    }
}
