package com.alrex.parcool.utilities;

import com.alrex.parcool.common.action.impl.HangDown;
import com.alrex.parcool.common.tags.BlockTags;
import com.alrex.parcool.compat.SableCompat;
import com.alrex.parcool.compat.SableLocalFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class WorldUtil {

	private static boolean isBlocked(Level level, AABB aabb) {
		return !level.noCollision(aabb)
			|| (SableCompat.isLoaded() && SableCompat.hasSubLevelCollision(level, aabb));
	}

	private static boolean isBlocked(Level level, Entity entity, AABB aabb) {
		return !level.noCollision(entity, aabb)
			|| (SableCompat.isLoaded() && SableCompat.hasSubLevelCollision(level, aabb));
	}

	private static AABB expandInDirection(AABB box, Vec3 dir, double distance) {
		Vec3 d = dir.normalize().scale(distance);
		return new AABB(
				Math.min(box.minX, box.minX + d.x()), Math.min(box.minY, box.minY + d.y()), Math.min(box.minZ, box.minZ + d.z()),
				Math.max(box.maxX, box.maxX + d.x()), Math.max(box.maxY, box.maxY + d.y()), Math.max(box.maxZ, box.maxZ + d.z())
		);
	}

	// Sub-level blocks live in their own Level, not the main world's chunks, so an
	// air result here might just mean "look in the sub-level instead".
	public static BlockState getBlockStateAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() && SableCompat.isLoaded()) {
			BlockState subState = SableCompat.getSubLevelBlockState(level, pos);
			if (subState != null) return subState;
		}
		return state;
	}

	public static BlockPos getClosestBlockToRelPositionFromEntityHeight(
			LivingEntity entity, Vec3 relative2DPosition, double heightFraction) {
		Vec3 entityPos = entity.position();
		double yPos = entity.getBoundingBox().minY + entity.getBbHeight() * heightFraction;
		return new BlockPos(
				(int) Math.floor(entityPos.x() + relative2DPosition.x()),
				(int) Math.floor(yPos),
				(int) Math.floor(entityPos.z() + relative2DPosition.z()));
	}

	// Used to find a wall to start/continue HWallRun on. Sweeps perpendicular ±60-120°
	// so we still find a wall when the player's body yaw hasn't caught up to it yet.
	// Snaps onto a sub-level's local axis at the end so a rotated Sable deck reports
	// its exact wall yaw (otherwise the run direction drifts off within a few ticks).
	@Nullable
	public static Vec3 getRunnableWall(LivingEntity entity, double range) {
		Vec3 facing;
		if (entity instanceof Player p) {
			facing = VectorUtil.fromYawDegree(p.yBodyRot);
		} else {
			facing = entity.getLookAngle().multiply(1, 0, 1);
			if (facing.lengthSqr() < 1e-6) return null;
			facing = facing.normalize();
		}

		Level level = entity.level();
		double width = entity.getBbWidth() * 0.4f;
		Vec3 pos = entity.position();
		List<AABB> boxes = Arrays.asList(
				new AABB(pos.x()-width, pos.y(),                          pos.z()-width, pos.x()+width, pos.y()+entity.getBbHeight()*0.3, pos.z()+width),
				new AABB(pos.x()-width, pos.y()+entity.getBbHeight()*0.85, pos.z()-width, pos.x()+width, pos.y()+entity.getBbHeight(),     pos.z()+width));

		// Bail if there's a wall directly in front. Bottom-slice only so a leaned-back
		// wall the player is running along (side wall, base-out) still passes.
		final double frontRange = entity.getBbWidth() * 0.25;
		AABB bottomBox = boxes.get(0);
		if (isBlocked(level, bottomBox.expandTowards(facing.x * frontRange, 0, facing.z * frontRange))) {
			return null;
		}

		// Bottom slice only: a leaned-back wall slopes out of horizontal probe reach
		// at head height past a few degrees of lean.
		final double[] angles = {Math.PI / 3, 5 * Math.PI / 12, Math.PI / 2, 7 * Math.PI / 12, 2 * Math.PI / 3};
		Vec3 wallSum = Vec3.ZERO;
		for (double a : angles) {
			for (int sign : new int[]{1, -1}) {
				Vec3 dir = facing.yRot((float) (sign * a));
				final double dx = dir.x * range;
				final double dz = dir.z * range;
				if (isBlocked(level, bottomBox.expandTowards(dx, 0, dz))) {
					wallSum = wallSum.add(dir);
				}
			}
		}
		if (wallSum.lengthSqr() < 1e-6) return null;
		Vec3 wall = wallSum.normalize();
		return snapToSubLevelAxis(entity, wall);
	}

	// Returns the full 3D sub-level axis closest to `wall`. Carries a Y component on a
	// tilted deck so callers (e.g. HorizontalWallRun's bonus-duration scaling) can read
	// the wall's pitch. Vanilla blocks are axis-aligned, so y=0 in the common case.
	private static Vec3 snapToSubLevelAxis(LivingEntity entity, Vec3 wall) {
		if (!SableCompat.isLoaded()) return wall;
		SableLocalFrame frame = SableLocalFrame.at(entity, 1.0);
		if (!frame.isSubLevel()) return wall;

		Vec3 best = null;
		double bestDot = 0.7; // require at least 45° horizontal alignment to snap
		for (Vec3 ax : new Vec3[]{frame.localX(), frame.localY(), frame.localZ()}) {
			Vec3 horiz = new Vec3(ax.x, 0, ax.z);
			if (horiz.lengthSqr() < 0.01) continue;
			Vec3 horizNorm = horiz.normalize();
			Vec3 axNorm = ax.normalize();
			for (int sign : new int[]{1, -1}) {
				double dot = horizNorm.scale(sign).dot(wall);
				if (dot > bestDot) {
					bestDot = dot;
					best = axNorm.scale(sign);
				}
			}
		}
		return best != null ? best : wall;
	}

	// Returns the first direction in which the player's lower-body slice is blocked.
	// Lower-body only so leaned-back walls (upper face tilted out of reach) still register.
	@Nullable
	public static Vec3 probeWall(LivingEntity entity, double range, Vec3... directions) {
		Level level = entity.level();
		final double width = entity.getBbWidth() * 0.49;
		Vec3 pos = entity.position();

		AABB bottom = new AABB(
				pos.x() - width, pos.y(),                             pos.z() - width,
				pos.x() + width, pos.y() + entity.getBbHeight() / 2.0, pos.z() + width
		);

		for (Vec3 dir : directions) {
			final double dx = dir.x * range;
			final double dz = dir.z * range;
			if (isBlocked(level, bottom.expandTowards(dx, 0, dz))) {
				return dir;
			}
		}
		return null;
	}

	// {facing, back, right, left} from the entity's look. Null if looking straight up/down.
	@Nullable
	private static Vec3[] facingRelativeDirections(LivingEntity entity) {
		Vec3 facing = entity.getLookAngle().multiply(1, 0, 1);
		if (facing.lengthSqr() < 1e-6) return null;
		facing = facing.normalize();
		return new Vec3[]{
				facing,
				facing.reverse(),
				facing.yRot((float) (-Math.PI / 2)),
				facing.yRot((float) ( Math.PI / 2))
		};
	}

	// VWR: only the wall directly in front counts as runnable.
	@Nullable
	public static Vec3 getWallInFacing(LivingEntity entity, double range) {
		Vec3[] dirs = facingRelativeDirections(entity);
		if (dirs == null) return null;
		return probeWall(entity, range, dirs[0]);
	}

	// WallJump kicks off side/back walls; skip facing so head-on collisions don't trigger.
	@Nullable
	public static Vec3 getWallNotInFacing(LivingEntity entity, double range) {
		Vec3[] dirs = facingRelativeDirections(entity);
		if (dirs == null) return null;
		return probeWall(entity, range, dirs[1], dirs[2], dirs[3]);
	}

	@Nullable
	public static Vec3 getAnyWall(LivingEntity entity, double range) {
		Vec3[] dirs = facingRelativeDirections(entity);
		if (dirs == null) return null;
		return probeWall(entity, range, dirs);
	}

	@Nullable
	public static Vec3 getVaultableStep(LivingEntity entity) {
		final double d = entity.getBbWidth() * 0.5;
		Level world = entity.getCommandSenderWorld();
		double distance = entity.getBbWidth() / 2;
		double baseLine = Math.min(entity.getBbHeight() * 0.86, getWallHeight(entity));
		double stepX = 0;
		double stepZ = 0;
		Vec3 pos = entity.position();

		AABB baseBoxBottom = new AABB(
				pos.x() - d,
				pos.y(),
				pos.z() - d,
				pos.x() + d,
				pos.y() + baseLine,
				pos.z() + d
		);
		AABB baseBoxTop = new AABB(
				pos.x() - d,
				pos.y() + baseLine,
				pos.z() - d,
				pos.x() + d,
				pos.y() + baseLine + entity.getBbHeight(),
				pos.z() + d
		);
		if (SableCompat.isLoaded()) {
			Vec3[] localAxes = SableCompat.getNearbySubLevelLocalXZAxes(world, entity.getBoundingBox().inflate(distance + 1.0));
			Vec3 lx = localAxes[0], lz = localAxes[1];
			boolean sableNearby = false;
			for (Vec3 dir : new Vec3[]{lx, lx.reverse(), lz, lz.reverse()}) {
				if (SableCompat.hasSubLevelCollision(world, expandInDirection(entity.getBoundingBox(), dir, distance + 1.0))) {
					sableNearby = true;
					break;
				}
			}
			if (sableNearby) {
				// Same baseLine as the vanilla branch so the top probe clears 1 block.
				double sableBase = entity.getBbHeight() * 0.86;
				AABB sableBoxBottom = new AABB(pos.x() - d, pos.y(),                    pos.z() - d, pos.x() + d, pos.y() + sableBase,                     pos.z() + d);
				AABB sableBoxTop    = new AABB(pos.x() - d, pos.y() + sableBase + 0.01, pos.z() - d, pos.x() + d, pos.y() + sableBase + entity.getBbHeight(), pos.z() + d);
				double sableProbe = distance * 1.5;
				Vec3 sableResult = Vec3.ZERO;
				for (Vec3 dir : new Vec3[]{lx, lx.reverse(), lz, lz.reverse()}) {
					Vec3 dirH = new Vec3(dir.x(), 0, dir.z());
					if (dirH.lengthSqr() < 1e-6) continue;
					dirH = dirH.normalize();
					if (SableCompat.hasSubLevelCollision(world, expandInDirection(sableBoxBottom, dirH, sableProbe))
							&& !SableCompat.hasSubLevelCollision(world, expandInDirection(sableBoxTop, dirH, distance + 1.8))) {
						sableResult = sableResult.add(dirH);
					}
				}
				if (sableResult.length() > 0.001) return sableResult.normalize();
			}
		}

		if (isBlocked(world, entity, baseBoxBottom.expandTowards(distance, 0, 0)) && !isBlocked(world, entity, baseBoxTop.expandTowards((distance + 1.8), 0, 0))) {
			stepX++;
		}
		if (isBlocked(world, entity, baseBoxBottom.expandTowards(-distance, 0, 0)) && !isBlocked(world, entity, baseBoxTop.expandTowards(-(distance + 1.8), 0, 0))) {
			stepX--;
		}
		if (isBlocked(world, entity, baseBoxBottom.expandTowards(0, 0, distance)) && !isBlocked(world, entity, baseBoxTop.expandTowards(0, 0, (distance + 1.8)))) {
			stepZ++;
		}
		if (isBlocked(world, entity, baseBoxBottom.expandTowards(0, 0, -distance)) && !isBlocked(world, entity, baseBoxTop.expandTowards(0, 0, -(distance + 1.8)))) {
			stepZ--;
		}
		if (stepX == 0 && stepZ == 0) return null;
		if (stepX == 0 || stepZ == 0) {
			Vec3 result = new Vec3(stepX, 0, stepZ);
			Vec3 blockPosition = entity.position().add(result).add(0, 0.5, 0);
			BlockPos target = new BlockPos(Mth.floor(blockPosition.x()), Mth.floor(blockPosition.y()), Mth.floor(blockPosition.z()));
			if (!world.isLoaded(target)) return null;
			BlockState state = getBlockStateAt(world, target);
			if (state.getBlock() instanceof StairBlock) {
				Half half = state.getValue(StairBlock.HALF);
				if (half != Half.BOTTOM) return result;
				Direction direction = state.getValue(StairBlock.FACING);
				if (stepZ > 0 && direction == Direction.SOUTH) return null;
				if (stepZ < 0 && direction == Direction.NORTH) return null;
				if (stepX > 0 && direction == Direction.EAST) return null;
				if (stepX < 0 && direction == Direction.WEST) return null;
			}
		}

		return new Vec3(stepX, 0, stepZ);
	}

	public static double getWallHeight(LivingEntity entity, Vec3 direction, double maxHeight, double accuracy) {
		direction = direction.normalize();
        Level world = entity.level();
		Vec3 pos = entity.position();
		double d = entity.getBbWidth() * 0.49;
		boolean canReturn = false;
		for (double height = 0; height < maxHeight; height += accuracy) {
			// Y epsilon: sub-level blocks can land exactly on a slice boundary and miss.
			AABB slice = new AABB(pos.x() - d, pos.y() + height - 0.01, pos.z() - d,
					pos.x() + d, pos.y() + height + accuracy + 0.01, pos.z() + d);
			AABB box = expandInDirection(slice, direction, entity.getBbWidth() * 0.65 + 0.5);
            if (isBlocked(world, entity, box)) {
				canReturn = true;
			} else {
				if (canReturn) {
					return height;
				}
			}
		}
		return maxHeight;
	}

	public static double getWallHeight(LivingEntity entity) {
		Vec3 wall = getWallInFacing(entity, entity.getBbWidth() * 0.5);
		if (wall == null) return 0;
		return getWallHeight(entity, wall, entity.getBbHeight(), entity.getBbHeight() / 18.0);
	}

	@Nullable
	public static HangDown.BarAxis getHangableBars(LivingEntity entity) {
		final double bbWidth = entity.getBbWidth() / 4;
		final double bbHeight = 0.35;
		AABB bb = new AABB(
				entity.getX() - bbWidth,
				entity.getY() + entity.getBbHeight(),
				entity.getZ() - bbWidth,
				entity.getX() + bbWidth,
				entity.getY() + entity.getBbHeight() + bbHeight,
				entity.getZ() + bbWidth
		);
		Level hangWorld = entity.getCommandSenderWorld();
		if (!isBlocked(hangWorld, entity, bb)) return null;
		BlockPos pos = new BlockPos(
				Mth.floor(entity.getX()),
				Mth.floor(entity.getY() + entity.getBbHeight() + 0.4),
				Mth.floor(entity.getZ())
		);
		if (!hangWorld.isLoaded(pos)) return null;
		BlockState state = getBlockStateAt(hangWorld, pos);
		Block block = state.getBlock();
		HangDown.BarAxis axis = null;
		if (block instanceof RotatedPillarBlock) {
			if (state.isCollisionShapeFullBlock(hangWorld, pos)) {
				return null;
			}
			Direction.Axis pillarAxis = state.getValue(RotatedPillarBlock.AXIS);
			switch (pillarAxis) {
				case X:
					axis = HangDown.BarAxis.X;
					break;
				case Z:
					axis = HangDown.BarAxis.Z;
					break;
			}
		} else if (block instanceof EndRodBlock) {
			if (state.isCollisionShapeFullBlock(hangWorld, pos)) {
				return null;
			}
			Direction direction = state.getValue(DirectionalBlock.FACING);
			switch (direction) {
				case EAST:
				case WEST:
					axis = HangDown.BarAxis.X;
					break;
				case NORTH:
				case SOUTH:
					axis = HangDown.BarAxis.Z;
			}
		} else if (block instanceof CrossCollisionBlock) {
			int zCount = 0;
			int xCount = 0;
			if (state.getValue(CrossCollisionBlock.NORTH)) zCount++;
			if (state.getValue(CrossCollisionBlock.SOUTH)) zCount++;
			if (state.getValue(CrossCollisionBlock.EAST)) xCount++;
			if (state.getValue(CrossCollisionBlock.WEST)) xCount++;
			if (zCount > 0 && xCount == 0) axis = HangDown.BarAxis.Z;
			if (xCount > 0 && zCount == 0) axis = HangDown.BarAxis.X;
		} else if (block instanceof WallBlock) {
			int zCount = 0;
			int xCount = 0;
			if (state.getValue(WallBlock.NORTH_WALL) != WallSide.NONE) zCount++;
			if (state.getValue(WallBlock.SOUTH_WALL) != WallSide.NONE) zCount++;
			if (state.getValue(WallBlock.EAST_WALL) != WallSide.NONE) xCount++;
			if (state.getValue(WallBlock.WEST_WALL) != WallSide.NONE) xCount++;
			if (zCount > 0 && xCount == 0) axis = HangDown.BarAxis.Z;
			if (xCount > 0 && zCount == 0) axis = HangDown.BarAxis.X;
		}

		return axis;
	}

    public static boolean existsSpaceBelow(LivingEntity entity) {
        Level world = entity.level();
        Vec3 center = entity.position();
        if (!world.isLoaded(new BlockPos(
				Mth.floor(center.x()),
				Mth.floor(center.y()),
				Mth.floor(center.z())
        ))) return false;
        double height = entity.getBbHeight() * 1.5;
        double width = entity.getBbWidth() * 2;
        AABB boundingBox = new AABB(
                center.x() - width,
                center.y() - 9,
                center.z() - width,
                center.x() + width,
                center.y() + height,
                center.z() + width
        );
        return !isBlocked(world, boundingBox);
    }
	public static boolean existsDivableSpace(LivingEntity entity) {
		Level world = entity.getCommandSenderWorld();
		double width = entity.getBbWidth() * 1.5;
		double height = entity.getBbHeight() * 1.5;
		double wideWidth = entity.getBbWidth() * 2;
		Vec3 center = entity.position();
        if (!world.isLoaded(new BlockPos(
				Mth.floor(center.x()),
				Mth.floor(center.y()),
				Mth.floor(center.z())
        ))) return false;
		Vec3 diveDirection = VectorUtil.fromYawDegree(entity.getYHeadRot());
		for (int i = 0; i < 4; i++) {
			Vec3 centerPoint = center.add(diveDirection.scale(width * i));
			AABB box = new AABB(
					centerPoint.x() - width,
					centerPoint.y() + 0.05,
					centerPoint.z() - width,
					centerPoint.x() + width,
					centerPoint.y() + height,
					centerPoint.z() + width
			);
			if (isBlocked(world, entity, box)) return false;
		}
		center = center.add(diveDirection.scale(4));
		AABB verticalWideBox = new AABB(
				center.x() - wideWidth,
                center.y() - 7,
				center.z() - wideWidth,
				center.x() + wideWidth,
				center.y() + height,
				center.z() + wideWidth
		);
        if (!isBlocked(world, verticalWideBox)) return true;
        BlockPos centerBlockPos = new BlockPos(
				Mth.floor(center.x()),
				Mth.floor(center.y() - 0.5),
				Mth.floor(center.z())
        );

        // check if water pool exists
        if (!world.isLoaded(centerBlockPos)) return false;
        verticalWideBox = new AABB(
                center.x() - wideWidth,
                center.y() - 2.9,
                center.z() - wideWidth,
                center.x() + wideWidth,
                center.y() + height,
                center.z() + wideWidth
        );
        int i = 0;
        int waterLevel = -1;
        for (; i < 6; i++) {
            Block block = world.getBlockState(centerBlockPos.below(i)).getBlock();
            if (block == Blocks.AIR) continue;
            if (block == Blocks.WATER) {
                waterLevel = i;
                break;
            }
            return false;
        }
        if (waterLevel == -1) return false;
        boolean filledWithWater = true;
        for (; i < waterLevel + 3; i++) {
            BlockState state = world.getBlockState(centerBlockPos.below(i));
            if (state.getBlock() != Blocks.WATER) {
                filledWithWater = false;
                break;
            }
        }
        return filledWithWater && !isBlocked(world, verticalWideBox);
	}

	// Probing only the look direction gives an exact wall yaw under any sub-level
	// orientation; sweeping every face would over-detect on tilted decks.
	@Nullable
	public static Vec3 getGrabbableWall(LivingEntity entity) {
		Vec3 facing = entity.getLookAngle().multiply(1, 0, 1);
		if (facing.lengthSqr() < 1e-6) return null;
		return getGrabbableWallInDirection(entity, facing.normalize());
	}

	// Probe a known wall direction so the cling survives looking away.
	//
	// Two reach tiers: a short probe for vertical walls, then an extended probe for
	// leaned-back walls whose face has receded out of short reach. The extended probe
	// is gated by a feet-level check so we don't false-positive across small gaps.
	@Nullable
	public static Vec3 getGrabbableWallInDirection(LivingEntity entity, Vec3 facing) {
		Level world = entity.level();
		double shortDistance = entity.getBbWidth() / 2;
		double baseLine1 = entity.getEyeHeight() + (entity.getBbHeight() - entity.getEyeHeight()) / 2;
		double baseLine2 = entity.getBbHeight() + (entity.getBbHeight() - entity.getEyeHeight()) / 2;

		Vec3 result = probeGrabbableInDirection(entity, world, facing, shortDistance, baseLine1);
		if (result != null) return result;
		result = probeGrabbableInDirection(entity, world, facing, shortDistance, baseLine2);
		if (result != null) return result;

		if (!hasWallNearFeet(entity, world, facing, shortDistance)) return null;

		double extendedDistance = entity.getBbWidth() * 1.3;
		result = probeGrabbableInDirection(entity, world, facing, extendedDistance, baseLine1);
		if (result != null) return result;
		return probeGrabbableInDirection(entity, world, facing, extendedDistance, baseLine2);
	}

	// Floor-to-shin probe. A leaned-back wall's base is its closest point, so this
	// confirms "right at the wall" when the upper probes miss the receded face.
	private static boolean hasWallNearFeet(LivingEntity entity, Level world, Vec3 facing, double distance) {
		Vec3 pos = entity.position();
		final double sideHalf = 0.05;
		double halfWidth = entity.getBbWidth() * 0.5;
		double xHalf = Math.abs(facing.x) * halfWidth + Math.abs(facing.z) * sideHalf;
		double zHalf = Math.abs(facing.z) * halfWidth + Math.abs(facing.x) * sideHalf;
		AABB feetBox = new AABB(
				pos.x() - xHalf, pos.y(),                            pos.z() - zHalf,
				pos.x() + xHalf, pos.y() + entity.getBbHeight() / 6, pos.z() + zHalf);
		double dx = facing.x * distance;
		double dz = facing.z * distance;
		return isBlocked(world, entity, feetBox.expandTowards(dx, 0, dz));
	}

	// Full-width along facing, ~0.05m perpendicular: the probe fits through a 1-wide
	// notch even when the player is off-centre. A full-width box would catch the
	// surrounding wall via the top check (~0.2m of slack on either side).
	@Nullable
	private static Vec3 probeGrabbableInDirection(LivingEntity entity, Level world, Vec3 facing,
			double distance, double baseLine) {
		Vec3 pos = entity.position();
		final double sideHalf = 0.05;
		double halfWidth = entity.getBbWidth() * 0.5;
		// Rotate the box with facing while keeping it AABB-aligned: at a cardinal facing
		// one axis gets the full half-width, the other gets sideHalf.
		double xHalf = Math.abs(facing.x) * halfWidth + Math.abs(facing.z) * sideHalf;
		double zHalf = Math.abs(facing.z) * halfWidth + Math.abs(facing.x) * sideHalf;

		AABB baseBoxSide = new AABB(
				pos.x() - xHalf, pos.y() + baseLine - entity.getBbHeight() / 6, pos.z() - zHalf,
				pos.x() + xHalf, pos.y() + baseLine,                              pos.z() + zHalf);
		AABB baseBoxTop  = new AABB(
				pos.x() - xHalf, pos.y() + baseLine,                              pos.z() - zHalf,
				pos.x() + xHalf, pos.y() + entity.getBbHeight(),                  pos.z() + zHalf);

		double dx = facing.x * distance;
		double dz = facing.z * distance;
		if (!isBlocked(world, entity, baseBoxSide.expandTowards(dx, 0, dz))) return null;
		if (isBlocked(world, entity, baseBoxTop.expandTowards(dx, 0, dz)))   return null;

		// Slick walls (ice) shouldn't be grabbable.
		BlockPos wallBlock = new BlockPos(
				Mth.floor(entity.getX() + facing.x * (halfWidth + 0.1)),
				Mth.floor(entity.getBoundingBox().minY + baseLine - 0.3),
				Mth.floor(entity.getZ() + facing.z * (halfWidth + 0.1))
		);
		if (!world.isLoaded(wallBlock)) return null;
		float slipperiness = getBlockStateAt(world, wallBlock).getFriction(world, wallBlock, entity);
		return slipperiness <= 0.9 ? facing : null;
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
}
