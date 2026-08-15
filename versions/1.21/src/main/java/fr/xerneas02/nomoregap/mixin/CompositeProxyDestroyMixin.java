package fr.xerneas02.nomoregap.mixin;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.interaction.PartRaycaster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Handles proxy parts before vanilla removes the proxy cell itself. */
@Mixin(ServerPlayerGameMode.class)
abstract class CompositeProxyDestroyMixin {
    @Shadow @Final protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void noMoreGap$destroyProxyPart(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (!(level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy)
                || !(level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite)) return;
        var target = PartRaycaster.raycastInCell(composite, level, player, 6, pos)
                .flatMap(hit -> composite.parts().find(hit.partId()));
        if (target.isEmpty()) {
            callback.setReturnValue(false);
            return;
        }
        var tool = player.getMainHandItem();
        var copiedTool = tool.copy();
        var partState = target.get().state();
        if (!player.isCreative()) tool.mineBlock(level, partState, pos, player);
        CompositeBlock.destroyPart(level, player, proxy.anchor(), composite, target.get().id(), copiedTool, pos);
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity remainingProxy) {
            player.connection.send(ClientboundBlockEntityDataPacket.create(remainingProxy));
        }
        callback.setReturnValue(true);
    }
}
