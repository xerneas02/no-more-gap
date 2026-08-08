package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SugarCaneBlock.class, CactusBlock.class})
abstract class PlantSupportMixin {
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$keepPlantStack(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                          Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random,
                                          CallbackInfoReturnable<BlockState> callback) {
        if (direction != Direction.DOWN || neighbor.getBlock() != ModBlocks.COMPOSITE) return;
        if (level.getBlockEntity(neighborPos) instanceof CompositeBlockEntity composite
                && composite.parts().view().stream().anyMatch(part -> part.state().getBlock() == state.getBlock())) {
            callback.setReturnValue(state);
        }
    }
}
