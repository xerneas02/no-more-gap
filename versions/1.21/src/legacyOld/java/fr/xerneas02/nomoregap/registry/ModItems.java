package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item COMPOSITE = Registry.register(BuiltInRegistries.ITEM, NoMoreGap.id("composite"),
            new BlockItem(ModBlocks.COMPOSITE, new Item.Properties()));

    private ModItems() {}
    public static void initialize() {}
}
