package fr.xerneas02.nomoregap.block;

import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.part.PartContainer;
import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeBlockTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void coverIsNotTheStructuralSource() {
        var parts = new PartContainer();
        parts.add(Blocks.WHITE_CARPET.defaultBlockState(), LocalTransform.IDENTITY, 0);
        var door = parts.add(Blocks.OAK_DOOR.defaultBlockState(), LocalTransform.IDENTITY, 0);
        assertEquals(door.id(), CompositeBlock.sourcePartId(parts.view()));
    }

    @Test void onlyPartsRestingOnTheCoverFollowItsBreak() {
        var carpet = new PartInstance(0, Blocks.WHITE_CARPET.defaultBlockState(), LocalTransform.IDENTITY, 0);
        var torch = new PartInstance(1, Blocks.TORCH.defaultBlockState(),
                new LocalTransform(FixedPoint.ZERO, FixedPoint.fromDouble(1.0 / 16), FixedPoint.ZERO, 0), 0);
        var footBlock = new PartInstance(2, Blocks.OAK_SLAB.defaultBlockState(), LocalTransform.IDENTITY, 0);

        assertTrue(CompositeBlock.restsOn(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO, torch, carpet));
        assertFalse(CompositeBlock.restsOn(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO, footBlock, carpet));
    }
}
