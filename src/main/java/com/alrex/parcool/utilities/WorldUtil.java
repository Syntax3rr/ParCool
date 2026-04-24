package com.alrex.parcool.utilities;

import com.alrex.parcool.common.action.impl.HangDown;
import com.alrex.parcool.common.tags.BlockTags;
import com.alrex.parcool.compat.SableCompat;
import com.alrex.parcool.compat.SubLevelHandle;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class WorldUtil {

	// Returns true when an AABB is physically blocked — either by vanilla world geometry
	// or by a sable sub-level block at an arbitrary orientation.
	private static boolean isBlocked(Level level, AABB aabb) {
		return !level.noCollision(aabb)
			|| (SableCompat.isLoaded() && SableCompat.hasSubLevelCollision(level, aabb));
	}

	private static boolean isBlocked(Level level, Entity entity, AABB aabb) {
		return !level.noCollision(entity, aabb)
			|| (SableCompat.isLoaded() && SableCompat.hasSubLevelCollision(level, aabb));
	}

	// Expands an AABB in an arbitrary (possibly non-axis-aligned) direction by distance.
	// Equivalent to expandTowards but works for diagonal/3D directions.
	private static AABB expandInDirection(AABB box, Vec3 dir, double distance) {
		Vec3 d = dir.normalize().scale(distance);
		return new AABB(
				Math.min(box.minX, box.minX + d.x()), Math.min(box.minY, box.minY + d.y()), Math.min(box.minZ, box.minZ + d.z()),
				Math.max(box.maxX, box.maxX + d.x()), Math.max(box.maxY, box.maxY + d.y()), Math.max(box.maxZ, box.maxZ + d.z())
		);
	}

	// Returns the BlockState at a world position, checking sable sub-levels when the
	// vanilla block is air (sub-level blocks are not in the main world's chunk data).
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

	@Nullable
	public static Vec3 getRunnableWall(LivingEntity entity, double range) {
		Level level = entity.level();

		// Probe in the sub-level's local coordinate system: treat the player as upright in
		// local (local-Y is up), build hitbox-sized boxes around their local position, and
		// query the sub-level's own Level directly with hasLocalCollision.  This avoids the
		// axis-aligned bound-enlargement that transformAABBToLocal introduces for rotated
		// sub-levels and makes probe geometry match what the player would actually reach
		// when standing on that sub-level (relevant for side-flipped sub-levels where the
		// local floor is a world-vertical wall).
		if (SableCompat.isLoaded()) {
			SubLevelHandle handle = SableCompat.firstSubLevelInRange(level, entity.getBoundingBox().inflate(range + 0.5));
			if (handle != null) {
				Vec3 pLocal = SableCompat.worldToLocal(handle, entity.position());
				double w = entity.getBbWidth() * 0.4;
				double h = entity.getBbHeight();
				AABB localBot = new AABB(pLocal.x-w, pLocal.y,          pLocal.z-w, pLocal.x+w, pLocal.y+h*0.30, pLocal.z+w);
				AABB localTop = new AABB(pLocal.x-w, pLocal.y+h*0.85,   pLocal.z-w, pLocal.x+w, pLocal.y+h,      pLocal.z+w);
				double probe = range + 0.15;
				int xDir = 0, zDir = 0;
				for (int[] p : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
					AABB sideExp = localBot.expandTowards(p[0]*probe, 0, p[1]*probe);
					AABB topExp  = localTop.expandTowards(p[0]*probe, 0, p[1]*probe);
					if (SableCompat.hasLocalCollision(handle, sideExp)
							&& SableCompat.hasLocalCollision(handle, topExp)) {
						xDir += p[0]; zDir += p[1];
					}
				}
				if (xDir != 0 || zDir != 0) {
					Vec3 worldDir = SableCompat.localDirectionToWorld(handle, new Vec3(xDir, 0, zDir));
					Vec3 horiz = new Vec3(worldDir.x(), 0, worldDir.z());
					// Walls whose world direction is purely world-vertical can't round-trip
					// through the downstream 2-double storage — skip gracefully.
					if (horiz.lengthSqr() >= 1e-4) {
						return horiz.normalize().scale(Math.sqrt(xDir*xDir + zDir*zDir));
					}
				}
			}
		}

		// Vanilla world-axis detection (vanilla blocks only — sable already handled above).
		double width = entity.getBbWidth() * 0.4f;
		double wallX = 0;
		double wallZ = 0;
		Vec3 pos = entity.position();

		List<AABB> boxes = Arrays.asList(
				new AABB(
						pos.x() - width,
						pos.y(),
						pos.z() - width,
						pos.x() + width,
						pos.y() + entity.getBbHeight() * 0.3,
						pos.z() + width
				),
				new AABB(
						pos.x() - width,
						pos.y() + entity.getBbHeight() * 0.85,
						pos.z() - width,
						pos.x() + width,
						pos.y() + entity.getBbHeight(),
						pos.z() + width
				)
		);

		if (boxes.stream().allMatch(box -> isBlocked(level, box.expandTowards(range, 0, 0))))  wallX++;
		if (boxes.stream().allMatch(box -> isBlocked(level, box.expandTowards(-range, 0, 0)))) wallX--;
		if (boxes.stream().allMatch(box -> isBlocked(level, box.expandTowards(0, 0, range))))  wallZ++;
		if (boxes.stream().allMatch(box -> isBlocked(level, box.expandTowards(0, 0, -range)))) wallZ--;
		if (wallX != 0 || wallZ != 0) return new Vec3(wallX, 0, wallZ);

		return null;
	}

	@Nullable
	public static Vec3 getWall(LivingEntity entity) {
		return getWall(entity, entity.getBbWidth() * 0.5);
	}

	@Nullable
	public static Vec3 getWall(LivingEntity entity, double range) {
		Level levelW = entity.level();

		// Same local-frame pattern as getRunnableWall: probe boxes built in the sub-level's
		// local coordinates so rotation doesn't enlarge the query volume.
		if (SableCompat.isLoaded()) {
			SubLevelHandle handle = SableCompat.firstSubLevelInRange(levelW, entity.getBoundingBox().inflate(range + 0.5));
			if (handle != null) {
				Vec3 pLocal = SableCompat.worldToLocal(handle, entity.position());
				double wL = entity.getBbWidth() * 0.49;
				double h = entity.getBbHeight();
				double halfH = h / 2.0;
				AABB localBot = new AABB(pLocal.x-wL, pLocal.y,         pLocal.z-wL, pLocal.x+wL, pLocal.y+halfH, pLocal.z+wL);
				AABB localTop = new AABB(pLocal.x-wL, pLocal.y+halfH,   pLocal.z-wL, pLocal.x+wL, pLocal.y+h,     pLocal.z+wL);
				double probe = range + 0.15;
				int xDir = 0, zDir = 0;
				for (int[] p : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
					AABB sideExp = localBot.expandTowards(p[0]*probe, 0, p[1]*probe);
					AABB topExp  = localTop.expandTowards(p[0]*probe, 0, p[1]*probe);
					if (SableCompat.hasLocalCollision(handle, sideExp)
							&& SableCompat.hasLocalCollision(handle, topExp)) {
						xDir += p[0]; zDir += p[1];
					}
				}
				if (xDir != 0 || zDir != 0) {
					Vec3 worldDir = SableCompat.localDirectionToWorld(handle, new Vec3(xDir, 0, zDir));
					Vec3 horiz = new Vec3(worldDir.x(), 0, worldDir.z());
					if (horiz.lengthSqr() >= 1e-4) {
						return horiz.normalize().scale(Math.sqrt(xDir*xDir + zDir*zDir));
					}
				}
			}
		}

		// Vanilla world-axis detection.
		final double width = entity.getBbWidth() * 0.49;
		double wallX = 0;
		double wallZ = 0;
		Vec3 pos = entity.position();

		List<AABB> boxes = new LinkedList<>();
		final int division = 2;
		double singleYHeight = entity.getBbHeight() / division;
		for (int i = 0; i < division; i++) {
			boxes.add(new AABB(
					pos.x() - width,
					pos.y() + singleYHeight * i,
					pos.z() - width,
					pos.x() + width,
					pos.y() + singleYHeight * (i + 1),
					pos.z() + width
			));
		}

		if (boxes.stream().allMatch(box -> isBlocked(levelW, box.expandTowards(range, 0, 0))))  wallX++;
		if (boxes.stream().allMatch(box -> isBlocked(levelW, box.expandTowards(-range, 0, 0)))) wallX--;
		if (boxes.stream().allMatch(box -> isBlocked(levelW, box.expandTowards(0, 0, range))))  wallZ++;
		if (boxes.stream().allMatch(box -> isBlocked(levelW, box.expandTowards(0, 0, -range)))) wallZ--;
		if (wallX != 0 || wallZ != 0) return new Vec3(wallX, 0, wallZ);

		return null;
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
		// Sable-first: probe in local axis directions so sloped sub-level walls don't fool
		// world-axis probes into looking like vaultable steps.
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
				// Use bbHeight*0.86 as the split (matching the vanilla cap on baseLine) so the
				// top probe starts above the top of a standard 1-block obstacle.  The 0.5×
				// split was too low: a 1-block obstacle extends past 0.9, putting its upper
				// portion inside the top probe and blocking the vault.
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
				// When the Sable path finds a vault direction use it; otherwise fall through
				// to the vanilla path (which also calls isBlocked and handles sublevel blocks).
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
			// Build a thin horizontal slice at this height, then expand toward the wall.
			// Expand by a small epsilon in Y so that sub-level blocks whose top/bottom face
			// lands exactly on a slice boundary (due to transformAABBToLocal float drift)
			// are still found rather than falling between adjacent slices.
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
		Vec3 wall = getWall(entity);
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
	public static Vec3 getGrabbableWall(LivingEntity entity) {
		final double d = entity.getBbWidth() * 0.5;
        Level world = entity.level();
		double distance = entity.getBbWidth() / 2;
		double baseLine1 = entity.getEyeHeight() + (entity.getBbHeight() - entity.getEyeHeight()) / 2;
		double baseLine2 = entity.getBbHeight() + (entity.getBbHeight() - entity.getEyeHeight()) / 2;
		Vec3 wall1 = getGrabbableWall(entity, distance, baseLine1);
		if (wall1 != null) return wall1;
		return getGrabbableWall(entity, distance, baseLine2);
	}

	private static Vec3 getGrabbableWall(LivingEntity entity, double distance, double baseLine) {
		final double d = entity.getBbWidth() * 0.49;
		Level world = entity.getCommandSenderWorld();
		Vec3 pos = entity.position();
		AABB baseBoxSide = new AABB(
				pos.x() - d,
				pos.y() + baseLine - entity.getBbHeight() / 6,
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
				pos.y() + entity.getBbHeight(),
				pos.z() + d
		);

		// Probe in the sub-level's local coordinate system: treat the player as upright in
		// local (local-Y is up) and build arm-reach + head boxes around their local position.
		// Queries go directly to the sub-level's Level via hasLocalCollision — no world→local
		// AABB bound enlargement from rotation, so "every block is a cliff" false positives
		// and close-range rotated-sub-level failures are both resolved.  Cliff semantics
		// (side blocked && top clear) are interpreted in the sub-level's frame: a cliff is
		// a lip in the sub-level's geometry, regardless of world orientation.
		if (SableCompat.isLoaded()) {
			SubLevelHandle handle = SableCompat.firstSubLevelInRange(world, entity.getBoundingBox().inflate(distance + 1.0));
			if (handle != null) {
				Vec3 pLocal = SableCompat.worldToLocal(handle, entity.position());
				double h = entity.getBbHeight();
				AABB localSide = new AABB(pLocal.x-d, pLocal.y+baseLine-h/6, pLocal.z-d, pLocal.x+d, pLocal.y+baseLine, pLocal.z+d);
				AABB localTop  = new AABB(pLocal.x-d, pLocal.y+baseLine,     pLocal.z-d, pLocal.x+d, pLocal.y+h,        pLocal.z+d);
				// Top-clear probe must reach at least as far as the side probe.  With an
				// asymmetric range (side reaches sideProbe=0.45, top reaches distance=0.3),
				// a player at gap 0.30-0.45 from a tall wall gets side hit (reaches wall) +
				// top clear (falls short of wall) → spurious cliff for every block in the band.
				double sideProbe = distance * 1.5;
				int xDir = 0, zDir = 0;
				for (int[] p : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
					AABB sideExp = localSide.expandTowards(p[0]*sideProbe, 0, p[1]*sideProbe);
					AABB topExp  = localTop.expandTowards(p[0]*sideProbe, 0, p[1]*sideProbe);
					if (SableCompat.hasLocalCollision(handle, sideExp)
							&& !SableCompat.hasLocalCollision(handle, topExp)) {
						xDir += p[0]; zDir += p[1];
					}
				}
				if (xDir != 0 || zDir != 0) {
					Vec3 worldDir = SableCompat.localDirectionToWorld(handle, new Vec3(xDir, 0, zDir));
					Vec3 horiz = new Vec3(worldDir.x(), 0, worldDir.z());
					// Walls whose world direction is purely world-vertical can't round-trip
					// through the downstream 2-double clingWallDirection storage — skip.
					if (horiz.lengthSqr() >= 1e-4) {
						Vec3 result = horiz.normalize().scale(Math.sqrt(xDir*xDir + zDir*zDir));
						Vec3 wallStep = result.normalize();
						BlockPos wallPos = new BlockPos(
								Mth.floor(pos.x() + wallStep.x() * (d + 0.1)),
								Mth.floor(entity.getBoundingBox().minY + baseLine - 0.3),
								Mth.floor(pos.z() + wallStep.z() * (d + 0.1))
						);
						float slip = getBlockStateAt(world, wallPos).getFriction(world, wallPos, entity);
						return slip <= 0.9 ? result : null;
					}
				}
			}
		}

		// Vanilla world-axis detection.
		int xDirection = 0;
		int zDirection = 0;

		if (!world.noCollision(entity, baseBoxSide.expandTowards(distance, 0, 0)) && world.noCollision(entity, baseBoxTop.expandTowards(distance, 0, 0)))
			xDirection++;
		if (!world.noCollision(entity, baseBoxSide.expandTowards(-distance, 0, 0)) && world.noCollision(entity, baseBoxTop.expandTowards(-distance, 0, 0)))
			xDirection--;
		if (!world.noCollision(entity, baseBoxSide.expandTowards(0, 0, distance)) && world.noCollision(entity, baseBoxTop.expandTowards(0, 0, distance)))
			zDirection++;
		if (!world.noCollision(entity, baseBoxSide.expandTowards(0, 0, -distance)) && world.noCollision(entity, baseBoxTop.expandTowards(0, 0, -distance)))
			zDirection--;
		if (xDirection == 0 && zDirection == 0) {
			return null;
		}
		float slipperiness;
		if (xDirection != 0 && zDirection != 0) {
			BlockPos blockPos1 = new BlockPos(
					Mth.floor(entity.getX() + xDirection),
					Mth.floor(entity.getBoundingBox().minY + baseLine - 0.3),
					Mth.floor(entity.getZ())
			);
			BlockPos blockPos2 = new BlockPos(
					Mth.floor(entity.getX()),
					Mth.floor(entity.getBoundingBox().minY + baseLine - 0.3),
					Mth.floor(entity.getZ() + zDirection)
			);
			if (!world.isLoaded(blockPos1)) return null;
			if (!world.isLoaded(blockPos2)) return null;
			slipperiness = Math.min(
					getBlockStateAt(world, blockPos1).getFriction(world, blockPos1, entity),
					getBlockStateAt(world, blockPos2).getFriction(world, blockPos2, entity)
			);
		} else {
			double blockX = entity.getX() + xDirection, blockZ = entity.getZ() + zDirection;
			BlockPos blockPos = new BlockPos(
                    Mth.floor(blockX),
                    Mth.floor(entity.getBoundingBox().minY + baseLine - 0.3),
                    Mth.floor(blockZ)
			);
			if (!world.isLoaded(blockPos)) return null;
			if (getBlockStateAt(world, blockPos).isAir()) {
				if (xDirection != 0) {
					blockZ = blockZ + Math.signum((blockZ - Math.floor(blockZ)) - 0.5);
				} else {
					blockX = blockX + Math.signum((blockX - Math.floor(blockX)) - 0.5);
				}
				blockPos = new BlockPos(
                        Mth.floor(blockX),
                        Mth.floor(entity.getBoundingBox().minY + baseLine - 0.3),
                        Mth.floor(blockZ)
				);
			}
			slipperiness = getBlockStateAt(world, blockPos).getFriction(world, blockPos, entity);
		}
		return slipperiness <= 0.9 ? new Vec3(xDirection, 0, zDirection) : null;
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
