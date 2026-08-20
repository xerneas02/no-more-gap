package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Neighbour-driven behaviour of parts stored in composites: fence connections
 * (via the neighbour-state mixin), button/lever redstone output and the
 * scheduled-tick path that un-presses buttons.
 */
public class NeighborGameTests extends GameTestBase {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void fenceConnectsToCompositeFencePart(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos fencePos = pos.offset(1, 0, 0);
        BlockPos controlPos = pos.offset(3, 0, 0);
        // Place the fences first, then create the composite: the neighbour change
        // runs updateShape, where the mod unwraps the composite into its fence part.
        setBlock(helper, fencePos, Blocks.OAK_FENCE.defaultBlockState());
        setBlock(helper, controlPos, Blocks.OAK_FENCE.defaultBlockState());

        createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_FENCE.defaultBlockState(), LocalTransform.IDENTITY, 0)));

        // The part-unwrapping helper itself must resolve the fence part.
        var unwrapped = fr.xerneas02.nomoregap.interaction.CompositePartUpdater.stateAt(
                helper.getLevel(), pos, Blocks.OAK_FENCE.defaultBlockState());
        assertEquals(Blocks.OAK_FENCE, unwrapped.getBlock(),
                "stateAt must unwrap the composite into its fence part");

        // Neighbour updates can be deferred to later ticks, so poll until the
        // fence picks up the connection (the mod mixin unwraps the composite).
        helper.succeedWhen(() -> helper.assertTrue(
                getBlockState(helper, fencePos).getValue(CrossCollisionBlock.WEST),
                "A fence next to a composite fence part must connect toward it"));

        assertFalse(getBlockState(helper, controlPos).getValue(CrossCollisionBlock.WEST),
                "A fence with air on its west side must not connect");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void leverInCompositeTogglesAndEmitsRedstone(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var lever = Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, false)
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState(), LocalTransform.IDENTITY, 0),
                new PartInstance(1, lever, new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        aimAtPart(helper, player, composite, 1);
        assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction());
        var powered = requireComposite(helper, pos).parts().find(1).orElseThrow().state();
        assertTrue(powered.getValue(LeverBlock.POWERED), "The lever part must power on");
        assertEquals(15, helper.getLevel().getSignal(pos, Direction.DOWN), "The composite must emit a redstone signal");

        aimAtPart(helper, player, composite, 1);
        assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction());
        var unpowered = requireComposite(helper, pos).parts().find(1).orElseThrow().state();
        assertFalse(unpowered.getValue(LeverBlock.POWERED), "The lever part must power off");
        assertEquals(0, helper.getLevel().getSignal(pos, Direction.DOWN));
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void buttonInCompositePressesAndUnpressesViaScheduledTick(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACING, Direction.NORTH)
                .setValue(ButtonBlock.POWERED, false)
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState(), LocalTransform.IDENTITY, 0),
                new PartInstance(1, button, new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));

        var player = newPlayer(helper);
        aimAtPart(helper, player, composite, 1);
        assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Pressing the button part must be consumed");
        assertTrue(requireComposite(helper, pos).parts().find(1).orElseThrow().state().getValue(ButtonBlock.POWERED),
                "The button part must be pressed");
        assertEquals(15, helper.getLevel().getSignal(pos, Direction.DOWN));
        assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE),
                "The composite must have a scheduled tick to un-press the button");

        // The scheduled tick must reach the part and un-press it. Use the
        // helper's assertions here: succeedWhen only tolerates
        // GameTestAssertException, JUnit assertions would crash the server.
        helper.succeedWhen(() -> {
            var current = requireComposite(helper, pos);
            helper.assertTrue(!current.parts().find(1).orElseThrow().state().getValue(ButtonBlock.POWERED),
                    "The button part must un-press after its scheduled tick");
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void unpoweredButtonInCompositeStaysUnpowered(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACING, Direction.NORTH)
                .setValue(ButtonBlock.POWERED, false)
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState(), LocalTransform.IDENTITY, 0),
                new PartInstance(1, button, new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));
        helper.runAtTickTime(30, () -> {
            var current = requireComposite(helper, pos);
            assertFalse(current.parts().find(1).orElseThrow().state().getValue(ButtonBlock.POWERED));
            assertEquals(0, helper.getLevel().getSignal(pos, Direction.DOWN));
            helper.succeed();
        });
    }
}
