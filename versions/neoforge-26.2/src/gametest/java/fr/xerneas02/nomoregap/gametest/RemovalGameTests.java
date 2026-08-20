package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Scenario B (NeoForge): removing a single part through the real block-break
 * path keeps every other part and its id, and a lone whole-cell part converts
 * back to the vanilla block.
 */
@ForEachTest(groups = "no_more_gap.removal")
public class RemovalGameTests extends NeoForgeTestBase {
    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Removing a torch keeps the carpet and the slab")
    static void removingTorchKeepsCarpetAndSlab(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(2, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(8, Blocks.MOSS_CARPET.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0),
                new PartInstance(15, Blocks.TORCH.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, new FixedPoint(144), FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        breakPart(helper, player, composite, 15);

        var after = requireComposite(helper, pos);
        helper.assertTrue(after.parts().size() == 2, "The composite must remain with two parts");
        helper.assertTrue(after.parts().find(15).isEmpty(), "The torch must be gone");
        helper.assertTrue(after.parts().find(2).isPresent() && after.parts().find(2).orElseThrow().state().getBlock() == Blocks.OAK_SLAB,
                "The slab must remain");
        helper.assertTrue(after.parts().find(8).isPresent() && after.parts().find(8).orElseThrow().state().getBlock() == Blocks.MOSS_CARPET,
                "The carpet must remain");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Removing the torch from slab+torch converts back to a vanilla slab")
    static void removingTorchFromSlabTorchConvertsBackToVanillaSlab(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(1, Blocks.TORCH.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        breakPart(helper, player, composite, 1);

        assertNoCompositeAt(helper, pos);
        helper.assertTrue(getBlockState(helper, pos).getBlock() == Blocks.OAK_SLAB,
                "A lone whole-cell part must convert back to the vanilla slab");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Removing the only part leaves an air block")
    static void removingTheOnlyPartLeavesAnAirBlock(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.TORCH.defaultBlockState(), LocalTransform.IDENTITY, 0)));

        var player = newPlayer(helper);
        breakPart(helper, player, composite, 0);

        assertNoCompositeAt(helper, pos);
        helper.assertTrue(getBlockState(helper, pos).isAir(), "Removing the last part must remove the composite");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    private static void breakPart(ExtendedGameTestHelper helper, TestPlayer player,
                                  fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity composite, int partId) {
        var pos = composite.getBlockPos();
        var state = getBlockState(helper, pos);
        aimAtPart(helper, player, composite, partId);
        ((CompositeBlock) state.getBlock()).playerDestroy(helper.getLevel(), player, pos, state, composite, ItemStack.EMPTY);
    }
}
