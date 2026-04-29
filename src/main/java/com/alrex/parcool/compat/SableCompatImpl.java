package com.alrex.parcool.compat;

import com.alrex.parcool.utilities.WorldUtil;
import com.alrex.parcool.utilities.probe.BarInfo;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

final class SableCompatImpl {
    private SableCompatImpl() {}

    // Per-tick world-space delta of a point carried by a containing sub-level.
    static Vec3 velocityAt(Level level, Vec3 worldPos) {
        AABB region = new AABB(worldPos, worldPos).inflate(0.5);
        BoundingBox3d box = new BoundingBox3d(region);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (!(sla instanceof SubLevel)) continue;
            Pose3dc last = sla.lastPose();
            if (last == null) continue;
            return sla.logicalPose().transformPosition(last.transformPositionInverse(worldPos)).subtract(worldPos);
        }
        return Vec3.ZERO;
    }

    static void applySubLevelTracking(Entity entity, double inflateRange) {
        BoundingBox3d region = new BoundingBox3d(entity.getBoundingBox().inflate(inflateRange));
        Vec3 pos = entity.position();
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(entity.level(), region)) {
            if (!(sla instanceof SubLevel)) continue;
            Pose3dc last = sla.lastPose();
            if (last == null) continue;
            Vec3 tracked = sla.logicalPose().transformPosition(last.transformPositionInverse(pos));
            if (!tracked.equals(pos)) entity.setPos(tracked.x(), tracked.y(), tracked.z());
            return;
        }
    }

    @Nullable
    static BlockState subLevelBlockStateAt(Level level, BlockPos worldPos) {
        SubLevelAccess sla = SableCompanion.INSTANCE.getContaining(level, Vec3.atCenterOf(worldPos));
        if (!(sla instanceof SubLevel subLevel)) return null;
        Vec3 local = sla.logicalPose().transformPositionInverse(Vec3.atCenterOf(worldPos));
        return subLevel.getLevel().getBlockState(BlockPos.containing(local));
    }

    @Nullable
    static BarInfo barInfoOverhead(Level level, BlockPos worldPos, LivingEntity entity) {
        AABB region = new AABB(worldPos).inflate(0.5);
        BoundingBox3d box = new BoundingBox3d(region);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (!(sla instanceof SubLevel subLevel)) continue;
            Vec3 worldCenter = Vec3.atCenterOf(worldPos);
            Vec3 local = sla.logicalPose().transformPositionInverse(worldCenter);
            BlockPos localPos = BlockPos.containing(local);
            BlockState state = subLevel.getLevel().getBlockState(localPos);
            if (state.isAir()) continue;
            Vec3 localAxis = WorldUtil.barAxisFromBlock(state, subLevel.getLevel(), localPos);
            if (localAxis == null) continue;
            Vec3 worldAxis = sla.logicalPose().transformNormal(localAxis);
            return new BarInfo(worldAxis, worldPos);
        }
        return null;
    }
}
