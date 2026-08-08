package fr.xerneas02.nomoregap.registry;

public final class ModRegistries {
    private ModRegistries() {}
    public static void initialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
    }
}
