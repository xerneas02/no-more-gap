package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;

public final class CreativeCompositeBreakingHandler {
    private CreativeCompositeBreakingHandler() {}

    public static InteractionResult attackBlock(Player player, Level level, InteractionHand hand,
                                                BlockPos pos, Direction direction) {
            if (!player.isCreative()) return InteractionResult.PASS;
            var anchorPos = pos;
            var composite = level.getBlockEntity(pos) instanceof CompositeBlockEntity direct ? direct : null;
            if (composite == null && level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy
                    && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity anchor) {
                anchorPos = proxy.anchor();
                composite = anchor;
            }
            if (composite == null || composite.parts().size() < 2) return InteractionResult.PASS;
            var targetComposite = composite;
            var target = (anchorPos.equals(pos) ? PartRaycaster.raycast(targetComposite, level, player, 6)
                    : PartRaycaster.raycastInCell(targetComposite, level, player, 6, pos))
                    .flatMap(hit -> targetComposite.parts().find(hit.partId()));
            if (target.isEmpty()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            CompositeBlock.destroyPart(level, player, anchorPos, targetComposite, target.get().id(),
                    player.getMainHandItem().copy());
            return InteractionResult.SUCCESS_SERVER;
    }
}
