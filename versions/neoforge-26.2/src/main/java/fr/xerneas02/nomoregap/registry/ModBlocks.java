package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public final class ModBlocks {
    public static final ResourceKey<Block> COMPOSITE_KEY = ResourceKey.create(Registries.BLOCK, NoMoreGap.id("composite"));
    public static final ResourceKey<Block> COMPOSITE_PROXY_KEY = ResourceKey.create(Registries.BLOCK, NoMoreGap.id("composite_proxy"));
    public static Block COMPOSITE;
    public static Block COMPOSITE_PROXY;
    private ModBlocks() {}
    public static void initialize() {}
}
