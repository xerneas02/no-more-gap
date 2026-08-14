package fr.xerneas02.nomoregap.block;

import com.mojang.serialization.MapCodec;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;

public final class CompositeProxyBlock extends BaseEntityBlock {
    public static final MapCodec<CompositeProxyBlock> CODEC = simpleCodec(CompositeProxyBlock::new);

    public CompositeProxyBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CompositeProxyBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy)
                || !(world.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite)) return Shapes.empty();
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player) {
            var targeted = fr.xerneas02.nomoregap.interaction.PartRaycaster.targetedShape(composite, world, player, pos);
            if (targeted.isPresent()) return targeted.get().move(composite.getBlockPos().getX() - pos.getX(),
                    composite.getBlockPos().getY() - pos.getY(), composite.getBlockPos().getZ() - pos.getZ());
        }
        return clipToCell(composite.geometry(world, context).selection(), composite.getBlockPos(), pos);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy)
                || !(world.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite)) return Shapes.empty();
        return clipToCell(composite.geometry(world, context).collision(), composite.getBlockPos(), pos);
    }

    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy
                && world.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite
                && occupiesCell(composite, world, pos)
                && world.getBlockState(proxy.anchor()).getBlock() instanceof CompositeBlock anchorBlock) {
            var target = fr.xerneas02.nomoregap.interaction.PartRaycaster.raycastInCell(composite, world, player, 6, pos);
            if (target.isEmpty()) return 0;
            var part = composite.parts().find(target.get().partId()).orElse(null);
            if (part == null) return 0;
            float hardness = part.state().getDestroySpeed(world, pos);
            return hardness == -1 ? 0 : player.getDestroySpeed(part.state()) / hardness
                    / (player.hasCorrectToolForDrops(part.state()) ? 30 : 100);
        }
        return 1;
    }

    @Override protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        var pos = context.getClickedPos();
        return !(context.getLevel().getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy)
                || !(context.getLevel().getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite)
                || !occupiesCell(composite, context.getLevel(), pos);
    }

    @Override protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy
                && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite) {
            fr.xerneas02.nomoregap.interaction.PartRaycaster.raycastInCell(composite, level, player, 6, pos)
                    .flatMap(hit -> composite.parts().find(hit.partId())).ifPresent(part -> {
                        var sound = part.state().getSoundType();
                        var anchor = composite.getBlockPos();
                        level.playLocalSound(anchor.getX() + 0.5 + part.transform().xDouble(),
                                anchor.getY() + 0.5 + part.transform().yDouble(),
                                anchor.getZ() + 0.5 + part.transform().zDouble(), sound.getHitSound(),
                                net.minecraft.sounds.SoundSource.BLOCKS, (sound.getVolume() + 1) / 4,
                                sound.getPitch() * 0.5f, false);
                    });
        }
    }

    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                                        BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof CompositeProxyBlockEntity proxy
                && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite
                && level.getBlockState(proxy.anchor()).getBlock() instanceof CompositeBlock) {
            fr.xerneas02.nomoregap.interaction.PartRaycaster.raycastInCell(composite, level, player, 6, pos)
                    .flatMap(hit -> composite.parts().find(hit.partId()))
                    .ifPresent(part -> CompositeBlock.destroyPart(level, player, proxy.anchor(), composite, part.id(), tool, pos));
        }
    }

    @Override protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy
                && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite
                && level.getBlockState(proxy.anchor()).getBlock() instanceof CompositeBlock) {
            fr.xerneas02.nomoregap.interaction.PartRaycaster.raycastInCell(composite, level, player, 6, pos)
                    .flatMap(hit -> composite.parts().find(hit.partId())).ifPresent(part -> {
                        var particle = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, part.state());
                        var random = player.getRandom();
                        for (int i = 0; i < 12; i++) level.addParticle(particle, pos.getX() + random.nextDouble(),
                                pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0, 0);
                    });
        }
    }

    private static boolean occupiesCell(CompositeBlockEntity composite, BlockGetter world, BlockPos proxyPos) {
        return !clipToCell(composite.geometry(world, CollisionContext.empty()).selection(),
                composite.getBlockPos(), proxyPos).isEmpty();
    }

    /** Converts anchor-local geometry to the local coordinates of one proxy cell. */
    private static VoxelShape clipToCell(VoxelShape source, BlockPos anchor, BlockPos cell) {
        int x = cell.getX() - anchor.getX(), y = cell.getY() - anchor.getY(), z = cell.getZ() - anchor.getZ();
        VoxelShape clipped = Shapes.empty();
        for (var box : source.toAabbs()) {
            double minX = Math.max(box.minX, x), minY = Math.max(box.minY, y), minZ = Math.max(box.minZ, z);
            double maxX = Math.min(box.maxX, x + 1), maxY = Math.min(box.maxY, y + 1), maxZ = Math.min(box.maxZ, z + 1);
            if (minX < maxX && minY < maxY && minZ < maxZ) {
                clipped = Shapes.or(clipped, Shapes.create(new AABB(minX - x, minY - y, minZ - z,
                        maxX - x, maxY - y, maxZ - z)));
            }
        }
        return clipped;
    }
}
