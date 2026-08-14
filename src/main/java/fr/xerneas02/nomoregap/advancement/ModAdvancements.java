package fr.xerneas02.nomoregap.advancement;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;

public final class ModAdvancements {
    private ModAdvancements() {}

    public static void grant(Player player, String id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var advancement = serverPlayer.level().getServer().getAdvancements().get(NoMoreGap.id(id));
        if (advancement == null) return;
        advancement.value().criteria().keySet().forEach(criterion ->
                serverPlayer.getAdvancements().award(advancement, criterion));
    }

    public static void checkComposite(Player player, CompositeBlockEntity composite) {
        var parts = composite.parts().view();
        if (parts.size() >= 2) grant(player, "it_fits");
        if (parts.stream().filter(part -> part.state().getBlock() instanceof CarpetBlock).count() >= 16) {
            grant(player, "carpet_diem");
        }
        if (parts.stream().anyMatch(part -> part.state().getBlock() instanceof DoorBlock)
                && parts.stream().anyMatch(part -> part.state().getBlock() instanceof CarpetBlock)) {
            grant(player, "open_to_everything");
        }
        if (parts.size() >= 64) grant(player, "stack_of_a_stack");
    }

    public static void checkCompactCircuit(Player player, CompositeBlockEntity composite) {
        if (composite.parts().view().stream().anyMatch(part -> part.state().getBlock() instanceof RedstoneLampBlock
                && part.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))) {
            grant(player, "compact_circuit");
        }
    }
}
