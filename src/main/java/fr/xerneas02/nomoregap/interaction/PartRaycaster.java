package fr.xerneas02.nomoregap.interaction;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public final class PartRaycaster {
    private PartRaycaster() {}

    public static Optional<PartHitResult> raycast(CompositeBlockEntity composite, BlockGetter world, Player player, double reach) {
        return raycast(composite, world, player, reach, null);
    }

    /** Raycasts only the visible geometry inside one anchor-relative world cell. */
    public static Optional<PartHitResult> raycastInCell(CompositeBlockEntity composite, BlockGetter world, Player player,
                                                         double reach, net.minecraft.core.BlockPos cell) {
        return raycast(composite, world, player, reach, cell);
    }

    public static Optional<VoxelShape> targetedShape(CompositeBlockEntity composite, BlockGetter world, Player player,
                                                      net.minecraft.core.BlockPos cell) {
        return raycast(composite, world, player, 6, cell).flatMap(hit -> composite.parts().find(hit.partId()))
                .map(part -> ShapeTransformer.transform(
                        part.state().getShape(world, composite.getBlockPos(), CollisionContext.of(player)), part.transform()));
    }

    private static Optional<PartHitResult> raycast(CompositeBlockEntity composite, BlockGetter world, Player player,
                                                    double reach, net.minecraft.core.BlockPos cell) {
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1).scale(reach));
        PartHitResult closest = null;
        boolean closestIsCover = false;
        for (var part : composite.parts().view()) {
            if (part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE
                    || part.state().getBlock() == fr.xerneas02.nomoregap.registry.ModBlocks.COMPOSITE_PROXY) continue;
            // Piston head parts are internal; they cannot be targeted or broken.
            if ((part.flags() & fr.xerneas02.nomoregap.part.PartFlags.PISTON_HEAD) != 0) continue;
            var shape = ShapeTransformer.transform(
                    part.state().getShape(world, composite.getBlockPos(), CollisionContext.of(player)), part.transform());
            for (var box : shape.toAabbs()) {
                if (cell != null && !intersectsCell(box, composite.getBlockPos(), cell)) continue;
                var testedBox = cell == null ? box : clipToCell(box, composite.getBlockPos(), cell);
                var hit = testedBox.move(composite.getBlockPos()).clip(start, end);
                if (hit.isEmpty()) continue;
                double distance = start.distanceToSqr(hit.get());
                boolean cover = part.state().getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock
                        || part.state().getBlock() instanceof net.minecraft.world.level.block.CarpetBlock
                        || part.state().getBlock() instanceof net.minecraft.world.level.block.MossyCarpetBlock;
                if (closest == null || distance < closest.distanceSquared() - 1.0e-8
                        || Math.abs(distance - closest.distanceSquared()) <= 1.0e-8 && cover && !closestIsCover) {
                    closest = new PartHitResult(part.id(), hit.get(), distance);
                    closestIsCover = cover;
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    private static net.minecraft.world.phys.AABB clipToCell(net.minecraft.world.phys.AABB box,
                                                             net.minecraft.core.BlockPos anchor,
                                                             net.minecraft.core.BlockPos cell) {
        int x = cell.getX() - anchor.getX(), y = cell.getY() - anchor.getY(), z = cell.getZ() - anchor.getZ();
        return new net.minecraft.world.phys.AABB(Math.max(box.minX, x), Math.max(box.minY, y), Math.max(box.minZ, z),
                Math.min(box.maxX, x + 1), Math.min(box.maxY, y + 1), Math.min(box.maxZ, z + 1));
    }

    private static boolean intersectsCell(net.minecraft.world.phys.AABB box, net.minecraft.core.BlockPos anchor,
                                          net.minecraft.core.BlockPos cell) {
        int x = cell.getX() - anchor.getX(), y = cell.getY() - anchor.getY(), z = cell.getZ() - anchor.getZ();
        return box.maxX > x && box.minX < x + 1 && box.maxY > y && box.minY < y + 1 && box.maxZ > z && box.minZ < z + 1;
    }
}
