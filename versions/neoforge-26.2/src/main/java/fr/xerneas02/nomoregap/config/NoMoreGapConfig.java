package fr.xerneas02.nomoregap.config;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class NoMoreGapConfig {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("no_more_gap.properties");
    private static int renderDistanceChunks;
    private static int maxCompositeParts = 64;
    private static boolean lavaLoggingReactions = true;
    private static boolean snowLoggedVegetationBiomeTint;
    private static boolean snowyVegetationGeneration = true;

    private NoMoreGapConfig() {}

    public static void initialize() {
        var properties = new Properties();
        if (Files.exists(PATH)) try (var reader = Files.newBufferedReader(PATH)) {
            properties.load(reader);
        } catch (IOException error) {
            NoMoreGap.LOGGER.warn("Could not load {}. Using defaults.", PATH, error);
        }
        renderDistanceChunks = boundedInt(properties, "composite_render_distance_chunks", 0, 0, 64);
        maxCompositeParts = boundedInt(properties, "max_composite_parts_default", 64, 2, 64);
        lavaLoggingReactions = bool(properties, "do_lava_logging_reactions_default", true);
        snowLoggedVegetationBiomeTint = bool(properties, "snow_logged_vegetation_biome_tint", false);
        snowyVegetationGeneration = bool(properties, "snowy_vegetation_generation", true);
        properties.setProperty("composite_render_distance_chunks", Integer.toString(renderDistanceChunks));
        properties.setProperty("max_composite_parts_default", Integer.toString(maxCompositeParts));
        properties.setProperty("do_lava_logging_reactions_default", Boolean.toString(lavaLoggingReactions));
        properties.setProperty("snow_logged_vegetation_biome_tint", Boolean.toString(snowLoggedVegetationBiomeTint));
        properties.setProperty("snowy_vegetation_generation", Boolean.toString(snowyVegetationGeneration));
        try {
            Files.createDirectories(PATH.getParent());
            try (var writer = Files.newBufferedWriter(PATH)) { properties.store(writer, "No More Gap configuration"); }
        } catch (IOException error) {
            NoMoreGap.LOGGER.warn("Could not save {}.", PATH, error);
        }
    }

    public static int renderDistanceBlocks(int vanillaChunks) { return (renderDistanceChunks == 0 ? vanillaChunks : Math.min(renderDistanceChunks, vanillaChunks)) * 16; }
    public static int maxCompositeParts() { return maxCompositeParts; }
    public static boolean lavaLoggingReactions() { return lavaLoggingReactions; }
    public static boolean snowLoggedVegetationBiomeTint() { return snowLoggedVegetationBiomeTint; }
    public static boolean snowyVegetationGeneration() { return snowyVegetationGeneration; }

    private static int boundedInt(Properties p, String key, int fallback, int min, int max) {
        int value = Integer.parseInt(p.getProperty(key, Integer.toString(fallback)).trim());
        if (value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        return value;
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key, Boolean.toString(fallback)).trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException(key + " must be true or false");
        return Boolean.parseBoolean(value);
    }
}
