package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.Friends;
import com.aitraviswilson.client.util.Palette;
import com.aitraviswilson.client.util.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Draws screen-space lines from the bottom or centre of the screen to other players. */
public class TracersModule extends ClientModule {
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line width in pixels.", 1, 1, 4, 1));
    private final NumberSetting maxDist = add(new NumberSetting("Max Distance", "Ignore players farther than this.", 100, 8, 256, 4));
    private final ModeSetting colorMode = add(new ModeSetting("Color Mode", "How lines are coloured.", "Distance", "Static", "Distance", "Health", "Rainbow"));
    private final ModeSetting color = add(new ModeSetting("Static Color", "Used by Static mode.", "White", Palette.NAMES));
    private final ModeSetting origin = add(new ModeSetting("Origin", "Where lines start.", "Bottom", "Bottom", "Crosshair"));
    private final BoolSetting friends = add(new BoolSetting("Friend Filter", "Draw friends in green instead of hiding them.", true));
    private final BoolSetting hideFriends = add(new BoolSetting("Hide Friends", "Do not draw lines to friends.", false));
    private final BoolSetting visibleOnly = add(new BoolSetting("Visible Only", "Only players you have line of sight to.", false));

    public TracersModule() {
        super("Tracers", "Lines pointing to players.", Category.RENDER);
    }

    @Override
    public void onHud(DrawContext ctx, MinecraftClient mc, float td) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double ox = sw / 2.0, oy = origin.get().equals("Bottom") ? sh : sh / 2.0;
        int drawn = 0;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || drawn >= 40) continue;
            float dist = mc.player.distanceTo(p);
            if (dist > maxDist.get()) continue;
            boolean friend = Friends.isFriend(p) || mc.player.isTeammate(p);
            if (friend && hideFriends.get()) continue;
            if (visibleOnly.get() && !mc.player.canSee(p)) continue;
            Vec3d body = p.getLerpedPos(td).add(0, p.getHeight() * 0.5, 0);
            Projection.Point pt = Projection.project(mc, td, body, sw, sh);
            double ex = pt.x(), ey = pt.y();
            if (!pt.visible()) {
                double dx = pt.dirX(), dy = pt.dirY();
                double t = Math.min(dx == 0 ? 1e9 : (sw / 2.0 - 2) / Math.abs(dx), dy == 0 ? 1e9 : (sh / 2.0 - 2) / Math.abs(dy));
                ex = sw / 2.0 + dx * t;
                ey = sh / 2.0 + dy * t;
            }
            ex = Math.max(0, Math.min(sw - 1, ex));
            ey = Math.max(0, Math.min(sh - 1, ey));
            int col = friend && friends.get() ? 0xFF55FF55 : pick(p, dist);
            line(ctx, ox, oy, ex, ey, thickness.getInt(), col);
            drawn++;
        }
    }

    private int pick(PlayerEntity p, float dist) {
        switch (colorMode.get()) {
            case "Distance" -> {
                float t = Math.min(1f, dist / maxDist.getFloat());
                return mix(0xFFFF4444, 0xFF44FF44, t);
            }
            case "Health" -> {
                float t = Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth()));
                return mix(0xFFFF4444, 0xFF44FF44, t);
            }
            case "Rainbow" -> {
                float h = (System.currentTimeMillis() % 4000L) / 4000f;
                return 0xFF000000 | com.aitraviswilson.client.gui.ColorUtil.hsvToRgb(h, 0.7f, 1f);
            }
            default -> {
                return Palette.get(color.get());
            }
        }
    }

    private static int mix(int a, int b, float t) {
        return com.aitraviswilson.client.gui.ColorUtil.mix(a, b, t);
    }

    private static void line(DrawContext ctx, double x1, double y1, double x2, double y2, int th, int col) {
        double dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.min(4000, Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps <= 0) return;
        double sx = dx / steps, sy = dy / steps;
        int half = th / 2;
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(x1 + sx * i) - half, y = (int) Math.round(y1 + sy * i) - half;
            ctx.fill(x, y, x + th, y + th, col);
        }
    }
}
