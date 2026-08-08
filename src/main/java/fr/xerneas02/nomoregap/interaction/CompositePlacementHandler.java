package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.OverlapTester;
import fr.xerneas02.nomoregap.geometry.SurfaceExtractor;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import fr.xerneas02.nomoregap.lava.LavaLogging;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
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
            if (held.getItem() instanceof BlockItem blockItem && original.getFluidState().is( net.minecraft.world.level.material.Fluids.LAVA)) {
                var placed = blockItem.getBlock().getStateForPlacement(new BlockPlaceContext(player, hand, held, hit));
                if (placed != null && canLavaLog(placed)) {
                    if (level.isClientSide()) return InteractionResult.SUCCESS;
                    level.setBlock(originalPos, placed.setValue(LavaLogging.LAVA_LOGGED, true), Block.UPDATE_ALL);
                    if (!player.isCreative()) held.shrink(1);
                    var sound = placed.getSoundType();
                    level.playSound(null, originalPos, sound.getPlaceSound(), SoundSource.BLOCKS,
                            (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
                    return InteractionResult.SUCCESS_SERVER;
                }
            }
            var compositeResult = useFluidBucketOnCompositePart(player, level, hand, originalPos, held);
            if (compositeResult != null) return compositeResult;
            if (held.is(Items.WATER_BUCKET) && isLavaLogged(original)) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                level.setBlock(originalPos, original.setValue(LavaLogging.LAVA_LOGGED, false)
                        .setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
                level.scheduleTick(originalPos, net.minecraft.world.level.material.Fluids.WATER,
                        net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
                if (!player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                level.playSound(null, originalPos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
                        SoundSource.BLOCKS, 1, 1);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (held.is(Items.BUCKET) && isLavaLogged(original)) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                level.setBlock(originalPos, original.setValue(LavaLogging.LAVA_LOGGED, false), Block.UPDATE_ALL);
                if (!player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.LAVA_BUCKET));
                level.playSound(null, originalPos, net.minecraft.sounds.SoundEvents.BUCKET_FILL_LAVA,
                        SoundSource.BLOCKS, 1, 1);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (held.is(Items.LAVA_BUCKET) && canLavaLog(original)) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                level.setBlock(originalPos, original.setValue(LavaLogging.LAVA_LOGGED, true), Block.UPDATE_ALL);
                level.scheduleTick(originalPos, net.minecraft.world.level.material.Fluids.LAVA,
                        net.minecraft.world.level.material.Fluids.LAVA.getTickDelay(level));
                consumeLavaBucket(player, hand);
                level.playSound(null, originalPos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_LAVA,
                        SoundSource.BLOCKS, 1, 1);
                return InteractionResult.SUCCESS_SERVER;
            }
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
            if (placed.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && placed.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                var top = placed.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                level.setBlock(hit.getBlockPos().above(), top, Block.UPDATE_ALL);
            }
            CompositePartUpdater.refreshAround(level, hit.getBlockPos());
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
        BlockState doorTop = null;
        if (original.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                && original.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            var candidate = level.getBlockState(pos.above());
            if (candidate.getBlock() == original.getBlock()) doorTop = candidate;
        }
        if (!level.setBlock(pos, ModBlocks.COMPOSITE.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS)) return InteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)) {
            level.setBlock(pos, original, Block.UPDATE_ALL);
            return InteractionResult.FAIL;
        }
        composite.addPart(original, LocalTransform.IDENTITY, 0);
        composite.addPart(((BlockItem) item).getBlock().defaultBlockState(), LocalTransform.IDENTITY, 0);
        if (doorTop != null) level.setBlock(pos.above(), doorTop, Block.UPDATE_CLIENTS);
        if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
        var sound = ((BlockItem) item).getBlock().defaultBlockState().getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean canLavaLog(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.hasProperty(LavaLogging.LAVA_LOGGED)
                && !state.getValue(BlockStateProperties.WATERLOGGED)
                && !state.getValue(LavaLogging.LAVA_LOGGED);
    }

    private static boolean isLavaLogged(BlockState state) {
        return state.hasProperty(LavaLogging.LAVA_LOGGED) && state.getValue(LavaLogging.LAVA_LOGGED);
    }

    /** Handles a bucket against the exact water/lava-loggable part hit inside a composite. */
    private static InteractionResult useFluidBucketOnCompositePart(net.minecraft.world.entity.player.Player player,
                                                                    net.minecraft.world.level.Level level,
                                                                    net.minecraft.world.InteractionHand hand,
                                                                    net.minecraft.core.BlockPos pos,
                                                                    ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity composite)
                || !(held.is(Items.WATER_BUCKET) || held.is(Items.LAVA_BUCKET) || held.is(Items.BUCKET))) return null;
        var hit = PartRaycaster.raycast(composite, level, player, 6).flatMap(result -> composite.parts().find(result.partId()));
        if (hit.isEmpty()) return null;
        var part = hit.get();
        var state = part.state();
        if (!state.hasProperty(BlockStateProperties.WATERLOGGED) || !state.hasProperty(LavaLogging.LAVA_LOGGED)) return null;

        if (held.is(Items.WATER_BUCKET) && !state.getValue(BlockStateProperties.WATERLOGGED)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            composite.replacePart(part.id(), state.setValue(BlockStateProperties.WATERLOGGED, true)
                    .setValue(LavaLogging.LAVA_LOGGED, false));
            level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER,
                    net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
            if (!player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1, 1);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.is(Items.LAVA_BUCKET) && !state.getValue(BlockStateProperties.WATERLOGGED)
                && !state.getValue(LavaLogging.LAVA_LOGGED)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            composite.replacePart(part.id(), state.setValue(LavaLogging.LAVA_LOGGED, true));
            level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.LAVA,
                    net.minecraft.world.level.material.Fluids.LAVA.getTickDelay(level));
            consumeLavaBucket(player, hand);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1, 1);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.is(Items.BUCKET) && state.getValue(LavaLogging.LAVA_LOGGED)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            composite.replacePart(part.id(), state.setValue(LavaLogging.LAVA_LOGGED, false));
            if (!player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.LAVA_BUCKET));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1, 1);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.is(Items.BUCKET) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            composite.replacePart(part.id(), state.setValue(BlockStateProperties.WATERLOGGED, false));
            if (!player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.WATER_BUCKET));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1, 1);
            return InteractionResult.SUCCESS_SERVER;
        }
        return null;
    }

    private static void consumeLavaBucket(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (!player.isCreative()) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
    }

    private record CoverTarget(net.minecraft.core.BlockPos pos, BlockState state) {}
}
