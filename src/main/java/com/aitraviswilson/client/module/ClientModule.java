package com.aitraviswilson.client.module;

import com.aitraviswilson.client.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Base class for all client modules (named to avoid clashing with java.lang.Module). */
public abstract class ClientModule {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    /** GLFW key code, or -1 for unbound. */
    private int keyCode = -1;

    protected ClientModule(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isEnabled() { return enabled; }
    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        try {
            if (enabled) onEnable(); else onDisable();
        } catch (RuntimeException ex) {
            this.enabled = false;
            System.err.println("[AITravisWilson] Module " + name + " failed: " + ex);
        }
    }

    public void toggle() { setEnabled(!enabled); }

    public void resetSettings() {
        for (Setting<?> s : settings) s.reset();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick(MinecraftClient client) {}
    public void onHud(DrawContext context, MinecraftClient client, float tickDelta) {}
}
