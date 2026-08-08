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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CompositeProxyBlock extends BaseEntityBlock {
    public static final MapCodec<CompositeProxyBlock> CODEC = simpleCodec(CompositeProxyBlock::new);

    public CompositeProxyBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CompositeProxyBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy)
                || !(world.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite)) return Shapes.empty();
        var anchor = proxy.anchor();
        return composite.geometry(world, context).selection().move(
                anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy)
                || !(world.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite)) return Shapes.empty();
        var anchor = proxy.anchor();
        return composite.geometry(world, context).collision().move(
                anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
    }

    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy
                && world.getBlockState(proxy.anchor()).getBlock() instanceof CompositeBlock anchorBlock) {
            return anchorBlock.getDestroyProgress(world.getBlockState(proxy.anchor()), player, world, proxy.anchor());
        }
        return 0;
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
}
