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
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    // How much slope (rise/run) accelerates or decelerates the slide per tick.
    // Combined with slipperiness so steep ice slides accelerate and sticky dirt hills stop you fast.
    private static final double SLOPE_ACCEL = 0.06;
    // Base friction multiplier: actual friction-per-tick = (1 - slipperiness) * BASE_FRICTION.
    // Calibrated so a normal-block flat slide lasts roughly maxSlidingTick ticks.
    private static final double BASE_FRICTION = 0.15;
    private static final double MAX_SLIDE_SPEED = 2.5;
    // Below this, the slide is considered spent.
    private static final double STOP_SLIDE_SPEED = 0.08;

	private Vec3 slidingVec = null;
    // Speed multiplier: starts at 1.0, modified each tick by friction and slope.
    // Only meaningful on the local client.
    private double slideSpeed = 1.0;

	@Override
	public boolean canStart(Player player, Parkourability parkourability, ByteBuffer startInfo) {
		// Slide is bound to world gravity: the player is upright in world space, sliding on
		// whatever surface is beneath them in world-Y terms.  Capture the look direction in
		// world-XZ (strip world-Y).  On sub-levels this is still the right frame as long as
		// the player is standing on a world-horizontal surface — which is the case whenever
		// Minecraft's own gravity + collision holds them (including a side-flipped sub-level
		// where the player stands on what is, from the sub-level's frame, a local wall).
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
        int maxSlidingTick = Math.min(
                parkourability.getActionInfo().getClientSetting().get(ParCoolConfig.Client.Integers.SlidingContinuableTick),
                parkourability.getActionInfo().getServerLimitation().get(ParCoolConfig.Server.Integers.MaxSlidingContinuableTick)
        );
		return getDoingTick() < maxSlidingTick
				&& parkourability.get(Crawl.class).isDoing();
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

        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double baseSpeed = attr != null ? attr.getValue() * 4.5 : 0.45;

        // Slide dynamics are world-gravity native: the floor is whatever world-Y-horizontal
        // surface the player is standing on, and their motion stays in world-XZ.  Sub-level
        // orientation only matters for (1) slope, via getSubLevelSlopeInDirection which reads
        // the sub-level's local-Y delta per world-horizontal step, and (2) co-movement with
        // moving sub-levels, via the displacement Y component.
        SableLocalFrame frame = SableLocalFrame.at(player, 0.5);
        Vec3 effectiveSlideVec = slidingVec;
        double slope = frame.isSubLevel()
                ? SableCompat.getSubLevelSlopeInDirection(player.level(), player.position(), effectiveSlideVec)
                : getTerrainSlope(player, effectiveSlideVec);

        // Slipperiness of the block underfoot.
        BlockPos belowPos = player.blockPosition().below();
        float slipperiness = WorldUtil.getBlockStateAt(player.level(), belowPos)
                .getFriction(player.level(), belowPos, player);

        // Friction: decay proportional to (1 - slipperiness), so ice retains speed much longer.
        slideSpeed *= 1.0 - (1.0 - slipperiness) * BASE_FRICTION;
        // Slope contribution: combines slope steepness with slipperiness so steep ice gives the
        // strongest effect; gentle grass slopes barely register.
        slideSpeed -= slope * slipperiness * SLOPE_ACCEL;

        // Reversal: slope + slipperiness overcame the remaining momentum.
        if (slideSpeed < 0) {
            slidingVec = slidingVec.reverse();
            effectiveSlideVec = effectiveSlideVec.reverse();
            // Start the reversed slide at a modest speed to avoid immediate re-reversal.
            slideSpeed = Math.min(-slideSpeed, 0.4);
        }

        slideSpeed = Math.min(slideSpeed, MAX_SLIDE_SPEED);

        if (slideSpeed < STOP_SLIDE_SPEED) return;

        Vec3 vec = effectiveSlideVec.scale(baseSpeed * slideSpeed).scale(player.onGround() ? 1.0 : 0.6);
        // Preserve world-Y (gravity) and add world-Y sub-level displacement for moving
        // sub-levels.  Sable floor tracking handles in-plane co-movement, so adding
        // horizontal displacement would double-count.
        Vec3 current = player.getDeltaMovement();
        double subVelY = frame.displacement().y();
        player.setDeltaMovement(vec.x(), current.y() + subVelY, vec.z());
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

    // Approximates rise/run slope along dir by comparing ground-surface Y at the current
    // position and half a block ahead.  Returns 0 when not on ground.
    private double getTerrainSlope(Player player, Vec3 dir) {
        if (!player.onGround()) return 0.0;
        Level level = player.level();
        Vec3 pos = player.position();
        Vec3 step = new Vec3(dir.x(), 0, dir.z()).normalize().scale(0.5);
        double y0 = groundSurfaceY(level, pos);
        double y1 = groundSurfaceY(level, pos.add(step));
        return (y1 - y0) * 2.0; // normalise to per-unit-horizontal
    }

    // Returns the Y coordinate of the top surface of the first non-air block at or below pos.
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
