package com.alrex.parcool.utilities.probe;

import com.alrex.parcool.utilities.geometry.OrientedBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record SurfaceContact(OrientedBox candidate, Vec3 directionToCandidate, Vec3 surfaceNormal) {
    public double tilt() { return surfaceNormal.y; }
    public AABB worldBounds() { return candidate.toEnclosingAABB(); }
}
