package fr.xerneas02.nomoregap.gametest;

import com.mojang.authlib.GameProfile;
import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import fr.xerneas02.nomoregap.interaction.CompositeInteractionHandler;
import fr.xerneas02.nomoregap.interaction.CompositePlacementHandler;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.UUID;

/**
 * Shared helpers for the NeoForge game tests. The tests drive the real
 * gameplay handlers of the port ({@link CompositePlacementHandler} and
 * {@link CompositeInteractionHandler}, the same code the NeoForge event
 * adapters call) inside a real server, with the mod's mixins applied.
 */
public abstract class NeoForgeTestBase {
    protected static final GameProfile TEST_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("no-more-gap-tester".getBytes(StandardCharsets.UTF_8)), "nmg-tester");

    // ------------------------------------------------------------ world access
    // GameTestHelper position methods re-apply absolutePos; operate on the
    // level directly with absolute positions instead.

    protected static void setBlock(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.getLevel().setBlock(pos, state, 3);
    }

    protected static BlockState getBlockState(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockState(pos);
    }

    @SuppressWarnings("unchecked")
    protected static <T extends net.minecraft.world.level.block.entity.BlockEntity> T getBlockEntity(
            GameTestHelper helper, BlockPos pos, Class<T> type) {
        var be = helper.getLevel().getBlockEntity(pos);
        return type.isInstance(be) ? (T) be : null;
    }

    // ---------------------------------------------------------------- players

    protected static TestPlayer newPlayer(GameTestHelper helper) {
        return new TestPlayer(helper.getLevel(), TEST_PROFILE, GameType.SURVIVAL);
    }

    /** Points the player's eye from {@code eye} toward {@code target}. */
    protected static void aim(TestPlayer player, Vec3 eye, Vec3 target) {
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    /**
     * Positions the player's eye just outside the largest box of a part, along
     * its thinnest axis, looking at the box centre: rays starting inside a box
     * are rejected by {@code AABB.clip} in current Minecraft versions.
     */
    protected static void aimAtPart(GameTestHelper helper, TestPlayer player, CompositeBlockEntity composite, int partId) {
        var part = composite.parts().find(partId).orElseThrow();
        var shape = ShapeTransformer.transform(
                part.state().getShape(helper.getLevel(), composite.getBlockPos(), CollisionContext.empty()),
                part.transform());
        var box = shape.toAabbs().stream()
                .max(Comparator.comparingDouble(b -> (b.maxX - b.minX) * (b.maxY - b.minY) * (b.maxZ - b.minZ)))
                .orElseThrow(() -> new IllegalStateException("Part " + partId + " has no geometry"));
        double centerX = composite.getBlockPos().getX() + (box.minX + box.maxX) / 2;
        double centerY = composite.getBlockPos().getY() + (box.minY + box.maxY) / 2;
        double centerZ = composite.getBlockPos().getZ() + (box.minZ + box.maxZ) / 2;
        double widthX = box.maxX - box.minX;
        double widthY = box.maxY - box.minY;
        double widthZ = box.maxZ - box.minZ;
        Vec3 eye;
        if (widthX <= widthY && widthX <= widthZ) {
            eye = new Vec3(box.maxX + 0.3, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        } else if (widthY <= widthX && widthY <= widthZ) {
            eye = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.3, (box.minZ + box.maxZ) / 2);
        } else {
            eye = new Vec3((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, box.maxZ + 0.3);
        }
        eye = eye.add(composite.getBlockPos().getX(), composite.getBlockPos().getY(), composite.getBlockPos().getZ());
        aim(player, eye, new Vec3(centerX, centerY, centerZ));
    }

    /** Sneaks and uses the given item on the block, like a player placing it. */
    protected static InteractionResult placeItem(GameTestHelper helper, TestPlayer player, BlockPos pos, Item item, Vec3 hitLocation) {
        player.setSneaking(true);
        player.getInventory().setItem(0, new ItemStack(item));
        return CompositePlacementHandler.useBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hitLocation, Direction.UP, pos, false));
    }

    /** Uses the block with an empty hand, like a player interacting with it. */
    protected static InteractionResult interact(GameTestHelper helper, TestPlayer player, BlockPos pos, Vec3 hitLocation, Direction face) {
        player.setSneaking(false);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        return useOn(helper, player, pos, hitLocation, face);
    }

    /**
     * Uses the block with whatever the player holds, running the port's real
     * pipeline (placement handler first, then interaction handler, exactly
     * like NeoForgeEvents.useBlock). The inventory is left untouched.
     */
    protected static InteractionResult useOn(GameTestHelper helper, TestPlayer player, BlockPos pos, Vec3 hitLocation, Direction face) {
        var hit = new BlockHitResult(hitLocation, face, pos, false);
        var result = CompositePlacementHandler.useBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction()) {
            result = CompositeInteractionHandler.useBlock(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
        }
        return result;
    }

    // ------------------------------------------------------------- composites

    protected static CompositeBlockEntity requireComposite(GameTestHelper helper, BlockPos pos) {
        var state = getBlockState(helper, pos);
        if (!(state.getBlock() instanceof CompositeBlock)) {
            throw new AssertionError("Expected a CompositeBlock at " + pos + " but found " + state);
        }
        var be = getBlockEntity(helper, pos, CompositeBlockEntity.class);
        if (be == null) throw new AssertionError("Missing CompositeBlockEntity at " + pos);
        return be;
    }

    protected static void assertNoCompositeAt(GameTestHelper helper, BlockPos pos) {
        var state = getBlockState(helper, pos);
        if (state.getBlock() instanceof CompositeBlock) {
            throw new AssertionError("Expected no CompositeBlock at " + pos + " but found " + state);
        }
        if (getBlockEntity(helper, pos, CompositeBlockEntity.class) != null) {
            throw new AssertionError("Orphan CompositeBlockEntity at " + pos);
        }
    }

    protected static void assertNoProxyNear(GameTestHelper helper, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (getBlockEntity(helper, pos, CompositeProxyBlockEntity.class) != null) {
                throw new AssertionError("Orphan proxy at " + pos);
            }
        }
    }

    protected static CompositeProxyBlockEntity requireProxy(GameTestHelper helper, BlockPos pos, BlockPos expectedAnchor) {
        var proxy = getBlockEntity(helper, pos, CompositeProxyBlockEntity.class);
        if (proxy == null) throw new AssertionError("Expected a CompositeProxyBlockEntity at " + pos);
        if (!expectedAnchor.equals(proxy.anchor())) {
            throw new AssertionError("Proxy anchor mismatch at " + pos + ": expected " + expectedAnchor + " but was " + proxy.anchor());
        }
        return proxy;
    }

    /** Creates a composite with the given parts and lets proxy/SNOWY refresh run. */
    protected static CompositeBlockEntity createComposite(GameTestHelper helper, BlockPos pos, java.util.List<fr.xerneas02.nomoregap.part.PartInstance> parts) {
        setBlock(helper, pos, ModBlocks.COMPOSITE.defaultBlockState());
        var composite = requireComposite(helper, pos);
        composite.replaceParts(parts);
        return composite;
    }

    protected static LocalTransform fixed(double xBlocks, double yBlocks, double zBlocks, int turns) {
        return new LocalTransform(FixedPoint.fromDouble(xBlocks), FixedPoint.fromDouble(yBlocks),
                FixedPoint.fromDouble(zBlocks), turns);
    }
}
