package fr.xerneas02.nomoregap.api;

import net.minecraft.world.level.block.state.BlockState;

/** Experimental API; may change before 1.0. */
@FunctionalInterface
public interface PartBehaviorAdapter {
    boolean supports(BlockState state);
}
