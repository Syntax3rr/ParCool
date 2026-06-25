package com.alrex.parcool.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

public class SableCompat {
    private static final boolean LOADED = ModList.get().isLoaded("sable");

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean hasSubLevelCollision(Level level, AABB aabb) {
        if (!LOADED) return false;
        return SableCompatImpl.hasSubLevelCollision(level, aabb);
    }

    @Nullable
    public static BlockState getSubLevelBlockState(Level level, BlockPos pos) {
        if (!LOADED) return null;
        return SableCompatImpl.getSubLevelBlockState(level, pos);
    }

    @Nullable
    public static BlockState getSubLevelFloorBlockState(Level level, Vec3 worldFootPos) {
        if (!LOADED) return null;
        return SableCompatImpl.getSubLevelFloorBlockState(level, worldFootPos);
    }

    public static double getSubLevelSlopeInDirection(Level level, Vec3 worldPos, Vec3 horizontalDir) {
        if (!LOADED) return 0.0;
        return SableCompatImpl.getSubLevelSlopeInDirection(level, worldPos, horizontalDir);
    }

    public static Vec3[] getNearbySubLevelLocalXZAxes(Level level, AABB searchAABB) {
        if (!LOADED) return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        return SableCompatImpl.getNearbySubLevelLocalXZAxes(level, searchAABB);
    }

    public static void applySubLevelTracking(Entity entity, double inflateRange) {
        if (!LOADED) return;
        SableCompatImpl.applySubLevelTracking(entity, inflateRange);
    }
}
