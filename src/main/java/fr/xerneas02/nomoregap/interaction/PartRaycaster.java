package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Optional;

public final class PartRaycaster {
    private PartRaycaster() {}

    public static Optional<PartHitResult> raycast(CompositeBlockEntity composite, BlockGetter world, Player player, double reach) {
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1).scale(reach));
        PartHitResult closest = null;
        for (var part : composite.parts().view()) {
            var shape = ShapeTransformer.transform(
                    part.state().getShape(world, composite.getBlockPos(), CollisionContext.of(player)), part.transform());
            for (var box : shape.toAabbs()) {
                var hit = box.move(composite.getBlockPos()).clip(start, end);
                if (hit.isEmpty()) continue;
                double distance = start.distanceToSqr(hit.get());
                if (closest == null || distance < closest.distanceSquared()) {
                    closest = new PartHitResult(part.id(), hit.get(), distance);
                }
            }
        }
        return Optional.ofNullable(closest);
    }
}
