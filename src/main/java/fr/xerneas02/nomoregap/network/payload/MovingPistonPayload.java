package fr.xerneas02.nomoregap.network.payload;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.state.BlockState;

public record MovingPistonPayload(BlockPos pos, BlockState movedState, Direction direction,
                                  boolean extending) implements CustomPacketPayload {
    public static final Type<MovingPistonPayload> TYPE = new Type<>(NoMoreGap.id("moving_piston"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovingPistonPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MovingPistonPayload::pos,
            ByteBufCodecs.fromCodecWithRegistries(BlockState.CODEC), MovingPistonPayload::movedState,
            Direction.STREAM_CODEC, MovingPistonPayload::direction,
            ByteBufCodecs.BOOL, MovingPistonPayload::extending,
            MovingPistonPayload::new);

    @Override public Type<MovingPistonPayload> type() { return TYPE; }
}
