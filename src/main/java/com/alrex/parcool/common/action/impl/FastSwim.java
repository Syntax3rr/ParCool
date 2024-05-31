package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.client.animation.impl.FastSwimAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

import java.nio.ByteBuffer;
import java.util.UUID;

public class FastSwim extends Action {
    private static final String FAST_SWIM_MODIFIER_NAME = "parcool.modifier.fastswimming";
    private static final UUID FAST_SWIM_MODIFIER_UUID = UUID.randomUUID();
    private double speedModifier = 0;
    private boolean toggleStatus;

    @Override
    public boolean canStart(Player player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        return canContinue(player, parkourability, stamina);
    }

    @Override
    public boolean canContinue(Player player, Parkourability parkourability, IStamina stamina) {
        return (!stamina.isExhausted()
                && player.isInWaterOrBubble()
                && player.getVehicle() == null
                && !player.isFallFlying()
                && player.isSprinting()
                && player.isSwimming()
                && !parkourability.get(Dash.class).isDoing()
                && ((ParCoolConfig.Client.FastRunControl.get() == Dash.ControlType.PressKey && KeyBindings.getKeyFastRunning().isDown())
                || (ParCoolConfig.Client.FastRunControl.get() == Dash.ControlType.Toggle && toggleStatus)
                || ParCoolConfig.Client.FastRunControl.get() == Dash.ControlType.Auto)
        );
    }

    @Override
    public void onClientTick(Player player, Parkourability parkourability, IStamina stamina) {
        if (player.isLocalPlayer()) {
            if (ParCoolConfig.Client.FastRunControl.get() == Dash.ControlType.Toggle
                    && parkourability.getAdditionalProperties().getSprintingTick() > 3
                    && player.isInWaterOrBubble()
                    && player.isSwimming()
            ) {
                if (KeyRecorder.keyFastRunning.isPressed())
                    toggleStatus = !toggleStatus;
            } else {
                toggleStatus = false;
            }
        }
    }

    @Override
    public void onWorkingTickInClient(Player player, Parkourability parkourability, IStamina stamina) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new FastSwimAnimator());
        }
    }

    @Override
    public void onStartInServer(Player player, Parkourability parkourability, ByteBuffer startData) {
        speedModifier = parkourability.get(Dash.class).getSpeedModifier(parkourability.getActionInfo());
    }

    @Override
    public void onServerTick(Player player, Parkourability parkourability, IStamina stamina) {
        AttributeInstance attr = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (attr == null) return;
        if (attr.getModifier(FAST_SWIM_MODIFIER_UUID) != null) attr.removeModifier(FAST_SWIM_MODIFIER_UUID);
        if (isDoing()) {
            player.setSprinting(true);
            attr.addTransientModifier(new AttributeModifier(
                    FAST_SWIM_MODIFIER_UUID,
                    FAST_SWIM_MODIFIER_NAME,
                    speedModifier / 8d,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.OnWorking;
    }
}
