package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
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
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class CompositePlacementHandler {
    private CompositePlacementHandler() {}

    public static void initialize() {
        UseBlockCallback.EVENT.register(CompositePlacementHandler::useBlock);
    }

    private static InteractionResult useBlock(net.minecraft.world.entity.player.Player player,
                                              net.minecraft.world.level.Level level,
                                              net.minecraft.world.InteractionHand hand,
                                              net.minecraft.world.phys.BlockHitResult hit) {
            var held = player.getItemInHand(hand);
            var effectiveHit = hit;
            if (level.getBlockEntity(hit.getBlockPos()) instanceof CompositeProxyBlockEntity proxy
                    && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity) {
                effectiveHit = new net.minecraft.world.phys.BlockHitResult(
                        hit.getLocation(), hit.getDirection(), proxy.anchor(), hit.isInside());
            }
            var originalPos = effectiveHit.getBlockPos();
            var original = level.getBlockState(originalPos);
            if (held.getItem() instanceof BlockItem blockItem) {
                var placement = new BlockPlaceContext(player, hand, held, effectiveHit);
                var placementPos = placement.getClickedPos();
                if (level.getFluidState(placementPos).isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA)) {
                    var placed = blockItem.getBlock().getStateForPlacement(placement);
                    if (placed != null && canLavaLog(placed)) {
                        if (level.isClientSide()) return InteractionResult.SUCCESS;
                        level.setBlock(placementPos, placed.setValue(LavaLogging.LAVA_LOGGED, true), Block.UPDATE_ALL);
                        if (!player.isCreative()) held.shrink(1);
                        var sound = placed.getSoundType();
                        level.playSound(null, placementPos, sound.getPlaceSound(), SoundSource.BLOCKS,
                                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
                        fr.xerneas02.nomoregap.advancement.ModAdvancements.grant(player, "hot_bath");
                        return InteractionResult.SUCCESS_SERVER;
                    }
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
            var coverTarget = footCoverTarget(level, effectiveHit, originalPos, original, held.getItem(), player);
            if (coverTarget != null) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                return addFootCover(player, level, hand, coverTarget.pos(), coverTarget.state(), held.getItem());
            }
            return tryPlaceInComposite(player, level, hand, effectiveHit, originalPos, original, held);
    }

    private static InteractionResult tryPlaceInComposite(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.level.Level level,
                                                          net.minecraft.world.InteractionHand hand,
                                                          net.minecraft.world.phys.BlockHitResult hit,
                                                          net.minecraft.core.BlockPos originalPos,
                                                          BlockState original,
                                                          ItemStack held) {
            var existingComposite = level.getBlockEntity(originalPos) instanceof CompositeBlockEntity existing ? existing : null;
            boolean footComposite = existingComposite != null && existingComposite.parts().view().stream().anyMatch(part ->
                    part.state().getBlock() instanceof SnowLayerBlock || part.state().getBlock() instanceof CarpetBlock
                            || part.state().getBlock() instanceof MossyCarpetBlock);
            Block heldBlock = held.getItem() instanceof BlockItem heldBlockItem ? heldBlockItem.getBlock() : null;
            boolean verticalBuildingBlock = heldBlock instanceof net.minecraft.world.level.block.FenceBlock
                    || heldBlock instanceof net.minecraft.world.level.block.WallBlock;
            boolean ceilingPlacement = player.isSecondaryUseActive() && hit.getDirection() == Direction.DOWN
                    && (heldBlock instanceof net.minecraft.world.level.block.LanternBlock || verticalBuildingBlock);
            var overhead = level.getBlockState(originalPos.above());
            boolean underSlabPlacement = player.isSecondaryUseActive()
                    && hit.getDirection() == Direction.UP
                    && verticalBuildingBlock
                    && overhead.getBlock() instanceof SlabBlock
                    && overhead.getValue(SlabBlock.TYPE)
                    == net.minecraft.world.level.block.state.properties.SlabType.TOP;
            if ((!player.isSecondaryUseActive() && !footComposite) || hit.getDirection() != Direction.UP
                    && !ceilingPlacement
                    || (!footComposite && !matches(original, held.getItem()))
                    || original.getBlock() == ModBlocks.COMPOSITE_PROXY) {
                return InteractionResult.PASS;
            }
            if (!(held.getItem() instanceof BlockItem blockItem)) return InteractionResult.PASS;
            var block = blockItem.getBlock();
            if (block instanceof EntityBlock || isUnsupportedRedstone(block)) return InteractionResult.PASS;
            if (!underSlabPlacement
                    && Block.isShapeFullBlock(original.getCollisionShape(level, originalPos, CollisionContext.of(player)))) {
                return InteractionResult.PASS;
            }
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
            if (block instanceof net.minecraft.world.level.block.LanternBlock
                    && placed.hasProperty(BlockStateProperties.HANGING)) {
                placed = placed.setValue(BlockStateProperties.HANGING, ceilingPlacement);
            }
            double localX = hit.getLocation().x - originalPos.getX();
            double localZ = hit.getLocation().z - originalPos.getZ();
            var context = CollisionContext.of(player);
            var occupied = existingComposite != null
                    ? existingComposite.geometry(level, context).selection()
                    : original.getShape(level, originalPos, context);
            var surface = ceilingPlacement ? SurfaceExtractor.bottomAt(occupied, localX, localZ)
                    : SurfaceExtractor.topAt(occupied, localX, localZ);
            if (surface.isEmpty()) return InteractionResult.PASS;
            int transformYUnits = surface.get().y().units()
                    - (ceilingPlacement ? FixedPoint.FULL_BLOCK.units() : 0);
            if (transformYUnits < FixedPoint.MIN_UNITS || transformYUnits > FixedPoint.MAX_UNITS) {
                return InteractionResult.FAIL;
            }
            var transform = new LocalTransform(FixedPoint.ZERO,
                    new FixedPoint(transformYUnits),
                    FixedPoint.ZERO, 0);
            var placedShape = placed.getShape(level, originalPos, context);
            if (OverlapTester.overlaps(occupied, placedShape, transform)) {
                return InteractionResult.PASS;
            }
            int addedParts = 1 + (existingComposite == null ? 1 : 0)
                    + (placed.getBlock() instanceof net.minecraft.world.level.block.DoorBlock ? 1 : 0);
            int maxParts = level instanceof net.minecraft.server.level.ServerLevel server
                    ? server.getGameRules().get(fr.xerneas02.nomoregap.rule.CompositeRules.MAX_PARTS)
                    : fr.xerneas02.nomoregap.util.NoMoreGapLimits.MAX_PARTS_PER_CELL;
            if ((existingComposite == null ? 0 : existingComposite.parts().size()) + addedParts > maxParts) {
                return InteractionResult.FAIL;
            }
            if (level.isClientSide()) return InteractionResult.SUCCESS;

            CompositeBlockEntity composite = existingComposite;
            if (composite == null) {
                if (!level.setBlock(originalPos, ModBlocks.COMPOSITE.defaultBlockState(), Block.UPDATE_ALL)) {
                    return InteractionResult.FAIL;
                }
                if (!(level.getBlockEntity(originalPos) instanceof CompositeBlockEntity created)) {
                    level.setBlock(originalPos, original, Block.UPDATE_ALL);
                    return InteractionResult.FAIL;
                }
                composite = created;
            }
            composite.beginUpdate();
            try {
                if (existingComposite == null) composite.addPart(original, LocalTransform.IDENTITY, 0);
                composite.addPart(placed, transform, 0);
                if (placed.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && placed.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    var top = placed.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                    composite.addPart(top, new LocalTransform(transform.x(), transform.y().add(FixedPoint.FULL_BLOCK),
                            transform.z(), transform.quarterTurns()), 0);
                }
            } finally {
                composite.endUpdate();
            }
            level.updateNeighborsAt(originalPos, ModBlocks.COMPOSITE);
            CompositePartUpdater.refreshAround(level, originalPos);
            if (!player.isCreative()) held.shrink(1);
            var sound = placed.getSoundType();
            level.playSound(null, originalPos.getX() + 0.5 + transform.xDouble(),
                    originalPos.getY() + 0.5 + transform.yDouble(), originalPos.getZ() + 0.5 + transform.zDouble(),
                    sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
            if (underSlabPlacement) {
                fr.xerneas02.nomoregap.advancement.ModAdvancements.grant(player, "low_profile");
            }
            fr.xerneas02.nomoregap.advancement.ModAdvancements.checkComposite(player, composite);
            return InteractionResult.SUCCESS_SERVER;
    }

    static boolean matches(BlockState state, Item item) {
        return item instanceof BlockItem blockItem
                && !(blockItem.getBlock() instanceof CompositeBlock)
                && !state.isAir()
                && state.getFluidState().isEmpty();
    }

    private static boolean isUnsupportedRedstone(Block block) {
        return block instanceof net.minecraft.world.level.block.RedStoneWireBlock
                || block instanceof net.minecraft.world.level.block.RepeaterBlock
                || block instanceof net.minecraft.world.level.block.ComparatorBlock;
    }

    private static CoverTarget footCoverTarget(net.minecraft.world.level.Level level, net.minecraft.world.phys.BlockHitResult hit,
                                               net.minecraft.core.BlockPos hitPos, BlockState hitState, Item item,
                                               net.minecraft.world.entity.player.Player player) {
        if (!(item instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof SnowLayerBlock || blockItem.getBlock() instanceof CarpetBlock
                || blockItem.getBlock() instanceof MossyCarpetBlock)) return null;
        if (hitState.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                && hitState.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            var below = hitPos.below();
            var belowState = level.getBlockState(below);
            return belowState.getBlock() == hitState.getBlock()
                    && belowState.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    && canFootCover(belowState, level, below, player) ? new CoverTarget(below, belowState) : null;
        }
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
        if (state.getBlock() instanceof SnowLayerBlock || state.getBlock() instanceof CarpetBlock || state.getBlock() instanceof MossyCarpetBlock
                || state.isAir() || !state.getFluidState().isEmpty()
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
        if (doorTop != null) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        composite.beginUpdate();
        try {
            composite.addPart(original, LocalTransform.IDENTITY, 0);
            composite.addPart(((BlockItem) item).getBlock().defaultBlockState(), LocalTransform.IDENTITY, 0);
            if (doorTop != null) composite.addPart(doorTop,
                    new LocalTransform(FixedPoint.ZERO, FixedPoint.FULL_BLOCK, FixedPoint.ZERO, 0), 0);
        } finally {
            composite.endUpdate();
        }
        level.updateNeighborsAt(pos, ModBlocks.COMPOSITE);
        CompositePartUpdater.refreshAround(level, pos);
        if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
        var sound = ((BlockItem) item).getBlock().defaultBlockState().getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        fr.xerneas02.nomoregap.advancement.ModAdvancements.grant(player, "at_the_blocks_feet");
        fr.xerneas02.nomoregap.advancement.ModAdvancements.checkComposite(player, composite);
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
