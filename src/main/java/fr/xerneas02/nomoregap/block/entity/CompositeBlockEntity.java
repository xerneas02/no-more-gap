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

public final class CompositeBlockEntity extends BlockEntity {
    private final PartContainer parts = new PartContainer();
    private final CompositeGeometryCache geometry = new CompositeGeometryCache();
    private long revision;
    private boolean geometryDirty = true;

    public CompositeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COMPOSITE, pos, state); }

    public PartContainer parts() { return parts; }
    public long revision() { return revision; }
    public boolean isGeometryDirty() { return geometryDirty; }
    public void geometryRebuilt() { geometryDirty = false; }

    public CompositeGeometryCache geometry(BlockGetter world, CollisionContext context) {
        if (!geometryDirty && geometry.isValid()) return geometry;
        VoxelShape collision = Shapes.empty(), selection = Shapes.empty(), occlusion = Shapes.empty();
        for (var part : parts.view()) {
            if (part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE) continue;
            collision = Shapes.or(collision, ShapeTransformer.transform(part.state().getCollisionShape(world, worldPosition, context), part.transform()));
            selection = Shapes.or(selection, ShapeTransformer.transform(part.state().getShape(world, worldPosition, context), part.transform()));
            occlusion = Shapes.or(occlusion, ShapeTransformer.transform(part.state().getOcclusionShape(), part.transform()));
        }
        geometry.update(collision, selection, occlusion, java.util.List.of());
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

    public void clearParts() {
        if (parts.isEmpty()) return;
        parts.clear();
        changed();
    }

    private void changed() {
        revision++;
        geometryDirty = true;
        geometry.invalidate();
        setChanged();
        if (level != null) {
            boolean lit = parts.view().stream().anyMatch(part -> part.state().getLightEmission() > 0);
            BlockState current = getBlockState();
            if (current.getValue(fr.xerneas02.nomoregap.block.CompositeBlock.LIT) != lit) {
                level.setBlock(worldPosition, current.setValue(fr.xerneas02.nomoregap.block.CompositeBlock.LIT, lit), Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_CLIENTS);
            }
            if (!level.isClientSide()) refreshProxies();
        }
    }

    public void clearProxies() {
        if (level == null || level.isClientSide()) return;
        for (int x = -1; x <= 2; x++) for (int y = -1; y <= 2; y++) for (int z = -1; z <= 2; z++) {
            if (x == 0 && y == 0 && z == 0) continue;
            var pos = worldPosition.offset(x, y, z);
            if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy && proxy.anchor().equals(worldPosition)) {
                level.removeBlock(pos, false);
            }
        }
    }

    private void refreshProxies() {
        clearProxies();
        var collision = geometry(level, CollisionContext.empty());
        for (var box : collision.collision().toAabbs()) {
            int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
            int maxX = (int) Math.floor(box.maxX - 1.0e-7), maxY = (int) Math.floor(box.maxY - 1.0e-7), maxZ = (int) Math.floor(box.maxZ - 1.0e-7);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                var pos = worldPosition.offset(x, y, z);
                if (!level.getBlockState(pos).isAir()) continue;
                level.setBlock(pos, fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY.defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof CompositeProxyBlockEntity proxy) {
                    proxy.setAnchor(worldPosition);
                    level.sendBlockUpdated(pos, proxy.getBlockState(), proxy.getBlockState(), Block.UPDATE_CLIENTS);
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
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
}
