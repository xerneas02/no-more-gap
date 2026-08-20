package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario C: doors stored as composite parts. Two halves with distinct ids and
 * correct Y offsets, coherent open/close interaction, and a removal that drops
 * both halves and converts the remaining lone part back to a vanilla block.
 */
public class DoorGameTests extends GameTestBase {
    private static final int DOOR_UNITS = 256;

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void placingDoorOnSlabStoresBothHalvesAsParts(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));

        var player = newPlayer(helper);
        player.setYRot(0); // facing SOUTH
        player.yHeadRot = 0;
        var result = placeItem(helper, player, pos, Items.OAK_DOOR,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        assertTrue(result.consumesAction(), "Placing a door on the slab must be consumed");

        var composite = requireComposite(helper, pos);
        assertEquals(3, composite.parts().size(), "Slab + two door halves");
        var slab = composite.parts().view().get(0);
        var lower = composite.parts().view().get(1);
        var upper = composite.parts().view().get(2);

        assertEquals(Blocks.OAK_SLAB, slab.state().getBlock());
        assertEquals(Blocks.OAK_DOOR, lower.state().getBlock());
        assertEquals(Blocks.OAK_DOOR, upper.state().getBlock());
        assertNotEquals(lower.id(), upper.id(), "The two door halves must have distinct ids");

        assertEquals(DoubleBlockHalf.LOWER, lower.state().getValue(DoorBlock.HALF));
        assertEquals(DoubleBlockHalf.UPPER, upper.state().getValue(DoorBlock.HALF));
        assertEquals(0, lower.transform().x().units());
        assertEquals(FixedPoint.HALF_BLOCK.units(), lower.transform().y().units(),
                "The lower half must sit on the slab top (Y=0.5)");
        assertEquals(0, lower.transform().z().units());
        assertEquals(0, upper.transform().x().units());
        assertEquals(FixedPoint.HALF_BLOCK.units() + DOOR_UNITS, upper.transform().y().units(),
                "The upper half must be one full block above the lower half");
        assertEquals(0, upper.transform().z().units());
        assertEquals(lower.state().getValue(DoorBlock.FACING), upper.state().getValue(DoorBlock.FACING),
                "Both halves must share the same facing");
        assertEquals(Direction.SOUTH, lower.state().getValue(DoorBlock.FACING));

        // The upper half reaches 2.5 blocks, so the cells above need proxies.
        requireProxy(helper, pos.offset(0, 1, 0), pos);
        requireProxy(helper, pos.offset(0, 2, 0), pos);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void interactingWithDoorTogglesBothHalves(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        LocalTransform.IDENTITY, 0),
                new PartInstance(1, door(DoubleBlockHalf.LOWER, false),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0),
                new PartInstance(2, door(DoubleBlockHalf.UPPER, false),
                        new LocalTransform(FixedPoint.ZERO, new FixedPoint(FixedPoint.HALF_BLOCK.units() + DOOR_UNITS), FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        // Click the door (empty hand) -> opens
        aimAtPart(helper, player, composite, 1);
        assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Interacting with the door must be consumed");
        var afterOpen = requireComposite(helper, pos);
        assertTrue(afterOpen.parts().find(1).orElseThrow().state().getValue(DoorBlock.OPEN), "Lower half must open");
        assertTrue(afterOpen.parts().find(2).orElseThrow().state().getValue(DoorBlock.OPEN), "Upper half must open in sync");

        // Click again -> closes
        aimAtPart(helper, player, afterOpen, 1);
        assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction());
        var afterClose = requireComposite(helper, pos);
        assertFalse(afterClose.parts().find(1).orElseThrow().state().getValue(DoorBlock.OPEN), "Lower half must close");
        assertFalse(afterClose.parts().find(2).orElseThrow().state().getValue(DoorBlock.OPEN), "Upper half must close in sync");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void removingDoorDropsBothHalvesAndConvertsSlabBack(GameTestHelper helper) {
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
        var state = getBlockState(helper, pos);
        ((CompositeBlock) state.getBlock()).playerDestroy(helper.getLevel(), player, pos, state, composite, ItemStack.EMPTY);

        assertNoCompositeAt(helper, pos);
        assertEquals(Blocks.OAK_SLAB, getBlockState(helper, pos).getBlock(),
                "The lone remaining slab must convert back to a vanilla slab");
        assertNoProxyNear(helper, pos, 4);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void removingTheSourceDoorCleansUpTheWholeComposite(GameTestHelper helper) {
        // The door is the first non-cover part, i.e. the composite source:
        // destroying it must tear down the whole cell, leaving no orphans.
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
        assertTrue(getBlockState(helper, pos).isAir(),
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
