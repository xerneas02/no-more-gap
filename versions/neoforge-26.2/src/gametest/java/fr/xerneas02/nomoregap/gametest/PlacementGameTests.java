package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.OverlapTester;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Scenario A (NeoForge): placing a torch on a bottom slab through the real
 * placement handler of the port must create a two-part composite with the
 * torch at Y=0.5, without overlap.
 */
@ForEachTest(groups = "no_more_gap.placement")
public class PlacementGameTests extends NeoForgeTestBase {
    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Slab + torch become a two-part composite")
    static void placingTorchOnSlabCreatesCompositeWithTwoParts(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));

        var player = newPlayer(helper);
        var result = placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        helper.assertTrue(result.consumesAction(), "Placing a torch on the slab must be consumed by the mod");

        var composite = requireComposite(helper, pos);
        helper.assertTrue(composite.parts().size() == 2, "The cell must hold exactly two parts");
        var slab = composite.parts().view().get(0);
        var torch = composite.parts().view().get(1);
        helper.assertTrue(slab.state().getBlock() == Blocks.OAK_SLAB, "The first part must be the slab");
        helper.assertTrue(LocalTransform.IDENTITY.equals(slab.transform()), "The slab must stay at Y=0");
        helper.assertTrue(torch.state().getBlock() == Blocks.TORCH, "The second part must be the torch");
        helper.assertTrue(torch.transform().y().units() == FixedPoint.HALF_BLOCK.units(),
                "The torch must be placed at Y=0.5 blocks");

        var level = helper.getLevel();
        var slabShape = slab.state().getShape(level, pos, CollisionContext.empty());
        var torchShape = torch.state().getShape(level, pos, CollisionContext.empty());
        helper.assertTrue(!OverlapTester.overlaps(slabShape, torchShape, torch.transform()),
                "The two parts must not overlap");

        helper.runAtTickTime(10, () -> {
            var late = requireComposite(helper, pos);
            helper.assertTrue(late.parts().size() == 2, "The composite must remain valid after ticks");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Full blocks reject placement")
    static void placingOnAFullBlockDoesNotCreateAComposite(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.STONE.defaultBlockState());

        var player = newPlayer(helper);
        var result = placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
        helper.assertTrue(!result.consumesAction(), "A torch cannot be stored on a full block");
        helper.assertTrue(!(getBlockState(helper, pos).getBlock() instanceof CompositeBlock),
                "The stone block must remain a vanilla block");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Composite stays valid and lit after ticks")
    static void compositeSurvivesTicksAndStaysLit(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        var player = newPlayer(helper);
        var result = placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        helper.assertTrue(result.consumesAction(), "Placing a torch must be consumed");

        helper.runAtTickTime(30, () -> {
            var composite = requireComposite(helper, pos);
            helper.assertTrue(composite.parts().size() == 2, "Parts must survive world ticks");
            helper.assertTrue(getBlockState(helper, pos).getValue(CompositeBlock.LIT),
                    "The torch must mark the composite as lit");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Adding a part preserves ids")
    static void addingAnotherPartKeepsIdsStable(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        var player = newPlayer(helper);
        helper.assertTrue(placeItem(helper, player, pos, Items.TORCH,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)).consumesAction(),
                "Placing the torch must be consumed");
        var composite = requireComposite(helper, pos);
        int slabId = composite.parts().view().get(0).id();
        int torchId = composite.parts().view().get(1).id();
        helper.assertTrue(slabId != torchId, "Ids must be distinct");

        helper.assertTrue(placeItem(helper, player, pos, Items.MOSS_CARPET,
                new Vec3(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5)).consumesAction(),
                "Placing the carpet must be consumed");
        var after = requireComposite(helper, pos);
        helper.assertTrue(after.parts().size() == 3, "A carpet must add a third part");
        helper.assertTrue(after.parts().find(slabId).isPresent(), "The slab id must be preserved");
        helper.assertTrue(after.parts().find(torchId).isPresent(), "The torch id must be preserved");
        helper.succeed();
    }
}
