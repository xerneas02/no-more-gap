package fr.xerneas02.nomoregap.render;

import fr.xerneas02.nomoregap.lava.LavaLoggingReactions;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.renderdata.CompositeRenderData;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.DoublePlantBlock;

import java.util.List;
import java.util.function.Predicate;

/** Emits immutable composite parts into vanilla chunk buffers instead of submitting them every frame. */
public final class CompositeChunkModel implements BlockStateModel {
    private final BlockStateModel fallback;

    public CompositeChunkModel(BlockStateModel fallback) { this.fallback = fallback; }

    public static boolean isChunkRendered(PartInstance part) {
        return part.flags() != LavaLoggingReactions.FORMED_ROCK;
    }

    @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                                    RandomSource random, Predicate<Direction> cullTest) {
        if (!(((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof CompositeRenderData data)) return;
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        boolean snowyCell = data.parts().stream().anyMatch(part -> part.state().getBlock() instanceof SnowLayerBlock
                && owns(data.cellOffset(), part));
        for (var part : data.parts()) {
            if (!isChunkRendered(part) || !owns(data.cellOffset(), part)) continue;
            var offset = part.state().getOffset(pos);
            float tx = (float) (part.transform().xDouble() - data.cellOffset().getX() + offset.x);
            float ty = (float) (part.transform().yDouble() - data.cellOffset().getY() + offset.y);
            float tz = (float) (part.transform().zDouble() - data.cellOffset().getZ() + offset.z);
            int turns = part.transform().quarterTurns();
            boolean whiteVegetation = snowyCell && part.state().getBlock() instanceof VegetationBlock
                    && !(part.state().getBlock() instanceof DoublePlantBlock)
                    && !NoMoreGapConfig.snowLoggedVegetationBiomeTint();
            emitter.pushTransform(quad -> transform(quad, level, pos, part.state(), tx, ty, tz, turns, whiteVegetation));
            try {
                random.setSeed(pos.asLong() ^ part.id());
                models.get(part.state()).emitQuads(emitter, level, pos, part.state(), random, ignored -> false);
            } finally {
                emitter.popTransform();
            }
        }
    }

    @Override public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        Object data = ((FabricBlockGetter) level).getBlockEntityRenderData(pos);
        return data instanceof CompositeRenderData composite ? new GeometryKey(composite, pos.immutable()) : null;
    }

    @Override public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof CompositeRenderData data) {
            for (var part : data.parts()) if (owns(data.cellOffset(), part)) {
                return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(part.state())
                        .particleMaterial(level, pos, part.state());
            }
        }
        return fallback.particleMaterial();
    }

    @Override public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        int flags = 0;
        if (((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof CompositeRenderData data) {
            var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
            for (var part : data.parts()) if (isChunkRendered(part) && owns(data.cellOffset(), part)) {
                flags |= models.get(part.state()).materialFlags(level, pos, part.state(), random);
            }
        }
        return flags;
    }

    @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {}
    @Override public Material.Baked particleMaterial() { return fallback.particleMaterial(); }
    @Override public int materialFlags() { return -1; }

    private record GeometryKey(CompositeRenderData data, BlockPos pos) {}

    private static boolean owns(BlockPos cell, PartInstance part) {
        int unit = NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        return Math.floorDiv(part.transform().x().units(), unit) == cell.getX()
                && Math.floorDiv(part.transform().y().units(), unit) == cell.getY()
                && Math.floorDiv(part.transform().z().units(), unit) == cell.getZ();
    }

    private static boolean transform(net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView quad,
                                     BlockAndTintGetter level, BlockPos pos, BlockState state,
                                     float tx, float ty, float tz, int turns, boolean suppressTint) {
        if (quad.tintIndex() >= 0) {
            if (suppressTint) {
                quad.tintIndex(-1);
            } else {
                var tint = Minecraft.getInstance().getBlockColors().getTintSource(state, quad.tintIndex());
                if (tint != null) quad.multiplyColor(tint.colorInWorld(state, level, pos) | 0xFF000000).tintIndex(-1);
            }
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.x(vertex), y = quad.y(vertex), z = quad.z(vertex);
            float rotatedX = switch (turns) { case 1 -> 1 - z; case 2 -> 1 - x; case 3 -> z; default -> x; };
            float rotatedZ = switch (turns) { case 1 -> x; case 2 -> 1 - z; case 3 -> 1 - x; default -> z; };
            quad.pos(vertex, rotatedX + tx, y + ty, rotatedZ + tz);
            if (quad.hasNormal(vertex)) {
                float nx = quad.normalX(vertex), ny = quad.normalY(vertex), nz = quad.normalZ(vertex);
                float rotatedNx = switch (turns) { case 1 -> -nz; case 2 -> -nx; case 3 -> nz; default -> nx; };
                float rotatedNz = switch (turns) { case 1 -> nx; case 2 -> -nz; case 3 -> -nx; default -> nz; };
                quad.normal(vertex, rotatedNx, ny, rotatedNz);
            }
        }
        Direction face = quad.nominalFace();
        for (int i = 0; face != null && i < turns; i++) face = face.getClockWise();
        quad.nominalFace(face);
        quad.cullFace(null);
        return true;
    }
}
