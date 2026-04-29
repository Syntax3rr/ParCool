package com.alrex.parcool.utilities.probe;

import com.alrex.parcool.utilities.geometry.OrientedBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

public interface CollisionSource {
    Iterable<OrientedBox> candidatesIn(AABB region, @Nullable Entity entity);
}
