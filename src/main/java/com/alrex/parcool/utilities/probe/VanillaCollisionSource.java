package com.alrex.parcool.utilities.probe;

import com.alrex.parcool.utilities.geometry.OrientedBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class VanillaCollisionSource implements CollisionSource {
    private final Level level;

    public VanillaCollisionSource(Level level) {
        this.level = level;
    }

    @Override
    public Iterable<OrientedBox> candidatesIn(AABB region, @Nullable Entity entity) {
        List<OrientedBox> out = new ArrayList<>();
        for (VoxelShape shape : level.getBlockCollisions(entity, region)) {
            for (AABB box : shape.toAabbs()) {
                out.add(OrientedBox.fromAABB(box));
            }
        }
        return out;
    }
}
