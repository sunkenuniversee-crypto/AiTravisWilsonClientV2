package com.aitraviswilson.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** All user-customisable GUI appearance values. */
public final class Theme {
    public static final String[] COLOR_NAMES = {
        "Accent", "Background", "Sidebar", "Panel", "Text", "Border", "Toggle On", "Toggle Off", "Slider", "Button"
    };
    public static final int ACCENT = 0, BG = 1, SIDEBAR = 2, PANEL = 3, TEXT = 4, BORDER = 5,
            TOGGLE_ON = 6, TOGGLE_OFF = 7, SLIDER = 8, BUTTON = 9;

    /** 0xRRGGBB for each entry of COLOR_NAMES. */
    public final int[] colors = new int[COLOR_NAMES.length];
    public float opacity = 0.94f;
    public int radius = 8;
    public boolean gradient = true;
    public boolean rainbow = false;
    public boolean blur = false;
    public boolean animations = true;
    public boolean sounds = true;

    public static Theme make(int accent, int bg, int sidebar, int panel, int text) {
        Theme t = new Theme();
        t.colors[ACCENT] = accent;
        t.colors[BG] = bg;
        t.colors[SIDEBAR] = sidebar;
        t.colors[PANEL] = panel;
        t.colors[TEXT] = text;
        boolean light = ((text >> 16) & 255) < 128;
        int pole = light ? 0x000000 : 0xFFFFFF;
        t.colors[BORDER] = ColorUtil.mix(panel, pole, 0.14f) & 0xFFFFFF;
        t.colors[TOGGLE_ON] = accent;
        t.colors[TOGGLE_OFF] = ColorUtil.mix(panel, pole, 0.25f) & 0xFFFFFF;
        t.colors[SLIDER] = accent;
        t.colors[BUTTON] = ColorUtil.mix(panel, pole, 0.08f) & 0xFFFFFF;
        return t;
    }

    public static Theme defaults() {
        return make(0x8B5CF6, 0x0F0F14, 0x14141B, 0x1B1B25, 0xEDEDF5);
    }

    public Theme copy() {
        Theme t = new Theme();
        System.arraycopy(colors, 0, t.colors, 0, colors.length);
        t.opacity = opacity;
        t.radius = radius;
        t.gradient = gradient;
        t.rainbow = rainbow;
        t.blur = blur;
        t.animations = animations;
        t.sounds = sounds;
        return t;
    }

    /** Opaque ARGB for a colour slot. */
    public int c(int slot) { return 0xFF000000 | colors[slot]; }

    /** Applies the rainbow effect (if on) to an ARGB colour. */
    public int fx(int argb) {
        if (!rainbow) return argb;
        float h = (System.currentTimeMillis() % 5000L) / 5000f;
        return 0xFF000000 | ColorUtil.hsvToRgb(h, 0.6f, 1f);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (int i = 0; i < COLOR_NAMES.length; i++) o.addProperty(COLOR_NAMES[i], ColorUtil.hex(colors[i]));
        o.addProperty("opacity", opacity);
        o.addProperty("radius", radius);
        o.addProperty("gradient", gradient);
        o.addProperty("rainbow", rainbow);
        o.addProperty("blur", blur);
        o.addProperty("animations", animations);
        o.addProperty("sounds", sounds);
        return o;
    }

    /** Tolerant: anything missing or invalid keeps the default. */
    public static Theme fromJson(JsonObject o) {
        Theme t = defaults();
        if (o == null) return t;
        for (int i = 0; i < COLOR_NAMES.length; i++) {
            JsonElement e = o.get(COLOR_NAMES[i]);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                int v = ColorUtil.parseHex(e.getAsString());
                if (v >= 0) t.colors[i] = v;
            }
        }
        JsonElement e = o.get("opacity");
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            t.opacity = Math.max(0.2f, Math.min(1f, e.getAsFloat()));
        }
        e = o.get("radius");
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            t.radius = Math.max(0, Math.min(14, e.getAsInt()));
        }
        t.gradient = bool(o, "gradient", t.gradient);
        t.rainbow = bool(o, "rainbow", t.rainbow);
        t.blur = bool(o, "blur", t.blur);
        t.animations = bool(o, "animations", t.animations);
        t.sounds = bool(o, "sounds", t.sounds);
        return t;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
    }
}
