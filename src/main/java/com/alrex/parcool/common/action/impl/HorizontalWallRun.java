package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.HorizontalWallRunAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.attachment.Attachments;
import com.alrex.parcool.common.attachment.client.Animation;
import com.alrex.parcool.common.attachment.common.Parkourability;
import com.alrex.parcool.common.info.ActionInfo;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.compat.SableLocalFrame;
import com.alrex.parcool.utilities.BufferUtil;
import com.alrex.parcool.utilities.MovementUtil;
import com.alrex.parcool.utilities.VectorUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.ByteBuffer;

public class HorizontalWallRun extends Action {
    public enum ControlType {
        PressKey, Auto
    }
	private int coolTime = 0;
	private float bodyYaw = 0;

	private int getMaxRunningTick(ActionInfo info) {
        Integer value = info.getClientSetting().get(ParCoolConfig.Client.Integers.WallRunContinuableTick);
        if (value == null) value = ParCoolConfig.Client.Integers.WallRunContinuableTick.DefaultValue;
        int base = Math.min(value, info.getServerLimitation().get(ParCoolConfig.Server.Integers.MaxWallRunContinuableTick));
        // Scale ticks with the wall's upward facing: wallY < 0 means the player→wall vector
        // points downward, i.e. the surface normal points more upward — a wall tilted away
        // from the player (steep ramp). These are easier to maintain so they get more time.
        if (runningWallDirection != null) {
            double wallY = runningWallDirection.normalize().y();
            if (wallY < 0) base = (int) Math.min(base * 3.0, base * (1.0 - wallY * 4.0));
        }
        return base;
	}

	private boolean wallIsRightward = false;
	private Vec3 runningWallDirection = null;
	private Vec3 runningDirection = null;

