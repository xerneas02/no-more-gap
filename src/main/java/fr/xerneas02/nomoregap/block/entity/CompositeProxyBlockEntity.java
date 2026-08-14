package fr.xerneas02.nomoregap.block.entity;

import fr.xerneas02.nomoregap.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class CompositeProxyBlockEntity extends BlockEntity {
    private BlockPos anchor = BlockPos.ZERO;

    public CompositeProxyBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.COMPOSITE_PROXY, pos, state); }
    public BlockPos anchor() { return anchor; }
    public void setAnchor(BlockPos anchor) { this.anchor = anchor.immutable(); setChanged(); }

    @Override public Object getRenderData() {
        if (level != null && level.getBlockEntity(anchor) instanceof CompositeBlockEntity composite) {
            return new fr.xerneas02.nomoregap.renderdata.CompositeRenderData(composite.revision(),
                    composite.parts().view(), worldPosition.subtract(anchor));
        }
        return null;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("anchor_x", anchor.getX()); output.putInt("anchor_y", anchor.getY()); output.putInt("anchor_z", anchor.getZ());
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        anchor = new BlockPos(input.getIntOr("anchor_x", 0), input.getIntOr("anchor_y", 0), input.getIntOr("anchor_z", 0));
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }
    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
}
