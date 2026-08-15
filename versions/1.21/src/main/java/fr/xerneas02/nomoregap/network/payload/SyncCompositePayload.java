package fr.xerneas02.nomoregap.network.payload;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record SyncCompositePayload(BlockPos pos, long revision, List<PartInstance> parts) implements CustomPacketPayload {
    public static final Type<SyncCompositePayload> TYPE = new Type<>(NoMoreGap.id("sync_composite"));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<PartInstance>> PARTS_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(PartInstance.CODEC).apply(ByteBufCodecs.list(NoMoreGapLimits.MAX_PARTS_PER_CELL));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCompositePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SyncCompositePayload::pos,
            ByteBufCodecs.VAR_LONG, SyncCompositePayload::revision,
            PARTS_CODEC, SyncCompositePayload::parts,
            SyncCompositePayload::new);

    public SyncCompositePayload { parts = List.copyOf(parts); }
    @Override public Type<SyncCompositePayload> type() { return TYPE; }
}
