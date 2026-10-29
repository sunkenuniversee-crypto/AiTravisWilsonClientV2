package com.aitraviswilson.client.util;

import java.util.function.DoubleToIntFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;

/** Screen-space wireframe helpers (lines are drawn with small filled squares). */
public final class Wire {
    private static final int[][] EDGES = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private Wire() {}

    /** Draws a line whose colour is col(t), t running t0..t1 along the line. */
    public static void line(DrawContext ctx, double x1, double y1, double x2, double y2, int th,
                            DoubleToIntFunction col, double t0, double t1) {
        double dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.min(3000, Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps <= 0) return;
        int half = th / 2;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            int x = (int) Math.round(x1 + dx * f) - half, y = (int) Math.round(y1 + dy * f) - half;
            ctx.fill(x, y, x + th, y + th, col.applyAsInt(t0 + (t1 - t0) * f));
        }
    }

    public static Projection.Point[] corners(MinecraftClient mc, float td, Box b, int sw, int sh) {
        double[][] c = {{b.minX, b.minY, b.minZ}, {b.maxX, b.minY, b.minZ}, {b.maxX, b.minY, b.maxZ}, {b.minX, b.minY, b.maxZ},
            {b.minX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.maxZ}, {b.minX, b.maxY, b.maxZ}};
        Projection.Point[] out = new Projection.Point[8];
        for (int i = 0; i < 8; i++) out[i] = Projection.project(mc, td, new net.minecraft.util.math.Vec3d(c[i][0], c[i][1], c[i][2]), sw, sh);
        return out;
    }

    /** 12-edge wireframe box. Edges with a corner behind the camera are skipped. */
    public static void box(DrawContext ctx, MinecraftClient mc, float td, Box b, int th, DoubleToIntFunction col) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        Projection.Point[] p = corners(mc, td, b, sw, sh);
        for (int e = 0; e < EDGES.length; e++) {
            Projection.Point a = p[EDGES[e][0]], c = p[EDGES[e][1]];
            if (a.behind() || c.behind()) continue;
            line(ctx, a.x(), a.y(), c.x(), c.y(), th, col, e / 12.0, (e + 1) / 12.0);
        }
    }
}
