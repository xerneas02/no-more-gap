package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.OverlapTester;
import fr.xerneas02.nomoregap.geometry.SurfaceExtractor;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class CompositePlacementHandler {
    private CompositePlacementHandler() {}

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var held = player.getItemInHand(hand);
            var originalPos = hit.getBlockPos();
            var original = level.getBlockState(originalPos);
            if ((original.getBlock() instanceof SlabBlock || original.getBlock() instanceof SnowLayerBlock)
                    && held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == original.getBlock()) {
                return InteractionResult.PASS;
            }
            var coverTarget = footCoverTarget(level, hit, originalPos, original, held.getItem(), player);
            if (player.isSecondaryUseActive() && coverTarget != null) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                return addFootCover(player, level, hand, coverTarget.pos(), coverTarget.state(), held.getItem());
            }
            if (!player.isSecondaryUseActive() || hit.getDirection() != Direction.UP || !matches(original, held.getItem())
                    || level.getBlockEntity(hit.getBlockPos()) != null) {
                return InteractionResult.PASS;
            }
            var block = ((BlockItem) held.getItem()).getBlock();
            if (block instanceof EntityBlock) return InteractionResult.PASS;
            var placed = block.getStateForPlacement(new BlockPlaceContext(player, hand, held, hit));
            if (placed == null) {
                placed = block.defaultBlockState();
                if (placed.hasProperty(BlockStateProperties.ATTACH_FACE)) {
                    placed = placed.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
                }
                if (placed.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    placed = placed.setValue(BlockStateProperties.HORIZONTAL_FACING, player.getDirection().getOpposite());
                }
            }
            double localX = hit.getLocation().x - hit.getBlockPos().getX();
            double localZ = hit.getLocation().z - hit.getBlockPos().getZ();
            var context = CollisionContext.of(player);
            var occupied = original.getCollisionShape(level, hit.getBlockPos(), context);
            var surface = SurfaceExtractor.topAt(occupied, localX, localZ);
            if (surface.isEmpty()) return InteractionResult.PASS;
            var transform = new LocalTransform(FixedPoint.ZERO, surface.get().y(), FixedPoint.ZERO, 0);
            var placedShape = placed.getShape(level, hit.getBlockPos(), context);
            // ponytail: oversized parts do not reserve adjacent cells; add multi-cell ownership when cross-cell collisions matter.
            if (OverlapTester.overlaps(occupied, placedShape, transform)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) return InteractionResult.SUCCESS;

            if (!level.setBlock(hit.getBlockPos(), ModBlocks.COMPOSITE.defaultBlockState(), Block.UPDATE_ALL)) {
                return InteractionResult.FAIL;
            }
            if (!(level.getBlockEntity(hit.getBlockPos()) instanceof CompositeBlockEntity composite)) {
                level.setBlock(hit.getBlockPos(), original, Block.UPDATE_ALL);
                return InteractionResult.FAIL;
            }
            composite.addPart(original, LocalTransform.IDENTITY, 0);
            composite.addPart(placed, transform, 0);
            if (!player.isCreative()) held.shrink(1);
            var sound = placed.getSoundType();
            level.playSound(null, hit.getBlockPos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    static boolean matches(BlockState state, Item item) {
        return item instanceof BlockItem blockItem
                && !(blockItem.getBlock() instanceof CompositeBlock)
                && !state.isAir()
                && state.getFluidState().isEmpty();
    }

    private static CoverTarget footCoverTarget(net.minecraft.world.level.Level level, net.minecraft.world.phys.BlockHitResult hit,
                                               net.minecraft.core.BlockPos hitPos, BlockState hitState, Item item,
                                               net.minecraft.world.entity.player.Player player) {
        if (!(item instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof SnowLayerBlock || blockItem.getBlock() instanceof CarpetBlock)) return null;
        if (hit.getDirection() == Direction.UP) {
            var above = hitPos.above();
            var aboveState = level.getBlockState(above);
            return canFootCover(aboveState, level, above, player) ? new CoverTarget(above, aboveState) : null;
        }
        double localY = hit.getLocation().y - hitPos.getY();
        return localY <= 0.125 && canFootCover(hitState, level, hitPos, player) ? new CoverTarget(hitPos, hitState) : null;
    }

    private static boolean canFootCover(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                        net.minecraft.world.entity.player.Player player) {
        if (state.getBlock() instanceof SnowLayerBlock || state.getBlock() instanceof CarpetBlock || state.isAir() || !state.getFluidState().isEmpty()
                || level.getBlockEntity(pos) != null) return false;
        return !Block.isShapeFullBlock(state.getCollisionShape(level, pos, CollisionContext.of(player)));
    }

    private static InteractionResult addFootCover(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level,
                                                  net.minecraft.world.InteractionHand hand, net.minecraft.core.BlockPos pos,
                                                  BlockState original, Item item) {
        if (!level.setBlock(pos, ModBlocks.COMPOSITE.defaultBlockState(), Block.UPDATE_ALL)) return InteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) {
            level.setBlock(pos, original, Block.UPDATE_ALL);
            return InteractionResult.FAIL;
        }
        composite.addPart(original, LocalTransform.IDENTITY, 0);
        composite.addPart(((BlockItem) item).getBlock().defaultBlockState(), LocalTransform.IDENTITY, 0);
        if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
        var sound = ((BlockItem) item).getBlock().defaultBlockState().getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        return InteractionResult.SUCCESS_SERVER;
    }

    private record CoverTarget(net.minecraft.core.BlockPos pos, BlockState state) {}
}
