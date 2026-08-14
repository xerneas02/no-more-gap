package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.interaction.CompositePartUpdater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lets vanilla connection logic see a compatible part instead of the composite shell. */
@Mixin({FenceBlock.class, WallBlock.class, IronBarsBlock.class})
abstract class CompositeNeighborStateMixin {
    @ModifyVariable(method = "updateShape", at = @At("HEAD"), argsOnly = true, index = 7)
    private BlockState noMoreGap$unwrapCompositeNeighbor(BlockState neighbor, BlockState state, LevelReader level,
                                                           ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                                           BlockPos neighborPos, BlockState originalNeighbor,
                                                           RandomSource random) {
        return CompositePartUpdater.stateAt(level, neighborPos, state);
    }
}
