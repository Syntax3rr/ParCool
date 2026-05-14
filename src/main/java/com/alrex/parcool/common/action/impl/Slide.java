package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.CrawlAnimator;
import com.alrex.parcool.client.animation.impl.SlidingAnimator;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.attachment.client.Animation;
import com.alrex.parcool.common.attachment.common.Parkourability;
import com.alrex.parcool.compat.SableCompat;
import com.alrex.parcool.compat.SableLocalFrame;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.MovementUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class Slide extends Action {
    private static final BehaviorEnforcer.ID ID_JUMP_CANCEL = BehaviorEnforcer.newID();

    // Slope contribution per tick (rise/run). Modulated by slipperiness, so ice
    // slopes pull hard and dirt slopes barely register.
    private static final double SLOPE_ACCEL = 0.06;
    // Friction per tick = (1 - slipperiness) * BASE_FRICTION. Tuned so a flat
    // normal-block slide lasts about maxSlidingTick ticks before stopping.
    private static final double BASE_FRICTION = 0.15;
    private static final double MAX_SLIDE_SPEED = 2.5;
    private static final double STOP_SLIDE_SPEED = 0.08;

	private Vec3 slidingVec = null;
    // Local-client only; remote clients animate from slidingVec alone.
    private double slideSpeed = 1.0;

	@Override
	public boolean canStart(Player player, Parkourability parkourability, ByteBuffer startInfo) {
		Vec3 lookingVec = player.getLookAngle().multiply(1, 0, 1);
		if (lookingVec.lengthSqr() < 1e-6) return false;
		lookingVec = lookingVec.normalize();
		startInfo.putDouble(lookingVec.x()).putDouble(lookingVec.z());
		return (KeyRecorder.keyCrawlState.isPressed()
				&& player.onGround()
				&& !parkourability.get(Roll.class).isDoing()
				&& !parkourability.get(Tap.class).isDoing()
				&& parkourability.get(Crawl.class).isDoing()
				&& !player.isInWaterOrBubble()
				&& parkourability.get(FastRun.class).getDashTick(parkourability.getAdditionalProperties()) > 5
		);
	}

	@Override
	public boolean canContinue(Player player, Parkourability parkourability) {
        if (!parkourability.get(Crawl.class).isDoing()) return false;
        int maxSlidingTick = Math.min(
                parkourability.getActionInfo().getClientSetting().get(ParCoolConfig.Client.Integers.SlidingContinuableTick),
                parkourability.getActionInfo().getServerLimitation().get(ParCoolConfig.Server.Integers.MaxSlidingContinuableTick)
        );
        if (getDoingTick() < maxSlidingTick) return true;
        return canSustainSlide(player);
	}

    // Past the tick cap, keep going as long as the terrain itself can sustain a slide.
    // Uses |slope| so an uphill traverse decays to zero, reverses, then accelerates
    // back down the hill instead of getting cut off mid-momentum.
    private boolean canSustainSlide(Player player) {
        if (slidingVec == null) return false;
        SableLocalFrame frame = SableLocalFrame.at(player, 0.5);
        float slipperiness = getFloorSlipperiness(player, frame);
        double slope = frame.isSubLevel()
                ? SableCompat.getSubLevelSlopeInDirection(player.level(), player.position(), slidingVec)
                : getTerrainSlope(player, slidingVec);
        double slopeCos = 1.0 / Math.sqrt(1.0 + slope * slope);
        double frictionLoss = (1.0 - slipperiness) * BASE_FRICTION * slopeCos;
        double slopeGain = Math.abs(slope) * slipperiness * SLOPE_ACCEL;
        return slopeGain >= frictionLoss;
    }

    // On a rotated sub-level, a world-Y "below" lookup lands in air and silently
    // turns ice into grass. Probe in the sub-level's local frame to avoid that.
    private static float getFloorSlipperiness(Player player, SableLocalFrame frame) {
        BlockPos belowPos = player.blockPosition().below();
        if (frame.isSubLevel()) {
            BlockState floor = SableCompat.getSubLevelFloorBlockState(player.level(), player.position());
            if (floor != null && !floor.isAir()) {
                return floor.getFriction(player.level(), belowPos, player);
            }
        }
        return WorldUtil.getBlockStateAt(player.level(), belowPos)
                .getFriction(player.level(), belowPos, player);
    }

	@Override
	public void onStartInLocalClient(Player player, Parkourability parkourability, ByteBuffer startData) {
        slidingVec = new Vec3(startData.getDouble(), 0, startData.getDouble());
        slideSpeed = 1.0;
		if (ParCoolConfig.Client.Booleans.EnableActionSounds.get())
            player.playSound(SoundEvents.SLIDE.get(), 1f, 1f);
		Animation animation = Animation.get(player);
		if (animation != null) {
			animation.setAnimator(new SlidingAnimator());
		}
        parkourability.getBehaviorEnforcer().addMarkerCancellingJump(ID_JUMP_CANCEL, this::isDoing);
	}

	@Override
	public void onStartInOtherClient(Player player, Parkourability parkourability, ByteBuffer startData) {
        slidingVec = new Vec3(startData.getDouble(), 0, startData.getDouble());
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get())
            player.playSound(SoundEvents.SLIDE.get(), 1f, 1f);
		Animation animation = Animation.get(player);
		if (animation != null) {
			animation.setAnimator(new SlidingAnimator());
		}
	}

	@Override
	public void onWorkingTickInLocalClient(Player player, Parkourability parkourability) {
		if (slidingVec == null) return;

        double speedMod = Math.min(
                parkourability.getActionInfo().getClientSetting().get(ParCoolConfig.Client.Doubles.SlideSpeedModifier),
                parkourability.getActionInfo().getServerLimitation().get(ParCoolConfig.Server.Doubles.MaxSlideSpeedModifier)
        );
        double baseSpeed = MovementUtil.getActionMovementSpeed(player) * speedMod;

        // Player is gravity-bound to world, so motion stays in world-XZ. The
        // sub-level only contributes slope and Y-drift for moving platforms.
        SableLocalFrame frame = SableLocalFrame.at(player, 0.5);
        Vec3 effectiveSlideVec = slidingVec;
        double slope = frame.isSubLevel()
                ? SableCompat.getSubLevelSlopeInDirection(player.level(), player.position(), effectiveSlideVec)
                : getTerrainSlope(player, effectiveSlideVec);

        float slipperiness = getFloorSlipperiness(player, frame);

        // Friction scales with cos(slope) so normal force shrinks on steep ground,
        // matching how kinetic friction works.
        double slopeCos = 1.0 / Math.sqrt(1.0 + slope * slope);
        slideSpeed *= 1.0 - (1.0 - slipperiness) * BASE_FRICTION * slopeCos;
        slideSpeed -= slope * slipperiness * SLOPE_ACCEL;

        if (slideSpeed < 0) {
            slidingVec = slidingVec.reverse();
            effectiveSlideVec = effectiveSlideVec.reverse();
            // Cap the reversed speed so we don't immediately re-reverse.
            slideSpeed = Math.min(-slideSpeed, 0.4);
        }

        slideSpeed = Math.min(slideSpeed, MAX_SLIDE_SPEED);

        if (slideSpeed < STOP_SLIDE_SPEED) return;

        Vec3 vec = effectiveSlideVec.scale(baseSpeed * slideSpeed).scale(player.onGround() ? 1.0 : 0.6);
        // In-plane sub-level motion is handled by Sable floor tracking; only carry Y here.
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(vec.x(), current.y() + frame.displacement().y(), vec.z());
	}

    @Override
	public void onWorkingTickInClient(Player player, Parkourability parkourability) {
        spawnSlidingParticle(player);
    }

	@Override
	public void onStopInLocalClient(Player player) {
		Animation animation = Animation.get(player);
		if (animation != null && !animation.hasAnimator()) {
			animation.setAnimator(new CrawlAnimator());
		}
        if (!Parkourability.get(player).get(Crawl.class).isDoing()) {
            player.swimAmount = 0;
            player.swimAmountO = 0;
        }
	}

	@Override
	public void onStopInOtherClient(Player player) {
		Animation animation = Animation.get(player);
		if (animation != null && !animation.hasAnimator()) {
			animation.setAnimator(new CrawlAnimator());
		}
        if (!Parkourability.get(player).get(Crawl.class).isDoing()) {
            player.swimAmount = 0;
            player.swimAmountO = 0;
        }
	}

    @Nullable
    public Vec3 getSlidingVector() {
        return slidingVec;
	}

	@Override
	public StaminaConsumeTiming getStaminaConsumeTiming() {
		return StaminaConsumeTiming.None;
	}

    // Rise/run along dir, sampled half a block ahead. 0 when airborne.
    private double getTerrainSlope(Player player, Vec3 dir) {
        if (!player.onGround()) return 0.0;
        Level level = player.level();
        Vec3 pos = player.position();
        Vec3 step = new Vec3(dir.x(), 0, dir.z()).normalize().scale(0.5);
        double y0 = groundSurfaceY(level, pos);
        double y1 = groundSurfaceY(level, pos.add(step));
        return (y1 - y0) * 2.0; // normalise to per-unit-horizontal
    }

    private static double groundSurfaceY(Level level, Vec3 pos) {
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos bp = BlockPos.containing(pos.x(), pos.y() - 0.1 - dy, pos.z());
            BlockState state = WorldUtil.getBlockStateAt(level, bp);
            if (!state.isAir()) return bp.getY() + 1.0;
        }
        return pos.y();
    }

	@OnlyIn(Dist.CLIENT)
	private void spawnSlidingParticle(Player player) {
		if (!ParCoolConfig.Client.Booleans.EnableActionParticles.get()) return;
		var level = player.level();
		var pos = player.position();
		var feetBlock = WorldUtil.getBlockStateAt(player.level(), player.blockPosition().below());
		float width = player.getBbWidth();
		var direction = getSlidingVector();
		if (direction == null) return;

		if (feetBlock.getRenderShape() != RenderShape.INVISIBLE) {
			var particlePos = new Vec3(
					pos.x() + (player.getRandom().nextDouble() - 0.5D) * width,
					pos.y() + 0.01D + 0.2 * player.getRandom().nextDouble(),
					pos.z() + (player.getRandom().nextDouble() - 0.5D) * width
			);
			var particleSpeed = direction
					.reverse()
					.scale(2.5 + 5 * player.getRandom().nextDouble())
					.add(0, 1.5, 0);
			var blockPos = player.position().add(0, -0.5, 0);
			level.addParticle(
					new BlockParticleOption(ParticleTypes.BLOCK, feetBlock).setPos(
							new BlockPos(
									Mth.floor(blockPos.x()),
									Mth.floor(blockPos.y()),
									Mth.floor(blockPos.z())
							)
					),
					particlePos.x(),
					particlePos.y(),
					particlePos.z(),
					particleSpeed.x(),
					particleSpeed.y(),
					particleSpeed.z()
			);
		}
	}


	@Override
	public void onWorkingTick(Player player, Parkourability parkourability) {
		player.setSprinting(false);
		if (player.getForcedPose() != Pose.SWIMMING) {
			player.setForcedPose(Pose.SWIMMING);
		}
	}

	@Override
	public void onStop(Player player) {
		player.setForcedPose(null);
	}
}
