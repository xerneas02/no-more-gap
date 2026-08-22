package fr.xerneas02.nomoregap.network;

import fr.xerneas02.nomoregap.network.payload.MovingPistonPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MovingPistonNetworking {
    private MovingPistonNetworking() {}

    public static void send(ServerLevel level, BlockPos pos, BlockState state,
                            Direction direction, boolean extending) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4),
                new MovingPistonPayload(pos, state, direction, extending));
    }
}
