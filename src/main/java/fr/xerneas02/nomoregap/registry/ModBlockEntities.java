package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<CompositeBlockEntity> COMPOSITE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, NoMoreGap.id("composite"),
            FabricBlockEntityTypeBuilder.create(CompositeBlockEntity::new, ModBlocks.COMPOSITE).build());
    public static final BlockEntityType<CompositeProxyBlockEntity> COMPOSITE_PROXY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, NoMoreGap.id("composite_proxy"),
            FabricBlockEntityTypeBuilder.create(CompositeProxyBlockEntity::new, ModBlocks.COMPOSITE_PROXY).build());

    private ModBlockEntities() {}
    public static void initialize() {}
}
