package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.compat.SableLocalFrame;
import com.alrex.parcool.client.animation.impl.HangAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.attachment.Attachments;
import com.alrex.parcool.common.attachment.client.Animation;
import com.alrex.parcool.common.attachment.common.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.VectorUtil;
import com.alrex.parcool.utilities.WorldUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class HangDown extends Action {
    private static final BehaviorEnforcer.ID ID_SNEAK_CANCEL = BehaviorEnforcer.newID();

	private double bodySwingAngleFactor = 0;
	private float armSwingAmount = 0;
	private boolean orthogonalToBar = false;

	public float getArmSwingAmount() {
		return armSwingAmount;
	}

	public double getBodySwingAngleFactor() {
		return bodySwingAngleFactor;
	}

	public boolean isOrthogonalToBar() {
		return orthogonalToBar;
	}

	// World-space run direction of the bar being hung from (unit, carries tilt on a sloped
	// sub-level deck), or null when not hanging.
	@Nullable
	public Vec3 getHangingBarDirection() {
		return hangingBarDir;
	}

	private Vec3 hangingBarDir = null;

	// Horizontal part of a (possibly tilted) bar direction, normalized; ZERO if vertical.
	private static Vec3 horizontalBar(Vec3 dir) {
		Vec3 h = new Vec3(dir.x, 0, dir.z);
		return h.lengthSqr() < 1e-9 ? Vec3.ZERO : h.normalize();
	}

	// Player's body is more across the bar than along it (>45°): controls left/right slide.
	private void updateOrthogonalToBar(Player player) {
		Vec3 barH = hangingBarDir == null ? Vec3.ZERO : horizontalBar(hangingBarDir);
		if (barH.lengthSqr() < 1e-9) { orthogonalToBar = false; return; }
		Vec3 bodyVec = VectorUtil.fromYawDegree(player.yBodyRot).normalize();
		orthogonalToBar = Math.abs(bodyVec.dot(barH)) < 0.7071067811865476;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public boolean canStart(Player player, Parkourability parkourability, ByteBuffer startInfo) {
		startInfo.putDouble(Math.max(-1, Math.min(1, 3 * player.getLookAngle().multiply(1, 0, 1).normalize().dot(player.getDeltaMovement()))));
		return (Math.abs(player.getDeltaMovement().y) < 0.2
				&& KeyBindings.getKeyHangDown().isDown()
				&& !parkourability.get(JumpFromBar.class).isDoing()
				&& !parkourability.get(ClingToCliff.class).isDoing()
				&& WorldUtil.getHangableBars(player) != null
				&& (KeyBindings.getKeyHangDown().getKey().equals(KeyBindings.getKeySneak().getKey()) || !player.isShiftKeyDown())
		);
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public boolean canContinue(Player player, Parkourability parkourability) {
        return (!player.getData(Attachments.STAMINA).isExhausted()
				&& KeyBindings.getKeyHangDown().isDown()
				&& parkourability.getActionInfo().can(HangDown.class)
				&& !parkourability.get(JumpFromBar.class).isDoing()
				&& !parkourability.get(ClingToCliff.class).isDoing()
				&& WorldUtil.getHangableBars(player) != null
		);
	}

	private void setup(Player player, ByteBuffer startData) {
		armSwingAmount = 0;
		bodySwingAngleFactor = startData.getDouble();
		hangingBarDir = WorldUtil.getHangableBars(player);
		updateOrthogonalToBar(player);
		player.setDeltaMovement(0, 0, 0);
		Animation animation = Animation.get(player);
		if (animation != null) animation.setAnimator(new HangAnimator());
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public void onStartInLocalClient(Player player, Parkourability parkourability, ByteBuffer startData) {
		setup(player, startData);
		if (!KeyBindings.getKeyHangDown().getKey().equals(KeyBindings.getKeySneak().getKey())) {
            parkourability.getBehaviorEnforcer().addMarkerCancellingSneak(ID_SNEAK_CANCEL, this::isDoing);
		}
		if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
			player.playSound(SoundEvents.HANG_DOWN.get(), 1.0f, 1.0f);
		}
	}

	@Override
	public void onStartInOtherClient(Player player, Parkourability parkourability, ByteBuffer startData) {
		setup(player, startData);
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get()) {
            player.playSound(SoundEvents.HANG_DOWN.get(), 1.0f, 1.0f);
        }
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public void onWorkingTickInLocalClient(Player player, Parkourability parkourability) {
		final double speed = 0.1;
		// Carry the bar's full per-tick motion: a hanging player isn't "standing on" the
		// sub-level, so Sable doesn't track them and a moving bar would otherwise slide away.
		SableLocalFrame frame = SableLocalFrame.at(player, 0.5);
		Vec3 baseDelta = frame.displacement();
		Vec3 move = Vec3.ZERO;
		if (hangingBarDir != null) {
			Vec3 bodyVec = VectorUtil.fromYawDegree(player.yBodyRot).normalize();
			Vec3 barH = horizontalBar(hangingBarDir);
			if (orthogonalToBar) {
				// Body across the bar: left/right slide along it (sign keeps "left" intuitive).
				Vec3 leftVec = bodyVec.yRot((float) (Math.PI / 2));
				double sign = Math.signum(leftVec.dot(barH));
				if (sign == 0) sign = 1;
				if (KeyBindings.isKeyLeftDown()) move = hangingBarDir.scale(speed * sign);
				else if (KeyBindings.isKeyRightDown()) move = hangingBarDir.scale(-speed * sign);
			} else {
				// Body along the bar: forward/back move along it (carries the bar's slope).
				double sign = Math.signum(bodyVec.dot(barH));
				if (sign == 0) sign = 1;
				if (KeyBindings.isKeyForwardDown()) move = hangingBarDir.scale(speed * sign);
				else if (KeyBindings.isKeyBackDown()) move = hangingBarDir.scale(-speed * sign);
			}
		}
		player.setDeltaMovement(move.add(baseDelta));
        armSwingAmount += (float) player.getDeltaMovement().multiply(1, 0, 1).lengthSqr();
	}

	@Override
    public void onWorkingTickInClient(Player player, Parkourability parkourability) {
		hangingBarDir = WorldUtil.getHangableBars(player);
		updateOrthogonalToBar(player);
		if (orthogonalToBar) {
			bodySwingAngleFactor /= 1.05;
		} else {
			bodySwingAngleFactor /= 1.5;
		}
	}

	@Override
	public void saveSynchronizedState(ByteBuffer buffer) {
		buffer.putFloat(armSwingAmount);
	}

	@Override
	public void restoreSynchronizedState(ByteBuffer buffer) {
		armSwingAmount = buffer.getFloat();
	}

	@OnlyIn(Dist.CLIENT)
	@Override
    public void onRenderTick(RenderFrameEvent event, Player player, Parkourability parkourability) {
		if (!isDoing() || hangingBarDir == null) return;
		Vec3 barH = horizontalBar(hangingBarDir);
		if (barH.lengthSqr() < 1e-9) return;
		Vec3 perpH = barH.yRot((float) (Math.PI / 2));
		Vec3 lookVec = player.getLookAngle().multiply(1, 0, 1);
		if (lookVec.lengthSqr() < 1e-9) return;
		lookVec = lookVec.normalize();
		// Snap the body toward whichever bar-aligned / bar-perpendicular direction the look
		// is nearest, so the player hangs cleanly along an arbitrarily-oriented bar.
		Vec3 idealLookVec = barH;
		double best = -2;
		for (Vec3 cand : new Vec3[]{barH, barH.reverse(), perpH, perpH.reverse()}) {
			double d = cand.dot(lookVec);
			if (d > best) { best = d; idealLookVec = cand; }
		}
		Vec3 bodyVec = VectorUtil.fromYawDegree(player.yBodyRot).normalize();
		double dot = Math.max(-1, Math.min(1, bodyVec.dot(idealLookVec)));
		double differenceAngle = Math.acos(dot) / 4;
		player.setYBodyRot((float) VectorUtil.toYawDegree(idealLookVec.yRot((float) differenceAngle)));
	}

	@Override
	public StaminaConsumeTiming getStaminaConsumeTiming() {
		return StaminaConsumeTiming.OnWorking;
	}
}
