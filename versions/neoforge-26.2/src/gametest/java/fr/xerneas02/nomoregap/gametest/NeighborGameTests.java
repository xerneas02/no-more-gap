package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Neighbour-driven behaviour of parts stored in composites: lever redstone
 * output and the scheduled-tick path that un-presses buttons.
 */
@ForEachTest(groups = "no_more_gap.neighbor")
public class NeighborGameTests extends NeoForgeTestBase {
    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A lever part toggles and emits redstone")
    static void leverInCompositeTogglesAndEmitsRedstone(ExtendedGameTestHelper helper) {
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
        helper.assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Toggling the lever must be consumed");
        var powered = requireComposite(helper, pos).parts().find(1).orElseThrow().state();
        helper.assertTrue(powered.getValue(LeverBlock.POWERED), "The lever part must power on");
        helper.assertTrue(helper.getLevel().getSignal(pos, Direction.DOWN) == 15,
                "The composite must emit a redstone signal");

        aimAtPart(helper, player, composite, 1);
        helper.assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Untoggling the lever must be consumed");
        var unpowered = requireComposite(helper, pos).parts().find(1).orElseThrow().state();
        helper.assertTrue(!unpowered.getValue(LeverBlock.POWERED), "The lever part must power off");
        helper.assertTrue(helper.getLevel().getSignal(pos, Direction.DOWN) == 0,
                "The composite must stop emitting redstone");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A button part presses and un-presses via its scheduled tick")
    static void buttonInCompositePressesAndUnpressesViaScheduledTick(ExtendedGameTestHelper helper) {
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
        helper.assertTrue(interact(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Pressing the button part must be consumed");
        helper.assertTrue(requireComposite(helper, pos).parts().find(1).orElseThrow().state().getValue(ButtonBlock.POWERED),
                "The button part must be pressed");
        helper.assertTrue(helper.getLevel().getSignal(pos, Direction.DOWN) == 15,
                "The composite must emit a redstone signal");

        // The scheduled tick must reach the part and un-press it (use the
        // helper's assertion: it raises a GameTestAssertException which the
        // framework polls correctly).
        helper.succeedWhen(() -> helper.assertTrue(
                !requireComposite(helper, pos).parts().find(1).orElseThrow().state().getValue(ButtonBlock.POWERED),
                "The button part must un-press after its scheduled tick"));
    }
}
