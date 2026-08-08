package fr.xerneas02.nomoregap.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import net.minecraft.world.level.block.state.BlockState;

public record PartInstance(int id, BlockState state, LocalTransform transform, int flags) {
    private static final Codec<FixedPoint> FIXED_CODEC = Codec.intRange(FixedPoint.MIN_UNITS, FixedPoint.MAX_UNITS).xmap(FixedPoint::new, FixedPoint::units);
    public static final Codec<LocalTransform> TRANSFORM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FIXED_CODEC.fieldOf("x").forGetter(LocalTransform::x),
            FIXED_CODEC.fieldOf("y").forGetter(LocalTransform::y),
            FIXED_CODEC.fieldOf("z").forGetter(LocalTransform::z),
            Codec.intRange(0, 3).fieldOf("rotation").forGetter(LocalTransform::quarterTurns)
    ).apply(instance, LocalTransform::new));
    public static final Codec<PartInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("id").forGetter(PartInstance::id),
            BlockState.CODEC.fieldOf("state").forGetter(PartInstance::state),
            TRANSFORM_CODEC.fieldOf("transform").forGetter(PartInstance::transform),
            Codec.INT.fieldOf("flags").forGetter(PartInstance::flags)
    ).apply(instance, PartInstance::new));

    public PartInstance {
        if (id < 0) throw new IllegalArgumentException("Part id must be non-negative");
        if (state == null || transform == null) throw new NullPointerException("Part state and transform are required");
    }
}
