package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoublePlantBlock.class)
abstract class DoublePlantBlockMixin {
    @Inject(method = "playerWillDestroy", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$breakCompositePlantBase(Level level, BlockPos pos, BlockState state, Player player,
                                                    CallbackInfoReturnable<BlockState> callback) {
        if (state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.UPPER
                || !(level.getBlockEntity(pos.below()) instanceof CompositeBlockEntity composite)) return;
        var lowerPos = pos.below();
        var lowerState = level.getBlockState(lowerPos);
        if (composite.parts().view().stream().noneMatch(part -> part.state().getBlock() == (Object) this
                && part.state().getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER)) return;
        lowerState.getBlock().playerDestroy(level, player, lowerPos, lowerState, composite, player.getMainHandItem());
        level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        callback.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
