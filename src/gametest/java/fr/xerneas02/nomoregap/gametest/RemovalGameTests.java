package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario B: removing a single part through the real block-break path
 * ({@code CompositeBlock.playerDestroy}) must keep every other part and its id,
 * and a lone whole-cell part must convert back to the vanilla block.
 */
public class RemovalGameTests extends GameTestBase {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void removingTorchKeepsCarpetAndSlab(GameTestHelper helper) {
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
        assertEquals(2, after.parts().size(), "The composite must remain with two parts");
        assertTrue(after.parts().find(15).isEmpty(), "The torch must be gone");
        assertEquals(Blocks.OAK_SLAB, after.parts().find(2).orElseThrow().state().getBlock(), "The slab must remain");
        assertEquals(Blocks.MOSS_CARPET, after.parts().find(8).orElseThrow().state().getBlock(), "The carpet must remain");
        assertEquals(LocalTransform.IDENTITY, after.parts().find(2).orElseThrow().transform(), "Slab transform unchanged");
        assertEquals(new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0),
                after.parts().find(8).orElseThrow().transform(), "Carpet transform unchanged");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void removingTorchFromSlabTorchConvertsBackToVanillaSlab(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(1, Blocks.TORCH.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        breakPart(helper, player, composite, 1);

        assertNoCompositeAt(helper, pos);
        assertEquals(Blocks.OAK_SLAB, getBlockState(helper, pos).getBlock(),
                "A lone whole-cell part must convert back to the vanilla slab");
        assertEquals(SlabType.BOTTOM, getBlockState(helper, pos).getValue(SlabBlock.TYPE));
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void removingTheOnlyPartLeavesAnAirBlock(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.TORCH.defaultBlockState(), LocalTransform.IDENTITY, 0)));

        var player = newPlayer(helper);
        breakPart(helper, player, composite, 0);

        assertNoCompositeAt(helper, pos);
        assertTrue(getBlockState(helper, pos).isAir(), "Removing the last part must remove the composite block");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void removingAnUnknownPartIsHarmless(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(1, Blocks.TORCH.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));

        assertFalse(composite.removePart(99), "Removing an unknown id must fail");
        assertEquals(2, composite.parts().size());
        assertTrue(getBlockState(helper, pos).getBlock() instanceof CompositeBlock);
        helper.succeed();
    }

    /** Breaks the composite block the way a player would, targeting the given part. */
    private void breakPart(GameTestHelper helper, TestPlayer player, fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity composite, int partId) {
        var pos = composite.getBlockPos();
        var state = getBlockState(helper, pos);
        aimAtPart(helper, player, composite, partId);
        assertFalse(partShape(helper, composite, partId).toAabbs().isEmpty(), "The targeted part must have geometry");
        ((CompositeBlock) state.getBlock()).playerDestroy(helper.getLevel(), player, pos, state, composite, ItemStack.EMPTY);
    }
}
