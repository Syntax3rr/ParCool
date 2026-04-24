package com.alrex.parcool.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

class SableCompatImpl {

    // Returns a handle to the first sub-level whose world bbox intersects searchAABB,
    // regardless of whether any blocks in the sub-level collide with the box.  Use this
    // to find "which sub-level is the player near" before doing local-frame probes.
    @Nullable
    static SubLevelHandle firstInRange(Level level, AABB searchAABB) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) return new SubLevelHandle(sla);
        }
        return null;
    }

    // Transforms a world-space position to the sub-level's local coordinates.
    static Vec3 worldToLocal(SubLevelHandle handle, Vec3 worldPos) {
        return ((SubLevelAccess) handle.sla).logicalPose().transformPositionInverse(worldPos);
    }

    // Rotation-only transform of a local-space direction to world space.  Computes
    // pose(dir) - pose(0) to drop the translation component.  Magnitude matches the
    // input for an unscaled sub-level (Sable's typical case); normalize at the call
    // site if non-uniform scale is a concern.
    static Vec3 localDirectionToWorld(SubLevelHandle handle, Vec3 localDir) {
        Pose3dc pose = ((SubLevelAccess) handle.sla).logicalPose();
        Vec3 origin = pose.transformPosition(Vec3.ZERO);
        return pose.transformPosition(localDir).subtract(origin);
    }

    // Queries the sub-level's own Level directly with a local-space AABB — no world→local
    // AABB transform, so no bound enlargement from rotation.  This gives physically
    // accurate collision results for rotated sub-levels, unlike hasCollisionIn which
    // transforms the 8 world corners and bounds them in local space (can enlarge up to √3×).
    static boolean hasLocalCollision(SubLevelHandle handle, AABB localAABB) {
        if (handle.sla instanceof SubLevel subLevel) return !subLevel.getLevel().noCollision(localAABB);
        return false;
    }

    // Returns a handle to the first sub-level whose geometry intersects aabb, or null.
    // Use with hasCollisionIn() to make subsequent probes against that specific sub-level,
    // avoiding the promiscuous "any sub-level" semantics of hasSubLevelCollision().
    @Nullable
    static SubLevelHandle firstColliding(Level level, AABB aabb) {
        BoundingBox3d box = new BoundingBox3d(aabb);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel subLevel) {
                AABB localAABB = transformAABBToLocal(aabb, sla.logicalPose());
                if (!subLevel.getLevel().noCollision(localAABB)) return new SubLevelHandle(sla);
            }
        }
        return null;
    }

    // Tests whether the sub-level identified by handle has collision in the world-space aabb.
    // The aabb is transformed into the sub-level's local frame internally, so callers pass
    // world coordinates without reasoning about orientation.
    static boolean hasCollisionIn(SubLevelHandle handle, AABB aabb) {
        if (handle.sla instanceof SubLevel subLevel) {
            AABB localAABB = transformAABBToLocal(aabb, ((SubLevelAccess) handle.sla).logicalPose());
            return !subLevel.getLevel().noCollision(localAABB);
        }
        return false;
    }

    static boolean hasSubLevelCollision(Level level, AABB aabb) {
        BoundingBox3d box = new BoundingBox3d(aabb);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel subLevel) {
                // Transform the AABB from world space to the sub-level's local coordinate space,
                // then ask that sub-level's level whether the transformed region is blocked.
                AABB localAABB = transformAABBToLocal(aabb, sla.logicalPose());
                if (!subLevel.getLevel().noCollision(localAABB)) return true;
            }
        }
        return false;
    }

    @Nullable
    static BlockState getSubLevelBlockState(Level level, BlockPos pos) {
        SubLevelAccess sla = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sla instanceof SubLevel subLevel) {
            // Inverse-transform the block's world-space center to the sub-level's local space,
            // then look up the block in the sub-level's own chunk data.
            Vec3 worldCenter = Vec3.atCenterOf(pos);
            Vec3 local = sla.logicalPose().transformPositionInverse(worldCenter);
            return subLevel.getLevel().getBlockState(BlockPos.containing(local.x, local.y, local.z));
        }
        return null;
    }

    // Returns the change in sub-level local Y when stepping from worldPos by horizontalDir.
    // Negative = downhill in world space, positive = uphill.
    static double getSubLevelSlopeInDirection(Level level, Vec3 worldPos, Vec3 horizontalDir) {
        SubLevelAccess sla = SableCompanion.INSTANCE.getContaining(level, BlockPos.containing(worldPos));
        if (!(sla instanceof SubLevel)) return 0.0;
        Pose3dc pose = sla.logicalPose();
        Vec3 local0 = pose.transformPositionInverse(worldPos);
        Vec3 local1 = pose.transformPositionInverse(worldPos.add(horizontalDir));
        return local1.y() - local0.y();
    }

    // Returns the sub-level's local Y axis in world space (= surface normal), or world (0,1,0) if none.
    // Checks slightly below worldPos as well so this works when the player is standing on top of a sub-level.
    static Vec3 getSubLevelSurfaceNormal(Level level, Vec3 worldPos) {
        SubLevelAccess sla = getSlaAt(level, worldPos);
        if (!(sla instanceof SubLevel)) return new Vec3(0, 1, 0);
        Vec3[] axes = computeLocalAxesInWorld(sla.logicalPose(), worldPos);
        return axes[1]; // local Y = surface normal
    }

    // Returns [localX_world, localZ_world] for the sub-level at worldPos,
    // or [world-X, world-Z] if no sub-level is present.
    static Vec3[] getSubLevelLocalXZAxes(Level level, Vec3 worldPos) {
        SubLevelAccess sla = getSlaAt(level, worldPos);
        if (!(sla instanceof SubLevel)) return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        Vec3[] axes = computeLocalAxesInWorld(sla.logicalPose(), worldPos);
        return new Vec3[]{axes[0], axes[2]}; // local X and local Z
    }

    // Returns [localX_world, localZ_world] for the first sub-level whose world bounding box
    // intersects searchAABB, or [world-X, world-Z] if none found.  Unlike getSubLevelLocalXZAxes
    // this works even when the player is beside (not inside) the sub-level, making it suitable
    // for wall-detection probes.
    static Vec3[] getNearbySubLevelLocalXZAxes(Level level, AABB searchAABB) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Vec3 center = new Vec3(
                        (searchAABB.minX + searchAABB.maxX) / 2.0,
                        (searchAABB.minY + searchAABB.maxY) / 2.0,
                        (searchAABB.minZ + searchAABB.maxZ) / 2.0
                );
                Vec3[] axes = computeLocalAxesInWorld(sla.logicalPose(), center);
                return new Vec3[]{axes[0], axes[2]};
            }
        }
        return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
    }

    // Returns how far pos moves from the previous tick to this tick due to sub-level motion,
    // in blocks/tick.  Mirrors Sable's own floor-tracking formula so the result is always
    // physically bounded (zero for a stationary sub-level, never a huge or NaN value).
    // getVelocity(level, sla, pos) cannot be used here: that overload expects local-space pos
    // and would produce enormous results when given world-space coordinates.
    static Vec3 getSubLevelDisplacementAt(Level level, AABB searchAABB, Vec3 pos) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Pose3dc last = sla.lastPose();
                if (last == null) continue;
                Vec3 localPos = last.transformPositionInverse(pos);
                Vec3 newPos = sla.logicalPose().transformPosition(localPos);
                return newPos.subtract(pos);
            }
        }
        return Vec3.ZERO;
    }

    // Re-expresses pos in the current frame of the nearest sub-level intersecting searchAABB.
    // Mirrors Sable's own floor-tracking transform: cur.transformPosition(last.transformPositionInverse(pos)).
    // Returns pos unchanged when no sub-level is found.
    static Vec3 getSubLevelTrackedPosition(Level level, AABB searchAABB, Vec3 pos) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Pose3dc last = sla.lastPose();
                if (last == null) continue;
                return sla.logicalPose().transformPosition(last.transformPositionInverse(pos));
            }
        }
        return pos;
    }

    // Returns the surface normal (local Y in world space) for the first sub-level intersecting
    // searchAABB, or world (0,1,0) if none found.  Uses getAllIntersecting so it works even when
    // the player is beside (not inside) the sub-level.
    static Vec3 getNearbySubLevelSurfaceNormal(Level level, AABB searchAABB) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Vec3 center = new Vec3(
                        (searchAABB.minX + searchAABB.maxX) / 2.0,
                        (searchAABB.minY + searchAABB.maxY) / 2.0,
                        (searchAABB.minZ + searchAABB.maxZ) / 2.0
                );
                Vec3[] axes = computeLocalAxesInWorld(sla.logicalPose(), center);
                return axes[1]; // local Y = surface normal
            }
        }
        return new Vec3(0, 1, 0);
    }

    // Single-pass lookup: returns a fully-populated SableLocalFrame (localX/Y/Z + displacement)
    // for the first sub-level intersecting searchAABB, or null if none found.  Backs
    // SableLocalFrame.at() so callers don't pay three separate getAllIntersecting() calls per
    // tick when they want multiple frame components together.
    @Nullable
    static SableLocalFrame buildFrameAt(Level level, AABB searchAABB, Vec3 pos) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Vec3 center = new Vec3(
                        (searchAABB.minX + searchAABB.maxX) / 2.0,
                        (searchAABB.minY + searchAABB.maxY) / 2.0,
                        (searchAABB.minZ + searchAABB.maxZ) / 2.0
                );
                Vec3[] axes = computeLocalAxesInWorld(sla.logicalPose(), center);
                Pose3dc last = sla.lastPose();
                Vec3 disp = Vec3.ZERO;
                if (last != null) {
                    Vec3 localPos = last.transformPositionInverse(pos);
                    Vec3 newPos = sla.logicalPose().transformPosition(localPos);
                    disp = newPos.subtract(pos);
                }
                return new SableLocalFrame(axes[0], axes[1], axes[2], disp, true);
            }
        }
        return null;
    }

    // Finds the sub-level containing worldPos, or slightly below (for players standing on top).
    private static SubLevelAccess getSlaAt(Level level, Vec3 worldPos) {
        SubLevelAccess sla = SableCompanion.INSTANCE.getContaining(level, BlockPos.containing(worldPos));
        if (!(sla instanceof SubLevel)) {
            sla = SableCompanion.INSTANCE.getContaining(level, BlockPos.containing(worldPos.add(0, -0.6, 0)));
        }
        return sla;
    }

    // Computes [localX, localY, localZ] axes in world space using a numerical Jacobian of
    // transformPositionInverse (world→local). Since that map applies R^T, differencing gives
    // the rows of R^T; the j-th column of R (= local axis j in world space) is assembled from
    // the j-th components of each finite-difference vector.
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

    // Transforms a world-space AABB into the sub-level's local coordinate space.
    private static AABB transformAABBToLocal(AABB aabb, Pose3dc pose) {
        double minX = Double.MAX_VALUE,  minY = Double.MAX_VALUE,  minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3(
                (i & 1) == 0 ? aabb.minX : aabb.maxX,
                (i & 2) == 0 ? aabb.minY : aabb.maxY,
                (i & 4) == 0 ? aabb.minZ : aabb.maxZ
            );
            Vec3 local = pose.transformPositionInverse(corner);
            if (local.x < minX) minX = local.x;
            if (local.y < minY) minY = local.y;
            if (local.z < minZ) minZ = local.z;
            if (local.x > maxX) maxX = local.x;
            if (local.y > maxY) maxY = local.y;
            if (local.z > maxZ) maxZ = local.z;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
