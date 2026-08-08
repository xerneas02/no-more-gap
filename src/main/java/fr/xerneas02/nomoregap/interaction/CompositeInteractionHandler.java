package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;

public final class CompositeInteractionHandler {
    private CompositeInteractionHandler() {}

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.isSecondaryUseActive()) return InteractionResult.PASS;
            var compositePos = hit.getBlockPos();
            var composite = level.getBlockEntity(compositePos) instanceof CompositeBlockEntity direct ? direct : null;
            if (composite == null && level.getBlockState(compositePos).getBlock() instanceof DoorBlock
                    && level.getBlockState(compositePos).getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    && level.getBlockEntity(compositePos.below()) instanceof CompositeBlockEntity below) {
                compositePos = compositePos.below();
                composite = below;
            }
            if (composite == null || composite.parts().isEmpty()) return InteractionResult.PASS;
            var part = composite.parts().view().getFirst();
            var state = part.state();
            if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof FenceGateBlock)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            var open = state.getValue(state.getBlock() instanceof DoorBlock ? DoorBlock.OPEN : FenceGateBlock.OPEN);
            var updated = state.setValue(state.getBlock() instanceof DoorBlock ? DoorBlock.OPEN : FenceGateBlock.OPEN, !open);
            composite.replacePart(part.id(), updated);
            var sound = state.getBlock() instanceof DoorBlock door
                    ? (!open ? door.type().doorOpen() : door.type().doorClose())
                    : (!open ? net.minecraft.sounds.SoundEvents.FENCE_GATE_OPEN : net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE);
            level.playSound(null, compositePos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                updateDoorTop(level, compositePos.above(), state, !open);
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static void updateDoorTop(net.minecraft.world.level.Level level, BlockPos topPos,
                                      net.minecraft.world.level.block.state.BlockState lower, boolean open) {
        var top = level.getBlockState(topPos);
        if (top.getBlock() == lower.getBlock() && top.hasProperty(DoorBlock.OPEN)) {
            level.setBlock(topPos, top.setValue(DoorBlock.OPEN, open), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }
}
