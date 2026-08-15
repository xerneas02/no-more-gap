package fr.xerneas02.nomoregap.network.payload;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent only: the server chooses the BlockState. */
public record AddPartPayload(BlockPos pos, long expectedRevision) implements CustomPacketPayload {
    public static final Type<AddPartPayload> TYPE = new Type<>(NoMoreGap.id("add_part"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddPartPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AddPartPayload::pos, ByteBufCodecs.VAR_LONG, AddPartPayload::expectedRevision, AddPartPayload::new);
    @Override public Type<AddPartPayload> type() { return TYPE; }
}
