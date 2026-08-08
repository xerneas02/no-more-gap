package fr.xerneas02.nomoregap.part;

import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.util.NoMoreGapLimits;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PartContainer {
    private final List<PartInstance> parts = new ArrayList<>();
    private int nextId;

    public PartInstance add(BlockState state, LocalTransform transform, int flags) {
        if (parts.size() >= NoMoreGapLimits.MAX_PARTS_PER_CELL) throw new IllegalStateException("Composite is full");
        var part = new PartInstance(nextId++, state, transform, flags);
        parts.add(part);
        return part;
    }

    public boolean addLoaded(PartInstance part) {
        if (parts.size() >= NoMoreGapLimits.MAX_PARTS_PER_CELL || find(part.id()).isPresent()) return false;
        parts.add(part);
        nextId = Math.max(nextId, part.id() + 1);
        return true;
    }

    public Optional<PartInstance> find(int id) { return parts.stream().filter(part -> part.id() == id).findFirst(); }
    public boolean remove(int id) { return parts.removeIf(part -> part.id() == id); }
    public boolean replace(int id, BlockState state) {
        for (int i = 0; i < parts.size(); i++) {
            var part = parts.get(i);
            if (part.id() == id) {
                parts.set(i, new PartInstance(id, state, part.transform(), part.flags()));
                return true;
            }
        }
        return false;
    }
    public void clear() { parts.clear(); }
    public List<PartInstance> view() { return Collections.unmodifiableList(parts); }
    public boolean isEmpty() { return parts.isEmpty(); }
    public int size() { return parts.size(); }
}
