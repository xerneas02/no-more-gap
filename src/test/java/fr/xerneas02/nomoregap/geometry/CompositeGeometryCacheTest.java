package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeGeometryCacheTest {
    @Test
    void occupancyKeepsCollisionAboveTheSelectionBox() {
        var cache = new CompositeGeometryCache();
        cache.update(Shapes.box(0, 0, 0, 1, 1.5, 1), Shapes.block(), Shapes.empty());
        assertEquals(1.5, cache.occupancy().bounds().maxY);
    }

    @Test
    void updateReplacesEveryShapeAndMarksValid() {
        var cache = new CompositeGeometryCache();
        cache.update(Shapes.block(), Shapes.block(), Shapes.box(0, 0, 0, 1, 1, 0.5));
        assertTrue(cache.isValid());
        assertEquals(1.0, cache.collision().bounds().maxY);
        assertEquals(0.5, cache.occlusion().bounds().maxZ);

        cache.update(Shapes.box(0, 0, 0, 1, 0.25, 1), Shapes.empty(), Shapes.empty());
        assertEquals(0.25, cache.collision().bounds().maxY);
        assertTrue(cache.selection().isEmpty());
        assertTrue(cache.occlusion().isEmpty());
        assertTrue(cache.isValid());
    }

    @Test
    void invalidateKeepsValuesButFlagsInvalid() {
        var cache = new CompositeGeometryCache();
        VoxelShape collision = Shapes.box(0, 0, 0, 1, 0.5, 1);
        cache.update(collision, Shapes.block(), Shapes.empty());
        cache.invalidate();
        assertFalse(cache.isValid());
        assertEquals(collision.bounds(), cache.collision().bounds(), "Stale values must remain readable");
        assertEquals(1.0, cache.occupancy().bounds().maxY);
    }

    @Test
    void freshCacheIsInvalidAndEmpty() {
        var cache = new CompositeGeometryCache();
        assertFalse(cache.isValid());
        assertTrue(cache.collision().isEmpty());
        assertTrue(cache.selection().isEmpty());
        assertTrue(cache.occlusion().isEmpty());
        assertTrue(cache.occupancy().isEmpty());
    }
}
