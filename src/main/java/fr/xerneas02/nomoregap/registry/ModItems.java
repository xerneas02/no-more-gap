package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final ResourceKey<Item> COMPOSITE_KEY = ResourceKey.create(Registries.ITEM, NoMoreGap.id("composite"));
    public static final Item COMPOSITE = Registry.register(BuiltInRegistries.ITEM, COMPOSITE_KEY,
            new BlockItem(ModBlocks.COMPOSITE, new Item.Properties().setId(COMPOSITE_KEY).useBlockDescriptionPrefix()));

    private ModItems() {}
    public static void initialize() {}
}
