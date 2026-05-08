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
        return SableCompatImpl.hasSubLevelCollision(level, aabb);
    }

    @Nullable
    public static BlockState getSubLevelBlockState(Level level, BlockPos pos) {
        return SableCompatImpl.getSubLevelBlockState(level, pos);
    }

    @Nullable
    public static BlockState getSubLevelFloorBlockState(Level level, Vec3 worldFootPos) {
        if (!LOADED) return null;
        return SableCompatImpl.getSubLevelFloorBlockState(level, worldFootPos);
    }

    @Nullable
    public static SubLevelHandle firstSubLevelInRange(Level level, AABB searchAABB) {
        if (!LOADED) return null;
        return SableCompatImpl.firstInRange(level, searchAABB);
    }

    public static Vec3 worldToLocal(@Nullable SubLevelHandle handle, Vec3 worldPos) {
        if (!LOADED || handle == null) return worldPos;
        return SableCompatImpl.worldToLocal(handle, worldPos);
    }

    public static Vec3 localDirectionToWorld(@Nullable SubLevelHandle handle, Vec3 localDir) {
        if (!LOADED || handle == null) return localDir;
        return SableCompatImpl.localDirectionToWorld(handle, localDir);
    }

    public static boolean hasLocalCollision(@Nullable SubLevelHandle handle, AABB localAABB) {
        if (!LOADED || handle == null) return false;
        return SableCompatImpl.hasLocalCollision(handle, localAABB);
    }

    public static AABB worldToLocalAABB(@Nullable SubLevelHandle handle, AABB worldAABB) {
        if (!LOADED || handle == null) return worldAABB;
        return SableCompatImpl.worldToLocalAABB(handle, worldAABB);
    }

    public static double getSubLevelSlopeInDirection(Level level, Vec3 worldPos, Vec3 horizontalDir) {
        if (!LOADED) return 0.0;
        return SableCompatImpl.getSubLevelSlopeInDirection(level, worldPos, horizontalDir);
    }

    public static Vec3[] getNearbySubLevelLocalXZAxes(Level level, AABB searchAABB) {
        if (!LOADED) return new Vec3[]{new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        return SableCompatImpl.getNearbySubLevelLocalXZAxes(level, searchAABB);
    }

    public static Vec3 getSubLevelDisplacementAt(Level level, AABB searchAABB, Vec3 pos) {
        if (!LOADED) return Vec3.ZERO;
        return SableCompatImpl.getSubLevelDisplacementAt(level, searchAABB, pos);
    }

    public static Vec3 getSubLevelTrackedPosition(Level level, AABB searchAABB, Vec3 pos) {
        if (!LOADED) return pos;
        return SableCompatImpl.getSubLevelTrackedPosition(level, searchAABB, pos);
    }

    public static void applySubLevelTracking(Entity entity, double inflateRange) {
        if (!LOADED) return;
        Vec3 pos = entity.position();
        Vec3 tracked = SableCompatImpl.getSubLevelTrackedPosition(entity.level(),
                entity.getBoundingBox().inflate(inflateRange), pos);
        if (!tracked.equals(pos)) entity.setPos(tracked.x(), tracked.y(), tracked.z());
    }
}
