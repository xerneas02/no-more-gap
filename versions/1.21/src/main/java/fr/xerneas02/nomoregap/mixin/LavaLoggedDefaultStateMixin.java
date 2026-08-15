package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.lava.LavaLogging;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Block.class)
abstract class LavaLoggedDefaultStateMixin {
    @ModifyVariable(method = "registerDefaultState", at = @At("HEAD"), argsOnly = true)
    private BlockState noMoreGap$defaultToNotLavaLogged(BlockState state) {
        return state.hasProperty(LavaLogging.LAVA_LOGGED)
                ? state.setValue(LavaLogging.LAVA_LOGGED, false)
                : state;
    }
}
