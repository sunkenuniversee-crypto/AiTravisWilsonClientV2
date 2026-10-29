package com.aitraviswilson.client.gui;

/** Pure colour maths (no Minecraft types) so it can be unit tested. */
public final class ColorUtil {
    private ColorUtil() {}

    /** h, s, v in 0..1; returns 0xRRGGBB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float hp = h * 6f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; }
        else if (hp < 2) { r = x; g = c; }
        else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; }
        else if (hp < 5) { r = x; b = c; }
        else { r = c; b = x; }
        float m = v - c;
        int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
        return (clamp8(ri) << 16) | (clamp8(gi) << 8) | clamp8(bi);
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    public static String hex(int rgb) { return String.format("%06X", rgb & 0xFFFFFF); }

    /** Parses "RRGGBB" or "#RRGGBB"; returns -1 when invalid. */
    public static int parseHex(String s) {
        if (s == null) return -1;
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return -1;
        try { return Integer.parseInt(s, 16); } catch (NumberFormatException e) { return -1; }
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 255, cb = (b >>> shift) & 255;
            out |= clamp8(Math.round(ca + (cb - ca) * t)) << shift;
        }
        return out;
    }

    public static int withAlpha(int argb, float mul) {
        int a = clamp8(Math.round(((argb >>> 24) & 255) * mul));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int clamp8(int v) { return Math.max(0, Math.min(255, v)); }
}
