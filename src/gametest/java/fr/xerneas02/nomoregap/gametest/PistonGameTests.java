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
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenario F: pistons stored as composite parts. The piston part must extend
 * and retract, push or pull the parts in front of it, respect the vanilla
 * 12-block limit and the block reactions, and handle a piston standing between
 * two blocks by moving both.
 */
public class PistonGameTests extends GameTestBase {

    private static PartInstance slab(int id, double x, double y, double z) {
        return new PartInstance(id, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                fixed(x, y, z, 0), 0);
    }

    private static PartInstance piston(int id, Direction facing, boolean sticky, double x, double y, double z) {
        var state = (sticky ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState()
                .setValue(PistonBaseBlock.FACING, facing)
                .setValue(PistonBaseBlock.EXTENDED, false);
        return new PartInstance(id, state, fixed(x, y, z, 0), 0);
    }

    private static LocalTransform t(double x, double y, double z) {
        return fixed(x, y, z, 0);
    }

    private void powerAt(GameTestHelper helper, BlockPos pos) {
        var lever = Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, true)
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL);
        setBlock(helper, pos, lever);
    }

    private static boolean hasPistonHead(CompositeBlockEntity composite) {
        return composite.parts().view().stream()
                .anyMatch(p -> (p.flags() & fr.xerneas02.nomoregap.part.PartFlags.PISTON_HEAD) != 0);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pistonPartPushesBlockInFront(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Piston part at (0,0,0) facing +X; a stone part at (1,0,0) in front.
        createComposite(helper, anchor, List.of(
                piston(0, Direction.EAST, false, 0, 0, 0),
                new PartInstance(1, Blocks.STONE.defaultBlockState(), t(1, 0, 0), 0)));

        powerAt(helper, anchor.offset(0, 0, 1));

        helper.succeedWhen(() -> {
            var composite = requireComposite(helper, anchor);
            var stone = composite.parts().find(1).orElse(null);
            helper.assertTrue(stone != null && stone.transform().xDouble() == 2.0,
                    "The stone part must be pushed one block forward, found "
                            + (stone == null ? "null" : stone.transform()));
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pistonBetweenTwoBlocksPushesBoth(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Piston facing +X with two blocks stacked in the same cell in front:
        // a stone at (1,0,0) and a planks part at (1,0,0) sharing the cell.
        createComposite(helper, anchor, List.of(
                piston(0, Direction.EAST, false, 0, 0, 0),
                new PartInstance(1, Blocks.STONE.defaultBlockState(), t(1, 0, 0), 0),
                new PartInstance(2, Blocks.OAK_PLANKS.defaultBlockState(), t(1, 0, 0), 0)));

        powerAt(helper, anchor.offset(0, 0, 1));

        helper.succeedWhen(() -> {
            // Both parts must have been pushed one cell forward (transform 2,0,0).
            var composite = requireComposite(helper, anchor);
            var stone = composite.parts().find(1).orElse(null);
            var planks = composite.parts().find(2).orElse(null);
            helper.assertTrue(stone != null && stone.transform().xDouble() == 2.0,
                    "The stone part must move to x=2, found " + (stone == null ? "null" : stone.transform()));
            helper.assertTrue(planks != null && planks.transform().xDouble() == 2.0,
                    "The planks part must move to x=2, found " + (planks == null ? "null" : planks.transform()));
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pistonPartPushesTwoPartsInSameCell(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Piston facing +X; two parts stacked at (1,0,0) (slab and carpet).
        createComposite(helper, anchor, List.of(
                piston(0, Direction.EAST, false, 0, 0, 0),
                new PartInstance(1, Blocks.STONE.defaultBlockState(), t(1, 0, 0), 0),
                new PartInstance(2, Blocks.RED_CARPET.defaultBlockState(), t(1, 0, 0), 0)));

        powerAt(helper, anchor.offset(0, 0, 1));

        helper.succeedWhen(() -> {
            var composite = requireComposite(helper, anchor);
            var stone = composite.parts().find(1).orElse(null);
            var carpet = composite.parts().find(2).orElse(null);
            helper.assertTrue(stone != null && stone.transform().xDouble() == 2.0,
                    "The stone part must move to x=2, found " + (stone == null ? "null" : stone.transform()));
            helper.assertTrue(carpet != null && carpet.transform().xDouble() == 2.0,
                    "The carpet part must move to x=2, found " + (carpet == null ? "null" : carpet.transform()));
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void stickyPistonPullsAdjacentPart(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Sticky piston facing +X; a stone part in front at (1,0,0).
        createComposite(helper, anchor, List.of(
                piston(0, Direction.EAST, true, 0, 0, 0),
                new PartInstance(1, Blocks.STONE.defaultBlockState(), t(1, 0, 0), 0)));

        powerAt(helper, anchor.offset(0, 0, 1));
        helper.succeedWhen(() -> {
            var composite = requireComposite(helper, anchor);
            var stone = composite.parts().find(1).orElse(null);
            helper.assertTrue(stone != null && stone.transform().xDouble() == 2.0,
                    "The sticky piston must push the stone forward, found "
                            + (stone == null ? "null" : stone.transform()));
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void immovableBlockBlocksPiston(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Piston facing +X; bedrock (immovable, PushReaction.BLOCK) in front.
        createComposite(helper, anchor, List.of(
                piston(0, Direction.EAST, false, 0, 0, 0),
                new PartInstance(1, Blocks.BEDROCK.defaultBlockState(), t(1, 0, 0), 0)));

        powerAt(helper, anchor.offset(0, 0, 1));

        helper.succeedWhen(() -> {
            var composite = requireComposite(helper, anchor);
            var bedrock = composite.parts().find(1).orElse(null);
            helper.assertTrue(bedrock != null && bedrock.transform().xDouble() == 1.0,
                    "Bedrock must not move, found "
                            + (bedrock == null ? "null" : bedrock.transform()));
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pistonPartDoesNotMoveBeyond12Blocks(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // 13 blocks in front of a piston facing +X, in a line.
        var parts = new java.util.ArrayList<PartInstance>();
        parts.add(piston(0, Direction.EAST, false, 0, 0, 0));
        for (int i = 1; i <= 13; i++) {
            parts.add(new PartInstance(i, Blocks.STONE.defaultBlockState(), t(i, 0, 0), 0));
        }
        createComposite(helper, anchor, parts);

        powerAt(helper, anchor.offset(0, 0, 1));

        helper.succeedWhen(() -> {
            var composite = requireComposite(helper, anchor);
            var first = composite.parts().find(1).orElse(null);
            helper.assertTrue(first != null && first.transform().xDouble() == 1.0,
                    "The first block must remain in place when the push limit is exceeded, found "
                            + (first == null ? "null" : first.transform()));
            helper.assertTrue(composite.parts().size() >= 14,
                    "The 14-part composite must remain intact, found " + composite.parts().size());
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pistonPartPushesOverProxyBoundary(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Piston at (0,0,0), a slab part at (1,0,0) whose geometry overflows
        // into the next cell via a proxy.
        var slab = new PartInstance(1,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), t(1, 0, 0), 0);
        createComposite(helper, anchor, List.of(
                piston(0, Direction.EAST, false, 0, 0, 0), slab));
        requireProxy(helper, anchor.offset(1, 0, 0), anchor);

        powerAt(helper, anchor.offset(0, 0, 1));

        helper.succeedWhen(() -> {
            var composite = requireComposite(helper, anchor);
            var moved = composite.parts().find(1).orElse(null);
            helper.assertTrue(moved != null && moved.transform().xDouble() == 2.0,
                    "The overflowing slab must move one cell forward, found "
                            + (moved == null ? "null" : moved.transform()));
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void buttonNextToPistonActivatesIt(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // User scenario: a piston on a slab, with a button next to it. Both are
        // parts of the same composite. The button is pressed (POWERED=true) so
        // the internal signal must fire the piston (EXTENDED=true) and spawn
        // the piston head part.
        var button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.POWERED, true);
        createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, false, 0, 0, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));

        helper.succeedWhen(() -> {
            var current = requireComposite(helper, anchor);
            var pistonPart = current.parts().find(1).orElse(null);
            helper.assertTrue(pistonPart != null && pistonPart.state().getValue(PistonBaseBlock.EXTENDED),
                    "The piston part must be extended, found " + (pistonPart == null ? "null" : pistonPart.state()));
            helper.assertTrue(hasPistonHead(current),
                    "An extended piston must have its head part present");
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void normalPistonRetractsAndRemovesHead(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // A normal piston with a pressed button (internal power). After the
        // button loses power the piston must retract: EXTENDED=false and the
        // head part removed. Normal pistons do not pull, but they still retract.
        var button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.POWERED, true);
        var composite = createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, false, 0, 0, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));
        // The piston is already powered; give it a few ticks to extend.
        helper.runAtTickTime(10, () -> {
            var current = requireComposite(helper, anchor);
            helper.assertTrue(hasPistonHead(current), "The piston must extend before retracting");
            // Un-press the button: the piston must retract.
            var buttonPart = current.parts().find(3).orElseThrow();
            current.replacePart(3, buttonPart.state().setValue(BlockStateProperties.POWERED, false));
        });
        helper.succeedWhen(() -> {
            var current = requireComposite(helper, anchor);
            var pistonPart = current.parts().find(1).orElse(null);
            helper.assertTrue(pistonPart != null && !pistonPart.state().getValue(PistonBaseBlock.EXTENDED),
                    "The normal piston must retract, found " + (pistonPart == null ? "null" : pistonPart.state()));
            helper.assertTrue(!hasPistonHead(current),
                    "The retracted piston must have its head removed");
        });
    }

    // ---------------------------------------------------------------- vanilla
    // The following tests drive a composite-contained piston against real
    // vanilla blocks placed in neighbouring cells. The blocks are placed FIRST,
    // because creating the composite (with a powered button) fires the piston
    // immediately.

    /** Creates a composite with a piston part and a pressed button part to power it. */
    private CompositeBlockEntity poweredPiston(GameTestHelper helper, BlockPos anchor, boolean sticky) {
        var button = Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        return createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, sticky, 0, 0, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pushesVanillaBlock(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var at2 = getBlockState(helper, anchor.offset(2, 0, 0));
            helper.assertTrue(at2.getBlock() == Blocks.STONE,
                    "The vanilla stone must be pushed one cell forward, found " + at2);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pushesMultipleVanillaBlocks(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, anchor.offset(2, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var stone = getBlockState(helper, anchor.offset(2, 0, 0));
            var planks = getBlockState(helper, anchor.offset(3, 0, 0));
            helper.assertTrue(stone.getBlock() == Blocks.STONE,
                    "The stone must move to the second cell, found " + stone);
            helper.assertTrue(planks.getBlock() == Blocks.OAK_PLANKS,
                    "The planks must move to the third cell, found " + planks);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void refusesObsidian(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.OBSIDIAN.defaultBlockState());
        poweredPiston(helper, anchor, false);

        helper.runAtTickTime(20, () -> {
            var composite = requireComposite(helper, anchor);
            var pistonPart = composite.parts().find(1).orElse(null);
            helper.assertTrue(pistonPart != null && !pistonPart.state().getValue(PistonBaseBlock.EXTENDED),
                    "The piston must not extend against obsidian, found "
                            + (pistonPart == null ? "null" : pistonPart.state()));
            helper.assertTrue(!hasPistonHead(composite), "No head must be created against obsidian");
            var obsidian = getBlockState(helper, anchor.offset(1, 0, 0));
            helper.assertTrue(obsidian.getBlock() == Blocks.OBSIDIAN, "Obsidian must stay in place");
            helper.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void respectsVanillaPushLimit(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // 12 stone blocks in front: exactly at the vanilla limit.
        for (int i = 1; i <= 12; i++) {
            setBlock(helper, anchor.offset(i, 0, 0), Blocks.STONE.defaultBlockState());
        }
        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var last = getBlockState(helper, anchor.offset(13, 0, 0));
            helper.assertTrue(last.getBlock() == Blocks.STONE,
                    "The 12th block must move to cell 13, found " + last);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void normalPistonDoesNotPullOnRetract(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
        var button = Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        var composite = createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, false, 0, 0, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));

        // The piston extends immediately (button already pressed), pushing the
        // stone to cell 2. Then un-power it: it must retract but leave the stone.
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(hasPistonHead(requireComposite(helper, anchor)), "The piston must extend first");
            var current = requireComposite(helper, anchor);
            current.replacePart(3, current.parts().find(3).orElseThrow().state().setValue(BlockStateProperties.POWERED, false));
        });
        helper.succeedWhen(() -> {
            var current = requireComposite(helper, anchor);
            var pistonPart = current.parts().find(1).orElse(null);
            helper.assertTrue(pistonPart != null && !pistonPart.state().getValue(PistonBaseBlock.EXTENDED),
                    "The normal piston must retract");
            helper.assertTrue(!hasPistonHead(current), "The head must be removed on retract");
            var stone = getBlockState(helper, anchor.offset(2, 0, 0));
            helper.assertTrue(stone.getBlock() == Blocks.STONE,
                    "The pushed stone must stay in the second cell (not pulled back), found " + stone);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void stickyPistonPushesThenPullsBlock(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
        var button = Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, true, 0, 0, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));

        // The sticky piston extends (pushing the stone to cell 2), then when it
        // loses power it retracts and pulls the stone back to cell 1.
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(hasPistonHead(requireComposite(helper, anchor)), "The sticky piston must extend");
            var current = requireComposite(helper, anchor);
            current.replacePart(3, current.parts().find(3).orElseThrow().state().setValue(BlockStateProperties.POWERED, false));
        });
        helper.succeedWhen(() -> {
            var current = requireComposite(helper, anchor);
            var pistonPart = current.parts().find(1).orElse(null);
            helper.assertTrue(pistonPart != null && !pistonPart.state().getValue(PistonBaseBlock.EXTENDED),
                    "The sticky piston must retract");
            helper.assertTrue(!hasPistonHead(current), "The head must be removed on retract");
            var stone = getBlockState(helper, anchor.offset(1, 0, 0));
            helper.assertTrue(stone.getBlock() == Blocks.STONE,
                    "The sticky piston must pull the stone back to the first cell, found " + stone);
        });
    }

    // ------------------------------------------------------ properties/bugs

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pushesTallGrassInsteadOfBlocking(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // A torch has PushReaction.DESTROY: vanilla destroys it and the piston
        // keeps pushing. The torch needs a solid support in this air-based test
        // world, so place a stone column under it. A stone behind the torch
        // proves the line continues through it.
        setBlock(helper, anchor.offset(1, -1, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.TORCH.defaultBlockState());
        setBlock(helper, anchor.offset(2, 0, 0), Blocks.STONE.defaultBlockState());
        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var stone = getBlockState(helper, anchor.offset(3, 0, 0));
            helper.assertTrue(stone.getBlock() == Blocks.STONE,
                    "The stone behind the torch must be pushed to cell 3, found " + stone);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void slimeBlockDragsAdjacentBlocks(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Piston facing +X pushes a slime block at cell 1; a stone at cell 2 in
        // front sticks to the slime and moves too; a stone at cell 0 (beside the
        // piston anchor) is adjacent to the slime and must be dragged along.
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.SLIME_BLOCK.defaultBlockState());
        setBlock(helper, anchor.offset(2, 0, 0), Blocks.STONE.defaultBlockState());
        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var slime = getBlockState(helper, anchor.offset(2, 0, 0));
            var stone = getBlockState(helper, anchor.offset(3, 0, 0));
            helper.assertTrue(slime.getBlock() == Blocks.SLIME_BLOCK,
                    "The slime block must move to cell 2, found " + slime);
            helper.assertTrue(stone.getBlock() == Blocks.STONE,
                    "The stone stuck to the slime must move to cell 3, found " + stone);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void honeyAndSlimeDoNotStickTogether(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Slime at cell 1, honey at cell 2 in the line: slime and honey do not
        // stick to each other, so the honey stays at cell 2 while the slime
        // moves to cell 2 (into the vacated honey cell? no) — actually the
        // honey blocks the line; slime/honey don't stick so the push would be
        // blocked. Instead test the side branch: a honey block beside the slime
        // is NOT dragged.
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.SLIME_BLOCK.defaultBlockState());
        setBlock(helper, anchor.offset(1, 0, 1), Blocks.HONEY_BLOCK.defaultBlockState());
        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var slime = getBlockState(helper, anchor.offset(2, 0, 0));
            var honey = getBlockState(helper, anchor.offset(1, 0, 1));
            helper.assertTrue(slime.getBlock() == Blocks.SLIME_BLOCK,
                    "The slime must move to cell 2, found " + slime);
            helper.assertTrue(honey.getBlock() == Blocks.HONEY_BLOCK,
                    "The honey beside the slime must NOT be dragged, found " + honey);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void stickyPistonBlockedPushDoesNotPull(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Obsidian at cell 1 blocks the sticky piston extension (it cannot
        // push), so the piston stays retracted. When the signal drops, it must
        // not pull the obsidian into the head cell.
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.OBSIDIAN.defaultBlockState());
        var button = Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, true, 0, 0, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));

        helper.runAtTickTime(10, () -> {
            // The extension was blocked: the piston must still be retracted and
            // the obsidian still in place.
            var current = requireComposite(helper, anchor);
            var pistonPart = current.parts().find(1).orElse(null);
            helper.assertTrue(pistonPart != null && !pistonPart.state().getValue(PistonBaseBlock.EXTENDED),
                    "The sticky piston must not extend against obsidian");
            var obsidian = getBlockState(helper, anchor.offset(1, 0, 0));
            helper.assertTrue(obsidian.getBlock() == Blocks.OBSIDIAN, "Obsidian must stay in cell 1");
            // Un-power: retraction must not pull anything.
            current.replacePart(3, current.parts().find(3).orElseThrow().state().setValue(BlockStateProperties.POWERED, false));
        });
        helper.succeedWhen(() -> {
            var obsidian = getBlockState(helper, anchor.offset(1, 0, 0));
            helper.assertTrue(obsidian.getBlock() == Blocks.OBSIDIAN,
                    "The sticky piston must not pull obsidian on retract, found " + obsidian);
        });
    }

    // ------------------------------------------- whole-composite movement

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pistonPushesWholeCompositeWithTopAndBottom(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // A composite in front of the piston holds two parts stacked vertically:
        // a bottom slab (y=0) and a top part (y=1). Pushing the composite must
        // move the WHOLE composite (both parts) one cell forward: the anchor
        // shifts and the parts keep their local transforms.
        var frontAnchor = anchor.offset(1, 0, 0);
        setBlock(helper, frontAnchor, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE.defaultBlockState());
        var composite = getBlockEntity(helper, frontAnchor, CompositeBlockEntity.class);
        composite.replaceParts(List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                new PartInstance(1, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 1, 0, 0), 0)));
        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            // The whole composite moved one cell forward: a composite must now
            // exist at frontAnchor + 1 holding both parts.
            var moved = getBlockEntity(helper, frontAnchor.offset(1, 0, 0), CompositeBlockEntity.class);
            helper.assertTrue(moved != null,
                    "The composite must move one cell forward, found no composite at " + frontAnchor.offset(1, 0, 0));
            if (moved != null) {
                var bottom = moved.parts().find(0).orElse(null);
                var top = moved.parts().find(1).orElse(null);
                helper.assertTrue(bottom != null && bottom.transform().xDouble() == 0.0
                                && bottom.transform().yDouble() == 0.0,
                        "The bottom part must keep its local transform, found "
                                + (bottom == null ? "null" : bottom.transform()));
                helper.assertTrue(top != null && top.transform().xDouble() == 0.0
                                && top.transform().yDouble() == 1.0,
                        "The top part must keep its local transform, found "
                                + (top == null ? "null" : top.transform()));
            }
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void stickyPistonPullsSlimeAndAttachedBlock(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Sticky piston pushes a slime block at cell 1, with a stone at cell 2
        // stuck to it (slime branch). On retraction, the sticky piston must pull
        // the slime back AND the stone stuck to it.
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.SLIME_BLOCK.defaultBlockState());
        setBlock(helper, anchor.offset(2, 0, 0), Blocks.STONE.defaultBlockState());
        var button = Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, true, 0, 0, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));

        // The sticky piston extends: slime -> cell 2, stone -> cell 3.
        helper.runAtTickTime(10, () -> {
            var current = requireComposite(helper, anchor);
            var slime = getBlockState(helper, anchor.offset(2, 0, 0));
            helper.assertTrue(slime.getBlock() == Blocks.SLIME_BLOCK,
                    "The slime must have been pushed to cell 2 before retract, found " + slime);
            // Un-power: sticky retraction pulls slime and its stuck stone back.
            var buttonPart = current.parts().find(3).orElse(null);
            helper.assertTrue(buttonPart != null, "The button part must still exist");
            current.replacePart(3, buttonPart.state().setValue(BlockStateProperties.POWERED, false));
        });
        helper.succeedWhen(() -> {
            var slime = getBlockState(helper, anchor.offset(1, 0, 0));
            var stone = getBlockState(helper, anchor.offset(2, 0, 0));
            helper.assertTrue(slime.getBlock() == Blocks.SLIME_BLOCK,
                    "The sticky piston must pull the slime back to cell 1, found " + slime);
            helper.assertTrue(stone.getBlock() == Blocks.STONE,
                    "The stone stuck to the slime must be pulled back to cell 2, found " + stone);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void pistonBetweenTwoCompositesPushesBoth(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Two composites in the push line: one at cell 1 and one at cell 2.
        // Both must be pushed one cell forward together.
        setBlock(helper, anchor.offset(1, 0, 0), fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE.defaultBlockState());
        var first = getBlockEntity(helper, anchor.offset(1, 0, 0), CompositeBlockEntity.class);
        first.replaceParts(List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0)));
        setBlock(helper, anchor.offset(2, 0, 0), fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE.defaultBlockState());
        var second = getBlockEntity(helper, anchor.offset(2, 0, 0), CompositeBlockEntity.class);
        second.replaceParts(List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0)));

        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var movedFirst = getBlockEntity(helper, anchor.offset(2, 0, 0), CompositeBlockEntity.class);
            var movedSecond = getBlockEntity(helper, anchor.offset(3, 0, 0), CompositeBlockEntity.class);
            helper.assertTrue(movedFirst != null, "The first composite must move to cell 2");
            helper.assertTrue(movedSecond != null, "The second composite must move to cell 3");
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void onlyBlockAboveHeadColumnSticks(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // A composite in front of the piston. The block directly ABOVE the cell
        // in front of the head must be dragged (glued upward), but a block BELOW
        // the head column must NOT be dragged.
        var frontAnchor = anchor.offset(1, 0, 0);
        setBlock(helper, frontAnchor, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE.defaultBlockState());
        var composite = getBlockEntity(helper, frontAnchor, CompositeBlockEntity.class);
        composite.replaceParts(List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0)));
        setBlock(helper, frontAnchor.offset(0, 1, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, frontAnchor.offset(0, -1, 0), Blocks.OAK_PLANKS.defaultBlockState());

        poweredPiston(helper, anchor, false);

        helper.succeedWhen(() -> {
            var movedComposite = getBlockEntity(helper, frontAnchor.offset(1, 0, 0), CompositeBlockEntity.class);
            var stoneAbove = getBlockState(helper, frontAnchor.offset(1, 1, 0));
            var planksBelow = getBlockState(helper, frontAnchor.offset(0, -1, 0));
            helper.assertTrue(movedComposite != null,
                    "The composite must move one cell forward, found no composite at " + frontAnchor.offset(1, 0, 0));
            helper.assertTrue(stoneAbove.getBlock() == Blocks.STONE,
                    "The block above the head column must be dragged forward, found " + stoneAbove);
            helper.assertTrue(planksBelow.getBlock() == Blocks.OAK_PLANKS,
                    "The block below the head column must NOT be dragged, found " + planksBelow);
        });
    }

    // ----------------------------------------- piston offset on Y, horizontal

    /**
     * Creates a composite with a slab at the bottom and a piston part offset to
     * y=0.5 (standing on the slab), facing EAST.
     */
    private CompositeBlockEntity offsetPistonOnSlab(GameTestHelper helper, BlockPos anchor, boolean sticky) {
        var button = Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        return createComposite(helper, anchor, List.of(
                new PartInstance(0, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                        fixed(0, 0, 0, 0), 0),
                piston(1, Direction.EAST, sticky, 0, 0.5, 0),
                new PartInstance(3, button, fixed(0, 0.5, 0, 0), 0)));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void offsetPistonPushesColumnInFront(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // A piston offset on Y (on a slab) faces EAST. In front of it there is a
        // column of 3 stone blocks: one at the bottom of the cell, one at the
        // head height (y+1) and one above (y+2). The piston pushes the cell
        // block (bottom) and glues ONE level above (y+1); the block at y+2 must
        // stay in place.
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, anchor.offset(1, 1, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, anchor.offset(1, 2, 0), Blocks.STONE.defaultBlockState());
        offsetPistonOnSlab(helper, anchor, false);

        helper.succeedWhen(() -> {
            var bottom = getBlockState(helper, anchor.offset(2, 0, 0));
            var middle = getBlockState(helper, anchor.offset(2, 1, 0));
            var top = getBlockState(helper, anchor.offset(1, 2, 0));
            helper.assertTrue(bottom.getBlock() == Blocks.STONE,
                    "The bottom block must be pushed to cell 2, found " + bottom);
            helper.assertTrue(middle.getBlock() == Blocks.STONE,
                    "The block at head height must be pushed to cell 2 (y+1), found " + middle);
            helper.assertTrue(top.getBlock() == Blocks.STONE,
                    "The block at y+2 must stay in place (only one level glued), found " + top);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void offsetPistonTopLevelPushesItsOwnLine(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Piston offset on Y faces EAST. At head height (y+1) there are TWO
        // stone blocks in a row: cell 1 and cell 2. The top block must push its
        // own horizontal line (both blocks move one cell forward), exactly like
        // the bottom line does.
        setBlock(helper, anchor.offset(1, 1, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, anchor.offset(2, 1, 0), Blocks.STONE.defaultBlockState());
        offsetPistonOnSlab(helper, anchor, false);

        helper.succeedWhen(() -> {
            var first = getBlockState(helper, anchor.offset(2, 1, 0));
            var second = getBlockState(helper, anchor.offset(3, 1, 0));
            helper.assertTrue(first.getBlock() == Blocks.STONE,
                    "The first top block must be pushed to cell 2 (y+1), found " + first);
            helper.assertTrue(second.getBlock() == Blocks.STONE,
                    "The second top block must be pushed to cell 3 (y+1), found " + second);
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void offsetStickyPistonRetractsColumn(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        // Same setup but sticky: after pushing, retraction pulls the cell block
        // and the glued level above back; y+2 stays.
        setBlock(helper, anchor.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, anchor.offset(1, 1, 0), Blocks.STONE.defaultBlockState());
        setBlock(helper, anchor.offset(1, 2, 0), Blocks.STONE.defaultBlockState());
        var composite = offsetPistonOnSlab(helper, anchor, true);

        helper.runAtTickTime(10, () -> {
            helper.assertTrue(hasPistonHead(requireComposite(helper, anchor)), "The sticky piston must extend");
            var bottom = getBlockState(helper, anchor.offset(2, 0, 0));
            helper.assertTrue(bottom.getBlock() == Blocks.STONE,
                    "The column must have been pushed to cell 2, found " + bottom);
            var buttonPart = composite.parts().find(3).orElse(null);
            helper.assertTrue(buttonPart != null, "The button part must still exist");
            composite.replacePart(3, buttonPart.state().setValue(BlockStateProperties.POWERED, false));
        });
        helper.succeedWhen(() -> {
            var bottom = getBlockState(helper, anchor.offset(1, 0, 0));
            var middle = getBlockState(helper, anchor.offset(1, 1, 0));
            var top = getBlockState(helper, anchor.offset(1, 2, 0));
            helper.assertTrue(bottom.getBlock() == Blocks.STONE,
                    "The bottom block must be pulled back to cell 1, found " + bottom);
            helper.assertTrue(middle.getBlock() == Blocks.STONE,
                    "The block at head height must be pulled back to cell 1 (y+1), found " + middle);
            helper.assertTrue(top.getBlock() == Blocks.STONE,
                    "The block at y+2 must stay in place, found " + top);
        });
    }
}
