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
import java.util.LinkedList;
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

	// Falls back to a Sable sub-level lookup when the world block is air — sub-level
	// blocks don't exist in the main world's chunk data.
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

	// Horizontal wall-run.
	//
	// Probing strategy:
	//   1. Front rejection at a short range so a side wall the player just glances
	//      toward doesn't trigger; only walls directly in the path do.
	//   2. Angular sweep around perpendicular (±60° through ±120° in 15° steps) so a
	//      yawed wall is found even when body yaw hasn't yet aligned with the wall's
	//      run direction.  Sum the blocked probes — the sum approximates the wall's
	//      side relative to the player.
	//   3. Snap the result to the nearest sub-level local horizontal axis when one
	//      is within 45° (cos > 0.7).  This recovers the *exact* wall yaw for Sable
	//      sub-levels — without this the action's run direction would be 15-45° off
	//      from the actual wall and the player drifts off within a few ticks.
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

		// Front rejection.  A leaned-back wall's base is the closest part to the
		// player, so check the bottom slice only — that catches "running into" a
		// leaned wall (its base blocks the lower body) without false-rejecting
		// "running along" a leaned wall (the side wall is in a perpendicular
		// direction, so a short forward extension doesn't reach it).
		final double frontRange = entity.getBbWidth() * 0.25;
		AABB bottomBox = boxes.get(0);
		if (isBlocked(level, bottomBox.expandTowards(facing.x * frontRange, 0, facing.z * frontRange))) {
			return null;
		}

		// Angular sweep around perpendicular; ±60° through ±120° from facing.
		final double[] angles = {Math.PI / 3, 5 * Math.PI / 12, Math.PI / 2, 7 * Math.PI / 12, 2 * Math.PI / 3};
		Vec3 wallSum = Vec3.ZERO;
		for (double a : angles) {
			for (int sign : new int[]{1, -1}) {
				Vec3 dir = facing.yRot((float) (sign * a));
				final double dx = dir.x * range;
				final double dz = dir.z * range;
				if (boxes.stream().allMatch(box -> isBlocked(level, box.expandTowards(dx, 0, dz)))) {
					wallSum = wallSum.add(dir);
				}
			}
		}
		if (wallSum.lengthSqr() < 1e-6) return null;
		Vec3 wall = wallSum.normalize();
		return snapToSubLevelAxis(entity, wall);
	}

	// Snap to the sub-level local axis whose horizontal projection best matches `wall`,
	// then return the full 3D axis (signed to agree with `wall` horizontally).  The
	// returned vector is the actual wall normal: on a tilted sub-level it carries a Y
	// component, so callers (e.g. HorizontalWallRun's bonus-duration scaling) can read
	// the wall's pitch.  Vanilla blocks are axis-aligned, so the no-Sable / non-tilted
	// case still produces a y=0 result.
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

	// Action-specific wall probes share this helper: returns the first horizontal
	// direction (from `directions`) where both the lower and upper player-body slices
	// are blocked when expanded by `range`.  Each action picks its own directions
	// (facing, perpendicular, opposite, etc.) so the returned wall normal follows the
	// actual wall yaw instead of being snapped to world cardinals.
	@Nullable
	public static Vec3 probeWall(LivingEntity entity, double range, Vec3... directions) {
		Level level = entity.level();
		final double width = entity.getBbWidth() * 0.49;
		Vec3 pos = entity.position();

		List<AABB> boxes = new LinkedList<>();
		final int division = 2;
		double singleYHeight = entity.getBbHeight() / division;
		for (int i = 0; i < division; i++) {
			boxes.add(new AABB(
					pos.x() - width, pos.y() + singleYHeight * i,       pos.z() - width,
					pos.x() + width, pos.y() + singleYHeight * (i + 1), pos.z() + width
			));
		}

		for (Vec3 dir : directions) {
			final double dx = dir.x * range;
			final double dz = dir.z * range;
			if (boxes.stream().allMatch(box -> isBlocked(level, box.expandTowards(dx, 0, dz)))) {
				return dir;
			}
		}
		return null;
	}

	// Convenience: returns the four directions {facing, back, right, left} relative to
	// the entity's horizontal look direction, or null if the look angle has no horizontal
	// component (entity looking straight up/down).
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

	// VWR: player runs up the wall in front of them — probe only the facing direction.
	@Nullable
	public static Vec3 getWallInFacing(LivingEntity entity, double range) {
		Vec3[] dirs = facingRelativeDirections(entity);
		if (dirs == null) return null;
		return probeWall(entity, range, dirs[0]);
	}

	// WallJump: the player kicks off a wall they aren't looking at (back, right, left).
	// Skipping the facing direction prevents head-on wall collisions from triggering jumps.
	@Nullable
	public static Vec3 getWallNotInFacing(LivingEntity entity, double range) {
		Vec3[] dirs = facingRelativeDirections(entity);
		if (dirs == null) return null;
		return probeWall(entity, range, dirs[1], dirs[2], dirs[3]);
	}

	// WallSlide: any wall touching the player triggers a slide.
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
				// Match the vanilla baseLine cap so the top probe clears a 1-block obstacle.
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
			// Y epsilon so sub-level blocks landing exactly on a slice boundary still match.
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
		// Vault uses the wall in front of the player.
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

	@Nullable
	// Cling-to-cliff probes only in the direction the player is looking: the action
	// requires the player to face the wall anyway, so a single facing-direction probe
	// gives exact wall yaw under any sub-level orientation without the over-detection
	// problem of probing every sub-level face.
	public static Vec3 getGrabbableWall(LivingEntity entity) {
		Vec3 facing = entity.getLookAngle().multiply(1, 0, 1);
		if (facing.lengthSqr() < 1e-6) return null;
		facing = facing.normalize();

		Level world = entity.level();
		double distance = entity.getBbWidth() / 2;
		double baseLine1 = entity.getEyeHeight() + (entity.getBbHeight() - entity.getEyeHeight()) / 2;
		double baseLine2 = entity.getBbHeight() + (entity.getBbHeight() - entity.getEyeHeight()) / 2;

		Vec3 result = probeGrabbableInDirection(entity, world, facing, distance, baseLine1);
		if (result != null) return result;
		return probeGrabbableInDirection(entity, world, facing, distance, baseLine2);
	}

	@Nullable
	// Probe is full-width along the facing axis but narrow (~0.05m) perpendicular,
	// so it slips through a 1-block-wide notch even when the player isn't centered
	// on it.  A full-width box would catch the surrounding wall via the top check
	// (the slack on either side of a 1-wide notch is only ~0.2m).
	private static Vec3 probeGrabbableInDirection(LivingEntity entity, Level world, Vec3 facing,
			double distance, double baseLine) {
		Vec3 pos = entity.position();
		final double sideHalf = 0.05;
		double halfWidth = entity.getBbWidth() * 0.5;
		// Mix facing- and perpendicular-axis halves into world x/z so the probe rotates
		// with facing while staying axis-aligned.  At a cardinal facing, one axis is the
		// full half-width and the other is sideHalf.
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

		// Friction check: sample the block one half-width past the entity along the facing direction.
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
