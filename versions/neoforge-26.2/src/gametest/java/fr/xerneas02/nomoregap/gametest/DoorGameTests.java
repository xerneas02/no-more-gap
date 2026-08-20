package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Scenario C (NeoForge): doors stored as composite parts, coherent open/close
 * interaction and removal.
 */
@ForEachTest(groups = "no_more_gap.door")
public class DoorGameTests extends NeoForgeTestBase {
    private static final int DOOR_UNITS = 256;

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "Placing a door on a slab stores both halves as parts")
    static void placingDoorOnSlabStoresBothHalvesAsParts(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));

        var player = newPlayer(helper);
        player.setYRot(0);
        player.yHeadRot = 0;
        var result = placeItem(helper, player, pos, Items.OAK_DOOR,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        helper.assertTrue(result.consumesAction(), "Placing a door on the slab must be consumed");

        var composite = requireComposite(helper, pos);
        helper.assertTrue(composite.parts().size() == 3, "Slab + two door halves");
        var lower = composite.parts().view().get(1);
        var upper = composite.parts().view().get(2);
        helper.assertTrue(lower.state().getBlock() == Blocks.OAK_DOOR && upper.state().getBlock() == Blocks.OAK_DOOR,
                "Both halves must be doors");
        helper.assertTrue(lower.id() != upper.id(), "The two door halves must have distinct ids");
        helper.assertTrue(lower.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && upper.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "Halves must be LOWER/UPPER");
        helper.assertTrue(lower.transform().y().units() == FixedPoint.HALF_BLOCK.units(),
                "The lower half must sit on the slab top (Y=0.5)");
        helper.assertTrue(upper.transform().y().units() == FixedPoint.HALF_BLOCK.units() + DOOR_UNITS,
                "The upper half must be one full block above the lower half");
        helper.assertTrue(lower.state().getValue(DoorBlock.FACING) == upper.state().getValue(DoorBlock.FACING),
                "Both halves must share the same facing");
        requireProxy(helper, pos.offset(0, 1, 0), pos);
        requireProxy(helper, pos.offset(0, 2, 0), pos);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "Interacting with a door toggles both halves in sync")
    static void interactingWithDoorTogglesBothHalves(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(1, door(DoubleBlockHalf.LOWER, false),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0),
                new PartInstance(2, door(DoubleBlockHalf.UPPER, false),
                        new LocalTransform(FixedPoint.ZERO, new FixedPoint(FixedPoint.HALF_BLOCK.units() + DOOR_UNITS), FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        aimAtPart(helper, player, composite, 1);
        helper.assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Interacting with the door must be consumed");
        var afterOpen = requireComposite(helper, pos);
        helper.assertTrue(afterOpen.parts().find(1).orElseThrow().state().getValue(DoorBlock.OPEN),
                "Lower half must open");
        helper.assertTrue(afterOpen.parts().find(2).orElseThrow().state().getValue(DoorBlock.OPEN),
                "Upper half must open in sync");

        aimAtPart(helper, player, afterOpen, 1);
        helper.assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Closing the door must be consumed");
        var afterClose = requireComposite(helper, pos);
        helper.assertTrue(!afterClose.parts().find(1).orElseThrow().state().getValue(DoorBlock.OPEN),
                "Lower half must close");
        helper.assertTrue(!afterClose.parts().find(2).orElseThrow().state().getValue(DoorBlock.OPEN),
                "Upper half must close in sync");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "6x6x6")
    @TestHolder(description = "Removing the source door cleans up the whole composite")
    static void removingTheSourceDoorCleansUpTheWholeComposite(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.MOSS_CARPET.defaultBlockState(), LocalTransform.IDENTITY, 0),
                new PartInstance(1, door(DoubleBlockHalf.LOWER, false),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0),
                new PartInstance(2, door(DoubleBlockHalf.UPPER, false),
                        new LocalTransform(FixedPoint.ZERO, new FixedPoint(FixedPoint.HALF_BLOCK.units() + DOOR_UNITS), FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        aimAtPart(helper, player, composite, 1);
        var state = getBlockState(helper, pos);
        ((CompositeBlock) state.getBlock()).playerDestroy(helper.getLevel(), player, pos, state, composite, ItemStack.EMPTY);

        assertNoCompositeAt(helper, pos);
        helper.assertTrue(getBlockState(helper, pos).isAir(),
                "Destroying the source part must remove the whole composite");
        assertNoProxyNear(helper, pos, 4);
        helper.succeed();
    }

    private static BlockState door(DoubleBlockHalf half, boolean open) {
        return Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, half)
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.OPEN, open)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.POWERED, false);
    }
}
