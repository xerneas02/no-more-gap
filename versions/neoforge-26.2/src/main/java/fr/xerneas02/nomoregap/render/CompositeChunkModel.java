package fr.xerneas02.nomoregap.render;

import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.lava.LavaLoggingReactions;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.renderdata.CompositeRenderData;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;
import net.neoforged.neoforge.common.extensions.IBlockGetterExtension;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Emits immutable composite parts into NeoForge's cached chunk geometry. */
public final class CompositeChunkModel extends DelegateBlockStateModel implements DynamicBlockStateModel {
    public static final ModelProperty<CompositeRenderData> DATA = new ModelProperty<>();
    private static final Set<Long> CHUNK_RENDERED = ConcurrentHashMap.newKeySet();

    public CompositeChunkModel(BlockStateModel delegate) { super(delegate); }

    public static boolean isChunkRendered(PartInstance part) {
        return part.flags() != LavaLoggingReactions.FORMED_ROCK;
    }

    public static boolean hasChunkGeometry(BlockPos pos) { return CHUNK_RENDERED.contains(pos.asLong()); }
    public static boolean hasChunkGeometry(long pos) { return CHUNK_RENDERED.contains(pos); }

    @Override public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                       RandomSource random, List<BlockStateModelPart> output) {
        long packedPos = pos.asLong();
        CompositeRenderData data = data(level, pos);
        if (data == null) {
            CHUNK_RENDERED.remove(packedPos);
            return;
        }
        int initialSize = output.size();
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        boolean snowyCell = data.parts().stream().anyMatch(part -> part.state().getBlock() instanceof SnowLayerBlock
                && owns(data.cellOffset(), part));
        for (var part : data.parts()) {
            if (!isChunkRendered(part) || !owns(data.cellOffset(), part)) continue;
            var model = models.get(part.state());
            if (model instanceof CompositeChunkModel) continue;
            var sourceParts = new ArrayList<BlockStateModelPart>();
            random.setSeed(pos.asLong() ^ part.id());
            model.collectParts(level, pos, part.state(), random, sourceParts);
            var offset = part.state().getOffset(pos);
            float tx = (float) (part.transform().xDouble() - data.cellOffset().getX() + offset.x);
            float ty = (float) (part.transform().yDouble() - data.cellOffset().getY() + offset.y);
            float tz = (float) (part.transform().zDouble() - data.cellOffset().getZ() + offset.z);
            boolean suppressTint = snowyCell && part.state().getBlock() instanceof VegetationBlock
                    && !(part.state().getBlock() instanceof DoublePlantBlock)
                    && !NoMoreGapConfig.snowLoggedVegetationBiomeTint();
            for (var source : sourceParts) output.add(transform(source, level, pos, part.state(), tx, ty, tz,
                    part.transform().quarterTurns(), suppressTint));
        }
        if (output.size() > initialSize) CHUNK_RENDERED.add(packedPos);
        else CHUNK_RENDERED.remove(packedPos);
    }

    @Override public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return new GeometryKey(data(level, pos), pos.immutable());
    }

    @Override public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        CompositeRenderData data = data(level, pos);
        if (data != null) for (var part : data.parts()) if (owns(data.cellOffset(), part)) {
            var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(part.state());
            if (!(model instanceof CompositeChunkModel)) return model.particleMaterial(level, pos, part.state());
        }
        return delegate.particleMaterial();
    }

    @Override public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int flags = 0;
        CompositeRenderData data = data(level, pos);
        if (data != null) for (var part : data.parts()) if (isChunkRendered(part) && owns(data.cellOffset(), part)) {
            var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(part.state());
            if (!(model instanceof CompositeChunkModel)) flags |= model.materialFlags(level, pos, part.state());
        }
        return flags;
    }

    private static BlockStateModelPart transform(BlockStateModelPart source, BlockAndTintGetter level, BlockPos pos,
                                                  BlockState state, float tx, float ty, float tz, int turns,
                                                  boolean suppressTint) {
        var quads = new ArrayList<BakedQuad>();
        addTransformed(quads, source.getQuads(null), level, pos, state, tx, ty, tz, turns, suppressTint);
        for (Direction direction : Direction.values())
            addTransformed(quads, source.getQuads(direction), level, pos, state, tx, ty, tz, turns, suppressTint);
        return new TransformedPart(List.copyOf(quads), source.useAmbientOcclusion(), source.particleMaterial(), source.materialFlags());
    }

    private static void addTransformed(List<BakedQuad> output, List<BakedQuad> input, BlockAndTintGetter level,
                                       BlockPos pos, BlockState state, float tx, float ty, float tz, int turns,
                                       boolean suppressTint) {
        for (BakedQuad baked : input) {
            var quad = new MutableQuad().setFrom(baked);
            if (quad.tintIndex() >= 0) {
                if (suppressTint) quad.setTintIndex(-1);
                else {
                    var tint = Minecraft.getInstance().getBlockColors().getTintSource(state, quad.tintIndex());
                    if (tint != null) {
                        int color = tint.colorInWorld(state, level, pos) | 0xFF000000;
                        for (int vertex = 0; vertex < 4; vertex++)
                            quad.setColor(vertex, ARGB.multiply(quad.color(vertex), color));
                        quad.setTintIndex(-1);
                    }
                }
            }
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = quad.x(vertex), y = quad.y(vertex), z = quad.z(vertex);
                float rotatedX = switch (turns) { case 1 -> 1 - z; case 2 -> 1 - x; case 3 -> z; default -> x; };
                float rotatedZ = switch (turns) { case 1 -> x; case 2 -> 1 - z; case 3 -> 1 - x; default -> z; };
                quad.setPosition(vertex, rotatedX + tx, y + ty, rotatedZ + tz);
                float nx = quad.normalX(vertex), ny = quad.normalY(vertex), nz = quad.normalZ(vertex);
                if (!Float.isNaN(nx)) quad.setNormal(vertex,
                        switch (turns) { case 1 -> -nz; case 2 -> -nx; case 3 -> nz; default -> nx; }, ny,
                        switch (turns) { case 1 -> nx; case 2 -> -nz; case 3 -> -nx; default -> nz; });
            }
            Direction face = quad.direction();
            for (int i = 0; i < turns; i++) face = face.getClockWise();
            output.add(quad.setDirection(face).toBakedQuad());
        }
    }

    private static @Nullable CompositeRenderData data(BlockAndTintGetter level, BlockPos pos) {
        return ((IBlockGetterExtension) level).getModelData(pos).get(DATA);
    }

    private static boolean owns(BlockPos cell, PartInstance part) {
        int unit = NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        return Math.floorDiv(part.transform().x().units(), unit) == cell.getX()
                && Math.floorDiv(part.transform().y().units(), unit) == cell.getY()
                && Math.floorDiv(part.transform().z().units(), unit) == cell.getZ();
    }

    private record GeometryKey(@Nullable CompositeRenderData data, BlockPos pos) {}

    private record TransformedPart(List<BakedQuad> quads, boolean useAmbientOcclusion,
                                   Material.Baked particleMaterial, int materialFlags) implements BlockStateModelPart {
        @Override public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? quads : List.of();
        }
    }
}
