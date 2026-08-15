package fr.xerneas02.nomoregap.network.payload;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RemovePartPayload(BlockPos pos, int partId, long expectedRevision) implements CustomPacketPayload {
    public static final Type<RemovePartPayload> TYPE = new Type<>(NoMoreGap.id("remove_part"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemovePartPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RemovePartPayload::pos,
            ByteBufCodecs.VAR_INT, RemovePartPayload::partId,
            ByteBufCodecs.VAR_LONG, RemovePartPayload::expectedRevision,
            RemovePartPayload::new);
    @Override public Type<RemovePartPayload> type() { return TYPE; }
}
