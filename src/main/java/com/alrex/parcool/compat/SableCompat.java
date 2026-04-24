package com.alrex.parcool.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

public class SableCompat {
    private static final boolean LOADED = ModList.get().isLoaded("sable");

    public static boolean isLoaded() {
        return LOADED;
    }

    // Returns true if any sable sub-level has collision in the given world-space AABB.
    public static boolean hasSubLevelCollision(Level level, AABB aabb) {
        return SableCompatImpl.hasSubLevelCollision(level, aabb);
    }

    // Returns the BlockState of a sable sub-level block at the given world position,
    // or null if no sub-level block occupies that position.
    @Nullable
    public static BlockState getSubLevelBlockState(Level level, BlockPos pos) {
        return SableCompatImpl.getSubLevelBlockState(level, pos);
    }

    // Handle to the first sub-level whose world bbox intersects searchAABB, or null.
    @Nullable
    public static SubLevelHandle firstSubLevelInRange(Level level, AABB searchAABB) {
        if (!LOADED) return null;
        return SableCompatImpl.firstInRange(level, searchAABB);
    }

    // World position in the sub-level's local coordinates.  Returns worldPos if sable
    // isn't loaded or handle is null.
    public static Vec3 worldToLocal(@Nullable SubLevelHandle handle, Vec3 worldPos) {
        if (!LOADED || handle == null) return worldPos;
        return SableCompatImpl.worldToLocal(handle, worldPos);
    }

    // Rotation-only transform of a local direction to world.
    public static Vec3 localDirectionToWorld(@Nullable SubLevelHandle handle, Vec3 localDir) {
        if (!LOADED || handle == null) return localDir;
        return SableCompatImpl.localDirectionToWorld(handle, localDir);
    }

    // Collision test against the sub-level's own Level with a local-space AABB — no
    // world→local bound enlargement that plagues AABB rotation transforms.
    public static boolean hasLocalCollision(@Nullable SubLevelHandle handle, AABB localAABB) {
        if (!LOADED || handle == null) return false;
        return SableCompatImpl.hasLocalCollision(handle, localAABB);
    }

    // Local-Y delta when stepping from worldPos by horizontalDir on the sub-level there.
    // Positive = uphill, negative = downhill.  Returns 0 when no sub-level is present.
    public static double getSubLevelSlopeInDirection(Level level, Vec3 worldPos, Vec3 horizontalDir) {
        if (!LOADED) return 0.0;
        return SableCompatImpl.getSubLevelSlopeInDirection(level, worldPos, horizontalDir);
    }

    // [localX, localZ] in world space — the sub-level's local horizontal axes, searched
    // across all sub-levels intersecting searchAABB.  Returns [world-X, world-Z] if none.
    public static Vec3[] getNearbySubLevelLocalXZAxes(Level level, AABB searchAABB) {
        if (!LOADED) return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        return SableCompatImpl.getNearbySubLevelLocalXZAxes(level, searchAABB);
    }

    // How far pos moves this tick due to sub-level motion (blocks/tick), bounded by
    // pose-delta.  searchAABB is used to locate the sub-level when pos is in air
    // beside it.  Returns Vec3.ZERO when no sub-level is found.
    public static Vec3 getSubLevelDisplacementAt(Level level, AABB searchAABB, Vec3 pos) {
        if (!LOADED) return Vec3.ZERO;
        return SableCompatImpl.getSubLevelDisplacementAt(level, searchAABB, pos);
    }

    // Re-expresses pos in the current frame of the nearest sub-level intersecting
    // searchAABB, so the caller can setPos() to stay fixed to a moving sub-level.
    public static Vec3 getSubLevelTrackedPosition(Level level, AABB searchAABB, Vec3 pos) {
        if (!LOADED) return pos;
        return SableCompatImpl.getSubLevelTrackedPosition(level, searchAABB, pos);
    }

    // Re-anchors the entity to any nearby sub-level's current frame so it co-moves
    // with the sub-level each tick.  No-op when no sub-level is found.
    public static void applySubLevelTracking(Entity entity, double inflateRange) {
        if (!LOADED) return;
        Vec3 pos = entity.position();
        Vec3 tracked = SableCompatImpl.getSubLevelTrackedPosition(entity.level(),
                entity.getBoundingBox().inflate(inflateRange), pos);
        if (!tracked.equals(pos)) entity.setPos(tracked.x(), tracked.y(), tracked.z());
    }
}
