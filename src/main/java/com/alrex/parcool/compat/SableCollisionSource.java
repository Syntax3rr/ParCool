package com.alrex.parcool.compat;

import com.alrex.parcool.utilities.geometry.OrientedBox;
import com.alrex.parcool.utilities.probe.CollisionSource;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class SableCollisionSource implements CollisionSource {
    private final Level level;

    public SableCollisionSource(Level level) {
        this.level = level;
    }

    @Override
    public Iterable<OrientedBox> candidatesIn(AABB region, @Nullable Entity entity) {
        List<OrientedBox> out = new ArrayList<>();
        BoundingBox3d worldBox = new BoundingBox3d(region);
        for (SubLevelAccess sla : SableCompanion.INSTANCE.getAllIntersecting(level, worldBox)) {
            if (!(sla instanceof SubLevel subLevel)) continue;

            Pose3dc pose = sla.logicalPose();
            AABB localRegion = transformAABBInverse(region, pose);
            Vec3[] worldAxes = computeWorldAxes(pose);
            Vector3dc scale = pose.scale();

            for (VoxelShape shape : subLevel.getLevel().getBlockCollisions(null, localRegion)) {
                for (AABB localBox : shape.toAabbs()) {
                    out.add(orientedFromLocal(localBox, pose, worldAxes, scale));
                }
            }
        }
        return out;
    }

    private static OrientedBox orientedFromLocal(AABB localBox, Pose3dc pose, Vec3[] worldAxes, Vector3dc scale) {
        Vec3 localCenter = new Vec3((localBox.minX + localBox.maxX) / 2,
                                    (localBox.minY + localBox.maxY) / 2,
                                    (localBox.minZ + localBox.maxZ) / 2);
        Vec3 worldCenter = pose.transformPosition(localCenter);
        Vec3 he = new Vec3((localBox.maxX - localBox.minX) / 2 * scale.x(),
                           (localBox.maxY - localBox.minY) / 2 * scale.y(),
                           (localBox.maxZ - localBox.minZ) / 2 * scale.z());
        return new OrientedBox(worldCenter, worldAxes[0], worldAxes[1], worldAxes[2], he);
    }

    private static Vec3[] computeWorldAxes(Pose3dc pose) {
        Quaterniondc q = pose.orientation();
        return new Vec3[]{
                rotated(q, 1, 0, 0),
                rotated(q, 0, 1, 0),
                rotated(q, 0, 0, 1),
        };
    }

    private static Vec3 rotated(Quaterniondc q, double x, double y, double z) {
        Vector3d v = new Vector3d(x, y, z);
        q.transform(v);
        return new Vec3(v.x, v.y, v.z);
    }

    private static AABB transformAABBInverse(AABB world, Pose3dc pose) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3(
                    (i & 1) == 0 ? world.minX : world.maxX,
                    (i & 2) == 0 ? world.minY : world.maxY,
                    (i & 4) == 0 ? world.minZ : world.maxZ
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
