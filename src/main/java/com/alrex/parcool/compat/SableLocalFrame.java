package com.alrex.parcool.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Bundles a Sable sub-level's local axes (in world space) and per-tick displacement so
// actions can compose motion relative to the sub-level's frame instead of assuming world-Y
// is gravity.  The WORLD singleton is the identity frame (localY = world-Y, zero displacement),
// returned when Sable isn't loaded or the entity isn't near a sub-level — call sites then use
// the same helpers unconditionally.
public record SableLocalFrame(
        Vec3 localX, Vec3 localY, Vec3 localZ,
        Vec3 displacement,
        boolean isSubLevel
) {
    public static final SableLocalFrame WORLD = new SableLocalFrame(
            new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), Vec3.ZERO, false);

    // Gates on isLoaded() so the vanilla build never touches a Sable class.
    public static SableLocalFrame at(Entity entity, double inflateRange) {
        if (!SableCompat.isLoaded()) return WORLD;
        SableLocalFrame frame = SableCompatImpl.buildFrameAt(
                entity.level(), entity.getBoundingBox().inflate(inflateRange), entity.position());
        return frame != null ? frame : WORLD;
    }

    // worldVec · localY — scalar "up" component in the sub-level's frame.
    public double verticalComponent(Vec3 worldVec) {
        return worldVec.dot(localY);
    }

    // Removes the localY component — leaves the vector lying in the sub-level's floor plane.
    public Vec3 projectOntoFloor(Vec3 worldVec) {
        return worldVec.subtract(localY.scale(worldVec.dot(localY)));
    }

    // Adds the sub-level's this-tick motion so the entity co-moves with a moving sub-level.
    public Vec3 applyDisplacement(Vec3 delta) {
        return delta.add(displacement);
    }
}
