package com.alrex.parcool.utilities;

import com.alrex.parcool.common.tags.BlockTags;
import com.alrex.parcool.compat.SableCompat;
import com.alrex.parcool.utilities.probe.BarInfo;
import com.alrex.parcool.utilities.probe.SurfaceContact;
import com.alrex.parcool.utilities.probe.SurfaceProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class WorldUtil {

	@Nullable
	public static Vec3 getRunnableWall(LivingEntity entity, double range) {
		AABB body = entity.getBoundingBox();
		double h = entity.getBbHeight();
		double bottomMark = body.minY + 0.3 * h;
		double topMark = body.minY + 0.85 * h;
		List<SurfaceContact> contacts = SurfaceProbe.findHorizontalContacts(entity, range, 0, h, c -> {
			double ny = c.tilt();
			if (ny < -0.85 || ny > 0.15) return false;
			AABB cb = c.worldBounds();
			return cb.minY <= bottomMark && cb.maxY >= topMark;
		});
		return SurfaceProbe.dominantDirection(contacts);
	}

	@Nullable
	public static Vec3 getWall(LivingEntity entity) {
		return getWall(entity, entity.getBbWidth() * 0.5);
	}

	@Nullable
	public static Vec3 getWall(LivingEntity entity, double range) {
		double h = entity.getBbHeight();
		List<SurfaceContact> contacts = SurfaceProbe.findHorizontalContacts(entity, range, 0, h, c -> {
			double ny = c.tilt();
			return ny >= -0.95 && ny <= 0.95;
		});
		return SurfaceProbe.dominantDirection(contacts);
	}

	@Nullable
	public static Vec3 getVaultableStep(LivingEntity entity) {
		AABB body = entity.getBoundingBox();
		double h = entity.getBbHeight();
		double distance = entity.getBbWidth() / 2;
		double baseLine = Math.min(h * 0.86, getWallHeight(entity));

		double topLimit = body.minY + baseLine + 0.05;
		List<SurfaceContact> contacts = SurfaceProbe.findHorizontalContacts(entity, distance, 0, baseLine, c -> {
			AABB cb = c.worldBounds();
			return cb.maxY <= topLimit;
		});
		Vec3 dir = SurfaceProbe.dominantDirection(contacts);
		if (dir == null) return null;
		Vec3 dirN = dir.normalize();
		boolean clearAbove = !SurfaceProbe.isBlockedInDirection(entity, dirN, distance + 1.8, baseLine + 0.01, baseLine + h);
		if (!clearAbove) return null;

		// Stair-block special case: a top-half stair facing toward us has no landing surface.
		Vec3 blockPosCenter = entity.position().add(dirN.scale(0.5 + distance)).add(0, 0.5, 0);
		BlockPos target = new BlockPos(Mth.floor(blockPosCenter.x), Mth.floor(blockPosCenter.y), Mth.floor(blockPosCenter.z));
		if (entity.level().isLoaded(target)) {
			BlockState state = entity.level().getBlockState(target);
			if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
				Direction face = state.getValue(StairBlock.FACING);
				double dx = dirN.x, dz = dirN.z;
				if (dz > 0.5 && face == Direction.SOUTH) return null;
				if (dz < -0.5 && face == Direction.NORTH) return null;
				if (dx > 0.5 && face == Direction.EAST) return null;
				if (dx < -0.5 && face == Direction.WEST) return null;
			}
		}
		return dir;
	}

	public static double getWallHeight(LivingEntity entity, Vec3 direction, double maxHeight, double accuracy) {
		Vec3 dir = direction.normalize();
		double range = entity.getBbWidth() * 0.65 + 0.5;
		boolean canReturn = false;
		for (double height = 0; height < maxHeight; height += accuracy) {
			boolean blocked = SurfaceProbe.isBlockedInDirection(entity, dir, range, height, height + accuracy);
			if (blocked) canReturn = true;
			else if (canReturn) return height;
		}
		return maxHeight;
	}

	public static double getWallHeight(LivingEntity entity) {
		Vec3 wall = getWall(entity);
		if (wall == null) return 0;
		return getWallHeight(entity, wall, entity.getBbHeight(), entity.getBbHeight() / 18);
	}

	@Nullable
	public static BarInfo getHangableBars(LivingEntity entity) {
		double bbWidth = entity.getBbWidth() / 4;
		double overheadHeight = 0.35;
		AABB probeRegion = new AABB(
				entity.getX() - bbWidth, entity.getY() + entity.getBbHeight(), entity.getZ() - bbWidth,
				entity.getX() + bbWidth, entity.getY() + entity.getBbHeight() + overheadHeight, entity.getZ() + bbWidth
		);
		if (!SurfaceProbe.isBlocked(entity, probeRegion)) return null;

		BlockPos worldPos = new BlockPos(
				Mth.floor(entity.getX()),
				Mth.floor(entity.getY() + entity.getBbHeight() + 0.4),
				Mth.floor(entity.getZ())
		);
		Level level = entity.level();
		if (level.isLoaded(worldPos)) {
			BlockState state = level.getBlockState(worldPos);
			if (!state.isAir()) {
				Vec3 axis = barAxisFromBlock(state, level, worldPos);
				if (axis == null) return null;
				return new BarInfo(axis, worldPos);
			}
		}
		if (SableCompat.isLoaded()) return SableCompat.barInfoOverhead(level, worldPos, entity);
		return null;
	}

	@Nullable
	public static Vec3 barAxisFromBlock(BlockState state, Level level, BlockPos pos) {
		Block block = state.getBlock();
		if (block instanceof RotatedPillarBlock) {
			if (state.isCollisionShapeFullBlock(level, pos)) return null;
			return switch (state.getValue(RotatedPillarBlock.AXIS)) {
				case X -> new Vec3(1, 0, 0);
				case Z -> new Vec3(0, 0, 1);
				default -> null;
			};
		}
		if (block instanceof EndRodBlock) {
			if (state.isCollisionShapeFullBlock(level, pos)) return null;
			return switch (state.getValue(DirectionalBlock.FACING)) {
				case EAST, WEST -> new Vec3(1, 0, 0);
				case NORTH, SOUTH -> new Vec3(0, 0, 1);
				default -> null;
			};
		}
		if (block instanceof CrossCollisionBlock) {
			int xCount = 0, zCount = 0;
			if (state.getValue(CrossCollisionBlock.NORTH)) zCount++;
			if (state.getValue(CrossCollisionBlock.SOUTH)) zCount++;
			if (state.getValue(CrossCollisionBlock.EAST)) xCount++;
			if (state.getValue(CrossCollisionBlock.WEST)) xCount++;
			if (zCount > 0 && xCount == 0) return new Vec3(0, 0, 1);
			if (xCount > 0 && zCount == 0) return new Vec3(1, 0, 0);
			return null;
		}
		if (block instanceof WallBlock) {
			int xCount = 0, zCount = 0;
			if (state.getValue(WallBlock.NORTH_WALL) != WallSide.NONE) zCount++;
			if (state.getValue(WallBlock.SOUTH_WALL) != WallSide.NONE) zCount++;
			if (state.getValue(WallBlock.EAST_WALL) != WallSide.NONE) xCount++;
			if (state.getValue(WallBlock.WEST_WALL) != WallSide.NONE) xCount++;
			if (zCount > 0 && xCount == 0) return new Vec3(0, 0, 1);
			if (xCount > 0 && zCount == 0) return new Vec3(1, 0, 0);
			return null;
		}
		return null;
	}

	public static boolean existsSpaceBelow(LivingEntity entity) {
		Level world = entity.level();
		Vec3 center = entity.position();
		BlockPos at = new BlockPos(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
		if (!world.isLoaded(at)) return false;
		double height = entity.getBbHeight() * 1.5;
		double width = entity.getBbWidth() * 2;
		AABB box = new AABB(
				center.x - width, center.y - 9, center.z - width,
				center.x + width, center.y + height, center.z + width
		);
		return !SurfaceProbe.isBlocked(entity, box);
	}

	public static boolean existsDivableSpace(LivingEntity entity) {
		Level world = entity.level();
		double width = entity.getBbWidth() * 1.5;
		double height = entity.getBbHeight() * 1.5;
		double wideWidth = entity.getBbWidth() * 2;
		Vec3 center = entity.position();
		BlockPos at = new BlockPos(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
		if (!world.isLoaded(at)) return false;
		Vec3 diveDirection = VectorUtil.fromYawDegree(entity.getYHeadRot());
		for (int i = 0; i < 4; i++) {
			Vec3 centerPoint = center.add(diveDirection.scale(width * i));
			AABB box = new AABB(
					centerPoint.x - width, centerPoint.y + 0.05, centerPoint.z - width,
					centerPoint.x + width, centerPoint.y + height, centerPoint.z + width
			);
			if (SurfaceProbe.isBlocked(entity, box)) return false;
		}
		Vec3 farCenter = center.add(diveDirection.scale(4));
		AABB verticalWideBox = new AABB(
				farCenter.x - wideWidth, farCenter.y - 7, farCenter.z - wideWidth,
				farCenter.x + wideWidth, farCenter.y + height, farCenter.z + wideWidth
		);
		if (!SurfaceProbe.isBlocked(entity, verticalWideBox)) return true;

		BlockPos centerBlockPos = new BlockPos(Mth.floor(farCenter.x), Mth.floor(farCenter.y - 0.5), Mth.floor(farCenter.z));
		if (!world.isLoaded(centerBlockPos)) return false;
		AABB waterColumn = new AABB(
				farCenter.x - wideWidth, farCenter.y - 2.9, farCenter.z - wideWidth,
				farCenter.x + wideWidth, farCenter.y + height, farCenter.z + wideWidth
		);
		int i = 0;
		int waterLevel = -1;
		for (; i < 6; i++) {
			Block block = world.getBlockState(centerBlockPos.below(i)).getBlock();
			if (block == Blocks.AIR) continue;
			if (block == Blocks.WATER) { waterLevel = i; break; }
			return false;
		}
		if (waterLevel == -1) return false;
		boolean filledWithWater = true;
		for (; i < waterLevel + 3; i++) {
			BlockState state = world.getBlockState(centerBlockPos.below(i));
			if (state.getBlock() != Blocks.WATER) { filledWithWater = false; break; }
		}
		return filledWithWater && !SurfaceProbe.isBlocked(entity, waterColumn);
	}

	@Nullable
	public static Vec3 getGrabbableWall(LivingEntity entity) {
		double h = entity.getBbHeight();
		double eye = entity.getEyeHeight();
		double baseLine1 = eye + (h - eye) / 2;
		double baseLine2 = h + (h - eye) / 2;
		Vec3 wall1 = getGrabbableWall(entity, baseLine1, h);
		if (wall1 != null) return wall1;
		return getGrabbableWall(entity, baseLine2, h);
	}

	@Nullable
	private static Vec3 getGrabbableWall(LivingEntity entity, double baseLine, double h) {
		AABB body = entity.getBoundingBox();
		double range = entity.getBbWidth() / 2;
		double sideMin = baseLine - h / 6;
		double sideMax = baseLine;
		double topLimit = body.minY + baseLine + 0.05;

		List<SurfaceContact> contacts = SurfaceProbe.findHorizontalContacts(entity, range, sideMin, sideMax, c -> {
			double ny = c.tilt();
			if (ny < -0.15 || ny > 0.85) return false;
			return c.worldBounds().maxY <= topLimit;
		});
		Vec3 dir = SurfaceProbe.dominantDirection(contacts);
		if (dir == null) return null;

		Vec3 dirN = dir.normalize();
		BlockPos blockPos = new BlockPos(
				Mth.floor(entity.getX() + dirN.x * (range + 0.1)),
				Mth.floor(entity.getBoundingBox().minY + baseLine - 0.3),
				Mth.floor(entity.getZ() + dirN.z * (range + 0.1))
		);
		Level level = entity.level();
		if (!level.isLoaded(blockPos)) return null;
		BlockState state = level.getBlockState(blockPos);
		if (state.isAir() && SableCompat.isLoaded()) {
			BlockState sub = SableCompat.subLevelBlockStateAt(level, blockPos);
			if (sub != null) state = sub;
		}
		float slip = state.getFriction(level, blockPos, entity);
		return slip <= 0.9 ? dir : null;
	}

	public static boolean isHideAbleBlock(BlockState blockState) {
		return blockState.getTags().anyMatch(it -> it.equals(BlockTags.HIDE_ABLE));
	}

	private static boolean getHideAbleSpace$isHideAble(Level world, Block block, BlockPos pos) {
		return world.isLoaded(pos) && world.getBlockState(pos).is(block) && world.getBlockState(pos.above()).isAir();
	}

	@Nullable
	public static Tuple<BlockPos, BlockPos> getHideAbleSpace(Entity entity, BlockPos base) {
		var world = entity.level();
		if (!world.isLoaded(base)) return null;
		BlockState state = world.getBlockState(base);
		Block block = state.getBlock();
		if (!isHideAbleBlock(state)) return null;
		if (!world.getBlockState(base.above()).isAir()) {
			if (getHideAbleSpace$isHideAble(world, block, base.above())) {
				return new Tuple<>(base, base.above());
			}
			return null;
		}
		double entityWidth = entity.getBbWidth();
		double entityHeight = entity.getBbHeight();
		if (entityHeight >= 2 || entityWidth >= 1) return null;
		if (entityHeight < 1) return new Tuple<>(base, base);
		var lookAngle = entity.getLookAngle();
		if (Math.abs(lookAngle.z()) > Math.abs(lookAngle.x())) {
			if (lookAngle.z() > 0) {
				if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
				if (lookAngle.x() > 0) {
					if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
					if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
				} else {
					if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
					if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
				}
				if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
			} else {
				if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
				if (lookAngle.x() > 0) {
					if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
					if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
				} else {
					if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
					if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
				}
				if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
			}
		} else {
			if (lookAngle.x() > 0) {
				if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
				if (lookAngle.z() > 0) {
					if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
					if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
				} else {
					if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
					if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
				}
				if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
			} else {
				if (getHideAbleSpace$isHideAble(world, block, base.west())) return new Tuple<>(base, base.west());
				if (lookAngle.z() > 0) {
					if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
					if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
				} else {
					if (getHideAbleSpace$isHideAble(world, block, base.north())) return new Tuple<>(base, base.north());
					if (getHideAbleSpace$isHideAble(world, block, base.south())) return new Tuple<>(base, base.south());
				}
				if (getHideAbleSpace$isHideAble(world, block, base.east())) return new Tuple<>(base, base.east());
			}
		}
		if (world.getBlockState(base.below()).is(block) && Math.abs(entity.getY() - base.below().getY()) < 0.2) {
			return new Tuple<>(base.below(), base);
		}
		return null;
	}

	public static Vec3 subLevelDisplacementAt(LivingEntity entity) {
		return SableCompat.subLevelVelocityAt(entity.level(), entity.position());
	}
}
