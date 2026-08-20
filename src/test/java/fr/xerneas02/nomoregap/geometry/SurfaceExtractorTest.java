package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceExtractorTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void findsHighestSurfaceUnderClickedPoint() {
        var slab = Block.box(0, 0, 0, 16, 8, 16);
        assertEquals(FixedPoint.HALF_BLOCK, SurfaceExtractor.topAt(slab, 0.5, 0.5).orElseThrow().y());

        var table = Shapes.or(Block.box(0, 0, 0, 4, 12, 4), Block.box(0, 10, 0, 16, 12, 16));
        assertEquals(FixedPoint.fromDouble(0.75), SurfaceExtractor.topAt(table, 0.5, 0.5).orElseThrow().y());
        assertEquals(FixedPoint.FULL_BLOCK, SurfaceExtractor.topAt(Shapes.block(), 0.5, 0.5).orElseThrow().y());
        assertTrue(SurfaceExtractor.topAt(slab, 1.1, 0.5).isEmpty());
    }

    @Test void findsLowestCeilingSurfaceUnderClickedPoint() {
        var topSlab = Block.box(0, 8, 0, 16, 16, 16);
        assertEquals(FixedPoint.HALF_BLOCK, SurfaceExtractor.bottomAt(topSlab, 0.5, 0.5).orElseThrow().y());
        assertTrue(SurfaceExtractor.bottomAt(topSlab, 1.1, 0.5).isEmpty());
    }

    @Test void usesFenceVisualTopInsteadOfItsTallCollision() {
        var fence = Blocks.OAK_FENCE.defaultBlockState();
        var world = EmptyBlockGetter.INSTANCE;
        var context = CollisionContext.empty();
        assertEquals(FixedPoint.FULL_BLOCK,
                SurfaceExtractor.topAt(fence.getShape(world, BlockPos.ZERO, context), 0.5, 0.5).orElseThrow().y());
        assertEquals(FixedPoint.fromDouble(1.5),
                SurfaceExtractor.topAt(fence.getCollisionShape(world, BlockPos.ZERO, context), 0.5, 0.5).orElseThrow().y());
    }

    /** The reported surface height must match the slab height in every vertical position. */
    @ParameterizedTest
    @ValueSource(doubles = {0.25, 0.5, 0.75, 1.0})
    void topAtReportsTheExactBoxHeight(double height) {
        var shape = Block.box(0, 0, 0, 16, (int) (height * 16), 16);
        var surface = SurfaceExtractor.topAt(shape, 0.5, 0.5).orElseThrow();
        assertEquals(SupportSurface.Kind.TOP, surface.kind());
        assertEquals(Direction.UP, surface.normal());
        assertEquals(FixedPoint.fromDouble(height), surface.y());
        assertEquals(FixedPoint.HALF_BLOCK, surface.x());
        assertEquals(FixedPoint.HALF_BLOCK, surface.z());
    }

    /** Out-of-shape clicks and empty shapes yield no surface. */
    @Test
    void emptyAndOutOfBoundsShapesYieldNoSurface() {
        assertTrue(SurfaceExtractor.topAt(Shapes.empty(), 0.5, 0.5).isEmpty());
        assertTrue(SurfaceExtractor.bottomAt(Shapes.empty(), 0.5, 0.5).isEmpty());
        assertTrue(SurfaceExtractor.topAt(Block.box(0, 0, 0, 4, 4, 4), 0.9, 0.5).isEmpty());
        assertTrue(SurfaceExtractor.bottomAt(Block.box(12, 12, 12, 16, 16, 16), 0.5, 0.5).isEmpty());
    }

    @Test
    void picksTheHighestBoxUnderThePoint() {
        var stacked = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(2, 4, 2, 14, 10, 14));
        assertEquals(FixedPoint.fromDouble(0.625), SurfaceExtractor.topAt(stacked, 0.5, 0.5).orElseThrow().y());
        assertEquals(FixedPoint.fromDouble(0.25), SurfaceExtractor.topAt(stacked, 0.9, 0.9).orElseThrow().y());
    }
}
