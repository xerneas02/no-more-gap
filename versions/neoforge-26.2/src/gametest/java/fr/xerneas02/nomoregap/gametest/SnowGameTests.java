package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

/**
 * Snow parts must keep the SNOWY ground property of the block below the
 * composite in sync.
 */
@ForEachTest(groups = "no_more_gap.snow")
public class SnowGameTests extends NeoForgeTestBase {
    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A snow part marks the ground snowy and removal clears it")
    static void snowPartMarksSnowyGroundAndRemovalClearsIt(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ground = pos.below();
        setBlock(helper, ground, Blocks.GRASS_BLOCK.defaultBlockState());
        helper.assertTrue(!getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY),
                "The ground must start non-snowy");

        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.SNOW.defaultBlockState(), LocalTransform.IDENTITY, 0)));
        helper.assertTrue(getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY),
                "A snow part at Y=0 must mark the block below as snowy");

        helper.assertTrue(composite.removePart(0), "The snow part must be removable");
        helper.assertTrue(!getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY),
                "Removing the snow must clear the SNOWY flag");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "Snow on a block without the SNOWY property is harmless")
    static void snowOnBlockWithoutSnowyPropertyIsHarmless(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ground = pos.below();
        setBlock(helper, ground, Blocks.DIRT.defaultBlockState());

        createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.SNOW.defaultBlockState(), LocalTransform.IDENTITY, 0)));
        helper.assertTrue(getBlockState(helper, ground).getBlock() == Blocks.DIRT,
                "A block without the SNOWY property must be left untouched");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 200, skyAccess = true)
    @EmptyTemplate(value = "5x5x5")
    @TestHolder(description = "A snow part floating mid-cell does not mark the ground")
    static void snowFloatingMidCellDoesNotMarkTheGround(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ground = pos.below();
        setBlock(helper, ground, Blocks.GRASS_BLOCK.defaultBlockState());

        createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.SNOW.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));
        helper.assertTrue(!getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY),
                "A snow part not aligned with the cell floor must not mark the ground");
        helper.succeed();
    }
}
