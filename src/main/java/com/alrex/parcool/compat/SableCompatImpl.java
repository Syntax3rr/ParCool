package com.alrex.parcool.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

class SableCompatImpl {

    static boolean hasSubLevelCollision(Level level, AABB aabb) {
        BoundingBox3d box = new BoundingBox3d(aabb);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (!(sla instanceof SubLevel subLevel)) continue;
            Pose3dc pose = sla.logicalPose();
            // Broad phase: the enclosing AABB in the sub-level's frame over-approximates
            // for off-axis rotations, so re-project each candidate block cell back to
            // world and require a real overlap before reporting a hit. The broad phase
            // is a superset, so this drops false positives without missing collisions.
            AABB localAABB = transformAABBToLocal(aabb, pose);
            for (VoxelShape shape : subLevel.getLevel().getBlockCollisions(null, localAABB)) {
                if (aabb.intersects(transformAABBToWorld(shape.bounds(), pose).inflate(1.0e-7))) {
                    return true;
                }
            }
        }
        return false;
    }

    // World-space block lookup: find the sub-level whose logical bounds contain this
    // world position and read its block. getContaining() can't be used here because it
    // keys on the sub-level's plot (storage) chunk, not its arbitrary logical pose.
    @Nullable
    static BlockState getSubLevelBlockState(Level level, BlockPos pos) {
        return getSubLevelBlockStateAt(level, Vec3.atCenterOf(pos));
    }

    // Samples the block at an exact world point rather than a block-grid centre, so it
    // lands in the right cell even when the sub-level is translated/rotated off the world
    // grid (callers reading a feature at a precise spot, e.g. a hang bar above the head).
    @Nullable
    static BlockState getSubLevelBlockStateAt(Level level, Vec3 worldPos) {
        BoundingBox3d box = new BoundingBox3d(new AABB(BlockPos.containing(worldPos)));
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel subLevel) {
                Vec3 local = sla.logicalPose().transformPositionInverse(worldPos);
                BlockState state = subLevel.getLevel().getBlockState(
                        BlockPos.containing(local.x, local.y, local.z));
                if (!state.isAir()) return state;
            }
        }
        return null;
    }

    // Floor lookup that survives arbitrary sub-level rotation. Steps down in world
    // space (the player is always world-vertical) and transforms each probe into
    // the sub-level's frame, so it still works when local Y points sideways or down.
    // Multiple depths cover the small collision-tolerance gap between foot and floor.
    @Nullable
    static BlockState getSubLevelFloorBlockState(Level level, Vec3 worldFootPos) {
        AABB probeAabb = new AABB(worldFootPos.x - 0.05, worldFootPos.y - 0.6, worldFootPos.z - 0.05,
                                  worldFootPos.x + 0.05, worldFootPos.y + 0.05, worldFootPos.z + 0.05);
        BoundingBox3d box = new BoundingBox3d(probeAabb);
        double[] depths = {0.05, 0.15, 0.3, 0.5};
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (!(sla instanceof SubLevel subLevel)) continue;
            Pose3dc pose = sla.logicalPose();
            for (double d : depths) {
                Vec3 local = pose.transformPositionInverse(worldFootPos.add(0, -d, 0));
                BlockState state = subLevel.getLevel().getBlockState(
                        BlockPos.containing(local.x, local.y, local.z));
                if (!state.isAir()) return state;
            }
        }
        return null;
    }

    // World-space lookup (see getSubLevelBlockState for why getContaining() is wrong here).
    static double getSubLevelSlopeInDirection(Level level, Vec3 worldPos, Vec3 horizontalDir) {
        BoundingBox3d box = new BoundingBox3d(new AABB(BlockPos.containing(worldPos)));
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Pose3dc pose = sla.logicalPose();
                return pose.transformPositionInverse(worldPos.add(horizontalDir)).y()
                        - pose.transformPositionInverse(worldPos).y();
            }
        }
        return 0.0;
    }

    static Vec3[] getNearbySubLevelLocalXZAxes(Level level, AABB searchAABB) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Vec3[] axes = computeLocalAxesInWorld(sla.logicalPose(), searchAABB.getCenter());
                return new Vec3[]{axes[0], axes[2]};
            }
        }
        return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
    }

    // Re-projects the entity's position through the sub-level's per-tick pose change so
    // it stays attached to a moving deck. Sable already carries entities standing on top
    // of a sub-level (LivingEntity#travel applies inheritedMotion when verticalCollisionBelow
    // sets a tracking sub-level); this only fills the gap for walls the player is
    // slid/run against but not standing on. Skip when Sable is already tracking so the
    // motion isn't applied twice.
    static void applySubLevelTracking(Entity entity, double inflateRange) {
        if (SableCompanion.INSTANCE.getTrackingSubLevel(entity) != null) return;
        Vec3 pos = entity.position();
        AABB searchAABB = entity.getBoundingBox().inflate(inflateRange);
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(entity.level(), box)) {
            if (sla instanceof SubLevel) {
                Pose3dc last = sla.lastPose();
                if (last == null) continue;
                Vec3 tracked = sla.logicalPose().transformPosition(last.transformPositionInverse(pos));
                if (!tracked.equals(pos)) entity.setPos(tracked.x(), tracked.y(), tracked.z());
                return;
            }
        }
    }

    // Single sub-level scan for axes + displacement; the per-call alternative does
    // separate lookups per tick.
    @Nullable
    static SableLocalFrame buildFrameAt(Level level, AABB searchAABB, Vec3 pos) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Pose3dc pose = sla.logicalPose();
                Vec3[] axes = computeLocalAxesInWorld(pose, searchAABB.getCenter());
                Pose3dc last = sla.lastPose();
                Vec3 disp = Vec3.ZERO;
                Vec3 translation = Vec3.ZERO;
                if (last != null) {
                    // Full motion at the player's position (rotation included)...
                    disp = pose.transformPosition(last.transformPositionInverse(pos)).subtract(pos);
                    // ...and the sub-level's rotation-free translation (motion of its origin).
                    translation = pose.transformPosition(Vec3.ZERO).subtract(last.transformPosition(Vec3.ZERO));
                }
                return new SableLocalFrame(axes[0], axes[1], axes[2], disp, translation, true);
            }
        }
        return null;
    }

    // Sable exposes only transformPositionInverse (R'), so we finite-difference it
    // to recover R's columns (the local axes expressed in world space).
    private static Vec3[] computeLocalAxesInWorld(Pose3dc pose, Vec3 worldPos) {
        final double eps = 0.1;
        Vec3 base = pose.transformPositionInverse(worldPos);
        Vec3 dX = pose.transformPositionInverse(worldPos.add(eps, 0, 0)).subtract(base);
        Vec3 dY = pose.transformPositionInverse(worldPos.add(0, eps, 0)).subtract(base);
        Vec3 dZ = pose.transformPositionInverse(worldPos.add(0, 0, eps)).subtract(base);
        return new Vec3[]{
            safeNormalize(new Vec3(dX.x(), dY.x(), dZ.x()), new Vec3(1, 0, 0)),
            safeNormalize(new Vec3(dX.y(), dY.y(), dZ.y()), new Vec3(0, 1, 0)),
            safeNormalize(new Vec3(dX.z(), dY.z(), dZ.z()), new Vec3(0, 0, 1))
        };
    }

    private static Vec3 safeNormalize(Vec3 v, Vec3 fallback) {
        double len = v.length();
        return len > 0.001 ? v.scale(1.0 / len) : fallback;
    }

    private static AABB transformAABBToLocal(AABB aabb, Pose3dc pose) {
        double minX = Double.MAX_VALUE,  minY = Double.MAX_VALUE,  minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 local = pose.transformPositionInverse(new Vec3(
                (i & 1) == 0 ? aabb.minX : aabb.maxX,
                (i & 2) == 0 ? aabb.minY : aabb.maxY,
                (i & 4) == 0 ? aabb.minZ : aabb.maxZ
            ));
            minX = Math.min(minX, local.x); minY = Math.min(minY, local.y); minZ = Math.min(minZ, local.z);
            maxX = Math.max(maxX, local.x); maxY = Math.max(maxY, local.y); maxZ = Math.max(maxZ, local.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AABB transformAABBToWorld(AABB aabb, Pose3dc pose) {
        double minX = Double.MAX_VALUE,  minY = Double.MAX_VALUE,  minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 world = pose.transformPosition(new Vec3(
                (i & 1) == 0 ? aabb.minX : aabb.maxX,
                (i & 2) == 0 ? aabb.minY : aabb.maxY,
                (i & 4) == 0 ? aabb.minZ : aabb.maxZ
            ));
            minX = Math.min(minX, world.x); minY = Math.min(minY, world.y); minZ = Math.min(minZ, world.z);
            maxX = Math.max(maxX, world.x); maxY = Math.max(maxY, world.y); maxZ = Math.max(maxZ, world.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
