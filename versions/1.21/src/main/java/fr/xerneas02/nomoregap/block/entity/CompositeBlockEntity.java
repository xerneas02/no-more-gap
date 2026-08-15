package fr.xerneas02.nomoregap.block.entity;

import fr.xerneas02.nomoregap.NoMoreGap;
import fr.xerneas02.nomoregap.part.PartContainer;
import fr.xerneas02.nomoregap.part.PartInstance;
import fr.xerneas02.nomoregap.geometry.CompositeGeometryCache;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Set;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;

public final class CompositeBlockEntity extends BlockEntity {
    private final PartContainer parts = new PartContainer();
    private final CompositeGeometryCache geometry = new CompositeGeometryCache();
    private long revision;
    private boolean geometryDirty = true;
    private final Set<BlockPos> proxyPositions = new HashSet<>();
    private final Set<BlockPos> snowyGroundPositions = new HashSet<>();
    private int updateDepth;
    private boolean updatePending;
    private final java.util.Map<Long, Set<Integer>> scheduledParts = new HashMap<>();
    private final java.util.Map<Integer, Integer> comparatorOutputs = new HashMap<>();

    public CompositeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COMPOSITE, pos, state); }

    public PartContainer parts() { return parts; }
    public long revision() { return revision; }
    public boolean isGeometryDirty() { return geometryDirty; }

    @Override public Object getRenderData() {
        return new fr.xerneas02.nomoregap.renderdata.CompositeRenderData(revision, parts.view(), BlockPos.ZERO);
    }

    public void schedulePart(int id, long gameTime) {
        scheduledParts.computeIfAbsent(gameTime, ignored -> new HashSet<>()).add(id);
    }

    public Set<Integer> takeScheduledParts(long gameTime) {
        var due = new HashSet<Integer>();
        for (var time : new HashSet<>(scheduledParts.keySet())) {
            if (time <= gameTime) due.addAll(scheduledParts.remove(time));
        }
        return due;
    }

    public int comparatorOutput(int id) { return comparatorOutputs.getOrDefault(id, 0); }
    public void setComparatorOutput(int id, int output) {
        if (comparatorOutputs.getOrDefault(id, 0) == output) return;
        comparatorOutputs.put(id, output);
        changed();
    }

    public CompositeGeometryCache geometry(BlockGetter world, CollisionContext context) {
        if (!geometryDirty && geometry.isValid()) return geometry;
        VoxelShape collision = Shapes.empty(), selection = Shapes.empty(), occlusion = Shapes.empty();
        for (var part : parts.view()) {
            if (isInternalPart(part.state())) continue;
            collision = Shapes.or(collision, ShapeTransformer.transform(part.state().getCollisionShape(world, worldPosition, context), part.transform()));
            selection = Shapes.or(selection, ShapeTransformer.transform(part.state().getShape(world, worldPosition, context), part.transform()));
            occlusion = Shapes.or(occlusion, ShapeTransformer.transform(part.state().getOcclusionShape(), part.transform()));
        }
        geometry.update(collision, selection, occlusion);
        geometryDirty = false;
        return geometry;
    }

    public PartInstance addPart(BlockState state, fr.xerneas02.nomoregap.geometry.LocalTransform transform, int flags) {
        if (isInternalPart(state)) throw new IllegalArgumentException("Composite internals cannot be stored as parts");
        var part = parts.add(state, transform, flags);
        changed();
        return part;
    }

    public boolean removePart(int id) {
        if (!parts.remove(id)) return false;
        comparatorOutputs.remove(id);
        changed();
        return true;
    }

    public boolean replacePart(int id, BlockState state) {
        if (!parts.replace(id, state)) return false;
        changed();
        return true;
    }

    public void clearParts() {
        if (parts.isEmpty()) return;
        parts.clear();
        changed();
    }

    public void replaceParts(List<PartInstance> replacement) {
        parts.replaceAll(replacement);
        changed();
    }

    public void beginUpdate() { updateDepth++; }

    public void endUpdate() {
        if (updateDepth == 0) throw new IllegalStateException("No composite update is active");
        if (--updateDepth == 0 && updatePending) {
            updatePending = false;
            changedNow();
        }
    }

    private void changed() {
        if (updateDepth > 0) {
            updatePending = true;
            return;
        }
        changedNow();
    }

    private void changedNow() {
        revision++;
        geometryDirty = true;
        geometry.invalidate();
        setChanged();
        if (level != null) {
            boolean lava = parts.view().stream().anyMatch(part -> part.state().hasProperty(fr.xerneas02.nomoregap.lava.LavaLogging.LAVA_LOGGED)
                    && part.state().getValue(fr.xerneas02.nomoregap.lava.LavaLogging.LAVA_LOGGED));
            boolean water = !lava && parts.view().stream().anyMatch(part -> part.state().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                    && part.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED));
            boolean lit = lava || parts.view().stream().anyMatch(part -> part.state().getLightEmission() > 0);
            BlockState current = getBlockState();
            var updated = current.setValue(fr.xerneas02.nomoregap.block.CompositeBlock.LIT, lit)
                    .setValue(fr.xerneas02.nomoregap.block.CompositeBlock.WATER, water)
                    .setValue(fr.xerneas02.nomoregap.block.CompositeBlock.LAVA, lava);
            if (current != updated) {
                level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_CLIENTS);
            }
            if (!level.isClientSide()) {
                refreshProxies();
                refreshSnowyGround();
            }
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                fr.xerneas02.nomoregap.lava.LavaLoggingReactions.tryReact(serverLevel, worldPosition);
            }
        }
    }

    private void refreshSnowyGround() {
        if (level == null || level.isClientSide()) return;
        int unit = fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK;
        var required = new HashSet<BlockPos>();
        for (var part : parts.view()) {
            if (!(part.state().getBlock() instanceof SnowLayerBlock)
                    || Math.floorMod(part.transform().y().units(), unit) != 0) continue;
            required.add(worldPosition.offset(
                    Math.floorDiv(part.transform().x().units(), unit),
                    Math.floorDiv(part.transform().y().units(), unit) - 1,
                    Math.floorDiv(part.transform().z().units(), unit)).immutable());
        }
        var affected = new HashSet<>(snowyGroundPositions);
        affected.addAll(required);
        for (var pos : affected) {
            var state = level.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.SNOWY)) continue;
            boolean snowy = required.contains(pos) || level.getBlockState(pos.above()).is(BlockTags.SNOW);
            if (state.getValue(BlockStateProperties.SNOWY) != snowy) {
                level.setBlock(pos, state.setValue(BlockStateProperties.SNOWY, snowy), Block.UPDATE_CLIENTS);
            }
        }
        snowyGroundPositions.clear();
        snowyGroundPositions.addAll(required);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        clearSnowyGround();
        super.preRemoveSideEffects(pos, state);
    }

    /** Vanilla calls this before playerDestroy; keep parts intact until their drops are calculated. */
    private void clearSnowyGround() {
        if (level == null || level.isClientSide()) return;
        for (var pos : snowyGroundPositions) {
            var state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY)
                    && !level.getBlockState(pos.above()).is(BlockTags.SNOW)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.SNOWY, false), Block.UPDATE_CLIENTS);
            }
        }
        snowyGroundPositions.clear();
    }

    public void clearProxies() {
        clearProxiesExcept(null);
    }

    /** Leaves the currently mined proxy to vanilla, which removes it after playerDestroy returns. */
    public void clearProxiesExcept(BlockPos preserved) {
        if (level == null || level.isClientSide()) return;
        if (proxyPositions.isEmpty()) findLoadedProxies();
        for (var pos : Set.copyOf(proxyPositions)) {
            if (pos.equals(preserved)) continue;
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy && proxy.anchor().equals(worldPosition)) {
                level.removeBlock(pos, false);
            }
        }
        proxyPositions.removeIf(pos -> !pos.equals(preserved));
    }

    /** Rebuilds every occupied neighbouring cell, including parts stacked above the anchor. */
    public void refreshProxies() {
        if (level == null || level.isClientSide()) return;
        var required = new HashSet<BlockPos>();
        var shape = geometry(level, CollisionContext.empty()).occupancy();
        for (var box : shape.toAabbs()) {
            int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
            int maxX = (int) Math.floor(box.maxX - 1.0e-7), maxY = (int) Math.floor(box.maxY - 1.0e-7), maxZ = (int) Math.floor(box.maxZ - 1.0e-7);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                required.add(worldPosition.offset(x, y, z).immutable());
            }
        }
        if (proxyPositions.isEmpty()) findLoadedProxies();
        for (var pos : Set.copyOf(proxyPositions)) {
            if (required.contains(pos)) continue;
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy && proxy.anchor().equals(worldPosition)) {
                level.removeBlock(pos, false);
            }
            proxyPositions.remove(pos);
        }
        int created = 0;
        for (var pos : required) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY.defaultBlockState(), Block.UPDATE_ALL);
                created++;
            }
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy) {
                if (!proxy.anchor().equals(worldPosition)) {
                    proxy.setAnchor(worldPosition);
                    level.sendBlockUpdated(pos, proxy.getBlockState(), proxy.getBlockState(), Block.UPDATE_CLIENTS);
                }
                proxyPositions.add(pos);
            }
        }
        for (var pos : proxyPositions) {
            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void findLoadedProxies() {
        if (level == null) return;
        for (var box : geometry(level, CollisionContext.empty()).occupancy().toAabbs()) {
            int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
            int maxX = (int) Math.floor(box.maxX - 1.0e-7), maxY = (int) Math.floor(box.maxY - 1.0e-7), maxZ = (int) Math.floor(box.maxZ - 1.0e-7);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            var pos = worldPosition.offset(x, y, z);
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy && proxy.anchor().equals(worldPosition)) {
                proxyPositions.add(pos.immutable());
            }
            }
        }
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("revision", revision);
        comparatorOutputs.forEach((id, value) -> output.putInt("comparator." + id, value));
        var list = output.list("parts", PartInstance.CODEC);
        parts.view().forEach(list::add);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        parts.clear();
        comparatorOutputs.clear();
        revision = Math.max(0, input.getLongOr("revision", 0));
        for (var part : input.listOrEmpty("parts", PartInstance.CODEC)) {
            if (isInternalPart(part.state())) {
                NoMoreGap.LOGGER.warn("Ignored invalid composite part {} at {}", part.id(), worldPosition);
                continue;
            }
            if (!parts.addLoaded(part)) {
                NoMoreGap.LOGGER.warn("Ignored excess or duplicate part {} at {}", part.id(), worldPosition);
                break;
            }
        }
        for (var part : parts.view()) {
            int output = input.getIntOr("comparator." + part.id(), 0);
            if (output != 0) comparatorOutputs.put(part.id(), output);
        }
        geometryDirty = true;
        geometry.invalidate();
        if (level != null) {
            if (level.isClientSide()) invalidateRenderCells();
            else changed();
        }
    }

    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level.isClientSide()) invalidateRenderCells();
    }

    private void invalidateRenderCells() {
        if (level == null || !level.isClientSide()) return;
        var positions = new HashSet<BlockPos>();
        positions.add(worldPosition);
        for (var box : geometry(level, CollisionContext.empty()).selection().toAabbs()) {
            int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
            int maxX = (int) Math.floor(box.maxX - 1.0e-7), maxY = (int) Math.floor(box.maxY - 1.0e-7), maxZ = (int) Math.floor(box.maxZ - 1.0e-7);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                positions.add(worldPosition.offset(x, y, z));
            }
        }
        for (var pos : positions) {
            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isInternalPart(BlockState state) {
        return state.getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE
                || state.getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
}
