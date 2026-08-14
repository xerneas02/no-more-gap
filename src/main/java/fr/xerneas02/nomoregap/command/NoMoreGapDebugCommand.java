package fr.xerneas02.nomoregap.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class NoMoreGapDebugCommand {
    private static final int STRESS_TEST_FLAG = 1 << 30;
    private static final Block[] STRESS_CARPETS = java.util.stream.Stream.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")
            .map(color -> BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(color + "_carpet")))
            .toArray(Block[]::new);

    private NoMoreGapDebugCommand() {}

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("no_more_gap"));
        dispatcher.register(root("nmg"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("add_test_part").executes(ctx -> add(ctx.getSource())))
                        .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                        .then(Commands.literal("inspect").executes(ctx -> inspect(ctx.getSource())))
                        .then(Commands.literal("stress_test")
                                .executes(ctx -> stressTest(ctx.getSource(), 6))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 12))
                                        .executes(ctx -> stressTest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("clear_stress_test")
                                .executes(ctx -> clearStressTest(ctx.getSource(), 16))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> clearStressTest(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static CompositeBlockEntity target(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        HitResult hit = player.pick(6, 0, false);
        if (hit instanceof BlockHitResult blockHit && player.level().getBlockEntity(blockHit.getBlockPos()) instanceof CompositeBlockEntity composite) return composite;
        source.sendFailure(Component.literal("Look at a composite block within 6 blocks."));
        return null;
    }

    private static int add(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var composite = target(source);
        if (composite == null) return 0;
        try {
            var part = composite.addPart(Blocks.STONE.defaultBlockState(), LocalTransform.IDENTITY, 0);
            source.sendSuccess(() -> Component.literal("Added test part " + part.id()), false);
            return 1;
        } catch (IllegalStateException full) {
            source.sendFailure(Component.literal("Composite is full."));
            return 0;
        }
    }

    private static int stressTest(CommandSourceStack source, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = player.level();
        BlockPos center = player.blockPosition().above(3);
        int placed = 0, skipped = 0;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            BlockPos pos = center.offset(x, 0, z);
            boolean clear = true;
            for (int y = 0; y < 4; y++) clear &= level.getBlockState(pos.above(y)).isAir();
            if (!clear || !level.setBlock(pos, ModBlocks.COMPOSITE.defaultBlockState(), Block.UPDATE_CLIENTS)
                    || !(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) {
                skipped++;
                continue;
            }
            composite.beginUpdate();
            try {
                for (int i = 0; i < NoMoreGapLimits.MAX_PARTS_PER_CELL; i++) {
                    composite.addPart(STRESS_CARPETS[i % STRESS_CARPETS.length].defaultBlockState(),
                            new LocalTransform(FixedPoint.ZERO,
                                    new FixedPoint(i * NoMoreGapLimits.FIXED_UNITS_PER_PIXEL), FixedPoint.ZERO, 0),
                            STRESS_TEST_FLAG);
                }
            } finally {
                composite.endUpdate();
            }
            placed++;
        }
        int finalPlaced = placed, finalSkipped = skipped;
        source.sendSuccess(() -> Component.literal("Stress test: " + finalPlaced + " composites, "
                + (finalPlaced * NoMoreGapLimits.MAX_PARTS_PER_CELL) + " parts, " + finalSkipped + " positions skipped. ")
                .append(Component.literal("Remove them with /nmg debug clear_stress_test " + radius)), true);
        return placed;
    }

    private static int clearStressTest(CommandSourceStack source, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = player.level();
        BlockPos center = player.blockPosition();
        int removed = 0;
        for (int x = -radius; x <= radius; x++) for (int y = -16; y <= 16; y++) for (int z = -radius; z <= radius; z++) {
            BlockPos pos = center.offset(x, y, z);
            if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)
                    || composite.parts().view().stream().noneMatch(part -> (part.flags() & STRESS_TEST_FLAG) != 0)) continue;
            composite.clearProxies();
            level.removeBlock(pos, false);
            removed++;
        }
        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal("Removed " + finalRemoved + " stress-test composites."), true);
        return removed;
    }

    private static int clear(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var composite = target(source);
        if (composite == null) return 0;
        composite.clearParts();
        source.sendSuccess(() -> Component.literal("Composite cleared."), false);
        return 1;
    }

    private static int inspect(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var composite = target(source);
        if (composite == null) return 0;
        source.sendSuccess(() -> Component.literal("Composite " + composite.getBlockPos() + ": parts=" + composite.parts().view()
                + ", revision=" + composite.revision() + ", geometryDirty=" + composite.isGeometryDirty() + ", ticker=false"), false);
        return 1;
    }
}
