package fr.xerneas02.nomoregap.command;

import com.mojang.brigadier.CommandDispatcher;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class NoMoreGapDebugCommand {
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
                        .then(Commands.literal("inspect").executes(ctx -> inspect(ctx.getSource()))));
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
            source.sendFailure(Component.literal("Composite already contains 16 parts."));
            return 0;
        }
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
