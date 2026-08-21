package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class CompositeInteractionHandler {
    private CompositeInteractionHandler() {}

    public static InteractionResult useBlock(net.minecraft.world.entity.player.Player player,
                                             net.minecraft.world.level.Level level,
                                             net.minecraft.world.InteractionHand hand,
                                             net.minecraft.world.phys.BlockHitResult hit) {
            var compositePos = hit.getBlockPos();
            var clickedState = level.getBlockState(compositePos);
            var clickedDoorTop = clickedState.getBlock() instanceof DoorBlock
                    && clickedState.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    ? clickedState : null;
            var composite = level.getBlockEntity(compositePos) instanceof CompositeBlockEntity direct ? direct : null;
            if (composite == null && level.getBlockEntity(compositePos) instanceof fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity proxy
                    && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity anchor) {
                compositePos = proxy.anchor();
                composite = anchor;
            }
            if (composite == null && clickedDoorTop != null
                    && level.getBlockEntity(compositePos.below()) instanceof CompositeBlockEntity below) {
                compositePos = compositePos.below();
                composite = below;
            }
            if (composite == null || composite.parts().isEmpty()) return InteractionResult.PASS;
            var targetComposite = composite;
            var target = clickedDoorTop != null
                    ? targetComposite.parts().view().stream().filter(part ->
                            part.state().getBlock() == clickedDoorTop.getBlock()
                                    && part.state().hasProperty(DoorBlock.HALF)
                                    && part.state().getValue(DoorBlock.HALF)
                                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER).findFirst()
                    : PartRaycaster.raycast(targetComposite, level, player, 6)
                            .flatMap(result -> targetComposite.parts().find(result.partId()));
            if (target.isEmpty()) return InteractionResult.PASS;
            var part = target.get();
            var state = part.state();
            if (state.getBlock() instanceof LeverBlock) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                boolean powered = !state.getValue(BlockStateProperties.POWERED);
                composite.replacePart(part.id(), state.setValue(BlockStateProperties.POWERED, powered));
                level.updateNeighborsAt(compositePos, composite.getBlockState().getBlock());
                CompositePartUpdater.refreshAround(level, compositePos);
                level.playSound(null, compositePos, net.minecraft.sounds.SoundEvents.LEVER_CLICK,
                        net.minecraft.sounds.SoundSource.BLOCKS, .3f, powered ? .6f : .5f);
                if (powered) fr.xerneas02.nomoregap.advancement.ModAdvancements.checkCompactCircuit(player, composite);
                if (level.getBlockEntity(compositePos) instanceof CompositeBlockEntity after) {
                    fr.xerneas02.nomoregap.piston.CompositePistonTrigger.tick(
                            (net.minecraft.server.level.ServerLevel) level, compositePos, after);
                }
                return InteractionResult.SUCCESS_SERVER;
            }
            if (isDirectlyUsable(state)) {
                if (level.isClientSide() && state.getBlock() instanceof ButtonBlock) return InteractionResult.SUCCESS;
                CompositeUseContext.begin(level, compositePos, composite, part.id());
                try {
                    var partHit = new net.minecraft.world.phys.BlockHitResult(hit.getLocation(), hit.getDirection(), compositePos, hit.isInside());
                    if (state.getBlock() instanceof TntBlock) {
                        var result = state.useItemOn(player.getItemInHand(hand), level, player, hand, partHit);
                        return result.consumesAction() ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
                    }
                    var result = state.useWithoutItem(level, player, partHit);
                    if (!level.isClientSide() && state.getBlock() instanceof ButtonBlock) {
                        fr.xerneas02.nomoregap.advancement.ModAdvancements.checkCompactCircuit(player, composite);
                    }
                    return result.consumesAction() ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
                } finally {
                    CompositeUseContext.end();
                    // A button/lever part may have just powered (or a piston part
                    // may be present); re-check the composite piston trigger so
                    // the piston fires on the interaction itself.
                    if (!level.isClientSide() && level.getBlockEntity(compositePos) instanceof CompositeBlockEntity after) {
                        fr.xerneas02.nomoregap.piston.CompositePistonTrigger.tick(
                                (net.minecraft.server.level.ServerLevel) level, compositePos, after);
                    }
                }
            }
            if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof FenceGateBlock)
                    && !(state.getBlock() instanceof TrapDoorBlock)) return InteractionResult.PASS;
            if (state.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            var open = state.getValue(BlockStateProperties.OPEN);
            var updated = state.setValue(BlockStateProperties.OPEN, !open);
            composite.replacePart(part.id(), updated);
            if (state.getBlock() instanceof DoorBlock) syncCompositeDoor(composite, state, !open);
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
    }

    private static boolean isDirectlyUsable(net.minecraft.world.level.block.state.BlockState state) {
        var block = state.getBlock();
        return block instanceof ButtonBlock || block instanceof DaylightDetectorBlock
                || block instanceof RepeaterBlock || block instanceof ComparatorBlock || block instanceof TntBlock;
    }

    private static void syncCompositeDoor(CompositeBlockEntity composite, net.minecraft.world.level.block.state.BlockState state,
                                          boolean open) {
        for (var other : java.util.List.copyOf(composite.parts().view())) {
            if (other.state().getBlock() == state.getBlock() && other.state().hasProperty(DoorBlock.HALF)) {
                composite.replacePart(other.id(), other.state().setValue(DoorBlock.OPEN, open)
                        .setValue(DoorBlock.POWERED, state.getValue(DoorBlock.POWERED)));
            }
        }
    }

    private static void updateDoorTop(net.minecraft.world.level.Level level, BlockPos topPos,
                                      net.minecraft.world.level.block.state.BlockState lower, boolean open, boolean powered) {
        var top = level.getBlockState(topPos);
        if (top.getBlock() == lower.getBlock() && top.hasProperty(DoorBlock.OPEN)) {
            level.setBlock(topPos, top.setValue(DoorBlock.OPEN, open).setValue(DoorBlock.POWERED, powered), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }
}
