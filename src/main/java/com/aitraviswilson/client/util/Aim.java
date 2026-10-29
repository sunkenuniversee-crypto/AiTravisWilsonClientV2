package com.aitraviswilson.client.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Aim {
    private Aim() {}

    public static void lookAt(PlayerEntity me, Vec3d target) {
        Vec3d eye = me.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        me.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        me.setPitch(MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, h)), -90f, 90f));
    }

    /** Vanilla critical-hit conditions (besides attack cooldown). */
    public static boolean canCrit(PlayerEntity p) {
        return p.fallDistance > 0.0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }
}
