package com.alrex.parcool.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// World-space basis and per-tick displacement of the sub-level under an entity.
// WORLD is the identity frame; returned when Sable isn't loaded or no sub-level
// is found, so call sites can use the result unconditionally.
public record SableLocalFrame(
        Vec3 localX, Vec3 localY, Vec3 localZ,
        Vec3 displacement,
        boolean isSubLevel
) {
    public static final SableLocalFrame WORLD = new SableLocalFrame(
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), Vec3.ZERO, false);

    public static SableLocalFrame at(Entity entity, double inflateRange) {
        if (!SableCompat.isLoaded()) return WORLD;
        SableLocalFrame frame = SableCompatImpl.buildFrameAt(
                entity.level(), entity.getBoundingBox().inflate(inflateRange), entity.position());
        return frame != null ? frame : WORLD;
    }

    public double verticalComponent(Vec3 worldVec) {
        return worldVec.dot(localY);
    }

    public Vec3 projectOntoFloor(Vec3 worldVec) {
        return worldVec.subtract(localY.scale(worldVec.dot(localY)));
    }

    public Vec3 applyDisplacement(Vec3 delta) {
        return delta.add(displacement);
    }

    // Local axis closest to world-up. Picks the axis with the largest |dot(worldUp)|
    // and flips it if needed so the result really does point upward.
    public Vec3 uprightAxis() {
        final Vec3 worldUp = new Vec3(0, 1, 0);
        double px = localX.dot(worldUp);
        double py = localY.dot(worldUp);
        double pz = localZ.dot(worldUp);
        Vec3 axis;     double proj;
        if      (Math.abs(px) >= Math.abs(py) && Math.abs(px) >= Math.abs(pz)) { axis = localX; proj = px; }
        else if (Math.abs(py) >= Math.abs(pz))                                  { axis = localY; proj = py; }
        else                                                                    { axis = localZ; proj = pz; }
        return proj < 0 ? axis.scale(-1) : axis;
    }
}
