package fr.xerneas02.nomoregap;

import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.CompositeProxyBlock;
import fr.xerneas02.nomoregap.block.entity.NeoForgeCompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.NeoForgeCompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.config.NoMoreGapConfig;
import fr.xerneas02.nomoregap.lava.LavaLoggingRules;
import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import fr.xerneas02.nomoregap.registry.ModItems;
import fr.xerneas02.nomoregap.rule.CompositeRules;
import fr.xerneas02.nomoregap.worldgen.SnowyVegetationFeature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Set;

public final class NeoForgeRegistries {
    private NeoForgeRegistries() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            ModBlocks.COMPOSITE = new CompositeBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_SLAB)
                    .setId(ModBlocks.COMPOSITE_KEY).sound(SoundType.EMPTY).noOcclusion().noLootTable()
                    .lightLevel(state -> state.getValue(CompositeBlock.LAVA) ? 15 : state.getValue(CompositeBlock.LIT) ? 14 : 0));
            registry.register(NoMoreGap.id("composite"), ModBlocks.COMPOSITE);
            ModBlocks.COMPOSITE_PROXY = new CompositeProxyBlock(BlockBehaviour.Properties.of().setId(ModBlocks.COMPOSITE_PROXY_KEY)
                    .strength(-1, 3_600_000).sound(SoundType.EMPTY).noOcclusion().noLootTable());
            registry.register(NoMoreGap.id("composite_proxy"), ModBlocks.COMPOSITE_PROXY);
        });
        event.register(Registries.ITEM, registry -> {
            ModItems.COMPOSITE = new BlockItem(ModBlocks.COMPOSITE,
                    new Item.Properties().setId(ModItems.COMPOSITE_KEY).useBlockDescriptionPrefix());
            registry.register(NoMoreGap.id("composite"), ModItems.COMPOSITE);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            ModBlockEntities.COMPOSITE = new BlockEntityType<>(NeoForgeCompositeBlockEntity::new, Set.of(ModBlocks.COMPOSITE));
            registry.register(NoMoreGap.id("composite"), ModBlockEntities.COMPOSITE);
            ModBlockEntities.COMPOSITE_PROXY = new BlockEntityType<>(NeoForgeCompositeProxyBlockEntity::new, Set.of(ModBlocks.COMPOSITE_PROXY));
            registry.register(NoMoreGap.id("composite_proxy"), ModBlockEntities.COMPOSITE_PROXY);
        });
        event.register(Registries.FEATURE, registry ->
                registry.register(NoMoreGap.id("snowy_vegetation"), new SnowyVegetationFeature()));
        event.register(Registries.GAME_RULE, registry -> {
            LavaLoggingRules.DO_REACTIONS = GameRules.registerBoolean("no_more_gap:do_lava_logging_reactions",
                    GameRuleCategory.MISC, NoMoreGapConfig.lavaLoggingReactions());
            CompositeRules.MAX_PARTS = GameRules.registerInteger("no_more_gap:max_composite_parts",
                    GameRuleCategory.MISC, NoMoreGapConfig.maxCompositeParts(), 2, 64);
        });
    }
}
