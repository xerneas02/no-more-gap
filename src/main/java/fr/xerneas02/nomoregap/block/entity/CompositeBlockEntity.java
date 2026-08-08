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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public final class CompositeBlockEntity extends BlockEntity {
    private final PartContainer parts = new PartContainer();
    private final CompositeGeometryCache geometry = new CompositeGeometryCache();
    private long revision;
    private boolean geometryDirty = true;
    private final Set<BlockPos> proxyPositions = new HashSet<>();
    private int updateDepth;
    private boolean updatePending;

    public CompositeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COMPOSITE, pos, state); }

    public PartContainer parts() { return parts; }
    public long revision() { return revision; }
    public boolean isGeometryDirty() { return geometryDirty; }

    public CompositeGeometryCache geometry(BlockGetter world, CollisionContext context) {
        if (!geometryDirty && geometry.isValid()) return geometry;
        VoxelShape collision = Shapes.empty(), selection = Shapes.empty(), occlusion = Shapes.empty();
        for (var part : parts.view()) {
            if (part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE) continue;
            collision = Shapes.or(collision, ShapeTransformer.transform(part.state().getCollisionShape(world, worldPosition, context), part.transform()));
            selection = Shapes.or(selection, ShapeTransformer.transform(part.state().getShape(world, worldPosition, context), part.transform()));
            occlusion = Shapes.or(occlusion, ShapeTransformer.transform(part.state().getOcclusionShape(), part.transform()));
        }
        geometry.update(collision, selection, occlusion);
        geometryDirty = false;
        return geometry;
    }

    public PartInstance addPart(BlockState state, fr.xerneas02.nomoregap.geometry.LocalTransform transform, int flags) {
        var part = parts.add(state, transform, flags);
        changed();
        return part;
    }

    public boolean removePart(int id) {
        if (!parts.remove(id)) return false;
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
            if (!level.isClientSide()) refreshProxies();
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                fr.xerneas02.nomoregap.lava.LavaLoggingReactions.tryReact(serverLevel, worldPosition);
            }
        }
    }

    public void clearProxies() {
        if (level == null || level.isClientSide()) return;
        if (proxyPositions.isEmpty()) findLoadedProxies();
        for (var pos : Set.copyOf(proxyPositions)) {
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy && proxy.anchor().equals(worldPosition)) {
                level.removeBlock(pos, false);
            }
        }
        proxyPositions.clear();
    }

    /** Rebuilds every occupied neighbouring cell, including parts stacked above the anchor. */
    public void refreshProxies() {
        if (level == null || level.isClientSide()) return;
        clearProxies();
        var shape = geometry(level, CollisionContext.empty()).selection();
        for (var box : shape.toAabbs()) {
            int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
            int maxX = (int) Math.floor(box.maxX - 1.0e-7), maxY = (int) Math.floor(box.maxY - 1.0e-7), maxZ = (int) Math.floor(box.maxZ - 1.0e-7);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                var pos = worldPosition.offset(x, y, z);
                if (!level.getBlockState(pos).isAir()) continue;
                level.setBlock(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY.defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy) {
                    proxy.setAnchor(worldPosition);
                    proxyPositions.add(pos.immutable());
                    level.sendBlockUpdated(pos, proxy.getBlockState(), proxy.getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private void findLoadedProxies() {
        if (level == null) return;
        for (var box : geometry(level, CollisionContext.empty()).selection().toAabbs()) {
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
        var list = output.list("parts", PartInstance.CODEC);
        parts.view().forEach(list::add);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        parts.clear();
        revision = Math.max(0, input.getLongOr("revision", 0));
        for (var part : input.listOrEmpty("parts", PartInstance.CODEC)) {
            if (!parts.addLoaded(part)) {
                NoMoreGap.LOGGER.warn("Ignored excess or duplicate part {} at {}", part.id(), worldPosition);
                break;
            }
        }
        geometryDirty = true;
        geometry.invalidate();
        if (level != null) changed();
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
}