	@Override
    public void onClientTick(Player player, Parkourability parkourability) {
		if (coolTime > 0) coolTime--;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public void onWorkingTickInLocalClient(Player player, Parkourability parkourability) {
		if (runningWallDirection == null) return;
		if (runningDirection == null) return;
		// Re-derive each tick so a rotating sub-level tracks correctly.
		Vec3 newWall = WorldUtil.getRunnableWall(player, player.getBbWidth() * 0.65f);
		if (newWall != null) {
			runningWallDirection = newWall;
			Vec3 perpRaw = newWall.normalize().yRot((float) (Math.PI / 2));
			Vec3 perp3d = new Vec3(perpRaw.x(), 0, perpRaw.z()).normalize();
			// Don't flip run direction on small wall rotations.
			runningDirection = perp3d.dot(runningDirection) >= 0 ? perp3d : perp3d.reverse();
		}
		Vec3 lookVec = VectorUtil.fromYawDegree(player.yBodyRot);
		double differenceAngle = Math.asin(
				new Vec3(
						lookVec.x() * runningDirection.x() + lookVec.z() * runningDirection.z(), 0,
						-lookVec.x() * runningDirection.z() + lookVec.z() * runningDirection.x()
				).normalize().z()
		);
		bodyYaw = (float) VectorUtil.toYawDegree(lookVec.yRot((float) (differenceAngle / 10)));
		Vec3 movement = player.getDeltaMovement();
		// Project wall to horizontal for the friction probe — a tilted wall's full 3D
		// vector has a shortened horizontal component, so scaling it directly lands
		// the block lookup inside the player instead of on the wall.
		Vec3 wallHoriz = new Vec3(runningWallDirection.x(), 0, runningWallDirection.z());
		if (wallHoriz.lengthSqr() < 1e-4) return;
		Vec3 wallProbe = wallHoriz.normalize().scale(player.getBbWidth() * 0.5 + 0.1);
		BlockPos leanedBlock = WorldUtil.getClosestBlockToRelPositionFromEntityHeight(player, wallProbe, 0.5);
		if (!player.getCommandSenderWorld().isLoaded(leanedBlock)) return;
		float slipperiness = WorldUtil.getBlockStateAt(player.getCommandSenderWorld(), leanedBlock).getFriction(player.getCommandSenderWorld(), leanedBlock, player);
		if (slipperiness <= 0.8) {
            double speedMod = Math.min(
                    parkourability.getActionInfo().getClientSetting().get(ParCoolConfig.Client.Doubles.HWallRunSpeedModifier),
                    parkourability.getActionInfo().getServerLimitation().get(ParCoolConfig.Server.Doubles.MaxHWallRunSpeedModifier)
            );
            double speedScale = MovementUtil.getActionMovementSpeed(player) * speedMod;
            // Player is world-vertical: run direction is world-horizontal, "up" is world-Y,
            // and we still pick up the sub-level's world-vertical drift for moving platforms.
            // Clamp preserved Y velocity to ≤ 0: a leaned-back wall pushes the player up
            // via collision, and feeding that back into decayedUp would runaway-accelerate
            // them up the wall.  Only downward (gravity-dragged) Y is kept.
            SableLocalFrame frame = SableLocalFrame.at(player, player.getBbWidth() * 0.65 + 0.5);
            Vec3 runHoriz = new Vec3(runningDirection.x() * speedScale, 0, runningDirection.z() * speedScale);
            double decayedUp = Math.min(movement.y, 0) * (slipperiness - 0.1) * ((double) getDoingTick()) / getMaxRunningTick(parkourability.getActionInfo());
            double subUp = frame.displacement().y;
            player.setDeltaMovement(runHoriz.add(0, decayedUp + subUp, 0));
		}
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public boolean canStart(Player player, Parkourability parkourability, ByteBuffer startInfo) {
		Vec3 wallDirection = WorldUtil.getRunnableWall(player, player.getBbWidth() * 0.65f);
		if (wallDirection == null) return false;
		Vec3 wallVec = wallDirection.normalize();
		Vec3 lookDirection = VectorUtil.fromYawDegree(player.yBodyRot);
		lookDirection = new Vec3(lookDirection.x(), 0, lookDirection.z()).normalize();
		//doing "wallDirection/direction" as complex number(x + z i) to calculate difference of player's direction to steps
		Vec3 dividedVec =
				new Vec3(
						wallVec.x() * lookDirection.x() + wallVec.z() * lookDirection.z(), 0,
						-wallVec.x() * lookDirection.z() + wallVec.z() * lookDirection.x()
				).normalize();
		if (Math.abs(dividedVec.z()) < 0.8) {
			return false;
		}
		BufferUtil.wrap(startInfo).putBoolean(dividedVec.z() > 0/*if true, wall is in right side*/);
		Vec3 runDirection = wallVec.yRot((float) (Math.PI / 2));
		if (runDirection.dot(lookDirection) < 0) {
			runDirection = runDirection.reverse();
		}
		// A wall tilted away from the player (steep-ramp-like) gives runDirection a downward
		// Y component; allow these so the bonus-duration scaling can apply.  Cut off only
		// the truly floor-like cases where the surface stops being a wall.
		if (runDirection.y() < -0.85) return false;
		runDirection = new Vec3(runDirection.x(), 0, runDirection.z()).normalize();
		startInfo.putDouble(wallDirection.x())
				.putDouble(wallDirection.y())
				.putDouble(wallDirection.z())
				.putDouble(runDirection.x())
				.putDouble(runDirection.z());

		return (!parkourability.get(WallJump.class).justJumped()
                && (
				(ParCoolConfig.Client.getInstance().HWallRunControl.get() == ControlType.PressKey && KeyBindings.getKeyHorizontalWallRun().isDown())
						|| ParCoolConfig.Client.getInstance().HWallRunControl.get() == ControlType.Auto
        )
				&& !parkourability.get(Crawl.class).isDoing()
				&& !parkourability.get(Dodge.class).isDoing()
				&& !parkourability.get(Vault.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing()
                && !player.isInWaterOrBubble()
                && Math.abs(player.getDeltaMovement().y()) < 0.5
				&& coolTime == 0
				&& !player.onGround()
				&& parkourability.getAdditionalProperties().getNotLandingTick() > 5
                && (
                parkourability.get(FastRun.class).canActWithRunning(player)
                        || parkourability.get(FastRun.class).getNotDashTick(parkourability.getAdditionalProperties()) < 10)
		);
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public boolean canContinue(Player player, Parkourability parkourability) {
		Vec3 wallDirection = WorldUtil.getRunnableWall(player, player.getBbWidth() * 0.65f);
		if (wallDirection == null) return false;
		if (!(player instanceof LocalPlayer localPlayer)) return false;
		if (localPlayer.input == null) return false;
		var moveVector = localPlayer.input.getMoveVector();
		// Use body yaw so input direction doesn't depend on where the player is looking.
		var actualInputVector
				= new Vec3(moveVector.x, 0, moveVector.y)
				.normalize()
				.yRot((float) -Math.toRadians(player.yBodyRot));
		// Input almost opposite to wall
		if (wallDirection.normalize().dot(actualInputVector) < -0.86) {
			return false;
		}
		return (getDoingTick() < getMaxRunningTick(parkourability.getActionInfo())
                && (!player.getData(Attachments.STAMINA).isExhausted() || parkourability.getActionInfo().getStaminaConsumptionOf(HorizontalWallRun.class) == 0)
				&& !parkourability.get(WallJump.class).justJumped()
				&& !parkourability.get(Crawl.class).isDoing()
				&& !parkourability.get(Dodge.class).isDoing()
				&& !parkourability.get(Vault.class).isDoing()
				&& ((ParCoolConfig.Client.getInstance().HWallRunControl.get() == ControlType.PressKey && KeyBindings.getKeyHorizontalWallRun().isDown())
				|| ParCoolConfig.Client.getInstance().HWallRunControl.get() == ControlType.Auto)
				&& !player.onGround()
                && (runningWallDirection == null || runningWallDirection.normalize().y() >= -0.85)
		);
	}

	@Override
	public void onStop(Player player) {
		coolTime = 10;
	}

	@Override
    public void onStartInLocalClient(Player player, Parkourability parkourability, ByteBuffer startData) {
		wallIsRightward = BufferUtil.getBoolean(startData);
		runningWallDirection = new Vec3(startData.getDouble(), startData.getDouble(), startData.getDouble());
		double rdX = startData.getDouble(), rdZ = startData.getDouble();
		runningDirection = new Vec3(rdX, 0, rdZ).normalize();
		if (ParCoolConfig.Client.Booleans.EnableActionSounds.get())
            player.playSound(SoundEvents.HORIZONTAL_WALL_RUN.get(), 1f, 1f);
		Animation animation = Animation.get(player);
		if (animation != null) {
			animation.setAnimator(new HorizontalWallRunAnimator(wallIsRightward));
		}
	}

	@Override
	public void onStartInOtherClient(Player player, Parkourability parkourability, ByteBuffer startData) {
		wallIsRightward = BufferUtil.getBoolean(startData);
		runningWallDirection = new Vec3(startData.getDouble(), startData.getDouble(), startData.getDouble());
		double rdX = startData.getDouble(), rdZ = startData.getDouble();
		runningDirection = new Vec3(rdX, 0, rdZ).normalize();
		Animation animation = Animation.get(player);
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get())
            player.playSound(SoundEvents.HORIZONTAL_WALL_RUN.get(), 1f, 1f);
		if (animation != null) {
			animation.setAnimator(new HorizontalWallRunAnimator(wallIsRightward));
		}
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public void onRenderTick(RenderFrameEvent event, Player player, Parkourability parkourability) {
		if (isDoing()) {
			if (runningDirection == null) return;
			Vec3 lookVec = VectorUtil.fromYawDegree(player.getYHeadRot());
			double differenceAngle = Math.asin(
					new Vec3(
							lookVec.x() * runningDirection.x() + lookVec.z() * runningDirection.z(), 0,
							-lookVec.x() * runningDirection.z() + lookVec.z() * runningDirection.x()
					).normalize().z()
			);
			if (Math.abs(differenceAngle) > Math.PI / 4) {
				player.setYRot((float) VectorUtil.toYawDegree(
						runningDirection.yRot((float) (-Math.signum(differenceAngle) * Math.PI / 4))
				));
			}
            player.yBodyRotO = player.yBodyRot = bodyYaw;
		}
	}

    @Override
    public void onWorkingTickInClient(Player player, Parkourability parkourability) {
        spawnRunningParticle(player);
    }

	@Override
	public void saveSynchronizedState(ByteBuffer buffer) {
		buffer.putFloat(bodyYaw);
	}

	@Override
	public void restoreSynchronizedState(ByteBuffer buffer) {
		bodyYaw = buffer.getFloat();
	}

	@Override
	public StaminaConsumeTiming getStaminaConsumeTiming() {
		return StaminaConsumeTiming.OnWorking;
	}

	@OnlyIn(Dist.CLIENT)
	public void spawnRunningParticle(Player player) {
		if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
		if (runningDirection == null || runningWallDirection == null) return;
		Level level = player.level();
		Vec3 pos = player.position();
		Vec3 wallProbeHoriz = new Vec3(runningWallDirection.x(), 0, runningWallDirection.z());
		if (wallProbeHoriz.lengthSqr() < 1e-4) return;
		Vec3 wallProbeP = wallProbeHoriz.normalize().scale(player.getBbWidth() * 0.5 + 0.1);
        BlockPos leanedBlock = WorldUtil.getClosestBlockToRelPositionFromEntityHeight(player, wallProbeP, 0.25);
		if (!level.isLoaded(leanedBlock)) return;
		float width = player.getBbWidth();
		BlockState blockstate = WorldUtil.getBlockStateAt(level, leanedBlock);

        Vec3 wallDirection = runningWallDirection.normalize();
        Vec3 orthogonalToWallVec = wallDirection.yRot((float) (Math.PI / 2));
        Vec3 particleBaseDirection = runningDirection.subtract(wallDirection);
        if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 particlePos = new Vec3(
                    pos.x() + (wallDirection.x() * 0.4 + orthogonalToWallVec.x() * (player.getRandom().nextDouble() - 0.5D)) * width,
                    pos.y() + 0.1D + 0.3 * player.getRandom().nextDouble(),
                    pos.z() + (wallDirection.z() * 0.4 + orthogonalToWallVec.z() * (player.getRandom().nextDouble() - 0.5D)) * width
            );
            Vec3 particleSpeed = particleBaseDirection
                    .yRot((float) (Math.PI * 0.2 * (player.getRandom().nextDouble() - 0.5)))
                    .scale(3 + 6 * player.getRandom().nextDouble())
                    .add(0, 1.5, 0);
            level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, blockstate).setPos(leanedBlock),
                    particlePos.x(),
                    particlePos.y(),
                    particlePos.z(),
                    particleSpeed.x(),
                    particleSpeed.y(),
                    particleSpeed.z()
            );
        }
    }
}
