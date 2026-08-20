package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.NoMoreGap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final ResourceKey<Item> COMPOSITE_KEY = ResourceKey.create(Registries.ITEM, NoMoreGap.id("composite"));
    public static Item COMPOSITE;
    private ModItems() {}
    public static void initialize() {}
}
