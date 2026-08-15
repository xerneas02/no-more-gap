package fr.xerneas02.nomoregap.api;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Experimental API; may change before 1.0. */
@FunctionalInterface
public interface PartGeometryProvider {
    VoxelShape shape(BlockState state);
}
