package fr.xerneas02.nomoregap.part;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip and validation tests for {@link PartInstance.CODEC}, the
 * serialization format used by the composite block entity and the sync payload.
 */
class PartInstanceCodecTest {
    private static RegistryOps<JsonElement> OPS;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        OPS = RegistryOps.create(JsonOps.INSTANCE, access);
    }

    private static BlockState oakSlab(SlabType type) {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    @Test
    void roundTripPreservesEveryField() {
        var part = new PartInstance(7, oakSlab(SlabType.TOP),
                new LocalTransform(new FixedPoint(-32), FixedPoint.HALF_BLOCK, new FixedPoint(240), 3), 0b1010);
        var decoded = roundTrip(part);
        assertEquals(part, decoded);
    }

    @ParameterizedTest
    @MethodSource("parts")
    void roundTripIsStable(PartInstance part) {
        assertEquals(part, roundTrip(part));
    }

    static Stream<PartInstance> parts() {
        var bottom = oakSlab(SlabType.BOTTOM);
        var top = oakSlab(SlabType.TOP);
        return Stream.of(
                new PartInstance(0, Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0),
                new PartInstance(1, bottom, new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 1), 0),
                new PartInstance(2, top, new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 2), 0),
                new PartInstance(3, bottom, new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 3), 0),
                new PartInstance(4, Blocks.TORCH.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0),
                new PartInstance(5, Blocks.OAK_DOOR.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, new FixedPoint(384), FixedPoint.ZERO, 0), 1),
                new PartInstance(6, Blocks.SNOW.defaultBlockState(), new LocalTransform(FixedPoint.ZERO, FixedPoint.ZERO, FixedPoint.ZERO, 0), 4),
                new PartInstance(42, Blocks.STONE.defaultBlockState(),
                        new LocalTransform(new FixedPoint(-256), new FixedPoint(256), new FixedPoint(-128), 0), 0),
                new PartInstance(Integer.MAX_VALUE, Blocks.DIRT.defaultBlockState(),
                        new LocalTransform(new FixedPoint(FixedPoint.MIN_UNITS), new FixedPoint(FixedPoint.MAX_UNITS), FixedPoint.ZERO, 0), Integer.MAX_VALUE)
        );
    }

    @Test
    void rejectsNegativeIds() {
        var json = encode(validPart()).getOrThrow().getAsJsonObject();
        json.addProperty("id", -1);
        assertTrue(decode(json).result().isEmpty());
    }

    @Test
    void rejectsRotationOutsideZeroToThree() {
        var json = encode(validPart()).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("transform").addProperty("rotation", 4);
        assertTrue(decode(json).result().isEmpty(), "rotation 4 must be rejected");
    }

    @Test
    void rejectsNegativeRotation() {
        var json = encode(validPart()).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("transform").addProperty("rotation", -1);
        assertTrue(decode(json).result().isEmpty());
    }

    @Test
    void rejectsFixedPointOutOfRange() {
        var json = encode(validPart()).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("transform").addProperty("x", FixedPoint.MAX_UNITS + 1);
        assertTrue(decode(json).result().isEmpty());
        json.getAsJsonObject("transform").addProperty("y", FixedPoint.MIN_UNITS - 1);
        assertTrue(decode(json).result().isEmpty());
    }

    @Test
    void rejectsMissingFields() {
        var json = encode(validPart()).getOrThrow().getAsJsonObject();
        json.remove("transform");
        assertTrue(decode(json).result().isEmpty());
    }

    private static PartInstance validPart() {
        return new PartInstance(1, Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
    }

    @Test
    void transformCodecRoundTrip() {
        var transform = new LocalTransform(new FixedPoint(16), new FixedPoint(-16), new FixedPoint(256), 2);
        var decoded = PartInstance.TRANSFORM_CODEC.decode(OPS, PartInstance.TRANSFORM_CODEC.encodeStart(OPS, transform)
                .getOrThrow()).getOrThrow().getFirst();
        assertEquals(transform, decoded);
    }

    private static PartInstance roundTrip(PartInstance part) {
        return decode(encode(part).getOrThrow()).getOrThrow();
    }

    private static com.mojang.serialization.DataResult<JsonElement> encode(PartInstance part) {
        return PartInstance.CODEC.encodeStart(OPS, part);
    }

    private static com.mojang.serialization.DataResult<PartInstance> decode(JsonElement json) {
        return PartInstance.CODEC.parse(OPS, json);
    }
}
