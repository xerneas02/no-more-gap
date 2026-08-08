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
            return anchorBlock.getDestroyProgress(world.getBlockState(proxy.anchor()), player, world, proxy.anchor());
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
                && level.getBlockState(proxy.anchor()).getBlock() instanceof CompositeBlock anchorBlock) {
            anchorBlock.attack(level.getBlockState(proxy.anchor()), level, proxy.anchor(), player);
        }
    }

    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                                        BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof CompositeProxyBlockEntity proxy
                && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite
                && level.getBlockState(proxy.anchor()).getBlock() instanceof CompositeBlock anchorBlock) {
            anchorBlock.playerDestroy(level, player, proxy.anchor(), level.getBlockState(proxy.anchor()), composite, tool);
        }
    }

    @Override protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy
                && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite
                && level.getBlockState(proxy.anchor()).getBlock() instanceof CompositeBlock anchorBlock) {
            anchorBlock.spawnDestroyParticles(level, player, proxy.anchor(), level.getBlockState(proxy.anchor()));
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
