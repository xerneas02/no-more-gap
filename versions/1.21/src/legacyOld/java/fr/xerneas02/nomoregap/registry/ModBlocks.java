package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.CompositeProxyBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final Block COMPOSITE = Registry.register(BuiltInRegistries.BLOCK, NoMoreGap.id("composite"),
            new CompositeBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_SLAB)
                    .sound(SoundType.EMPTY).noOcclusion().noLootTable()
                    .lightLevel(state -> state.getValue(CompositeBlock.LAVA) ? 15 : state.getValue(CompositeBlock.LIT) ? 14 : 0)));
    public static final Block COMPOSITE_PROXY = Registry.register(BuiltInRegistries.BLOCK, NoMoreGap.id("composite_proxy"),
            new CompositeProxyBlock(BlockBehaviour.Properties.of()
                    .strength(-1, 3_600_000).sound(SoundType.EMPTY).noOcclusion().noLootTable()));

    private ModBlocks() {}
    public static void initialize() {}
}
