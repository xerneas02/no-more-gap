package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario E: parts whose geometry overflows into a neighbouring cell must be
 * backed by a {@code CompositeProxyBlock}/{@code CompositeProxyBlockEntity}
 * pointing at the source composite, and the proxy must disappear as soon as the
 * overflow is gone.
 */
public class ProxyGameTests extends GameTestBase {
    private static fr.xerneas02.nomoregap.part.PartInstance slab(int id, double xBlocks, double yBlocks) {
        return new PartInstance(id, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                fixed(xBlocks, yBlocks, 0, 0), 0);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void overflowingPartCreatesProxyInNeighboringCell(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(slab(0, 0, 0), slab(1, 1, 0)));

        var proxyPos = pos.offset(1, 0, 0);
        var proxy = requireProxy(helper, proxyPos, pos);
        assertEquals(ModBlocks.COMPOSITE_PROXY, getBlockState(helper, proxyPos).getBlock());

        // The proxy collision must be the part portion inside that cell.
        var collision = getBlockState(helper, proxyPos).getCollisionShape(helper.getLevel(), proxyPos, CollisionContext.empty());
        var bounds = collision.bounds();
        assertEquals(0.0, bounds.minX, 1.0e-7);
        assertEquals(0.0, bounds.minY, 1.0e-7);
        assertEquals(0.0, bounds.minZ, 1.0e-7);
        assertEquals(1.0, bounds.maxX, 1.0e-7);
        assertEquals(0.5, bounds.maxY, 1.0e-7);
        assertEquals(1.0, bounds.maxZ, 1.0e-7);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void removingOverflowingPartRemovesTheProxy(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(slab(0, 0, 0), slab(1, 1, 0)));
        var proxyPos = pos.offset(1, 0, 0);
        requireProxy(helper, proxyPos, pos);

        assertTrue(composite.removePart(1));
        assertTrue(getBlockState(helper, proxyPos).isAir(), "The proxy must be removed with the overflow");
        assertNoProxyNear(helper, pos, 3);
        assertEquals(1, requireComposite(helper, pos).parts().size());
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void overflowingPartInMinusXCreatesProxy(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 2));
        createComposite(helper, pos, List.of(slab(0, 0, 0), slab(1, -1, 0)));
        requireProxy(helper, pos.offset(-1, 0, 0), pos);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void overflowingPartAboveCreatesProxy(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        createComposite(helper, pos, List.of(slab(0, 0, 0), slab(1, 0, 1)));
        requireProxy(helper, pos.offset(0, 1, 0), pos);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void interactingThroughProxyTargetsTheSourceComposite(GameTestHelper helper) {
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
        assertTrue(interact(helper, player, proxyPos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Using the proxy must reach the source composite part");
        assertTrue(requireComposite(helper, pos).parts().find(1).orElseThrow().state().getValue(LeverBlock.POWERED),
                "The lever part inside the source composite must toggle");
        helper.succeed();
    }
}
