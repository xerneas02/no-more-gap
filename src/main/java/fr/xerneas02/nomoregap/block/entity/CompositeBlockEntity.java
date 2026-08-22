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

public class CompositeBlockEntity extends BlockEntity {
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
    /** Transient piston edge state; persisted extension state remains the source of truth after reload. */
    private final java.util.Map<Integer, Boolean> pistonPower = new HashMap<>();
    /** Server-side link between a piston-head part and the piston part that created it. */
    private final java.util.Map<Integer, Integer> pistonHeadOwners = new HashMap<>();

    public CompositeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COMPOSITE, pos, state); }

    public PartContainer parts() { return parts; }
    public long revision() { return revision; }
    public boolean isGeometryDirty() { return geometryDirty; }

    public Object getRenderData() {
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

    public boolean pistonPowerChanged(int id, boolean powered, boolean initialPower) {
        var previous = pistonPower.put(id, powered);
        return previous == null ? powered != initialPower : powered != previous;
    }

    public void retainPistonPower(Set<Integer> ids) {
        pistonPower.keySet().retainAll(ids);
    }

    public void setPistonHeadOwner(int headId, int pistonId) {
        pistonHeadOwners.put(headId, pistonId);
        setChanged();
    }

    public boolean isPistonHeadOwnedBy(int headId, int pistonId) {
        return pistonHeadOwner(headId) == pistonId;
    }

    public boolean hasPistonHeadOwner(int headId) {
        return pistonHeadOwner(headId) >= 0;
    }

    public int pistonHeadOwner(int headId) {
        var explicit = pistonHeadOwners.get(headId);
        if (explicit != null) return explicit;
        var head = parts.find(headId).orElse(null);
        if (head == null || (head.flags() & fr.xerneas02.nomoregap.part.PartFlags.PISTON_HEAD) == 0) return -1;
        for (var piston : parts.view()) {
            if (!(piston.state().getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock)) continue;
            var facing = piston.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            if (head.transform().x().units() == piston.transform().x().units() + facing.getStepX() * fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK
                    && head.transform().y().units() == piston.transform().y().units() + facing.getStepY() * fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK
                    && head.transform().z().units() == piston.transform().z().units() + facing.getStepZ() * fr.xerneas02.nomoregap.util.NoMoreGapLimits.FIXED_UNITS_PER_BLOCK) {
                return piston.id();
            }
        }
        return -1;
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
        pistonPower.remove(id);
        pistonHeadOwners.remove(id);
        changed();
        return true;
    }

    public boolean replacePart(int id, BlockState state) {
        if (!parts.replace(id, state)) return false;
        changed();
        return true;
    }

    /** Replaces the transform of one part, used by piston pushes that keep the part in the same anchor. */
    public boolean replaceTransform(int id, fr.xerneas02.nomoregap.geometry.LocalTransform transform) {
        var current = parts.find(id).orElse(null);
        if (current == null) return false;
        var replaced = new PartInstance(id, current.state(), transform, current.flags());
        var list = new java.util.ArrayList<PartInstance>(parts.view());
        list.replaceAll(part -> part.id() == id ? replaced : part);
        parts.replaceAll(list);
        changed();
        return true;
    }

    public void clearParts() {
        if (parts.isEmpty()) return;
        parts.clear();
        pistonHeadOwners.clear();
        changed();
    }

    /** Atomically replaces the part list while preserving ids and geometry. */
    public void replaceParts(java.util.List<PartInstance> replacement) {
        parts.replaceAll(replacement);
        pistonHeadOwners.keySet().removeIf(id -> replacement.stream().noneMatch(part -> part.id() == id));
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
                // Any part mutation may change piston power; re-check the
                // trigger so a piston part fires even if no neighbour update
                // arrives. The trigger's own firing flag prevents re-entrancy.
                fr.xerneas02.nomoregap.piston.CompositePistonTrigger.tick(serverLevel, worldPosition, this);
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
        var required = occupiedCells(geometry(level, CollisionContext.empty()).occupancy(), false);
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
        for (var pos : occupiedCells(geometry(level, CollisionContext.empty()).occupancy(), false)) {
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy && proxy.anchor().equals(worldPosition)) {
                proxyPositions.add(pos.immutable());
            }
        }
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("revision", revision);
        comparatorOutputs.forEach((id, value) -> output.putInt("comparator." + id, value));
        pistonHeadOwners.forEach((headId, pistonId) -> output.putInt("pistonHeadOwner." + headId, pistonId));
        var list = output.list("parts", PartInstance.CODEC);
        parts.view().forEach(list::add);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        parts.clear();
        comparatorOutputs.clear();
        pistonHeadOwners.clear();
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
            int pistonOwner = input.getIntOr("pistonHeadOwner." + part.id(), -1);
            if (pistonOwner >= 0) pistonHeadOwners.put(part.id(), pistonOwner);
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
        var positions = occupiedCells(geometry(level, CollisionContext.empty()).selection(), true);
        for (var pos : positions) {
            var state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private Set<BlockPos> occupiedCells(VoxelShape shape, boolean includeAnchor) {
        var positions = new HashSet<BlockPos>();
        for (var box : shape.toAabbs()) {
            int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
            int maxX = (int) Math.floor(box.maxX - 1.0e-7), maxY = (int) Math.floor(box.maxY - 1.0e-7), maxZ = (int) Math.floor(box.maxZ - 1.0e-7);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                if (includeAnchor || x != 0 || y != 0 || z != 0) positions.add(worldPosition.offset(x, y, z).immutable());
            }
        }
        if (includeAnchor) positions.add(worldPosition);
        return positions;
    }

    private static boolean isInternalPart(BlockState state) {
        return state.getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE
                || state.getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
}
