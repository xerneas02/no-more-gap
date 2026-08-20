package fr.xerneas02.nomoregap.part;

import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour-focused tests for {@link PartContainer}: id allocation, loaded ids,
 * replaceAll semantics, limits and the container lifecycle.
 */
class PartContainerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PartInstance part(int id, int yUnits, int flags) {
        var state = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        return new PartInstance(id, state, new LocalTransform(FixedPoint.ZERO, new FixedPoint(yUnits), FixedPoint.ZERO, 0), flags);
    }

    private static PartInstance part(int id) { return part(id, 0, 0); }

    @Test
    void addAllocatesSequentialIdsFromZero() {
        var parts = new PartContainer();
        assertEquals(0, parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id());
        assertEquals(1, parts.add(Blocks.DIRT.defaultBlockState(), LocalTransform.IDENTITY, 0).id());
        assertEquals(2, parts.add(Blocks.OAK_SLAB.defaultBlockState(), LocalTransform.IDENTITY, 0).id());
    }

    @Test
    void removeThenAddKeepsMonotonicIds() {
        var parts = new PartContainer();
        var first = parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        var second = parts.add(Blocks.DIRT.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertTrue(parts.remove(first.id()));
        var third = parts.add(Blocks.GRASS_BLOCK.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertEquals(second.id() + 1, third.id(), "Removed ids must never be reused");
        assertTrue(parts.find(first.id()).isEmpty());
        assertTrue(parts.find(second.id()).isPresent());
        assertTrue(parts.find(third.id()).isPresent());
    }

    @Test
    void loadedNonContiguousIdsRebuildNextId() {
        var parts = new PartContainer();
        assertTrue(parts.addLoaded(part(2)));
        assertTrue(parts.addLoaded(part(8)));
        assertTrue(parts.addLoaded(part(15)));
        assertEquals(16, parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id(),
                "The next allocated id must be max(loaded) + 1");
        assertEquals(17, parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id());
    }

    @Test
    void loadedIdLowerThanCurrentNextIdDoesNotRewind() {
        var parts = new PartContainer();
        parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0); // nextId -> 1
        assertTrue(parts.addLoaded(part(10)));                                    // nextId -> 11
        assertTrue(parts.addLoaded(part(3)));                                     // must NOT rewind
        assertEquals(11, parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id());
    }

    @Test
    void addLoadedRejectsDuplicateAndFullContainer() {
        var parts = new PartContainer();
        assertTrue(parts.addLoaded(part(1)));
        assertFalse(parts.addLoaded(part(1)), "Duplicate loaded id must be rejected");
        for (int i = 0; i < NoMoreGapLimits.MAX_PARTS_PER_CELL - 1; i++) {
            assertTrue(parts.addLoaded(part(100 + i)));
        }
        assertFalse(parts.addLoaded(part(999)), "Full container must reject loaded parts");
    }

    @Test
    void replacePreservesIdTransformAndFlags() {
        var parts = new PartContainer();
        var original = parts.add(Blocks.STONE.defaultBlockState(),
                new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 2), 7);
        assertTrue(parts.replace(original.id(), Blocks.DIRT.defaultBlockState()));
        var replaced = parts.find(original.id()).orElseThrow();
        assertEquals(Blocks.DIRT.defaultBlockState(), replaced.state());
        assertEquals(original.id(), replaced.id());
        assertEquals(original.transform(), replaced.transform());
        assertEquals(original.flags(), replaced.flags());
        assertFalse(parts.replace(original.id() + 100, Blocks.DIRT.defaultBlockState()));
    }

    @Test
    void replaceAllPreservesIdsAndRebuildsNextId() {
        var parts = new PartContainer();
        parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        var replacement = List.of(part(2, 128, 1), part(8, 0, 0), part(15, 256, 3));
        parts.replaceAll(replacement);
        assertEquals(List.of(2, 8, 15), parts.view().stream().map(PartInstance::id).toList());
        assertEquals(3, parts.size());
        assertEquals(16, parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id());
        assertEquals(replacement, parts.view().subList(0, 3));
    }

    @Test
    void replaceAllRejectsDuplicateIds() {
        var parts = new PartContainer();
        assertThrows(IllegalArgumentException.class, () -> parts.replaceAll(List.of(part(1), part(1))));
    }

    @Test
    void replaceAllRejectsMoreThanTheTechnicalLimit() {
        var parts = new PartContainer();
        var tooMany = java.util.stream.IntStream.range(0, NoMoreGapLimits.MAX_PARTS_PER_CELL + 1)
                .mapToObj(id -> part(id)).toList();
        assertThrows(IllegalArgumentException.class, () -> parts.replaceAll(tooMany));
        assertTrue(parts.isEmpty(), "Failed replaceAll must not mutate the container");
    }

    @Test
    void replaceAllWithEmptyListResetsNextId() {
        var parts = new PartContainer();
        parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        parts.replaceAll(List.of());
        assertTrue(parts.isEmpty());
        assertEquals(0, parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id());
    }

    @Test
    void clearKeepsTheAllocationSequence() {
        var parts = new PartContainer();
        parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        parts.add(Blocks.DIRT.defaultBlockState(), LocalTransform.IDENTITY, 0);
        parts.clear();
        assertTrue(parts.isEmpty());
        assertEquals(2, parts.add(Blocks.GRASS_BLOCK.defaultBlockState(), LocalTransform.IDENTITY, 0).id(),
                "clear() empties the list but must not rewind id allocation");
    }

    @Test
    void addRefusesWhenAtTheTechnicalLimit() {
        var parts = new PartContainer();
        for (int i = 0; i < NoMoreGapLimits.MAX_PARTS_PER_CELL; i++) {
            parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        }
        assertThrows(IllegalStateException.class,
                () -> parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0));
    }

    @Test
    void findAndRemoveOperateOnIdsOnly() {
        var parts = new PartContainer();
        var a = parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        var b = parts.add(Blocks.DIRT.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertTrue(parts.find(b.id()).isPresent());
        assertFalse(parts.remove(a.id() + 100));
        assertTrue(parts.remove(b.id()));
        assertTrue(parts.find(b.id()).isEmpty());
        assertEquals(1, parts.size());
    }

    @Test
    void viewIsUnmodifiableAndLive() {
        var parts = new PartContainer();
        parts.add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        var view = parts.view();
        assertThrows(UnsupportedOperationException.class, view::clear);
        parts.add(Blocks.DIRT.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertEquals(2, view.size(), "The view must reflect subsequent mutations");
    }

    @Test
    void invalidPartInstancesAreRejectedByTheRecord() {
        assertThrows(IllegalArgumentException.class, () -> new PartInstance(-1, Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0));
        assertThrows(NullPointerException.class, () -> new PartInstance(0, null, LocalTransform.IDENTITY, 0));
        assertThrows(NullPointerException.class, () -> new PartInstance(0, Blocks.STONE.defaultBlockState(), null, 0));
    }
}
