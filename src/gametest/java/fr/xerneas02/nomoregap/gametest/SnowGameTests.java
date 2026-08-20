package fr.xerneas02.nomoregap.gametest;

import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Snow parts must keep the SNOWY ground property of the block below the
 * composite in sync, both when the snow is added and when it is removed.
 */
public class SnowGameTests extends GameTestBase {
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void snowPartMarksSnowyGroundAndRemovalClearsIt(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ground = pos.below();
        setBlock(helper, ground, Blocks.GRASS_BLOCK.defaultBlockState());
        assertFalse(getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY));

        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.SNOW.defaultBlockState(), LocalTransform.IDENTITY, 0)));
        assertTrue(getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY),
                "A snow part at Y=0 must mark the block below as snowy");

        assertTrue(composite.removePart(0));
        assertFalse(getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY),
                "Removing the snow must clear the SNOWY flag");
        assertNoProxyNear(helper, pos, 3);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void snowOnBlockWithoutSnowyPropertyIsHarmless(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ground = pos.below();
        setBlock(helper, ground, Blocks.DIRT.defaultBlockState());

        var composite = createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.SNOW.defaultBlockState(), LocalTransform.IDENTITY, 0)));
        assertEquals(Blocks.DIRT, getBlockState(helper, ground).getBlock(),
                "A block without the SNOWY property must be left untouched");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
    public void snowFloatingMidCellDoesNotMarkTheGround(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ground = pos.below();
        setBlock(helper, ground, Blocks.GRASS_BLOCK.defaultBlockState());

        createComposite(helper, pos, List.of(
                new PartInstance(0, Blocks.SNOW.defaultBlockState(),
                        new LocalTransform(FixedPoint.ZERO, FixedPoint.HALF_BLOCK, FixedPoint.ZERO, 0), 0)));
        assertFalse(getBlockState(helper, ground).getValue(BlockStateProperties.SNOWY),
                "A snow part not aligned with the cell floor must not mark the ground");
        helper.succeed();
    }
}
