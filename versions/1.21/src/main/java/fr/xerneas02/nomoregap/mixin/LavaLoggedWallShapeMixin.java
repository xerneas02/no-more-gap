package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.lava.LavaLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallBlock.class)
abstract class LavaLoggedWallShapeMixin {
    @Shadow protected abstract VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);
    @Shadow protected abstract VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$useVanillaShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        if (state.getValue(LavaLogging.LAVA_LOGGED)) {
            callback.setReturnValue(getShape(state.setValue(LavaLogging.LAVA_LOGGED, false), level, pos, context));
        }
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$useVanillaCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                     CollisionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        if (state.getValue(LavaLogging.LAVA_LOGGED)) {
            callback.setReturnValue(getCollisionShape(state.setValue(LavaLogging.LAVA_LOGGED, false), level, pos, context));
        }
    }
}
