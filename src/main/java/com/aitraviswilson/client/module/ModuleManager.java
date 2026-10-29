package com.aitraviswilson.client.module;

import com.aitraviswilson.client.module.impl.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class ModuleManager {
    private final List<ClientModule> modules = new ArrayList<>();
    private final boolean[] keyDown = new boolean[GLFW.GLFW_KEY_LAST + 1];

    public ModuleManager() {
        // Combat
        modules.add(new FastCrystalModule());
        modules.add(new AutoAnchorModule());
        modules.add(new AutoSafeAnchorModule());
        modules.add(new AutoTotemModule());
        modules.add(new AutoDoubleHandModule());
        modules.add(new AutoShieldBreakerModule());
        modules.add(new SpearLungeModule());
        modules.add(new TriggerBotModule());
        modules.add(new AutoJumpResetModule());
        modules.add(new AutoHealthPotModule());
        // Render
        modules.add(new NameTagsModule());
        modules.add(new TracersModule());
        modules.add(new PlayerOutlineModule());
        modules.add(new WorldVisualsModule());
        modules.add(new HudModule(this));
        // Player
        modules.add(new HoverTotemModule());
    }

    public List<ClientModule> getModules() { return Collections.unmodifiableList(modules); }

    public ClientModule byName(String name) {
        for (ClientModule m : modules) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    public List<ClientModule> byCategory(Category c) {
        List<ClientModule> out = new ArrayList<>();
        for (ClientModule m : modules) if (m.getCategory() == c) out.add(m);
        return out;
    }

    public void tick(MinecraftClient client) {
        pollKeybinds(client);
        if (client.player == null || client.world == null) return;
        for (ClientModule m : modules) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick(client);
            } catch (RuntimeException ex) {
                System.err.println("[AITravisWilson] " + m.getName() + " tick error: " + ex);
            }
        }
    }

    public void renderHud(DrawContext context, MinecraftClient client, float tickDelta) {
        if (client.player == null || client.world == null) return;
        for (ClientModule m : modules) {
            if (!m.isEnabled()) continue;
            try {
                m.onHud(context, client, tickDelta);
            } catch (RuntimeException ex) {
                System.err.println("[AITravisWilson] " + m.getName() + " hud error: " + ex);
            }
        }
    }

    /** Edge-triggered module toggling; ignored while any screen is open. */
    private void pollKeybinds(MinecraftClient client) {
        if (client.getWindow() == null) return;
        long handle = client.getWindow().getHandle();
        for (ClientModule m : modules) {
            int key = m.getKeyCode();
            if (key < 0 || key > GLFW.GLFW_KEY_LAST) continue;
            boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
            if (down && !keyDown[key] && client.currentScreen == null) m.toggle();
            keyDown[key] = down;
        }
    }
}
