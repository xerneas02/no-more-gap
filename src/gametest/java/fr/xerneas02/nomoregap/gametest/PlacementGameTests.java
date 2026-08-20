package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.OverlapTester;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario A: placing a torch on top of a bottom oak slab inside the same cell
 * must convert the cell into a composite holding exactly two non-overlapping
 * parts (slab at Y=0, torch at Y=0.5).
 */
public class PlacementGameTests extends GameTestBase {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void placingTorchOnSlabCreatesCompositeWithTwoParts(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));

        var player = newPlayer(helper);
        var result = placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        assertTrue(result.consumesAction(), "Placing a torch on the slab must be consumed by the mod");

        var composite = requireComposite(helper, pos);
        assertEquals(2, composite.parts().size(), "The cell must hold exactly two parts");
        var slab = composite.parts().view().get(0);
        var torch = composite.parts().view().get(1);

        assertEquals(Blocks.OAK_SLAB, slab.state().getBlock());
        assertEquals(LocalTransform.IDENTITY, slab.transform(), "The slab must stay at Y=0");
        assertEquals(Blocks.TORCH, torch.state().getBlock());
        assertEquals(0, torch.transform().x().units());
        assertEquals(FixedPoint.HALF_BLOCK.units(), torch.transform().y().units(),
                "The torch must be placed at Y=0.5 blocks");
        assertEquals(0, torch.transform().z().units());

        var level = helper.getLevel();
        var slabShape = slab.state().getShape(level, pos, CollisionContext.empty());
        var torchShape = torch.state().getShape(level, pos, CollisionContext.empty());
        assertFalse(OverlapTester.overlaps(slabShape, torchShape, torch.transform()),
                "The two parts must not overlap");
        assertTrue(OverlapTester.overlaps(slabShape, torchShape, LocalTransform.IDENTITY),
                "Sanity check: unshifted the torch would overlap the slab");

        // The world state must remain valid a few ticks later.
        helper.runAtTickTime(10, () -> {
            var late = requireComposite(helper, pos);
            assertEquals(2, late.parts().size());
            assertEquals(Blocks.TORCH, late.parts().find(torch.id()).orElseThrow().state().getBlock());
            helper.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void placingOnAFullBlockDoesNotCreateAComposite(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.STONE.defaultBlockState());

        var player = newPlayer(helper);
        var result = placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
        assertFalse(result.consumesAction(), "A torch cannot be stored on a full block");
        assertFalse(getBlockState(helper, pos).getBlock() instanceof CompositeBlock,
                "The stone block must remain a vanilla block");
        helper.succeed();
    }

        @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void compositeKeepsPartsAfterSeveralWorldTicks(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        var player = newPlayer(helper);
        var result = placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        assertTrue(result.consumesAction());

        helper.runAtTickTime(30, () -> {
            var composite = requireComposite(helper, pos);
            assertEquals(2, composite.parts().size(), "Parts must survive world ticks");
            var state = getBlockState(helper, pos);
            assertTrue(state.getValue(CompositeBlock.LIT), "The torch must mark the composite as lit");
            helper.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void addingTwoPartsKeepsIdsStable(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        var player = newPlayer(helper);
        assertTrue(placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)).consumesAction());
        var composite = requireComposite(helper, pos);
        int slabId = composite.parts().view().get(0).id();
        int torchId = composite.parts().view().get(1).id();
        assertNotEquals(slabId, torchId);

        // Sneak-placing a carpet on top of the torch adds a third part with a fresh id.
        assertTrue(placeItem(helper, player, pos, Items.MOSS_CARPET,
                new Vec3(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5)).consumesAction());
        var after = requireComposite(helper, pos);
        assertEquals(3, after.parts().size());
        assertTrue(after.parts().find(slabId).isPresent(), "The slab id must be preserved");
        assertTrue(after.parts().find(torchId).isPresent(), "The torch id must be preserved");
        helper.succeed();
    }
}
