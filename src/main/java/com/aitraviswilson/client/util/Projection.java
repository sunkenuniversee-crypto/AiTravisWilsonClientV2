package com.aitraviswilson.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Projects world positions to GUI-scaled screen coordinates with plain maths (first-person camera). */
public final class Projection {
    public record Point(double x, double y, boolean visible, double dirX, double dirY, boolean behind) {}

    private Projection() {}

    public static Point project(MinecraftClient mc, float td, Vec3d world, int sw, int sh) {
        PlayerEntity me = mc.player;
        Vec3d cam = me.getCameraPosVec(td);
        double yaw = Math.toRadians(me.getYaw(td)), pitch = Math.toRadians(me.getPitch(td));
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        Vec3d f = new Vec3d(-sy * cp, -sp, cy * cp);
        Vec3d r = new Vec3d(-cy, 0, -sy);
        Vec3d u = r.crossProduct(f);
        Vec3d d = world.subtract(cam);
        double z = d.dotProduct(f), x = d.dotProduct(r), y = d.dotProduct(u);
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = sw / (double) sh;
        if (z <= 0.05) {
            double len = Math.max(1e-6, Math.hypot(x, y));
            return new Point(sw / 2.0, sh / 2.0, false, x / len, -y / len, true);
        }
        double nx = x / (z * tanHalf * aspect), ny = y / (z * tanHalf);
        boolean on = Math.abs(nx) <= 1.2 && Math.abs(ny) <= 1.2;
        double len = Math.max(1e-6, Math.hypot(nx, ny));
        return new Point((nx * 0.5 + 0.5) * sw, (0.5 - ny * 0.5) * sh, on, nx / len, -ny / len, false);
    }
}
