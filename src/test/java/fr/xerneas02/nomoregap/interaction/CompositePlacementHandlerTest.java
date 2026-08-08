package fr.xerneas02.nomoregap.interaction;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositePlacementHandlerTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void acceptsBlockItemsOnDryNonAirBlocks() {
        var bottom = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        assertTrue(CompositePlacementHandler.matches(bottom, Items.TORCH));
        assertTrue(CompositePlacementHandler.matches(bottom, Items.STONE));
        assertTrue(CompositePlacementHandler.matches(bottom.setValue(SlabBlock.TYPE, SlabType.TOP), Items.TORCH));
        assertFalse(CompositePlacementHandler.matches(bottom.setValue(SlabBlock.WATERLOGGED, true), Items.TORCH));
        assertFalse(CompositePlacementHandler.matches(bottom, Items.STICK));
        assertFalse(CompositePlacementHandler.matches(Blocks.AIR.defaultBlockState(), Items.TORCH));
        assertTrue(CompositePlacementHandler.matches(Blocks.SNOW.defaultBlockState(), Items.TORCH));
    }
}
