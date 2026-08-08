package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;

public final class CreativeCompositeBreakingHandler {
    private CreativeCompositeBreakingHandler() {}

    public static void initialize() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!player.isCreative() || !(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)
                    || composite.parts().size() < 2) return InteractionResult.PASS;
            var target = PartRaycaster.raycast(composite, level, player, 6)
                    .flatMap(hit -> composite.parts().find(hit.partId()));
            if (target.isEmpty() || target.get().id() == composite.parts().view().getFirst().id()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            CompositeBlock.restoreAfterRemoval(level, pos, composite, target.get().id());
            return InteractionResult.SUCCESS_SERVER;
        });
    }
}
