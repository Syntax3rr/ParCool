package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.api.SoundEvents;
import com.alrex.parcool.client.animation.impl.ClingToCliffAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.attachment.client.Animation;
import com.alrex.parcool.common.attachment.common.Parkourability;
import com.alrex.parcool.compat.SableLocalFrame;
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

public class ClingToCliff extends Action {
    public enum ControlType {
        PressKey, Toggle
    }

    private static final BehaviorEnforcer.ID ID_SNEAK_CANCEL = BehaviorEnforcer.newID();
    private static final BehaviorEnforcer.ID ID_FALL_FLY_CANCEL = BehaviorEnforcer.newID();
	private float armSwingAmount = 0;
	private FacingDirection facingDirection = FacingDirection.ToWall;
	@Nullable
	private Vec3 clingWallDirection = null;

	public float getArmSwingAmount() {
		return armSwingAmount;
	}

	@Override
	public void onWorkingTick(Player player, Parkourability parkourability) {
		player.fallDistance = 0;
	}

	public FacingDirection getFacingDirection() {
		return facingDirection;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public boolean canStart(Player player, Parkourability parkourability, ByteBuffer startInfo) {
		boolean value = (player.getDeltaMovement().y() < 0.2
				&& !parkourability.get(HorizontalWallRun.class).isDoing()
				&& KeyBindings.getKeyGrabWall().isDown()
				&& (KeyBindings.getKeyGrabWall().getKey().equals(KeyBindings.getKeySneak().getKey()) || !player.isShiftKeyDown())
		);
		if (!value) return false;
		Vec3 wallVec = WorldUtil.getGrabbableWall(player);
		if (wallVec == null) return false;
		startInfo.putDouble(wallVec.x())
				.putDouble(wallVec.z());
		//Check whether player is facing to wall
		return 0.5 < wallVec.normalize().dot(player.getLookAngle().multiply(1, 0, 1).normalize());
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public boolean canContinue(Player player, Parkourability parkourability) {
		// Re-probe along the stored wall direction so looking around doesn't drop the cling.
		Vec3 probeDir = clingWallDirection != null ? clingWallDirection : player.getLookAngle().multiply(1, 0, 1);
		if (probeDir.lengthSqr() < 1e-6) return false;
		return (parkourability.getActionInfo().can(ClingToCliff.class)
                && isGrabbing()
				&& !parkourability.get(HorizontalWallRun.class).isDoing()
				&& !parkourability.get(ClimbUp.class).isDoing()
				&& WorldUtil.getGrabbableWallInDirection(player, probeDir.normalize()) != null
		);

    }

    private boolean isGrabbing() {
		return ParCoolConfig.Client.getInstance().ClingToCliffControl.get() == ControlType.PressKey
                ? KeyBindings.getKeyGrabWall().isDown()
                : !KeyRecorder.keyGrabWall.isPressed();
	}

    @Override
    public void onStart(Player player, Parkourability parkourability, ByteBuffer startData) {
        parkourability.getBehaviorEnforcer().addMarkerCancellingFallFlying(ID_FALL_FLY_CANCEL, this::isDoing);
        armSwingAmount = 0;
    }

	@OnlyIn(Dist.CLIENT)
	@Override
	public void onStartInLocalClient(Player player, Parkourability parkourability, ByteBuffer startData) {
		clingWallDirection = new Vec3(startData.getDouble(), 0, startData.getDouble());
		facingDirection = FacingDirection.ToWall;
		armSwingAmount = 0;
		if (!KeyBindings.getKeyGrabWall().getKey().equals(KeyBindings.getKeySneak().getKey())) {
            parkourability.getBehaviorEnforcer().addMarkerCancellingSneak(ID_SNEAK_CANCEL, this::isDoing);
		}
		if (ParCoolConfig.Client.Booleans.EnableActionSounds.get())
            player.playSound(SoundEvents.CLING_TO_CLIFF.get(), 1f, 1f);
		Animation animation = Animation.get(player);
		if (animation != null) animation.setAnimator(new ClingToCliffAnimator());
	}

	@Override
	public void onStartInOtherClient(Player player, Parkourability parkourability, ByteBuffer startData) {
		clingWallDirection = new Vec3(startData.getDouble(), 0, startData.getDouble());
		facingDirection = FacingDirection.ToWall;
		armSwingAmount = 0;
        if (ParCoolConfig.Client.Booleans.EnableActionSounds.get())
            player.playSound(SoundEvents.CLING_TO_CLIFF.get(), 1f, 1f);
		Animation animation = Animation.get(player);
		if (animation != null) animation.setAnimator(new ClingToCliffAnimator());
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void onWorkingTickInLocalClient(Player player, Parkourability parkourability) {
        armSwingAmount += (float) player.getDeltaMovement().multiply(1, 0, 1).lengthSqr();
        // Gravity is along world-Y, so only carry sub-level Y motion for moving platforms.
        SableLocalFrame frame = SableLocalFrame.at(player, player.getBbWidth() * 0.5 + 0.5);
        Vec3 baseDelta = new Vec3(0, frame.displacement().y, 0);
        if (KeyBindings.isLeftAndRightDown()) {
			player.setDeltaMovement(baseDelta);
		} else {
			if (clingWallDirection != null && facingDirection == FacingDirection.ToWall) {
				// uprightAxis is the sub-level's "up" re-signed to point world-up: traversal
				// follows tilted decks without flipping on sideways/upside-down ones.
				Vec3 traversal = frame.uprightAxis().cross(clingWallDirection.normalize()).normalize();
				Vec3 vec = traversal.scale(0.1);
                if (KeyBindings.isKeyLeftDown()) player.setDeltaMovement(vec.add(baseDelta));
                else if (KeyBindings.isKeyRightDown()) player.setDeltaMovement(vec.reverse().add(baseDelta));
				else player.setDeltaMovement(baseDelta);
			} else {
				player.setDeltaMovement(baseDelta);
			}
		}
	}

	@Override
	public void onWorkingTickInClient(Player player, Parkourability parkourability) {
		// Re-probe along the stored wall so the player can look around without dropping
		// the cling. Only the first tick (before clingWallDirection is set) uses look.
		Vec3 probeDir = clingWallDirection != null ? clingWallDirection : player.getLookAngle().multiply(1, 0, 1);
		if (probeDir.lengthSqr() < 1e-6) return;
		clingWallDirection = WorldUtil.getGrabbableWallInDirection(player, probeDir.normalize());
		if (clingWallDirection == null) return;
		clingWallDirection = clingWallDirection.normalize();
		Vec3 lookingAngle = player.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 angle =
				new Vec3(
						clingWallDirection.x() * lookingAngle.x() + clingWallDirection.z() * lookingAngle.z(), 0,
						-clingWallDirection.x() * lookingAngle.z() + clingWallDirection.z() * lookingAngle.x()
				).normalize();
		if (angle.x() > 0.342) {
			facingDirection = FacingDirection.ToWall;
		} else if (angle.z() < 0) {
			facingDirection = FacingDirection.RightAgainstWall;
		} else {
			facingDirection = FacingDirection.LeftAgainstWall;
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
		if (isDoing() && clingWallDirection != null) {
			switch (facingDirection) {
				case ToWall:
					player.setYBodyRot((float) VectorUtil.toYawDegree(clingWallDirection));
					break;
				case RightAgainstWall:
                    player.yBodyRotO = player.yBodyRot = (float) VectorUtil.toYawDegree(clingWallDirection.yRot((float) (-Math.PI / 2)));
					break;
				case LeftAgainstWall:
                    player.yBodyRotO = player.yBodyRot = (float) VectorUtil.toYawDegree(clingWallDirection.yRot((float) (Math.PI / 2)));
			}
		}
	}

	@Override
	public StaminaConsumeTiming getStaminaConsumeTiming() {
		return StaminaConsumeTiming.OnWorking;
	}

	public enum FacingDirection {
		ToWall, RightAgainstWall, LeftAgainstWall
	}
}
