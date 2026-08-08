package fr.xerneas02.nomoregap.block;

import com.mojang.serialization.MapCodec;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.stats.Stats;
import net.minecraft.sounds.SoundSource;
import fr.xerneas02.nomoregap.interaction.PartRaycaster;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.interaction.CompositeUseContext;
import fr.xerneas02.nomoregap.mixin.BlockTickInvoker;

public final class CompositeBlock extends BaseEntityBlock {
    public static final MapCodec<CompositeBlock> CODEC = simpleCodec(CompositeBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty WATER = BooleanProperty.create("water");
    public static final BooleanProperty LAVA = BooleanProperty.create("lava");

    public CompositeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(WATER, false).setValue(LAVA, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CompositeBlockEntity(pos, state); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeBlockEntity composite) || composite.parts().isEmpty()) {
            return net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState().getShape(world, pos, context);
        }
        return composite.geometry(world, context).selection();
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeBlockEntity composite) || composite.parts().isEmpty()) {
            return net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState().getCollisionShape(world, pos, context);
        }
        return composite.geometry(world, context).collision();
    }

    @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.empty(); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT, WATER, LAVA);
    }

    @Override protected FluidState getFluidState(BlockState state) {
        if (state.getValue(LAVA)) return Fluids.LAVA.getSource(false);
        if (state.getValue(WATER)) return Fluids.WATER.getSource(false);
        return super.getFluidState(state);
    }

    @Override protected int getSignal(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.core.Direction direction) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity composite) {
            return composite.parts().view().stream()
                    .mapToInt(part -> part.state().getSignal(world, pos, direction)).max().orElse(0);
        }
        return 0;
    }

    @Override protected int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.core.Direction direction) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity composite) {
            return composite.parts().view().stream()
                    .mapToInt(part -> part.state().getDirectSignal(world, pos, direction)).max().orElse(0);
        }
        return 0;
    }

    @Override public void onExplosionHit(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                         net.minecraft.world.level.Explosion explosion,
                                         java.util.function.BiConsumer<ItemStack, BlockPos> drops) {
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) {
            for (var part : composite.parts().view()) {
                if (part.state().getBlock() instanceof net.minecraft.world.level.block.TntBlock) {
                    part.state().onExplosionHit(level, pos, explosion, drops);
                }
            }
        }
        super.onExplosionHit(state, level, pos, explosion, drops);
    }

    @Override protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity composite && !composite.parts().isEmpty()) {
            return new ItemStack(composite.parts().view().getFirst().state().getBlock());
        }
        return super.getCloneItemStack(world, pos, state, includeData);
    }

    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity composite && !composite.parts().isEmpty()) {
            var target = targetedPart(composite, world, player);
            if (target.isEmpty()) return 0;
            var base = target.get().state();
            float hardness = base.getDestroySpeed(world, pos);
            if (hardness == -1) return 0;
            return player.getDestroySpeed(base) / hardness / (player.hasCorrectToolForDrops(base) ? 30 : 100);
        }
        return super.getDestroyProgress(state, player, world, pos);
    }

    @Override protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() || !(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)
                || composite.parts().isEmpty()) return;
        var target = targetedPart(composite, level, player);
        if (target.isEmpty()) return;
        var sound = target.get().state().getSoundType();
        player.playSound(sound.getHitSound(), (sound.getVolume() + 1) / 4, sound.getPitch() * 0.5f);
    }

    @Override protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) || composite.parts().isEmpty()) return;
        var target = targetedPart(composite, level, player);
        if (target.isEmpty()) return;
        var base = target.get().state();
        var particle = new BlockParticleOption(ParticleTypes.BLOCK, base);
        var random = player.getRandom();
        for (int i = 0; i < 12; i++) {
            level.addParticle(particle, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * 0.5,
                    pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) return;
        for (var part : composite.parts().view()) {
            if (part.state().getBlock() != net.minecraft.world.level.block.Blocks.TORCH) continue;
            double x = pos.getX() + 0.5 + part.transform().xDouble();
            double y = pos.getY() + 0.7 + part.transform().yDouble();
            double z = pos.getZ() + 0.5 + part.transform().zDouble();
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
        }
    }

    @Override protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) return;
        composite.refreshProxies();
        for (var part : java.util.List.copyOf(composite.parts().view())) {
            if (part.state().getBlock() instanceof net.minecraft.world.level.block.ButtonBlock
                    && part.state().getValue(BlockStateProperties.POWERED)) {
                CompositeUseContext.run(level, pos, composite, part.id(), () ->
                        ((BlockTickInvoker) part.state().getBlock()).noMoreGap$tick(part.state(), level, pos, random));
            }
        }
    }

    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                                        BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005f);
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || !(blockEntity instanceof CompositeBlockEntity composite)) return;
        composite.clearProxies();
        if (!composite.parts().isEmpty()) {
            var target = targetedPart(composite, level, player);
            if (target.isEmpty()) return;
            var targetPart = target.get();
            var sound = targetPart.state().getSoundType();
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            if (targetPart.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                var doorParts = composite.parts().view().stream()
                        .filter(part -> part.state().getBlock() == targetPart.state().getBlock()).toList();
                var lower = doorParts.stream().filter(part -> part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER).findFirst().orElse(targetPart);
                if (player.hasCorrectToolForDrops(lower.state())) {
                    Block.dropResources(lower.state(), server, pos, null, player, tool);
                }
                restoreAfterRemoval(level, pos, composite, doorParts.stream().map(PartInstance::id).collect(java.util.stream.Collectors.toSet()));
                return;
            }
            if (player.hasCorrectToolForDrops(targetPart.state())) {
                Block.dropResources(targetPart.state(), server, pos, null, player, tool);
            }
            restoreAfterRemoval(level, pos, composite, targetPart.id());
        }
    }

    public static void restoreAfterRemoval(Level level, BlockPos pos, CompositeBlockEntity composite, int removedId) {
        restoreAfterRemoval(level, pos, composite, java.util.Set.of(removedId));
    }

    public static void restoreAfterRemoval(Level level, BlockPos pos, CompositeBlockEntity composite, java.util.Set<Integer> removedIds) {
        composite.clearProxies();
        var remaining = composite.parts().view().stream()
                .filter(part -> !removedIds.contains(part.id()))
                .filter(part -> part.state().getBlock() != fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE
                        && part.state().getBlock() != fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY)
                .toList();
        BlockState doorTop = null;
        if (remaining.stream().anyMatch(part -> part.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                && part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)) {
            var candidate = level.getBlockState(pos.above());
            if (candidate.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) doorTop = candidate;
        }
        if (remaining.size() == 1 && isWholeCellPart(remaining.getFirst())) {
            var part = remaining.getFirst();
            var transform = part.transform();
            var targetPos = pos.offset(transform.x().units() / fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK,
                    transform.y().units() / fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK,
                    transform.z().units() / fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK);
            if (targetPos.equals(pos) || level.getBlockState(targetPos).isAir()) {
                level.setBlock(targetPos, part.state(), Block.UPDATE_ALL);
                if (!targetPos.equals(pos)) level.removeBlock(pos, false);
            } else {
                composite.replaceParts(remaining);
            }
        } else if (!remaining.isEmpty()) {
            composite.replaceParts(remaining);
        } else {
            level.removeBlock(pos, false);
        }
        if (doorTop != null) level.setBlock(pos.above(), doorTop, Block.UPDATE_CLIENTS);
        if (!remaining.isEmpty()) level.scheduleTick(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE, 1);
    }

    private static java.util.Optional<PartInstance> targetedPart(CompositeBlockEntity composite, BlockGetter world, Player player) {
        return PartRaycaster.raycast(composite, world, player, 6)
                .flatMap(hit -> composite.parts().find(hit.partId()));
    }

    /** A lone part aligned with a world cell no longer needs a composite anchor/proxy. */
    private static boolean isWholeCellPart(PartInstance part) {
        int unit = fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        var transform = part.transform();
        return transform.quarterTurns() == 0
                && transform.x().units() % unit == 0
                && transform.y().units() % unit == 0
                && transform.z().units() % unit == 0;
    }
}
