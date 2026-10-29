package com.aitraviswilson.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** A keyboard key (GLFW code). -1 means unbound. */
public class KeySetting extends Setting<Integer> {
    public KeySetting(String name, String description, int def) {
        super(name, description, def);
    }

    public boolean isBound() { return value >= 0; }

    /** True while the bound key is physically held. Unbound keys are never "down". */
    public boolean isDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (value < 0 || value > GLFW.GLFW_KEY_LAST || mc.getWindow() == null) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), value) == GLFW.GLFW_PRESS;
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            int v = e.getAsInt();
            if (v >= -1 && v <= GLFW.GLFW_KEY_LAST) value = v;
        }
    }
}
