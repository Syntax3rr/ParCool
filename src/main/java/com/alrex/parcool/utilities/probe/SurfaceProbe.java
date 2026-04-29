package com.alrex.parcool.utilities.probe;

import com.alrex.parcool.utilities.geometry.OrientedBox;
import com.alrex.parcool.utilities.geometry.Slabs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class SurfaceProbe {
    private SurfaceProbe() {}

    public static List<SurfaceContact> findHorizontalContacts(Entity entity, double range,
                                                              double slabMinYOffset, double slabMaxYOffset,
                                                              Predicate<SurfaceContact> filter) {
        AABB body = entity.getBoundingBox();
        Vec3 bodyCenter = new Vec3((body.minX + body.maxX) / 2,
                                   (body.minY + body.maxY) / 2,
                                   (body.minZ + body.maxZ) / 2);
        AABB region = body.inflate(range + 0.5);
        CompositeCollisionSource source = CompositeCollisionSource.defaultFor(entity.level());

        List<SurfaceContact> contacts = new ArrayList<>();
        for (OrientedBox candidate : source.candidatesIn(region, entity)) {
            Vec3 toCandidate = candidate.center.subtract(bodyCenter);
            double horizLenSq = toCandidate.x * toCandidate.x + toCandidate.z * toCandidate.z;
            if (horizLenSq < 1e-6) continue;
            Vec3 dir = new Vec3(toCandidate.x, 0, toCandidate.z).scale(1.0 / Math.sqrt(horizLenSq));

            OrientedBox slab = Slabs.buildHorizontalSlab(body, dir, range, slabMinYOffset, slabMaxYOffset);
            if (!slab.intersects(candidate)) continue;

            Vec3 normal = candidate.surfaceNormalToward(bodyCenter);
            SurfaceContact contact = new SurfaceContact(candidate, dir, normal);
            if (filter.test(contact)) contacts.add(contact);
        }
        return contacts;
    }

    @Nullable
    public static Vec3 dominantDirection(List<SurfaceContact> contacts) {
        if (contacts.isEmpty()) return null;
        Vec3 sum = Vec3.ZERO;
        for (SurfaceContact c : contacts) sum = sum.add(c.directionToCandidate());
        return sum.lengthSqr() < 1e-6 ? null : sum;
    }

    // Returns true if any candidate within `range` in direction `dir` intersects the slab.
    public static boolean isBlockedInDirection(Entity entity, Vec3 dir, double range,
                                               double slabMinYOffset, double slabMaxYOffset) {
        AABB body = entity.getBoundingBox();
        AABB region = body.inflate(range + 0.5);
        CompositeCollisionSource source = CompositeCollisionSource.defaultFor(entity.level());
        OrientedBox slab = Slabs.buildHorizontalSlab(body, dir, range, slabMinYOffset, slabMaxYOffset);
        for (OrientedBox candidate : source.candidatesIn(region, entity)) {
            if (slab.intersects(candidate)) return true;
        }
        return false;
    }

    // Generic blocked-AABB check used by space/dive/hide queries; combines vanilla and Sable
    // collision through the same composite source.
    public static boolean isBlocked(Entity entity, AABB region) {
        CompositeCollisionSource source = CompositeCollisionSource.defaultFor(entity.level());
        for (OrientedBox candidate : source.candidatesIn(region, entity)) {
            if (candidate.intersects(region)) return true;
        }
        return false;
    }
}
