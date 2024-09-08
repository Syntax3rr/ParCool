package com.alrex.parcool.utilities;

import com.alrex.parcool.common.action.impl.HangDown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class WorldUtil {

	/// <summary>
	/// Get the closest block to the relative position from the entity's height.
	/// </summary>
	/// <param name="entity">The entity to check.</param>
	/// <param name="relative2DPosition">The relative position to check. (We drop the Y coordinate)</param>
	/// <param name="heightFraction">The percentage of the player's height</param>
	/// <returns>The closest block to the relative position from the entity's height.</returns>
	public static BlockPos getClosestBlockToRelPositionFromEntityHeight(LivingEntity entity, Vec3 relative2DPosition, double heightFraction) {
		Vec3 entityPos = entity.position();
		double yPos = entity.getBoundingBox().minY + entity.getBbHeight() * heightFraction;

        return new BlockPos(
                (int) Math.floor(entityPos.x() + relative2DPosition.x()),
                (int) Math.floor(entityPos.y() + yPos),
                (int) Math.floor(entityPos.z() + relative2DPosition.z()));
	}

	public static Vec3 getRunnableWall(LivingEntity entity, double range) {
		double width = entity.getBbWidth() * 0.4f;
		double wallX = 0;
		double wallZ = 0;
		Vec3 pos = entity.position();

        AABB baseBox1 = new AABB(
				pos.x() - width,
				pos.y(),
				pos.z() - width,
				pos.x() + width,
                pos.y() + entity.getBbHeight() / 1.63,
                pos.z() + width
        );
        AABB baseBox2 = new AABB(
                pos.x() - width,
                pos.y() + entity.getBbHeight() / 1.63,
                pos.z() - width,
                pos.x() + width,
				pos.y() + entity.getBbHeight(),
				pos.z() + width
		);

        if (!entity.level().noCollision(baseBox1.expandTowards(range, 0, 0))
                && !entity.level().noCollision(baseBox2.expandTowards(range, 0, 0))
        ) {
			wallX++;
		}
        if (!entity.level().noCollision(baseBox1.expandTowards(-range, 0, 0))
                && !entity.level().noCollision(baseBox2.expandTowards(-range, 0, 0))
        ) {
			wallX--;
		}
        if (!entity.level().noCollision(baseBox1.expandTowards(0, 0, range))
                && !entity.level().noCollision(baseBox2.expandTowards(0, 0, range))
        ) {
			wallZ++;
		}
        if (!entity.level().noCollision(baseBox1.expandTowards(0, 0, -range))
                && !entity.level().noCollision(baseBox1.expandTowards(0, 0, -range))
        ) {
			wallZ--;
		}
		if (wallX == 0 && wallZ == 0) return null;

		return new Vec3(wallX, 0, wallZ);
	}

	@Nullable
	public static Vec3 getWall(LivingEntity entity) {
		double range = entity.getBbWidth() / 2;
        final double width = entity.getBbWidth() * 0.49;
		double wallX = 0;
		double wallZ = 0;
		Vec3 pos = entity.position();

		AABB baseBox = new AABB(
				pos.x() - width,
				pos.y(),
				pos.z() - width,
				pos.x() + width,
				pos.y() + entity.getBbHeight(),
				pos.z() + width
		);

		if (!entity.getCommandSenderWorld().noCollision(entity, baseBox.expandTowards(range, 0, 0))) {
			wallX++;
		}
		if (!entity.getCommandSenderWorld().noCollision(entity, baseBox.expandTowards(-range, 0, 0))) {
			wallX--;
		}
		if (!entity.getCommandSenderWorld().noCollision(entity, baseBox.expandTowards(0, 0, range))) {
			wallZ++;
		}
		if (!entity.getCommandSenderWorld().noCollision(entity, baseBox.expandTowards(0, 0, -range))) {
			wallZ--;
		}
		if (wallX == 0 && wallZ == 0) return null;

		return new Vec3(wallX, 0, wallZ);
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
		if (!world.noCollision(entity, baseBoxBottom.expandTowards(distance, 0, 0)) && world.noCollision(entity, baseBoxTop.expandTowards((distance + 1.8), 0, 0))) {
			stepX++;
		}
		if (!world.noCollision(entity, baseBoxBottom.expandTowards(-distance, 0, 0)) && world.noCollision(entity, baseBoxTop.expandTowards(-(distance + 1.8), 0, 0))) {
			stepX--;
		}
		if (!world.noCollision(entity, baseBoxBottom.expandTowards(0, 0, distance)) && world.noCollision(entity, baseBoxTop.expandTowards(0, 0, (distance + 1.8)))) {
			stepZ++;
		}
		if (!world.noCollision(entity, baseBoxBottom.expandTowards(0, 0, -distance)) && world.noCollision(entity, baseBoxTop.expandTowards(0, 0, -(distance + 1.8)))) {
			stepZ--;
		}
		if (stepX == 0 && stepZ == 0) return null;
		if (stepX == 0 || stepZ == 0) {
			Vec3 result = new Vec3(stepX, 0, stepZ);
			Vec3 blockPosition = entity.position().add(result).add(0, 0.5, 0);
            BlockPos target = new BlockPos(new Vec3i((int) Math.floor(blockPosition.x()), (int) Math.floor(blockPosition.y()), (int) Math.floor(blockPosition.z())));
			if (!world.isLoaded(target)) return null;
			BlockState state = world.getBlockState(target);
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
        final double d = entity.getBbWidth() * 0.49;
		direction = direction.normalize();
		Level world = entity.getCommandSenderWorld();
		Vec3 pos = entity.position();
		boolean canReturn = false;
		for (double height = 0; height < maxHeight; height += accuracy) {
			AABB box = new AABB(
                    pos.x() + d + (direction.x() > 0 ? 1 : 0),
                    pos.y() + height,
                    pos.z() + d + (direction.z() > 0 ? 1 : 0),
                    pos.x() - d + (direction.x() < 0 ? -1 : 0),
                    pos.y() + height + accuracy,
                    pos.z() - d + (direction.z() < 0 ? -1 : 0)
			);
			if (!world.noCollision(entity, box)) {
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
		Level world = entity.getCommandSenderWorld();
		final double accuracy = entity.getBbHeight() / 18; // normally about 0.1
		final double d = entity.getBbWidth() * 0.5;
		int loopNum = (int) Math.round(entity.getBbHeight() / accuracy);
		Vec3 pos = entity.position();
		boolean canReturn = false;
		for (int i = 0; i < loopNum; i++) {
			AABB box = new AABB(
                    pos.x() + d + (wall.x() > 0 ? 1 : 0),
                    pos.y() + accuracy * i,
                    pos.z() + d + (wall.z() > 0 ? 1 : 0),
                    pos.x() - d + (wall.x() < 0 ? -1 : 0),
                    pos.y() + accuracy * (i + 1),
                    pos.z() - d + (wall.z() < 0 ? -1 : 0)
			);

			if (!world.noCollision(entity, box)) {
				canReturn = true;
			} else {
				if (canReturn) return accuracy * i;
			}
		}
		return entity.getBbHeight();
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
		if (entity.getCommandSenderWorld().noCollision(entity, bb)) return null;
		BlockPos pos = new BlockPos(
				(int) Math.floor(entity.getX()),
				(int) Math.floor(entity.getY() + entity.getBbHeight() + 0.4),
				(int) Math.floor(entity.getZ())
		);
		if (!entity.getCommandSenderWorld().isLoaded(pos)) return null;
		BlockState state = entity.getCommandSenderWorld().getBlockState(pos);
		Block block = state.getBlock();
		HangDown.BarAxis axis = null;
		if (block instanceof RotatedPillarBlock) {
			if (state.isCollisionShapeFullBlock(entity.getCommandSenderWorld(), pos)) {
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
		} else if (block instanceof DirectionalBlock) {
			if (state.isCollisionShapeFullBlock(entity.getCommandSenderWorld(), pos)) {
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
                (int) Math.floor(center.x()),
                (int) Math.floor(center.y()),
                (int) Math.floor(center.z())
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
        return world.noCollision(boundingBox);
    }
	public static boolean existsDivableSpace(LivingEntity entity) {
		Level world = entity.getCommandSenderWorld();
		double width = entity.getBbWidth() * 1.5;
		double height = entity.getBbHeight() * 1.5;
		double wideWidth = entity.getBbWidth() * 2;
		Vec3 center = entity.position();
        if (!world.isLoaded(new BlockPos(
                (int) Math.floor(center.x()),
                (int) Math.floor(center.y()),
                (int) Math.floor(center.z())
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
			if (!world.noCollision(entity, box)) return false;
		}
		center = center.add(diveDirection.scale(4));
		AABB verticalWideBox = new AABB(
				center.x() - wideWidth,
				center.y() - 9,
				center.z() - wideWidth,
				center.x() + wideWidth,
				center.y() + height,
				center.z() + wideWidth
		);
        if (world.noCollision(verticalWideBox)) return true;
        BlockPos centerBlockPos = new BlockPos(
                (int) Math.floor(center.x()),
                (int) Math.floor(center.y() - 0.5),
                (int) Math.floor(center.z())
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
        return filledWithWater && world.noCollision(verticalWideBox);
	}

	@Nullable
	public static Vec3 getGrabbableWall(LivingEntity entity) {
		final double d = entity.getBbWidth() * 0.5;
		Level world = entity.getCommandSenderWorld();
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
					(int) (entity.getX() + xDirection),
					(int) (entity.getBoundingBox().minY + baseLine - 0.3),
					(int) entity.getZ()
			);
			BlockPos blockPos2 = new BlockPos(
					(int) entity.getX(),
					(int) (entity.getBoundingBox().minY + baseLine - 0.3),
					(int) (entity.getZ() + zDirection)
			);
			if (!entity.getCommandSenderWorld().isLoaded(blockPos1)) return null;
			if (!entity.getCommandSenderWorld().isLoaded(blockPos2)) return null;
			slipperiness = Math.min(
					entity.getCommandSenderWorld().getBlockState(blockPos1).getFriction(entity.getCommandSenderWorld(), blockPos1, entity),
					entity.getCommandSenderWorld().getBlockState(blockPos2).getFriction(entity.getCommandSenderWorld(), blockPos2, entity)
			);
		} else {
			BlockPos blockPos = new BlockPos(
					(int) (entity.getX() + xDirection),
					(int) (entity.getBoundingBox().minY + baseLine - 0.3),
					(int) (entity.getZ() + zDirection)
			);
			if (!entity.getCommandSenderWorld().isLoaded(blockPos)) return null;
			slipperiness = entity.getCommandSenderWorld().getBlockState(blockPos).getFriction(entity.getCommandSenderWorld(), blockPos, entity);
		}
		return slipperiness <= 0.9 ? new Vec3(xDirection, 0, zDirection) : null;
	}
}
