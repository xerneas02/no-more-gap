package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario D: a real persistence cycle. The block entity is written through the
 * vanilla save path ({@code saveWithFullMetadata}), the chunk is flushed to
 * disk, the block is wiped, and the entity is rebuilt through the real chunk
 * load path ({@code BlockEntity.loadStatic}).
 */
public class PersistenceGameTests extends GameTestBase {
    private static final int LAVA_FLAG = 1; // LavaLoggingReactions.FORMED_ROCK

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void compositeSurvivesSaveAndReloadWithExactParts(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(2, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                        fixed(0, 0, 0, 0), 0),
                new PartInstance(8, Blocks.TORCH.defaultBlockState(), fixed(0, 0.5, 0, 2), 0),
                new PartInstance(15, Blocks.MOSS_CARPET.defaultBlockState(), fixed(0, 1.125, 0, 0), 4)));

        var level = helper.getLevel();
        long revisionBefore = composite.revision();
        var occupancyBefore = composite.geometry(level, CollisionContext.empty()).occupancy().bounds();

        // 1. Real save: full metadata + flush the chunk through the region file.
        var tag = composite.saveWithFullMetadata(level.registryAccess());
        level.getChunkSource().save(true);

        // 2. Wipe the cell, then rebuild the entity through the real chunk-load path.
        var state = getBlockState(helper, pos);
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var reloaded = BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
        assertInstanceOf(CompositeBlockEntity.class, reloaded, "loadStatic must reconstruct the composite entity");
        level.setBlock(pos, state, 3);
        level.removeBlockEntity(pos);
        level.setBlockEntity(reloaded);

        var after = (CompositeBlockEntity) reloaded;
        assertEquals(3, after.parts().size(), "The reloaded composite must keep every part");
        assertEquals(List.of(2, 8, 15), after.parts().view().stream().map(PartInstance::id).toList(),
                "Part ids must survive the reload");
        assertEquals(composite.parts().view(), after.parts().view(),
                "States, transforms and flags must survive the reload exactly");
        assertEquals(revisionBefore, after.revision(), "The revision must survive the reload");
        assertEquals(occupancyBefore, after.geometry(level, CollisionContext.empty()).occupancy().bounds(),
                "The geometry must be identical after reload");

        // 3. The next allocated id must continue from the loaded maximum.
        assertEquals(16, after.parts().add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id(),
                "The next part id must be max(loaded ids) + 1");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void compositeSurvivesSaveAndReloadOfChunkData(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(1, Blocks.TORCH.defaultBlockState(), fixed(0, 0.5, 0, 0), 0)));

        var level = helper.getLevel();
        var tag = composite.saveWithFullMetadata(level.registryAccess());
        level.getChunkSource().save(true);

        var state = getBlockState(helper, pos);
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var reloaded = (CompositeBlockEntity) BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
        level.setBlock(pos, state, 3);
        level.removeBlockEntity(pos);
        level.setBlockEntity(reloaded);

        assertEquals(2, reloaded.parts().size());
        assertEquals(Blocks.TORCH, reloaded.parts().find(1).orElseThrow().state().getBlock());
        // A loaded composite must be fully functional: adding and removing parts still works.
        var added = reloaded.parts().add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertTrue(reloaded.removePart(added.id()));
        assertEquals(2, reloaded.parts().size());
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void proxyAnchorSurvivesReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(1, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(1, 0, 0, 0), 0)));
        var proxyPos = pos.offset(1, 0, 0);
        var proxy = requireProxy(helper, proxyPos, pos);

        var level = helper.getLevel();
        var tag = proxy.saveWithFullMetadata(level.registryAccess());
        level.getChunkSource().save(true);

        var state = getBlockState(helper, proxyPos);
        level.removeBlockEntity(proxyPos);
        level.setBlock(proxyPos, Blocks.AIR.defaultBlockState(), 3);
        var reloaded = (CompositeProxyBlockEntity) BlockEntity.loadStatic(proxyPos, state, tag, level.registryAccess());
        level.setBlock(proxyPos, state, 3);
        level.removeBlockEntity(proxyPos);
        level.setBlockEntity(reloaded);

        assertEquals(pos, reloaded.anchor(), "The proxy anchor must survive the reload");
        assertTrue(composite.parts().size() >= 2, "The anchor composite must be untouched");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void lavaLoggedPartSurvivesReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var lavaSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(fr.xerneas02.nomoregap.lava.LavaLogging.LAVA_LOGGED, true);
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, lavaSlab, LocalTransform.IDENTITY, LAVA_FLAG)));

        var level = helper.getLevel();
        var tag = composite.saveWithFullMetadata(level.registryAccess());
        level.getChunkSource().save(true);
        var state = getBlockState(helper, pos);
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var reloaded = (CompositeBlockEntity) BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
        level.setBlock(pos, state, 3);
        level.removeBlockEntity(pos);
        level.setBlockEntity(reloaded);

        var part = reloaded.parts().view().getFirst();
        assertTrue(part.state().getValue(fr.xerneas02.nomoregap.lava.LavaLogging.LAVA_LOGGED),
                "LAVA_LOGGED must survive the reload");
        assertEquals(LAVA_FLAG, part.flags(), "Flags must survive the reload");
        assertTrue(reloaded.getBlockState().getValue(fr.xerneas02.nomoregap.block.CompositeBlock.LAVA),
                "The composite block must still report lava");
        helper.succeed();
    }
}
