package fr.xerneas02.nomoregap.network.payload;

import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Encode/decode round trips for the three network payload codecs. They are pure
 * byte-buffer codecs, so they can be tested without a running server.
 */
class NetworkPayloadCodecTest {
    private static RegistryAccess REGISTRIES;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void addPartPayloadRoundTrips() {
        var payload = new AddPartPayload(new BlockPos(10, 20, -30), 42L);
        assertEquals(payload, roundTrip(AddPartPayload.CODEC, payload));
    }

    @Test
    void removePartPayloadRoundTrips() {
        var payload = new RemovePartPayload(new BlockPos(-1, 0, 300), 7, Long.MAX_VALUE);
        assertEquals(payload, roundTrip(RemovePartPayload.CODEC, payload));
    }

    @Test
    void syncCompositePayloadRoundTrips() {
        var parts = List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState(), LocalTransform.IDENTITY, 0),
                new PartInstance(1, Blocks.TORCH.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 2), 0),
                new PartInstance(2, Blocks.MOSS_CARPET.defaultBlockState(), LocalTransform.IDENTITY, 4));
        var payload = new SyncCompositePayload(new BlockPos(1, 2, 3), 99L, parts);
        var decoded = roundTrip(SyncCompositePayload.CODEC, payload);
        assertEquals(payload.pos(), decoded.pos());
        assertEquals(payload.revision(), decoded.revision());
        assertEquals(payload.parts(), decoded.parts());
    }

    @Test
    void syncPayloadRejectsMoreThanThePartLimit() {
        var tooMany = java.util.stream.IntStream.range(0, 65)
                .mapToObj(id -> new PartInstance(id, Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0)).toList();
        var payload = new SyncCompositePayload(BlockPos.ZERO, 0, tooMany);
        assertThrows(Exception.class, () -> encode(SyncCompositePayload.CODEC, payload),
                "Encoding a payload with more than 64 parts must fail");
    }

    @Test
    void syncPayloadNormalisesThePartsList() {
        var source = new java.util.ArrayList<PartInstance>();
        source.add(new PartInstance(0, Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0));
        var payload = new SyncCompositePayload(BlockPos.ZERO, 1, source);
        source.clear();
        assertEquals(1, payload.parts().size(), "The payload must defensively copy its parts list");
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var encoded = encode(codec, value);
        var decoded = codec.decode(encoded);
        assertEquals(0, encoded.readableBytes(), "No trailing bytes may remain after decode");
        return decoded;
    }

    private static <T> RegistryFriendlyByteBuf encode(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        codec.encode(buf, value);
        return buf;
    }
}
