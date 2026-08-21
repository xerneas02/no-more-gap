package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.CompositeProxyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Safety net for vanilla piston behaviour while composites are in the world:
 * anchor and proxy blocks must not be moved by a vanilla piston, because the
 * vanilla movement does not carry the composite block entity data. The
 * composite piston pipeline handles their movement instead.
 */
@Mixin(PistonBaseBlock.class)
public abstract class CompositePistonVanillaMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void noMoreGap$blockVanillaPushOfComposites(
            BlockState state, Level level, BlockPos pos, Direction direction, boolean allowDestroy,
            Direction pistonDirection, CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof CompositeBlock || state.getBlock() instanceof CompositeProxyBlock) {
            callback.setReturnValue(false);
        }
    }
}
