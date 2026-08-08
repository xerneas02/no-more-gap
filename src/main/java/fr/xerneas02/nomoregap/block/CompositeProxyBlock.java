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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CompositeProxyBlock extends BaseEntityBlock {
    public static final MapCodec<CompositeProxyBlock> CODEC = simpleCodec(CompositeProxyBlock::new);

    public CompositeProxyBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CompositeProxyBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy)
                || !(world.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity composite)) return Shapes.empty();
        var anchor = proxy.anchor();
        return composite.geometry(world, context).collision().move(
                anchor.getX() - pos.getX(), anchor.getY() - pos.getY(), anchor.getZ() - pos.getZ());
    }
}
