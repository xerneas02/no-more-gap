package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.lava.LavaLogging;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the unit-test JVM actually runs under the Fabric Loader (via
 * {@code fabric-loader-junit}), so game classes are loaded with the mod's
 * mixin transformations applied.
 */
class LoaderEnvironmentTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fabricLoaderIsActiveAndSeesTheMod() {
        var loader = FabricLoader.getInstance();
        assertNotNull(loader);
        var mod = loader.getModContainer("no_more_gap").orElseThrow();
        assertEquals("no_more_gap", mod.getMetadata().getId());
        assertTrue(loader.isDevelopmentEnvironment());
    }

    /** WaterloggedStateDefinitionMixin injects LAVA_LOGGED into every waterloggable block. */
    @Test
    void lavaLoggingPropertyIsInjectedByMixin() {
        assertTrue(Blocks.OAK_SLAB.defaultBlockState().hasProperty(LavaLogging.LAVA_LOGGED),
                "The LAVA_LOGGED property should be injected by the mixin into waterloggable blocks");
        assertTrue(Blocks.OAK_STAIRS.defaultBlockState().hasProperty(LavaLogging.LAVA_LOGGED));
        assertFalse(Blocks.STONE.defaultBlockState().hasProperty(LavaLogging.LAVA_LOGGED));
    }
}
