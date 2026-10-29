package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.gui.ColorUtil;
import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.Friends;
import com.aitraviswilson.client.util.Palette;
import com.aitraviswilson.client.util.Projection;
import com.aitraviswilson.client.util.Wire;
import java.util.function.DoubleToIntFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Animated RGB outline around other players (3D box, 2D box or corner brackets). */
public class PlayerOutlineModule extends ClientModule {
    private final ModeSetting style = add(new ModeSetting("Style", "Outline shape.", "3D Box", "3D Box", "2D Box", "Corners"));
    private final ModeSetting colorMode = add(new ModeSetting("Color Mode", "How the outline is coloured.", "Rainbow", "Rainbow", "Gradient", "Static", "Health"));
    private final ModeSetting colorA = add(new ModeSetting("Color A", "Static / gradient start.", "Cyan", Palette.NAMES));
    private final ModeSetting colorB = add(new ModeSetting("Color B", "Gradient end.", "Magenta", Palette.NAMES));
    private final NumberSetting speed = add(new NumberSetting("RGB Speed", "Animation speed.", 1.0, 0.1, 4, 0.1));
    private final NumberSetting spread = add(new NumberSetting("Color Spread", "How much the colour changes along the outline.", 0.6, 0, 2, 0.1));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line width in pixels.", 1, 1, 4, 1));
    private final NumberSetting fill = add(new NumberSetting("Fill Opacity", "Translucent fill (2D Box only).", 30, 0, 120, 5));
    private final NumberSetting maxDist = add(new NumberSetting("Max Distance", "Skip players farther than this.", 80, 8, 256, 4));
    private final BoolSetting healthBar = add(new BoolSetting("Health Bar", "Vertical health bar (2D Box / Corners).", true));
    private final BoolSetting visibleOnly = add(new BoolSetting("Visible Only", "Only players you can see.", false));
    private final BoolSetting friendsGreen = add(new BoolSetting("Friends In Green", "Draw friends and teammates in green.", true));

    public PlayerOutlineModule() {
        super("Player Outline", "RGB outline around players.", Category.RENDER);
    }

    private DoubleToIntFunction colors(PlayerEntity p, boolean friend) {
        if (friend && friendsGreen.get()) return t -> 0xFF55FF55;
        double time = System.currentTimeMillis() / 4000.0 * speed.get();
        double off = (p.getUuid().hashCode() & 0xFF) / 255.0;
        double sp = spread.get();
        switch (colorMode.get()) {
            case "Static": { int c = Palette.get(colorA.get()); return t -> c; }
            case "Health": {
                int c = ColorUtil.mix(0xFFFF4444, 0xFF44FF44, Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth())));
                return t -> c;
            }
            case "Gradient": {
                int a = Palette.get(colorA.get()), b = Palette.get(colorB.get());
                return t -> ColorUtil.mix(a, b, (float) (0.5 + 0.5 * Math.sin((time * 4 + t * sp * 3 + off) * Math.PI)));
            }
            default:
                return t -> 0xFF000000 | ColorUtil.hsvToRgb((float) ((time + t * sp + off) % 1.0), 0.75f, 1f);
        }
    }

    @Override
    public void onHud(DrawContext ctx, MinecraftClient mc, float td) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int th = thickness.getInt();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || mc.player.distanceTo(p) > maxDist.get()) continue;
            if (visibleOnly.get() && !mc.player.canSee(p)) continue;
            boolean friend = Friends.isFriend(p) || mc.player.isTeammate(p);
            DoubleToIntFunction col = colors(p, friend);
            Vec3d pos = p.getLerpedPos(td);
            double w = p.getWidth() / 2.0;
            Box box = new Box(pos.x - w, pos.y, pos.z - w, pos.x + w, pos.y + p.getHeight(), pos.z + w);
            if (style.get().equals("3D Box")) {
                Wire.box(ctx, mc, td, box, th, col);
                continue;
            }
            Projection.Point[] c = Wire.corners(mc, td, box, sw, sh);
            double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
            boolean bad = false;
            for (Projection.Point q : c) {
                if (q.behind()) { bad = true; break; }
                minX = Math.min(minX, q.x()); maxX = Math.max(maxX, q.x());
                minY = Math.min(minY, q.y()); maxY = Math.max(maxY, q.y());
            }
            if (bad || maxX < 0 || minX > sw || maxY < 0 || minY > sh) continue;
            if (style.get().equals("2D Box")) {
                if (fill.getInt() > 0) ctx.fill((int) minX, (int) minY, (int) maxX, (int) maxY, ColorUtil.withAlpha(col.applyAsInt(0.5), fill.getInt() / 255f));
                Wire.line(ctx, minX, minY, maxX, minY, th, col, 0, 0.25);
                Wire.line(ctx, maxX, minY, maxX, maxY, th, col, 0.25, 0.5);
                Wire.line(ctx, maxX, maxY, minX, maxY, th, col, 0.5, 0.75);
                Wire.line(ctx, minX, maxY, minX, minY, th, col, 0.75, 1.0);
            } else {
                double lx = Math.max(4, (maxX - minX) * 0.25), ly = Math.max(4, (maxY - minY) * 0.2);
                corner(ctx, minX, minY, lx, ly, th, col, 0.0);
                corner(ctx, maxX, minY, -lx, ly, th, col, 0.25);
                corner(ctx, maxX, maxY, -lx, -ly, th, col, 0.5);
                corner(ctx, minX, maxY, lx, -ly, th, col, 0.75);
            }
            if (healthBar.get()) {
                double f = Math.max(0, Math.min(1, p.getHealth() / p.getMaxHealth()));
                int bx = (int) minX - 4 - th;
                ctx.fill(bx, (int) minY, bx + 2, (int) maxY, 0xAA000000);
                ctx.fill(bx, (int) (maxY - (maxY - minY) * f), bx + 2, (int) maxY, ColorUtil.mix(0xFFFF4444, 0xFF44FF44, (float) f));
            }
        }
    }

    private static void corner(DrawContext ctx, double x, double y, double dx, double dy, int th, DoubleToIntFunction col, double t0) {
        Wire.line(ctx, x, y, x + dx, y, th, col, t0, t0 + 0.1);
        Wire.line(ctx, x, y, x, y + dy, th, col, t0, t0 + 0.1);
    }
}
