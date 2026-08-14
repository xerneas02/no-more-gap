package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;

public final class CreativeCompositeBreakingHandler {
    private CreativeCompositeBreakingHandler() {}

    public static void initialize() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
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
            var sound = target.get().state().getSoundType();
            level.playSound(null, anchorPos, sound.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            if (target.get().state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                var doorParts = targetComposite.parts().view().stream()
                        .filter(part -> part.state().getBlock() == target.get().state().getBlock())
                        .map(fr.xerneas02.nomoregap.part.PartInstance::id).collect(java.util.stream.Collectors.toSet());
                CompositeBlock.restoreAfterRemoval(level, anchorPos, targetComposite, doorParts);
            } else {
                CompositeBlock.restoreAfterRemoval(level, anchorPos, targetComposite, target.get().id());
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }
}
