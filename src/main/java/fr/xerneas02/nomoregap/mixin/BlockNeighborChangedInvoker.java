package fr.xerneas02.nomoregap.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockBehaviour.class)
public interface BlockNeighborChangedInvoker {
    @Invoker("neighborChanged")
    void noMoreGap$neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   Orientation orientation, boolean movedByPiston);
}
