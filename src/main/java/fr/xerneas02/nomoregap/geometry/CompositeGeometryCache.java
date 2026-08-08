package fr.xerneas02.nomoregap.geometry;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class CompositeGeometryCache {
    private VoxelShape collision = Shapes.empty();
    private VoxelShape selection = Shapes.empty();
    private VoxelShape occlusion = Shapes.empty();
    private List<SupportSurface> supports = List.of();
    private boolean valid;

    public void update(VoxelShape collision, VoxelShape selection, VoxelShape occlusion, List<SupportSurface> supports) {
        this.collision = collision;
        this.selection = selection;
        this.occlusion = occlusion;
        this.supports = List.copyOf(supports);
        valid = true;
    }

    public void invalidate() { valid = false; }
    public boolean isValid() { return valid; }
    public VoxelShape collision() { return collision; }
    public VoxelShape selection() { return selection; }
    public VoxelShape occlusion() { return occlusion; }
    public List<SupportSurface> supports() { return supports; }
}
