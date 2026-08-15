package fr.xerneas02.nomoregap.worldgen;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class SnowyVegetationFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, NoMoreGap.id("snowy_vegetation"));

    private SnowyVegetationFeature() { super(NoneFeatureConfiguration.CODEC); }

    public static void initialize() {
        net.minecraft.core.Registry.register(BuiltInRegistries.FEATURE, NoMoreGap.id("snowy_vegetation"),
                new SnowyVegetationFeature());
        if (NoMoreGapConfig.snowyVegetationGeneration()) {
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                    GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
        }
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        int startX = context.origin().getX();
        int startZ = context.origin().getZ();
        boolean placed = false;
        for (int x = startX; x < startX + 16; x++) for (int z = startZ; z < startZ + 16; z++) {
            var pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            var plant = level.getBlockState(pos);
            if (plant.getBlock() instanceof BushBlock) {
                if (plant.is(net.minecraft.tags.BlockTags.CROPS)
                        || level.getBlockState(pos.below()).is(Blocks.FARMLAND)) continue;
            } else {
                pos = pos.below();
                plant = level.getBlockState(pos);
                if (!(plant.getBlock() instanceof net.minecraft.world.level.block.FenceBlock)
                        && !(plant.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock)) continue;
                if (!level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) continue;
            }
            if (!level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) continue;
            if (!level.setBlock(pos, ModBlocks.COMPOSITE.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS)) continue;
            if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) {
                level.setBlock(pos, plant, Block.UPDATE_CLIENTS);
                continue;
            }
            composite.beginUpdate();
            try {
                composite.addPart(plant, LocalTransform.IDENTITY, 0);
                composite.addPart(Blocks.SNOW.defaultBlockState(), LocalTransform.IDENTITY, 0);
            } finally {
                composite.endUpdate();
            }
            level.scheduleTick(pos, ModBlocks.COMPOSITE, 1);
            var groundPos = pos.below();
            var ground = level.getBlockState(groundPos);
            if (ground.hasProperty(BlockStateProperties.SNOWY)) {
                level.setBlock(groundPos, ground.setValue(BlockStateProperties.SNOWY, true), Block.UPDATE_CLIENTS);
            }
            placed = true;
        }
        return placed;
    }
}
