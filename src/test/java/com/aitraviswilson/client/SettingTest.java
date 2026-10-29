package com.aitraviswilson.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

class SettingTest {
    @Test
    void numberSettingClampsAndSnaps() {
        NumberSetting s = new NumberSetting("x", "", 5, 1, 10, 1);
        s.set(99.0);
        assertEquals(10.0, s.get());
        s.set(-4.0);
        assertEquals(1.0, s.get());
    }

    @Test
    void numberSettingIgnoresBadJson() {
        NumberSetting s = new NumberSetting("x", "", 5, 1, 10, 1);
        s.fromJson(new JsonPrimitive("nope"));
        assertEquals(5.0, s.get());
    }

    @Test
    void modeSettingRejectsUnknownAndCycles() {
        ModeSetting m = new ModeSetting("m", "", "a", "a", "b");
        m.fromJson(new JsonPrimitive("zzz"));
        assertEquals("a", m.get());
        m.cycle();
        assertTrue(m.get().equals("b"));
    }
}
