package com.alrex.parcool.utilities;

import com.alrex.parcool.common.action.impl.FastRun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class MovementUtil {
    private MovementUtil() {}

    private static final ResourceLocation VANILLA_SPRINT_ID = ResourceLocation.withDefaultNamespace("sprinting");

    // Player MOVEMENT_SPEED with sprint and FastRun stripped, so parkour actions
    // scale with persistent buffs (potions, armor, curios) but don't compound the
    // boost the player is already getting from sprinting or FastRun.  Mirrors
    // vanilla AttributeInstance#calculateValue otherwise.
    public static double getActionMovementSpeed(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return 0.1;

        double base = attr.getBaseValue();
        for (AttributeModifier m : attr.getModifiers()) {
            if (isExcluded(m.id())) continue;
            if (m.operation() == AttributeModifier.Operation.ADD_VALUE) {
                base += m.amount();
            }
        }
        double total = base;
        for (AttributeModifier m : attr.getModifiers()) {
            if (isExcluded(m.id())) continue;
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                total += base * m.amount();
            }
        }
        for (AttributeModifier m : attr.getModifiers()) {
            if (isExcluded(m.id())) continue;
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                total *= 1.0 + m.amount();
            }
        }
        return Math.max(0, total);
    }

    private static boolean isExcluded(ResourceLocation id) {
        return VANILLA_SPRINT_ID.equals(id) || FastRun.FAST_RUNNING_MODIFIER.equals(id);
    }
}
