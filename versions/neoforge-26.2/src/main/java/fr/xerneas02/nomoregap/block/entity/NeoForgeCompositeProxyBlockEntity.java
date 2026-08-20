package fr.xerneas02.nomoregap.block.entity;

import fr.xerneas02.nomoregap.render.CompositeChunkModel;
import fr.xerneas02.nomoregap.renderdata.CompositeRenderData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.model.data.ModelData;

public final class NeoForgeCompositeProxyBlockEntity extends CompositeProxyBlockEntity {
    public NeoForgeCompositeProxyBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }

    @Override public ModelData getModelData() {
        Object data = getRenderData();
        return data instanceof CompositeRenderData composite
                ? ModelData.of(CompositeChunkModel.DATA, composite) : ModelData.EMPTY;
    }

    @Override public void onLoad() {
        super.onLoad();
        requestModelDataUpdate();
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        requestModelDataUpdate();
    }

    @Override public void setChanged() {
        super.setChanged();
        requestModelDataUpdate();
    }
}
