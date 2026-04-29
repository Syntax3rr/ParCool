package com.alrex.parcool.utilities.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record BarInfo(Vec3 axis, BlockPos blockPos) {}
