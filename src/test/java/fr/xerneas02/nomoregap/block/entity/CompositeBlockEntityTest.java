package fr.xerneas02.nomoregap.block.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeBlockEntityTest {
    @Test
    void pistonExtensionTakesTwoTicks() {
        assertEquals(0, CompositeBlockEntity.pistonExtensionProgress(10, 10, 0));
        assertEquals(0.5f, CompositeBlockEntity.pistonExtensionProgress(11, 10, 0));
        assertEquals(1, CompositeBlockEntity.pistonExtensionProgress(12, 10, 0));
    }
}
