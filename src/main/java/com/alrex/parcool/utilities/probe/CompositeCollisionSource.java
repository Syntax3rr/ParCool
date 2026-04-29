package com.alrex.parcool.utilities.probe;

import com.alrex.parcool.compat.SableCompat;
import com.alrex.parcool.utilities.geometry.OrientedBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class CompositeCollisionSource implements CollisionSource {
    private final List<CollisionSource> sources;

    public CompositeCollisionSource(List<CollisionSource> sources) {
        this.sources = sources;
    }

    public static CompositeCollisionSource defaultFor(Level level) {
        List<CollisionSource> sources = new ArrayList<>();
        sources.add(new VanillaCollisionSource(level));
        CollisionSource sable = SableCompat.collisionSourceFor(level);
        if (sable != null) sources.add(sable);
        return new CompositeCollisionSource(sources);
    }

    @Override
    public Iterable<OrientedBox> candidatesIn(AABB region, @Nullable Entity entity) {
        List<OrientedBox> out = new ArrayList<>();
        for (CollisionSource s : sources) {
            for (OrientedBox box : s.candidatesIn(region, entity)) out.add(box);
        }
        return out;
    }
}
