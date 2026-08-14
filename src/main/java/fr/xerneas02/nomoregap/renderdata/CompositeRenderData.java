package fr.xerneas02.nomoregap.renderdata;

import fr.xerneas02.nomoregap.part.PartInstance;
import net.minecraft.core.BlockPos;

import java.util.List;

public record CompositeRenderData(long revision, List<PartInstance> parts, BlockPos cellOffset) {
    public CompositeRenderData {
        parts = List.copyOf(parts);
        cellOffset = cellOffset.immutable();
    }
}
