package fr.xerneas02.nomoregap.part;

import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PartContainerTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void ownsIdsAndImmutableView() {
        var parts = new PartContainer();
        var first = parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        var second = parts.add(Blocks.DIRT.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertNotEquals(first.id(), second.id());
        assertEquals(first, parts.find(first.id()).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> parts.view().clear());
        assertTrue(parts.remove(first.id()));
        parts.clear();
        assertTrue(parts.isEmpty());
    }

    @Test void rejectsSeventeenthAndDuplicateLoadedId() {
        var parts = new PartContainer();
        for (int i = 0; i < NoMoreGapLimits.MAX_PARTS_PER_CELL; i++) parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertEquals(NoMoreGapLimits.MAX_PARTS_PER_CELL, parts.size());
        assertThrows(IllegalStateException.class, () -> parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0));
        assertFalse(parts.addLoaded(parts.view().getFirst()));
    }
}
