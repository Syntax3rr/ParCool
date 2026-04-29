package com.alrex.parcool.utilities.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Slabs {
    private Slabs() {}

    private static final Vec3 WORLD_Y = new Vec3(0, 1, 0);

    // Builds an OrientedBox that sits just outside the body's face in `worldDir`, extending
    // `range` further out. Y span is set to [bodyMinY + minYOffset, bodyMinY + maxYOffset]
    // so callers can probe specific height bands (e.g. body-top for cliff edge detection).
    public static OrientedBox buildHorizontalSlab(AABB body, Vec3 worldDir, double range,
                                                  double minYOffset, double maxYOffset) {
        Vec3 dir = horizontalize(worldDir);
        Vec3 perp = WORLD_Y.cross(dir).normalize();

        Vec3 bodyCenter = new Vec3((body.minX + body.maxX) / 2, 0, (body.minZ + body.maxZ) / 2);
        double bodyHalfX = (body.maxX - body.minX) / 2;
        double bodyHalfZ = (body.maxZ - body.minZ) / 2;

        // Distance from body center to the body face along `dir` (axis-aligned body projection).
        double bodyHalfInDir = bodyHalfX * Math.abs(dir.x) + bodyHalfZ * Math.abs(dir.z);
        double slabHalfThickness = range / 2;
        Vec3 slabCenter = bodyCenter.add(dir.scale(bodyHalfInDir + slabHalfThickness));

        // Width perpendicular to dir: project body onto perp. Same projection formula.
        double bodyHalfInPerp = bodyHalfX * Math.abs(perp.x) + bodyHalfZ * Math.abs(perp.z);

        double yMin = body.minY + minYOffset;
        double yMax = body.minY + maxYOffset;
        double yCenter = (yMin + yMax) / 2;
        double halfY = (yMax - yMin) / 2;

        Vec3 center = new Vec3(slabCenter.x, yCenter, slabCenter.z);
        Vec3 he = new Vec3(slabHalfThickness, halfY, bodyHalfInPerp);
        return new OrientedBox(center, dir, WORLD_Y, perp, he);
    }

    private static Vec3 horizontalize(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        double len = h.length();
        if (len < 1e-9) throw new IllegalArgumentException("worldDir has no horizontal component");
        return h.scale(1.0 / len);
    }
}
