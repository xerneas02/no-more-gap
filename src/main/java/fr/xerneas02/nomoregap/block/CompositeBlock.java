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

public final class CompositeBlock extends BaseEntityBlock {
    public static final MapCodec<CompositeBlock> CODEC = simpleCodec(CompositeBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public CompositeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
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
        builder.add(LIT);
    }

    @Override protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity composite && !composite.parts().isEmpty()) {
            return new ItemStack(composite.parts().view().getFirst().state().getBlock());
        }
        return super.getCloneItemStack(world, pos, state, includeData);
    }

    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity composite && !composite.parts().isEmpty()) {
            var base = targetedPart(composite, world, player).state();
            float hardness = base.getDestroySpeed(world, pos);
            if (hardness == -1) return 0;
            return player.getDestroySpeed(base) / hardness / (player.hasCorrectToolForDrops(base) ? 30 : 100);
        }
        return super.getDestroyProgress(state, player, world, pos);
    }

    @Override protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() || !(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)
                || composite.parts().isEmpty()) return;
        var sound = targetedPart(composite, level, player).state().getSoundType();
        player.playSound(sound.getHitSound(), (sound.getVolume() + 1) / 4, sound.getPitch() * 0.5f);
    }

    @Override protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite) || composite.parts().isEmpty()) return;
        var base = targetedPart(composite, level, player).state();
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

    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                                        BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005f);
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || !(blockEntity instanceof CompositeBlockEntity composite)) return;
        composite.clearProxies();
        if (!composite.parts().isEmpty()) {
            var target = targetedPart(composite, level, player);
            var sound = target.state().getSoundType();
            level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            if (target.id() == composite.parts().view().getFirst().id()) {
                for (var part : composite.parts().view()) {
                    if (player.hasCorrectToolForDrops(part.state())) {
                        Block.dropResources(part.state(), server, pos, null, player, tool);
                    }
                }
            } else {
                if (player.hasCorrectToolForDrops(target.state())) {
                    Block.dropResources(target.state(), server, pos, null, player, tool);
                }
                restoreAfterRemoval(level, pos, composite, target.id());
            }
        }
    }

    public static void restoreAfterRemoval(Level level, BlockPos pos, CompositeBlockEntity composite, int removedId) {
        composite.clearProxies();
        var remaining = composite.parts().view().stream().filter(part -> part.id() != removedId).toList();
        if (remaining.size() == 1 && remaining.getFirst().transform().equals(fr.xerneas02.nomoregap.geometry.LocalTransform.IDENTITY)) {
            level.setBlock(pos, remaining.getFirst().state(), Block.UPDATE_ALL);
        } else if (!remaining.isEmpty()) {
            level.setBlock(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CompositeBlockEntity rebuilt) {
                remaining.forEach(part -> rebuilt.addPart(part.state(), part.transform(), part.flags()));
            }
        }
    }

    private static PartInstance targetedPart(CompositeBlockEntity composite, BlockGetter world, Player player) {
        return PartRaycaster.raycast(composite, world, player, 6)
                .flatMap(hit -> composite.parts().find(hit.partId()))
                .orElseGet(() -> composite.parts().view().getFirst());
    }
}
