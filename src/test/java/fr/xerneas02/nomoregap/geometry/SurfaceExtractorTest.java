package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceExtractorTest {
    @Test void findsHighestSurfaceUnderClickedPoint() {
        var slab = Block.box(0, 0, 0, 16, 8, 16);
        assertEquals(FixedPoint.HALF_BLOCK, SurfaceExtractor.topAt(slab, 0.5, 0.5).orElseThrow().y());

        var table = Shapes.or(Block.box(0, 0, 0, 4, 12, 4), Block.box(0, 10, 0, 16, 12, 16));
        assertEquals(FixedPoint.fromDouble(0.75), SurfaceExtractor.topAt(table, 0.5, 0.5).orElseThrow().y());
        assertTrue(SurfaceExtractor.topAt(Shapes.block(), 0.5, 0.5).isEmpty());
        assertTrue(SurfaceExtractor.topAt(slab, 1.1, 0.5).isEmpty());
    }
}
