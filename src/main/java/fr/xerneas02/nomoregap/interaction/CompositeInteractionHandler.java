package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class CompositeInteractionHandler {
    private CompositeInteractionHandler() {}

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var compositePos = hit.getBlockPos();
            var composite = level.getBlockEntity(compositePos) instanceof CompositeBlockEntity direct ? direct : null;
            if (composite == null && level.getBlockState(compositePos).getBlock() instanceof DoorBlock
                    && level.getBlockState(compositePos).getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    && level.getBlockEntity(compositePos.below()) instanceof CompositeBlockEntity below) {
                compositePos = compositePos.below();
                composite = below;
            }
            if (composite == null || composite.parts().isEmpty()) return InteractionResult.PASS;
            var targetComposite = composite;
            var part = PartRaycaster.raycast(targetComposite, level, player, 6)
                    .flatMap(result -> targetComposite.parts().find(result.partId()))
                    .orElse(composite.parts().view().getFirst());
            var state = part.state();
            if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof FenceGateBlock)
                    && !(state.getBlock() instanceof TrapDoorBlock)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            var open = state.getValue(BlockStateProperties.OPEN);
            var updated = state.setValue(BlockStateProperties.OPEN, !open);
            composite.replacePart(part.id(), updated);
            var sound = state.getBlock() instanceof DoorBlock door
                    ? (!open ? door.type().doorOpen() : door.type().doorClose())
                    : state.getBlock() instanceof TrapDoorBlock
                    ? (!open ? net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN : net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE)
                    : (!open ? net.minecraft.sounds.SoundEvents.FENCE_GATE_OPEN : net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE);
            level.playSound(null, compositePos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                updateDoorTop(level, compositePos.above(), state, !open, state.getValue(BlockStateProperties.POWERED));
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static void updateDoorTop(net.minecraft.world.level.Level level, BlockPos topPos,
                                      net.minecraft.world.level.block.state.BlockState lower, boolean open, boolean powered) {
        var top = level.getBlockState(topPos);
        if (top.getBlock() == lower.getBlock() && top.hasProperty(DoorBlock.OPEN)) {
            level.setBlock(topPos, top.setValue(DoorBlock.OPEN, open).setValue(DoorBlock.POWERED, powered), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }
}
