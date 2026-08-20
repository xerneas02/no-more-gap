package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Scenario D (NeoForge): a real persistence cycle through the vanilla save/load
 * path (saveWithFullMetadata + chunk flush + BlockEntity.loadStatic).
 */
@ForEachTest(groups = "no_more_gap.persistence")
public class PersistenceGameTests extends NeoForgeTestBase {
    private static final int FORMED_ROCK = 1; // LavaLoggingReactions.FORMED_ROCK

    @GameTest(timeoutTicks = 300, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "A composite survives save and reload with exact parts")
    static void compositeSurvivesSaveAndReloadWithExactParts(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(2, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                        fixed(0, 0, 0, 0), 0),
                new PartInstance(8, Blocks.TORCH.defaultBlockState(), fixed(0, 0.5, 0, 2), 0),
                new PartInstance(15, Blocks.MOSS_CARPET.defaultBlockState(), fixed(0, 1.125, 0, 0), 4)));

        var level = helper.getLevel();
        long revisionBefore = composite.revision();
        var occupancyBefore = composite.geometry(level, CollisionContext.empty()).occupancy().bounds();

        var tag = composite.saveWithFullMetadata(level.registryAccess());
        level.getChunkSource().save(true);

        var state = getBlockState(helper, pos);
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var reloaded = (CompositeBlockEntity) BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
        level.setBlock(pos, state, 3);
        level.removeBlockEntity(pos);
        level.setBlockEntity(reloaded);

        var after = reloaded;
        helper.assertTrue(after.parts().size() == 3, "The reloaded composite must keep every part");
        helper.assertTrue(after.parts().view().stream().map(PartInstance::id).toList().equals(List.of(2, 8, 15)),
                "Part ids must survive the reload");
        helper.assertTrue(after.parts().view().equals(composite.parts().view()),
                "States, transforms and flags must survive the reload exactly");
        helper.assertTrue(after.revision() == revisionBefore, "The revision must survive the reload");
        helper.assertTrue(after.geometry(level, CollisionContext.empty()).occupancy().bounds().equals(occupancyBefore),
                "The geometry must be identical after reload");
        helper.assertTrue(after.parts().add(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0).id() == 16,
                "The next part id must be max(loaded ids) + 1");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 300, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "A proxy anchor survives reload")
    static void proxyAnchorSurvivesReload(ExtendedGameTestHelper helper) {
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

        helper.assertTrue(reloaded.anchor().equals(pos), "The proxy anchor must survive the reload");
        helper.assertTrue(composite.parts().size() >= 2, "The anchor composite must be untouched");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 300, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "A lava-logged part survives reload")
    static void lavaLoggedPartSurvivesReload(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var lavaSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(fr.xerneas02.nomoregap.lava.LavaLogging.LAVA_LOGGED, true);
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, lavaSlab, LocalTransform.IDENTITY, FORMED_ROCK)));

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
        helper.assertTrue(part.state().getValue(fr.xerneas02.nomoregap.lava.LavaLogging.LAVA_LOGGED),
                "LAVA_LOGGED must survive the reload");
        helper.assertTrue(part.flags() == FORMED_ROCK, "Flags must survive the reload");
        helper.succeed();
    }
}
