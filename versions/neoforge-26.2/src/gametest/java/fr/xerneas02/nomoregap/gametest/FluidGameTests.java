package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.lava.LavaLogging;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Waterlogging and lava logging of parts: fluid state, bucket interactions and
 * the lava + water reaction.
 */
@ForEachTest(groups = "no_more_gap.fluid")
public class FluidGameTests extends NeoForgeTestBase {
    private static final int FORMED_ROCK = 1;

    private static BlockState waterSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(BlockStateProperties.WATERLOGGED, true);
    }

    private static BlockState lavaSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(LavaLogging.LAVA_LOGGED, true);
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A waterlogged part makes the composite emit a water fluid")
    static void waterloggedPartMakesCompositeEmitWaterFluid(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, waterSlab(), LocalTransform.IDENTITY, 0)));

        helper.assertTrue(getBlockState(helper, pos).getValue(CompositeBlock.WATER), "The block must flag water");
        helper.assertTrue(helper.getLevel().getFluidState(pos).is(Fluids.WATER),
                "The composite must emit a water fluid state");
        helper.assertTrue(composite.parts().view().getFirst().state().getValue(BlockStateProperties.WATERLOGGED),
                "The part must be waterlogged");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A bucket collects water from a waterlogged part")
    static void bucketCollectsWaterFromWaterloggedPart(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, waterSlab(), LocalTransform.IDENTITY, 0)));

        var player = newPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET));
        aimAtPart(helper, player, composite, 0);
        helper.assertTrue(useOn(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Collecting water with a bucket must be consumed");
        var part = requireComposite(helper, pos).parts().find(0).orElseThrow();
        helper.assertTrue(!part.state().getValue(BlockStateProperties.WATERLOGGED), "The part must be drained");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == Items.WATER_BUCKET,
                "The player must hold a water bucket afterwards");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A lava-logged part emits lava and a bucket collects it")
    static void lavaLoggedPartEmitsLavaAndBucketCollectsIt(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, lavaSlab(), LocalTransform.IDENTITY, FORMED_ROCK)));

        helper.assertTrue(getBlockState(helper, pos).getValue(CompositeBlock.LAVA), "The block must flag lava");
        helper.assertTrue(helper.getLevel().getFluidState(pos).is(Fluids.LAVA),
                "The composite must emit a lava fluid state");

        var player = newPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET));
        aimAtPart(helper, player, composite, 0);
        helper.assertTrue(useOn(helper, player, pos, player.getEyePosition(), Direction.UP).consumesAction(),
                "Collecting lava with a bucket must be consumed");
        var part = requireComposite(helper, pos).parts().find(0).orElseThrow();
        helper.assertTrue(!part.state().getValue(LavaLogging.LAVA_LOGGED), "The part must no longer be lava-logged");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == Items.LAVA_BUCKET,
                "The player must hold a lava bucket afterwards");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A lava-logged composite touching water forms obsidian")
    static void lavaLoggedCompositeTouchingWaterFormsObsidian(ExtendedGameTestHelper helper) {
        BlockPos lavaPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos waterPos = lavaPos.offset(1, 0, 0);
        var lavaComposite = createComposite(helper, lavaPos, List.of(
                new PartInstance(0, lavaSlab(), LocalTransform.IDENTITY, 0)));
        setBlock(helper, waterPos, waterSlab());

        lavaComposite.addPart(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);

        helper.assertTrue(getBlockState(helper, lavaPos).getBlock() == Blocks.OBSIDIAN,
                "Lava-logged composite touching water must form obsidian");
        assertNoCompositeAt(helper, lavaPos);
        assertNoProxyNear(helper, lavaPos, 3);
        helper.succeed();
    }
}
