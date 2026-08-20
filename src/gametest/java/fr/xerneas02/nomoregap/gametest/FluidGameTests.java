package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.lava.LavaLogging;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Waterlogging and lava logging of parts: fluid state of the composite block,
 * bucket interactions through the real use pipeline, and the lava + water
 * reaction.
 */
public class FluidGameTests extends GameTestBase {
    private static final int FORMED_ROCK = 1;

    private static BlockState waterSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(BlockStateProperties.WATERLOGGED, true);
    }

    private static BlockState lavaSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(LavaLogging.LAVA_LOGGED, true);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void waterloggedPartMakesCompositeEmitWaterFluid(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, waterSlab(), LocalTransform.IDENTITY, 0)));

        assertTrue(getBlockState(helper, pos).getValue(CompositeBlock.WATER), "The block must flag water");
        assertTrue(helper.getLevel().getFluidState(pos).is(Fluids.WATER), "The composite must emit a water fluid state");
        assertFalse(helper.getLevel().getFluidState(pos).is(Fluids.LAVA));
        assertTrue(composite.parts().view().getFirst().state().getValue(BlockStateProperties.WATERLOGGED));
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void bucketCollectsWaterFromWaterloggedPart(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, waterSlab(), LocalTransform.IDENTITY, 0)));

        var player = newPlayer(helper);
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(Items.BUCKET));
        aimAtPart(helper, player, composite, 0);
        var result = useOn(helper, player, pos, player.getEyePosition(), Direction.UP);
        assertTrue(result.consumesAction(), "Collecting water with a bucket must be consumed");

        var part = requireComposite(helper, pos).parts().find(0).orElseThrow();
        assertFalse(part.state().getValue(BlockStateProperties.WATERLOGGED), "The part must be drained");
        assertFalse(getBlockState(helper, pos).getValue(CompositeBlock.WATER));
        assertEquals(Items.WATER_BUCKET, player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getItem(),
                "The player must hold a water bucket afterwards");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void lavaLoggedPartEmitsLavaAndBucketCollectsIt(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, lavaSlab(), LocalTransform.IDENTITY, FORMED_ROCK)));

        assertTrue(getBlockState(helper, pos).getValue(CompositeBlock.LAVA), "The block must flag lava");
        assertTrue(helper.getLevel().getFluidState(pos).is(Fluids.LAVA), "The composite must emit a lava fluid state");

        var player = newPlayer(helper);
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(Items.BUCKET));
        aimAtPart(helper, player, composite, 0);
        assertTrue(useOn(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction());
        var part = requireComposite(helper, pos).parts().find(0).orElseThrow();
        assertFalse(part.state().getValue(LavaLogging.LAVA_LOGGED), "The part must no longer be lava-logged");
        assertFalse(getBlockState(helper, pos).getValue(CompositeBlock.LAVA));
        assertEquals(Items.LAVA_BUCKET, player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getItem());
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void waterBucketTurnsLavaLoggingIntoWaterlogging(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, lavaSlab(), LocalTransform.IDENTITY, 0)));

        var player = newPlayer(helper);
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(Items.WATER_BUCKET));
        aimAtPart(helper, player, composite, 0);
        assertTrue(useOn(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction());
        var part = requireComposite(helper, pos).parts().find(0).orElseThrow().state();
        assertFalse(part.getValue(LavaLogging.LAVA_LOGGED), "Lava logging must be replaced");
        assertTrue(part.getValue(BlockStateProperties.WATERLOGGED), "Waterlogging must take over");
        assertTrue(getBlockState(helper, pos).getValue(CompositeBlock.WATER));
        assertFalse(getBlockState(helper, pos).getValue(CompositeBlock.LAVA));
        assertEquals(Items.BUCKET, player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getItem());
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void lavaLoggedCompositeTouchingWaterFormsObsidian(GameTestHelper helper) {
        BlockPos lavaPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos waterPos = lavaPos.offset(1, 0, 0);
        var lavaComposite = createComposite(helper, lavaPos, List.of(
                new PartInstance(0, lavaSlab(), LocalTransform.IDENTITY, 0)));
        // The reacting neighbour is a plain vanilla waterlogged block, exactly
        // like a player-placed waterlogged slab next to lava-logged content.
        setBlock(helper, waterPos, waterSlab());

        // Any refresh of the lava composite triggers the vanilla-style reaction.
        lavaComposite.addPart(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);

        assertEquals(Blocks.OBSIDIAN, getBlockState(helper, lavaPos).getBlock(),
                "Lava-logged composite touching water must form obsidian");
        assertNoCompositeAt(helper, lavaPos);
        assertNoProxyNear(helper, lavaPos, 3);
        helper.succeed();
    }
}
