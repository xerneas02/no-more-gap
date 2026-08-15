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
    @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CompositeBlockEntity(pos, state); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeBlockEntity composite) || composite.parts().isEmpty()) {
            return net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState().getShape(world, pos, context);
        }
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player) {
            var targeted = PartRaycaster.targetedShape(composite, world, player, null);
            if (targeted.isPresent()) return targeted.get();
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
                    .filter(part -> part.state().getBlock() != this
                            && part.state().getBlock() != fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY)
                    .mapToInt(part -> part.state().getSignal(world, pos, direction)).max().orElse(0);
        }
        return 0;
    }

    @Override protected int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, net.minecraft.core.Direction direction) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity composite) {
            return composite.parts().view().stream()
                    .filter(part -> part.state().getBlock() != this
                            && part.state().getBlock() != fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY)
                    .mapToInt(part -> part.state().getDirectSignal(world, pos, direction)).max().orElse(0);
        }
        return 0;
    }

    @Override protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                             Block neighborBlock,
                                             net.minecraft.world.level.redstone.Orientation orientation,
                                             boolean movedByPiston) {
        if (!level.isClientSide()) fr.xerneas02.nomoregap.interaction.CompositePartUpdater.refreshAround(level, pos);
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
        var part = target.get();
        var sound = part.state().getSoundType();
        level.playLocalSound(pos.getX() + 0.5 + part.transform().xDouble(),
                pos.getY() + 0.5 + part.transform().yDouble(), pos.getZ() + 0.5 + part.transform().zDouble(),
                sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 4,
                sound.getPitch() * 0.5f, false);
    }

    @Override protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) || composite.parts().isEmpty()) return;
        var target = targetedPart(composite, level, player);
        if (target.isEmpty()) return;
        var base = target.get().state();
        var particle = new BlockParticleOption(ParticleTypes.BLOCK, base);
        var random = player.getRandom();
        var transform = target.get().transform();
        for (int i = 0; i < 12; i++) {
            level.addParticle(particle, pos.getX() + transform.xDouble() + random.nextDouble(),
                    pos.getY() + transform.yDouble() + random.nextDouble() * 0.5,
                    pos.getZ() + transform.zDouble() + random.nextDouble(), 0, 0, 0);
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
        for (var id : composite.takeScheduledParts(level.getGameTime())) {
            var part = composite.parts().find(id).orElse(null);
            if (part != null && part.state().getBlock() instanceof net.minecraft.world.level.block.ButtonBlock) {
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
        targetedPart(composite, level, player).ifPresent(part -> destroyPart(level, player, pos, composite, part.id(), tool, null));
    }

    /** Shared by the anchor and its proxy cells, so both destroy the exact same selected part. */
    public static void destroyPart(Level level, Player player, BlockPos pos, CompositeBlockEntity composite, int partId,
                                   ItemStack tool) {
        destroyPart(level, player, pos, composite, partId, tool, null);
    }

    public static void destroyPart(Level level, Player player, BlockPos pos, CompositeBlockEntity composite, int partId,
                                   ItemStack tool, BlockPos minedProxy) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        var targetPart = composite.parts().find(partId).orElse(null);
        if (targetPart == null) return;
        composite.clearProxiesExcept(minedProxy);
        var sound = targetPart.state().getSoundType();
        level.playSound(null, pos.getX() + 0.5 + targetPart.transform().xDouble(),
                pos.getY() + 0.5 + targetPart.transform().yDouble(),
                pos.getZ() + 0.5 + targetPart.transform().zDouble(), sound.getBreakSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        var directlyRemoved = targetPart.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                ? composite.parts().view().stream().filter(part -> part.state().getBlock() == targetPart.state().getBlock())
                .map(PartInstance::id).collect(java.util.stream.Collectors.toSet())
                : java.util.Set.of(targetPart.id());
        var removed = targetPart.id() == sourcePartId(composite.parts().view())
                ? composite.parts().view().stream().map(PartInstance::id).collect(java.util.stream.Collectors.toSet())
                : new java.util.HashSet<>(directlyRemoved);
        if (isFootCover(targetPart)) {
            for (boolean changed = true; changed;) {
                changed = composite.parts().view().stream()
                        .filter(part -> !removed.contains(part.id()))
                        .filter(part -> removed.stream().map(composite.parts()::find).flatMap(java.util.Optional::stream)
                                .anyMatch(support -> restsOn(level, pos, part, support)))
                        .anyMatch(part -> removed.add(part.id()));
            }
        }
        for (var part : composite.parts().view()) {
            if (!removed.contains(part.id()) || (part.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)) continue;
            if (!player.preventsBlockDrops()) {
                Block.dropResources(part.state(), server, partPos(pos, part), null, player, tool);
            }
        }
        for (var part : composite.parts().view()) {
            if (removed.contains(part.id())
                    && part.state().getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock
                    && part.state().getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                level.setBlock(partPos(pos, part).above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        restoreAfterRemoval(level, pos, composite, removed, minedProxy);
    }

    public static void restoreAfterRemoval(Level level, BlockPos pos, CompositeBlockEntity composite, int removedId) {
        restoreAfterRemoval(level, pos, composite, java.util.Set.of(removedId));
    }

    public static void restoreAfterRemoval(Level level, BlockPos pos, CompositeBlockEntity composite, java.util.Set<Integer> removedIds) {
        restoreAfterRemoval(level, pos, composite, removedIds, null);
    }

    private static void restoreAfterRemoval(Level level, BlockPos pos, CompositeBlockEntity composite,
                                            java.util.Set<Integer> removedIds, BlockPos minedProxy) {
        composite.clearProxiesExcept(minedProxy);
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
        if (isWholeDoorPair(remaining)) {
            var lower = remaining.stream().filter(part -> part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER).findFirst().orElseThrow();
            var upper = remaining.stream().filter(part -> part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER).findFirst().orElseThrow();
            level.setBlock(pos, lower.state(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            level.setBlock(pos.above(), upper.state(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            level.updateNeighborsAt(pos, lower.state().getBlock());
            level.updateNeighborsAt(pos.above(), upper.state().getBlock());
        } else if (remaining.size() == 1 && isWholeCellPart(remaining.getFirst())
                && !(remaining.getFirst().state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
            var part = remaining.getFirst();
            var transform = part.transform();
            var targetPos = pos.offset(transform.x().units() / fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK,
                    transform.y().units() / fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK,
                    transform.z().units() / fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK);
            // A remaining part in a proxy cell must stay composite until the clicked proxy has been removed.
            if (targetPos.equals(pos)) {
                int flags = part.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        ? Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS
                        : Block.UPDATE_ALL;
                level.setBlock(targetPos, part.state(), flags);
            } else {
                composite.replaceParts(remaining);
            }
        } else if (!remaining.isEmpty()) {
            composite.replaceParts(remaining);
        } else {
            level.removeBlock(pos, false);
        }
        if (doorTop != null) level.setBlock(pos.above(), doorTop, Block.UPDATE_CLIENTS);
        if (!remaining.isEmpty()) {
            level.updateNeighborsAt(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE);
            level.scheduleTick(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE, 1);
        }
    }

    private static BlockPos partPos(BlockPos anchor, PartInstance part) {
        return anchor.offset((int) Math.floor(part.transform().xDouble()), (int) Math.floor(part.transform().yDouble()),
                (int) Math.floor(part.transform().zDouble()));
    }

    static int sourcePartId(java.util.List<PartInstance> parts) {
        return parts.stream().filter(part -> !isFootCover(part))
                .findFirst().orElse(parts.getFirst()).id();
    }

    private static boolean isFootCover(PartInstance part) {
        return part.state().getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock
                || part.state().getBlock() instanceof net.minecraft.world.level.block.CarpetBlock
                || part.state().getBlock() instanceof net.minecraft.world.level.block.MossyCarpetBlock;
    }

    static boolean restsOn(BlockGetter level, BlockPos pos, PartInstance part, PartInstance support) {
        for (var upper : fr.xerneas02.nomoregap.geometry.ShapeTransformer.transform(
                part.state().getShape(level, pos, CollisionContext.empty()), part.transform()).toAabbs()) {
            for (var lower : fr.xerneas02.nomoregap.geometry.ShapeTransformer.transform(
                    support.state().getShape(level, pos, CollisionContext.empty()), support.transform()).toAabbs()) {
                if (Math.abs(upper.minY - lower.maxY) < 1.0e-7
                        && Math.min(upper.maxX, lower.maxX) > Math.max(upper.minX, lower.minX)
                        && Math.min(upper.maxZ, lower.maxZ) > Math.max(upper.minZ, lower.minZ)) return true;
            }
        }
        return false;
    }

    private static boolean isWholeDoorPair(java.util.List<PartInstance> parts) {
        if (parts.size() != 2 || !(parts.getFirst().state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                || parts.getFirst().state().getBlock() != parts.getLast().state().getBlock()) return false;
        int unit = fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        return parts.stream().anyMatch(part -> part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                && part.transform().x().units() == 0 && part.transform().y().units() == 0 && part.transform().z().units() == 0)
                && parts.stream().anyMatch(part -> part.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                && part.transform().x().units() == 0 && part.transform().y().units() == unit && part.transform().z().units() == 0);
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
