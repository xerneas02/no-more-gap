package fr.xerneas02.nomoregap.gametest;

import com.mojang.authlib.GameProfile;
import fr.xerneas02.nomoregap.block.CompositeBlock;
import fr.xerneas02.nomoregap.block.entity.CompositeBlockEntity;
import fr.xerneas02.nomoregap.block.entity.CompositeProxyBlockEntity;
import fr.xerneas02.nomoregap.geometry.FixedPoint;
import fr.xerneas02.nomoregap.geometry.LocalTransform;
import fr.xerneas02.nomoregap.geometry.ShapeTransformer;
import fr.xerneas02.nomoregap.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared helpers for the No More Gap game tests: mock players, ray aiming and
 * composite assertions. Game tests are run by the Fabric Game Test framework on
 * a dedicated server with the mod and its mixins loaded.
 *
 * <p>Note: {@code GameTestHelper} position methods ({@code setBlock},
 * {@code getBlockState}, {@code getBlockEntity}, ...) treat their input as
 * structure-relative and re-apply {@link GameTestHelper#absolutePos}. All
 * helpers here therefore operate on the level directly with absolute positions.
 */
public abstract class GameTestBase {
    protected static final GameProfile TEST_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("no-more-gap-tester".getBytes(StandardCharsets.UTF_8)), "nmg-tester");

    // ------------------------------------------------------------ world access

    /** Sets a block at an absolute position, bypassing the helper's relative mapping. */
    protected void setBlock(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.getLevel().setBlock(pos, state, 3);
    }

    protected BlockState getBlockState(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockState(pos);
    }

    protected <T extends net.minecraft.world.level.block.entity.BlockEntity> T getBlockEntity(
            GameTestHelper helper, BlockPos pos, Class<T> type) {
        var be = helper.getLevel().getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }

    // ---------------------------------------------------------------- players

    protected TestPlayer newPlayer(GameTestHelper helper) {
        return new TestPlayer(helper.getLevel(), TEST_PROFILE, GameType.SURVIVAL);
    }

    /** Positions the player's eye at {@code eye} and turns its view toward {@code target}. */
    protected void aim(TestPlayer player, Vec3 eye, Vec3 target) {
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
     * its thinnest axis, looking at the box centre. The ray therefore enters
     * the part from outside, which is required by {@code AABB.clip} in current
     * Minecraft versions (rays starting inside a box are rejected).
     */
    protected void aimAtPart(GameTestHelper helper, TestPlayer player, CompositeBlockEntity composite, int partId) {
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

    /** Fires the real right-click pipeline, exactly like the game does. */
    protected InteractionResult useOn(GameTestHelper helper, TestPlayer player, BlockPos pos, Vec3 hitLocation, Direction face) {
        return UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND,
                new BlockHitResult(hitLocation, face, pos, false));
    }

    /** Sneaks and uses the given item on the block, like a player placing a block. */
    protected InteractionResult placeItem(GameTestHelper helper, TestPlayer player, BlockPos pos, Item item, Vec3 hitLocation) {
        player.setSneaking(true);
        player.getInventory().setItem(0, new ItemStack(item));
        return useOn(helper, player, pos, hitLocation, Direction.UP);
    }

    /** Uses the block with an empty hand, like a player interacting with it. */
    protected InteractionResult interact(GameTestHelper helper, TestPlayer player, BlockPos pos, Vec3 hitLocation, Direction face) {
        player.setSneaking(false);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        return useOn(helper, player, pos, hitLocation, face);
    }

    // ------------------------------------------------------------- composites

    protected CompositeBlockEntity requireComposite(GameTestHelper helper, BlockPos pos) {
        var state = getBlockState(helper, pos);
        assertTrue(state.getBlock() instanceof CompositeBlock,
                "Expected a CompositeBlock at " + pos + " but found " + state);
        var be = getBlockEntity(helper, pos, CompositeBlockEntity.class);
        assertNotNull(be, "Missing CompositeBlockEntity at " + pos);
        return be;
    }

    protected void assertNoCompositeAt(GameTestHelper helper, BlockPos pos) {
        var state = getBlockState(helper, pos);
        assertFalse(state.getBlock() instanceof CompositeBlock,
                "Expected no CompositeBlock at " + pos + " but found " + state);
        assertNull(getBlockEntity(helper, pos, CompositeBlockEntity.class), "Orphan CompositeBlockEntity at " + pos);
    }

    protected void assertNoProxyNear(GameTestHelper helper, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            assertNull(getBlockEntity(helper, pos, CompositeProxyBlockEntity.class), "Orphan proxy at " + pos);
        }
    }

    protected CompositeProxyBlockEntity requireProxy(GameTestHelper helper, BlockPos pos, BlockPos expectedAnchor) {
        var proxy = getBlockEntity(helper, pos, CompositeProxyBlockEntity.class);
        assertNotNull(proxy, "Expected a CompositeProxyBlockEntity at " + pos);
        assertEquals(expectedAnchor, proxy.anchor(), "Proxy anchor mismatch at " + pos);
        return proxy;
    }

    /** Creates a composite with the given parts and lets proxy/SNOWY refresh run. */
    protected CompositeBlockEntity createComposite(GameTestHelper helper, BlockPos pos, java.util.List<fr.xerneas02.nomoregap.part.PartInstance> parts) {
        setBlock(helper, pos, ModBlocks.COMPOSITE.defaultBlockState());
        var composite = requireComposite(helper, pos);
        composite.replaceParts(parts);
        return composite;
    }

    protected static LocalTransform fixed(double xBlocks, double yBlocks, double zBlocks, int turns) {
        return new LocalTransform(FixedPoint.fromDouble(xBlocks), FixedPoint.fromDouble(yBlocks),
                FixedPoint.fromDouble(zBlocks), turns);
    }

    protected static VoxelShape partShape(GameTestHelper helper, CompositeBlockEntity composite, int partId) {
        var part = composite.parts().find(partId).orElseThrow();
        return ShapeTransformer.transform(
                part.state().getShape(helper.getLevel(), composite.getBlockPos(), CollisionContext.empty()),
                part.transform());
    }
}
