package com.alrex.parcool.compat;

import com.alrex.parcool.utilities.probe.BarInfo;
import com.alrex.parcool.utilities.probe.CollisionSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

public final class SableCompat {
    private static final boolean LOADED = ModList.get().isLoaded("sable");

    private SableCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    @Nullable
    public static CollisionSource collisionSourceFor(Level level) {
        if (!LOADED) return null;
        return new SableCollisionSource(level);
    }

    public static Vec3 subLevelVelocityAt(Level level, Vec3 worldPos) {
        if (!LOADED) return Vec3.ZERO;
        return SableCompatImpl.velocityAt(level, worldPos);
    }

    public static void applySubLevelTracking(Entity entity, double inflateRange) {
        if (!LOADED) return;
        SableCompatImpl.applySubLevelTracking(entity, inflateRange);
    }

    @Nullable
    public static BlockState subLevelBlockStateAt(Level level, BlockPos worldPos) {
        if (!LOADED) return null;
        return SableCompatImpl.subLevelBlockStateAt(level, worldPos);
    }

    @Nullable
    public static BarInfo barInfoOverhead(Level level, BlockPos worldPos, LivingEntity entity) {
        if (!LOADED) return null;
        return SableCompatImpl.barInfoOverhead(level, worldPos, entity);
    }
}
