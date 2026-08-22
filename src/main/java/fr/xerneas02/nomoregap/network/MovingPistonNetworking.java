package fr.xerneas02.nomoregap.network;

import fr.xerneas02.nomoregap.network.payload.MovingPistonPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class MovingPistonNetworking {
    private MovingPistonNetworking() {}

    public static void send(ServerLevel level, BlockPos pos, BlockState state,
                            Direction direction, boolean extending) {
        var payload = new MovingPistonPayload(pos, state, direction, extending);
        for (var player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(level, pos)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }
}
