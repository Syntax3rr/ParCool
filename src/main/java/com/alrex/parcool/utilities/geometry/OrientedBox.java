package com.alrex.parcool.utilities.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class OrientedBox {
    public final Vec3 center;
    public final Vec3 axisX, axisY, axisZ;
    public final Vec3 halfExtents;

    public OrientedBox(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ, Vec3 halfExtents) {
        this.center = center;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
        this.halfExtents = halfExtents;
    }

    public static OrientedBox fromAABB(AABB aabb) {
        Vec3 center = new Vec3((aabb.minX + aabb.maxX) / 2, (aabb.minY + aabb.maxY) / 2, (aabb.minZ + aabb.maxZ) / 2);
        Vec3 he = new Vec3((aabb.maxX - aabb.minX) / 2, (aabb.maxY - aabb.minY) / 2, (aabb.maxZ - aabb.minZ) / 2);
        return new OrientedBox(center, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), he);
    }

    public AABB toEnclosingAABB() {
        double absX = Math.abs(axisX.x) * halfExtents.x + Math.abs(axisY.x) * halfExtents.y + Math.abs(axisZ.x) * halfExtents.z;
        double absY = Math.abs(axisX.y) * halfExtents.x + Math.abs(axisY.y) * halfExtents.y + Math.abs(axisZ.y) * halfExtents.z;
        double absZ = Math.abs(axisX.z) * halfExtents.x + Math.abs(axisY.z) * halfExtents.y + Math.abs(axisZ.z) * halfExtents.z;
        return new AABB(center.x - absX, center.y - absY, center.z - absZ,
                        center.x + absX, center.y + absY, center.z + absZ);
    }

    public boolean intersects(OrientedBox other) {
        Vec3[] axesA = {axisX, axisY, axisZ};
        Vec3[] axesB = {other.axisX, other.axisY, other.axisZ};
        for (Vec3 a : axesA) if (separates(a, this, other)) return false;
        for (Vec3 b : axesB) if (separates(b, this, other)) return false;
        for (Vec3 a : axesA) {
            for (Vec3 b : axesB) {
                Vec3 cross = a.cross(b);
                if (cross.lengthSqr() < 1e-10) continue;
                if (separates(cross, this, other)) return false;
            }
        }
        return true;
    }

    public boolean intersects(AABB aabb) {
        return intersects(fromAABB(aabb));
    }

    private static boolean separates(Vec3 axis, OrientedBox a, OrientedBox b) {
        double ra = a.halfExtents.x * Math.abs(axis.dot(a.axisX))
                  + a.halfExtents.y * Math.abs(axis.dot(a.axisY))
                  + a.halfExtents.z * Math.abs(axis.dot(a.axisZ));
        double rb = b.halfExtents.x * Math.abs(axis.dot(b.axisX))
                  + b.halfExtents.y * Math.abs(axis.dot(b.axisY))
                  + b.halfExtents.z * Math.abs(axis.dot(b.axisZ));
        double dist = Math.abs(axis.dot(b.center.subtract(a.center)));
        return dist > ra + rb;
    }

    // Outward normal of the OBB face whose center is closest to outsidePoint.
    public Vec3 surfaceNormalToward(Vec3 outsidePoint) {
        Vec3 toPt = outsidePoint.subtract(center);
        double dx = toPt.dot(axisX), dy = toPt.dot(axisY), dz = toPt.dot(axisZ);
        double rx = halfExtents.x > 0 ? Math.abs(dx) / halfExtents.x : 0;
        double ry = halfExtents.y > 0 ? Math.abs(dy) / halfExtents.y : 0;
        double rz = halfExtents.z > 0 ? Math.abs(dz) / halfExtents.z : 0;
        if (rx >= ry && rx >= rz) return dx >= 0 ? axisX : axisX.reverse();
        if (ry >= rz) return dy >= 0 ? axisY : axisY.reverse();
        return dz >= 0 ? axisZ : axisZ.reverse();
    }
}
