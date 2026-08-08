package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.geometry.CompositeFaceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets wall attachments use an occupied composite face as their vanilla support. */
@Mixin({WallTorchBlock.class, RedstoneWallTorchBlock.class, WallBannerBlock.class, WallSignBlock.class})
abstract class CompositeWallAttachmentMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$allowCompositeFace(BlockState state, LevelReader level, BlockPos pos,
                                               CallbackInfoReturnable<Boolean> callback) {
        Direction face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Boolean supported = CompositeFaceSupport.supports(level, pos.relative(face.getOpposite()), face);
        if (supported != null) callback.setReturnValue(supported);
    }
}
