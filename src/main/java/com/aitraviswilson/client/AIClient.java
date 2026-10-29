package com.aitraviswilson.client;

import com.aitraviswilson.client.config.ConfigManager;
import com.aitraviswilson.client.gui.ClickGuiScreen;
import com.aitraviswilson.client.gui.ThemeManager;
import com.aitraviswilson.client.module.ModuleManager;
import com.aitraviswilson.client.module.impl.HoverTotemModule;
import com.aitraviswilson.client.util.Friends;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class AIClient implements ClientModInitializer {
    public static final String NAME = "AI Travis Wilson Client";
    private static ModuleManager moduleManager;
    private static ConfigManager configManager;
    private static boolean guiKeyDown;

    public static ModuleManager modules() { return moduleManager; }
    public static ConfigManager config() { return configManager; }

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        configManager = new ConfigManager(moduleManager);
        ThemeManager.init();
        Friends.load();
        configManager.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pollGuiKey(client);
            moduleManager.tick(client);
        });
        HudRenderCallback.EVENT.register((context, tickCounter) ->
                moduleManager.renderHud(context, MinecraftClient.getInstance(), tickCounter.getTickProgress(false)));
        HoverTotemModule.registerTooltip(moduleManager);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            configManager.save();
            ThemeManager.saveCurrent();
        });
    }

    private static void pollGuiKey(MinecraftClient client) {
        if (client.getWindow() == null) return;
        boolean down = GLFW.glfwGetKey(client.getWindow().getHandle(), configManager.getGuiKey()) == GLFW.GLFW_PRESS;
        if (down && !guiKeyDown && client.currentScreen == null && client.player != null) {
            client.setScreen(new ClickGuiScreen());
        }
        guiKeyDown = down;
    }
}
