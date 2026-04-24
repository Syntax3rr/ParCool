package com.alrex.parcool.compat;

import net.minecraft.core.BlockPos;
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

    // Returns true if sable sub-level blocks fill the given AABB (wall/floor detection).
    public static boolean hasSubLevelCollision(Level level, AABB aabb) {
        return SableCompatImpl.hasSubLevelCollision(level, aabb);
    }

    // Returns a handle to the first sub-level whose world bbox intersects searchAABB, or null.
    // Unlike firstSubLevelColliding, this doesn't require the sub-level to have collision
    // geometry in the search region — use it to identify "which sub-level is the player near"
    // before doing local-frame probes.
    @Nullable
    public static SubLevelHandle firstSubLevelInRange(Level level, AABB searchAABB) {
        if (!LOADED) return null;
        return SableCompatImpl.firstInRange(level, searchAABB);
    }

    // Transforms a world position to the sub-level's local coordinates.  Returns worldPos
    // unchanged if sable isn't loaded or handle is null.
    public static Vec3 worldToLocal(@Nullable SubLevelHandle handle, Vec3 worldPos) {
        if (!LOADED || handle == null) return worldPos;
        return SableCompatImpl.worldToLocal(handle, worldPos);
    }

    // Rotation-only transform of a local direction to world.  Returns localDir unchanged
    // if sable isn't loaded or handle is null.
    public static Vec3 localDirectionToWorld(@Nullable SubLevelHandle handle, Vec3 localDir) {
        if (!LOADED || handle == null) return localDir;
        return SableCompatImpl.localDirectionToWorld(handle, localDir);
    }

    // Queries the sub-level directly with a local-space AABB — no world→local bound
    // enlargement.  Returns false if sable isn't loaded or handle is null.
    public static boolean hasLocalCollision(@Nullable SubLevelHandle handle, AABB localAABB) {
        if (!LOADED || handle == null) return false;
        return SableCompatImpl.hasLocalCollision(handle, localAABB);
    }

    // Returns a handle to the first sub-level whose geometry intersects aabb, or null.
    // Use with hasCollisionIn for follow-up probes against the same sub-level, so e.g. a
    // cliff's "top clear" check ignores other overlapping sub-levels and respects the
    // original sub-level's orientation automatically.
    @Nullable
    public static SubLevelHandle firstSubLevelColliding(Level level, AABB aabb) {
        if (!LOADED) return null;
        return SableCompatImpl.firstColliding(level, aabb);
    }

    // Tests whether the specific sub-level identified by handle has collision in aabb
    // (world space).  Returns false when sable isn't loaded or handle is null.
    public static boolean hasCollisionIn(@Nullable SubLevelHandle handle, AABB aabb) {
        if (!LOADED || handle == null) return false;
        return SableCompatImpl.hasCollisionIn(handle, aabb);
    }

    // Returns the BlockState of a sable sub-level block at the given world position,
    // or null if no sub-level block occupies that position.
    @Nullable
    public static BlockState getSubLevelBlockState(Level level, BlockPos pos) {
        return SableCompatImpl.getSubLevelBlockState(level, pos);
    }

    // Returns how much local Y changes when stepping from worldPos by horizontalDir
    // on the sable sub-level at that position. Negative = downhill, positive = uphill.
    // Returns 0 if sable is not loaded or no sub-level is present.
    public static double getSubLevelSlopeInDirection(Level level, Vec3 worldPos, Vec3 horizontalDir) {
        if (!LOADED) return 0.0;
        return SableCompatImpl.getSubLevelSlopeInDirection(level, worldPos, horizontalDir);
    }

    // Returns the sub-level's local Y axis in world space (the surface normal pointing "up"
    // relative to the sub-level). Returns (0,1,0) when sable is not loaded or no sub-level found.
    public static Vec3 getSubLevelSurfaceNormal(Level level, Vec3 worldPos) {
        if (!LOADED) return new Vec3(0, 1, 0);
        return SableCompatImpl.getSubLevelSurfaceNormal(level, worldPos);
    }

    // Returns [localX_world, localZ_world] — the sub-level's local horizontal axes expressed in
    // world space. Returns [world-X, world-Z] when sable is not loaded or no sub-level found.
    public static Vec3[] getSubLevelLocalXZAxes(Level level, Vec3 worldPos) {
        if (!LOADED) return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        return SableCompatImpl.getSubLevelLocalXZAxes(level, worldPos);
    }

    // Like getSubLevelLocalXZAxes but searches all sub-levels that intersect searchAABB, not just
    // ones containing the query point.  Use this for wall-detection probes where the player is
    // beside (not inside) the sub-level.  Returns [world-X, world-Z] if none found.
    public static Vec3[] getNearbySubLevelLocalXZAxes(Level level, AABB searchAABB) {
        if (!LOADED) return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        return SableCompatImpl.getNearbySubLevelLocalXZAxes(level, searchAABB);
    }

    // Returns how far pos moves this tick due to sub-level motion (blocks/tick).
    // Uses pose-delta math so the result is physically bounded (zero for stationary sub-levels).
    // searchAABB finds the sub-level even when pos is in air beside a wall.
    // Returns Vec3.ZERO if Sable is not loaded or no sub-level is found.
    public static Vec3 getSubLevelDisplacementAt(Level level, AABB searchAABB, Vec3 pos) {
        if (!LOADED) return Vec3.ZERO;
        return SableCompatImpl.getSubLevelDisplacementAt(level, searchAABB, pos);
    }

    // Returns pos re-expressed in the current frame of the nearest intersecting sub-level,
    // so the caller can setPos() to stay fixed relative to it. Returns pos unchanged if
    // Sable is not loaded or no sub-level is found.
    public static Vec3 getSubLevelTrackedPosition(Level level, AABB searchAABB, Vec3 pos) {
        if (!LOADED) return pos;
        return SableCompatImpl.getSubLevelTrackedPosition(level, searchAABB, pos);
    }

    // Returns the surface normal (local Y axis in world space) for the nearest sub-level
    // intersecting searchAABB.  Unlike getSubLevelSurfaceNormal, uses getAllIntersecting so it
    // works when the player is beside the sub-level (e.g. clinging to its wall).
    // Returns (0,1,0) when sable is not loaded or no sub-level found.
    public static Vec3 getNearbySubLevelSurfaceNormal(Level level, AABB searchAABB) {
        if (!LOADED) return new Vec3(0, 1, 0);
        return SableCompatImpl.getNearbySubLevelSurfaceNormal(level, searchAABB);
    }
}
