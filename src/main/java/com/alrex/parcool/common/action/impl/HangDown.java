package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
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
import com.alrex.parcool.utilities.probe.BarInfo;
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

	@Nullable
	private Vec3 hangingBarAxis = null;

	public float getArmSwingAmount() { return armSwingAmount; }
	public double getBodySwingAngleFactor() { return bodySwingAngleFactor; }
	public boolean isOrthogonalToBar() { return orthogonalToBar; }

	@Nullable
	public Vec3 getHangingBarAxis() { return hangingBarAxis; }

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
		BarInfo info = WorldUtil.getHangableBars(player);
		hangingBarAxis = info == null ? null : horizontalize(info.axis());
		updateOrthogonalToBar(player);
		player.setDeltaMovement(0, 0, 0);
		Animation animation = Animation.get(player);
		if (animation != null) animation.setAnimator(new HangAnimator());
	}

	private void updateOrthogonalToBar(Player player) {
		if (hangingBarAxis == null) {
			orthogonalToBar = false;
			return;
		}
		Vec3 bodyVec = VectorUtil.fromYawDegree(player.yBodyRot);
		double dot = Math.abs(bodyVec.dot(hangingBarAxis));
		orthogonalToBar = dot < Math.sqrt(0.5);
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
		Vec3 motion = Vec3.ZERO;
		if (hangingBarAxis != null) {
			Vec3 bodyVec = VectorUtil.fromYawDegree(player.yBodyRot);
			Vec3 left = bodyVec.yRot((float) (Math.PI / 2));
			final double speed = 0.1;

			Vec3 reference = orthogonalToBar ? left : bodyVec;
			double sign = reference.dot(hangingBarAxis) >= 0 ? 1 : -1;
			Vec3 alongBar = hangingBarAxis.scale(sign * speed);

			if (orthogonalToBar) {
				if (KeyBindings.isKeyLeftDown()) motion = alongBar;
				else if (KeyBindings.isKeyRightDown()) motion = alongBar.reverse();
			} else {
				if (KeyBindings.isKeyForwardDown()) motion = alongBar;
				else if (KeyBindings.isKeyBackDown()) motion = alongBar.reverse();
			}
		}
		motion = motion.add(WorldUtil.subLevelDisplacementAt(player));
		player.setDeltaMovement(motion);
        armSwingAmount += (float) player.getDeltaMovement().multiply(1, 0, 1).lengthSqr();
	}

	@Override
    public void onWorkingTickInClient(Player player, Parkourability parkourability) {
		BarInfo info = WorldUtil.getHangableBars(player);
		hangingBarAxis = info == null ? null : horizontalize(info.axis());
		updateOrthogonalToBar(player);
		bodySwingAngleFactor /= orthogonalToBar ? 1.05 : 1.5;
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
		if (!isDoing() || hangingBarAxis == null) return;
		Vec3 bodyVec = VectorUtil.fromYawDegree(player.yBodyRot).normalize();
		Vec3 lookVec = player.getLookAngle();
		Vec3 lookH = horizontalize(new Vec3(lookVec.x, 0, lookVec.z));
		if (lookH == null) return;

		Vec3 perp = new Vec3(-hangingBarAxis.z, 0, hangingBarAxis.x);
		Vec3[] options = {hangingBarAxis, hangingBarAxis.reverse(), perp, perp.reverse()};
		Vec3 idealLookVec = options[0];
		double bestDot = -2;
		for (Vec3 o : options) {
			double d = lookH.dot(o);
			if (d > bestDot) { bestDot = d; idealLookVec = o; }
		}

		double differenceAngle = Math.acos(Math.max(-1, Math.min(1, bodyVec.dot(idealLookVec)))) / 4;
		player.setYBodyRot((float) VectorUtil.toYawDegree(idealLookVec.yRot((float) differenceAngle)));
	}

	@Override
	public StaminaConsumeTiming getStaminaConsumeTiming() {
		return StaminaConsumeTiming.OnWorking;
	}

	@Nullable
	private static Vec3 horizontalize(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		double len = h.length();
		return len < 1e-6 ? null : h.scale(1.0 / len);
	}
}
