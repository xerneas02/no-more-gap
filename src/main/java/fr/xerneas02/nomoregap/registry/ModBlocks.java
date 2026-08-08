package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.CompositeProxyBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final ResourceKey<Block> COMPOSITE_KEY = ResourceKey.create(Registries.BLOCK, NoMoreGap.id("composite"));
    public static final ResourceKey<Block> COMPOSITE_PROXY_KEY = ResourceKey.create(Registries.BLOCK, NoMoreGap.id("composite_proxy"));
    public static final Block COMPOSITE = Registry.register(BuiltInRegistries.BLOCK, COMPOSITE_KEY,
            new CompositeBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_SLAB)
                    .setId(COMPOSITE_KEY).sound(SoundType.EMPTY).noOcclusion()
                    .lightLevel(state -> state.getValue(CompositeBlock.LAVA) ? 15 : state.getValue(CompositeBlock.LIT) ? 14 : 0)));
    public static final Block COMPOSITE_PROXY = Registry.register(BuiltInRegistries.BLOCK, COMPOSITE_PROXY_KEY,
            new CompositeProxyBlock(BlockBehaviour.Properties.of().setId(COMPOSITE_PROXY_KEY)
                    .strength(-1, 3_600_000).sound(SoundType.EMPTY).noOcclusion().noLootTable()));

    private ModBlocks() {}
    public static void initialize() {}
}
