package com.aitraviswilson.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitraviswilson.client.gui.ColorUtil;
import com.aitraviswilson.client.gui.Theme;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ThemeTest {
    @Test
    void hsvRoundTrip() {
        for (int rgb : new int[] {0xFF0000, 0x00FF00, 0x0000FF, 0x8B5CF6, 0x123456}) {
            float[] hsv = ColorUtil.rgbToHsv(rgb);
            assertEquals(rgb, ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]));
        }
    }

    @Test
    void hexParsing() {
        assertEquals(0xABCDEF, ColorUtil.parseHex("#abcdef"));
        assertEquals(-1, ColorUtil.parseHex("xyz"));
        assertEquals("00FF10", ColorUtil.hex(0x00FF10));
    }

    @Test
    void themeJsonRoundTripAndTolerance() {
        Theme t = Theme.defaults();
        t.colors[Theme.ACCENT] = 0x112233;
        t.radius = 5;
        t.rainbow = true;
        Theme back = Theme.fromJson(t.toJson());
        assertEquals(0x112233, back.colors[Theme.ACCENT]);
        assertEquals(5, back.radius);
        assertTrue(back.rainbow);

        JsonObject bad = new JsonObject();
        bad.addProperty("Accent", "not a colour");
        bad.addProperty("radius", 999);
        Theme safe = Theme.fromJson(bad);
        assertEquals(Theme.defaults().colors[Theme.ACCENT], safe.colors[Theme.ACCENT]);
        assertEquals(14, safe.radius);
    }
}
