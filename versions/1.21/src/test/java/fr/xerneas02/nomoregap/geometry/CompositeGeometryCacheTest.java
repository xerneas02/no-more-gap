package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeGeometryCacheTest {
    @Test void occupancyKeepsCollisionAboveTheSelectionBox() {
        var cache = new CompositeGeometryCache();
        cache.update(Shapes.box(0, 0, 0, 1, 1.5, 1), Shapes.block(), Shapes.empty());
        assertEquals(1.5, cache.occupancy().bounds().maxY);
    }
}
