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

    @Nullable
    static SubLevelHandle firstInRange(Level level, AABB searchAABB) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) return new SubLevelHandle(sla);
        }
        return null;
    }

    static Vec3 worldToLocal(SubLevelHandle handle, Vec3 worldPos) {
        return ((SubLevelAccess) handle.sla).logicalPose().transformPositionInverse(worldPos);
    }

    // pose(dir) - pose(0) drops translation, leaving the rotation-only transform.
    static Vec3 localDirectionToWorld(SubLevelHandle handle, Vec3 localDir) {
        Pose3dc pose = ((SubLevelAccess) handle.sla).logicalPose();
        Vec3 origin = pose.transformPosition(Vec3.ZERO);
        return pose.transformPosition(localDir).subtract(origin);
    }

    static boolean hasLocalCollision(SubLevelHandle handle, AABB localAABB) {
        if (handle.sla instanceof SubLevel subLevel) return !subLevel.getLevel().noCollision(localAABB);
        return false;
    }

    static boolean hasSubLevelCollision(Level level, AABB aabb) {
        BoundingBox3d box = new BoundingBox3d(aabb);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel subLevel) {
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
            Vec3 worldCenter = Vec3.atCenterOf(pos);
            Vec3 local = sla.logicalPose().transformPositionInverse(worldCenter);
            return subLevel.getLevel().getBlockState(BlockPos.containing(local.x, local.y, local.z));
        }
        return null;
    }

    static double getSubLevelSlopeInDirection(Level level, Vec3 worldPos, Vec3 horizontalDir) {
        SubLevelAccess sla = SableCompanion.INSTANCE.getContaining(level, BlockPos.containing(worldPos));
        if (!(sla instanceof SubLevel)) return 0.0;
        Pose3dc pose = sla.logicalPose();
        return pose.transformPositionInverse(worldPos.add(horizontalDir)).y()
                - pose.transformPositionInverse(worldPos).y();
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

    static Vec3 getSubLevelDisplacementAt(Level level, AABB searchAABB, Vec3 pos) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Pose3dc last = sla.lastPose();
                if (last == null) continue;
                return sla.logicalPose().transformPosition(last.transformPositionInverse(pos)).subtract(pos);
            }
        }
        return Vec3.ZERO;
    }

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

    // One scan to build axes + displacement, instead of three separate lookups per tick.
    @Nullable
    static SableLocalFrame buildFrameAt(Level level, AABB searchAABB, Vec3 pos) {
        BoundingBox3d box = new BoundingBox3d(searchAABB);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (sla instanceof SubLevel) {
                Vec3[] axes = computeLocalAxesInWorld(sla.logicalPose(), searchAABB.getCenter());
                Pose3dc last = sla.lastPose();
                Vec3 disp = last == null ? Vec3.ZERO
                        : sla.logicalPose().transformPosition(last.transformPositionInverse(pos)).subtract(pos);
                return new SableLocalFrame(axes[0], axes[1], axes[2], disp, true);
            }
        }
        return null;
    }

    // Recover the columns of R (local axes in world space) by finite-differencing
    // transformPositionInverse, which applies R'.
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
