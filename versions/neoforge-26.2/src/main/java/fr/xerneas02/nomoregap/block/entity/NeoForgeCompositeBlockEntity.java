package fr.xerneas02.nomoregap.block.entity;

import fr.xerneas02.nomoregap.render.CompositeChunkModel;
import fr.xerneas02.nomoregap.renderdata.CompositeRenderData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;

public final class NeoForgeCompositeBlockEntity extends CompositeBlockEntity {
    public NeoForgeCompositeBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }

    @Override public ModelData getModelData() {
        return ModelData.of(CompositeChunkModel.DATA, (CompositeRenderData) getRenderData());
    }

    @Override public void setChanged() {
        super.setChanged();
        requestModelDataUpdate();
    }
}
