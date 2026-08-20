package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Scenario E (NeoForge): parts overflowing into a neighbouring cell are backed
 * by a proxy block/entity pointing at the source composite.
 */
@ForEachTest(groups = "no_more_gap.proxy")
public class ProxyGameTests extends NeoForgeTestBase {
    private static PartInstance slab(int id, double xBlocks, double yBlocks) {
        return new PartInstance(id, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                fixed(xBlocks, yBlocks, 0, 0), 0);
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "An overflowing part creates a proxy in the neighbouring cell")
    static void overflowingPartCreatesProxyInNeighboringCell(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(slab(0, 0, 0), slab(1, 1, 0)));

        var proxyPos = pos.offset(1, 0, 0);
        var proxy = requireProxy(helper, proxyPos, pos);
        helper.assertTrue(getBlockState(helper, proxyPos).getBlock() == ModBlocks.COMPOSITE_PROXY,
                "The proxy cell must hold a CompositeProxyBlock");

        var collision = getBlockState(helper, proxyPos).getCollisionShape(helper.getLevel(), proxyPos, CollisionContext.empty());
        var bounds = collision.bounds();
        helper.assertTrue(Math.abs(bounds.minX) < 1.0e-7 && Math.abs(bounds.minY) < 1.0e-7 && Math.abs(bounds.minZ) < 1.0e-7,
                "Proxy collision must start at the cell origin");
        helper.assertTrue(Math.abs(bounds.maxX - 1.0) < 1.0e-7 && Math.abs(bounds.maxY - 0.5) < 1.0e-7,
                "Proxy collision must match the clipped part geometry");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "Removing the overflowing part removes the proxy")
    static void removingOverflowingPartRemovesTheProxy(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(slab(0, 0, 0), slab(1, 1, 0)));
        var proxyPos = pos.offset(1, 0, 0);
        requireProxy(helper, proxyPos, pos);

        helper.assertTrue(composite.removePart(1), "The overflowing part must be removable");
        helper.assertTrue(getBlockState(helper, proxyPos).isAir(), "The proxy must be removed with the overflow");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "Overflow above the anchor creates a proxy")
    static void overflowingPartAboveCreatesProxy(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        createComposite(helper, pos, List.of(slab(0, 0, 0), slab(1, 0, 1)));
        requireProxy(helper, pos.offset(0, 1, 0), pos);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "Interacting through the proxy targets the source composite")
    static void interactingThroughProxyTargetsTheSourceComposite(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var lever = Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, false)
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        var composite = createComposite(helper, pos, List.of(
                slab(0, 0, 0),
                new PartInstance(1, lever, fixed(1, 0, 0, 0), 0)));

        var proxyPos = pos.offset(1, 0, 0);
        requireProxy(helper, proxyPos, pos);

        var player = newPlayer(helper);
        aimAtPart(helper, player, composite, 1);
        helper.assertTrue(interact(helper, player, proxyPos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Using the proxy must reach the source composite part");
        helper.assertTrue(requireComposite(helper, pos).parts().find(1).orElseThrow().state().getValue(LeverBlock.POWERED),
                "The lever part inside the source composite must toggle");
        helper.succeed();
    }
}
