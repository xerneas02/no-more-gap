package fr.xerneas02.nomoregap.registry;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static BlockEntityType<CompositeBlockEntity> COMPOSITE;
    public static BlockEntityType<CompositeProxyBlockEntity> COMPOSITE_PROXY;
    private ModBlockEntities() {}
    public static void initialize() {}
}
