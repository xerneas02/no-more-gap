package fr.xerneas02.nomoregap.mixin.client;

import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TerrainParticle.class)
abstract class CompositeTerrainParticleMixin {
    @Inject(method = "createTerrainParticle", at = @At("HEAD"), cancellable = true)
    private static void noMoreGap$usePartTexture(BlockParticleOption option, ClientLevel level,
                                                  double x, double y, double z, double vx, double vy, double vz,
                                                  CallbackInfoReturnable<TerrainParticle> callback) {
        if (option.getState().getBlock() != ModBlocks.COMPOSITE
                && option.getState().getBlock() != ModBlocks.COMPOSITE_PROXY) return;
        CompositeBlockEntity composite = null;
        for (int dy = 0; dy >= -1 && composite == null; dy--) {
            var entity = level.getBlockEntity(BlockPos.containing(x, y + dy, z));
            if (entity instanceof CompositeBlockEntity anchor) composite = anchor;
            else if (entity instanceof CompositeProxyBlockEntity proxy
                    && level.getBlockEntity(proxy.anchor()) instanceof CompositeBlockEntity anchor) composite = anchor;
        }
        if (composite == null) return;
        BlockState nearest = null;
        double best = Double.POSITIVE_INFINITY;
        double localX = x - composite.getBlockPos().getX();
        double localY = y - composite.getBlockPos().getY();
        double localZ = z - composite.getBlockPos().getZ();
        for (var part : composite.parts().view()) {
            for (var box : ShapeTransformer.transform(part.state().getCollisionShape(
                    level, composite.getBlockPos(), CollisionContext.empty()), part.transform()).toAabbs()) {
                double dx = Math.max(box.minX - localX, Math.max(0, localX - box.maxX));
                double dy = Math.max(box.minY - localY, Math.max(0, localY - box.maxY));
                double dz = Math.max(box.minZ - localZ, Math.max(0, localZ - box.maxZ));
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < best) {
                    best = distance;
                    nearest = part.state();
                }
            }
        }
        if (nearest != null) callback.setReturnValue(new TerrainParticle(level, x, y, z, vx, vy, vz, nearest));
    }
}
