package fr.xerneas02.nomoregap.interaction;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeUseContextTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void attachedControlsSurviveTransientAirWrites() {
        assertTrue(CompositeUseContext.keepsAttachedControl(
                Blocks.STONE_BUTTON.defaultBlockState(), Blocks.AIR.defaultBlockState()));
        assertTrue(CompositeUseContext.keepsAttachedControl(
                Blocks.LEVER.defaultBlockState(), Blocks.AIR.defaultBlockState()));
        assertFalse(CompositeUseContext.keepsAttachedControl(
                Blocks.TNT.defaultBlockState(), Blocks.AIR.defaultBlockState()));
    }

    @Test void unrelatedCompositeWritesAreNotCapturedAsButtonState() {
        assertTrue(CompositeUseContext.belongsToPart(
                Blocks.STONE_BUTTON.defaultBlockState(), Blocks.STONE_BUTTON.defaultBlockState()));
        assertTrue(CompositeUseContext.belongsToPart(
                Blocks.TNT.defaultBlockState(), Blocks.AIR.defaultBlockState()));
        assertFalse(CompositeUseContext.belongsToPart(
                Blocks.STONE_BUTTON.defaultBlockState(),
                Blocks.REDSTONE_LAMP.defaultBlockState()));
    }
}
