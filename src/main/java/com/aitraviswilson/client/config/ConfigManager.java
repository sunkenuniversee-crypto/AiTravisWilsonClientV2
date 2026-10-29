package com.aitraviswilson.client.config;

import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.module.ModuleManager;
import com.aitraviswilson.client.setting.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/** Saves and loads module state, settings and keybinds. Never throws on bad files. */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final ModuleManager modules;
    private final Path file;
    private int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    public ConfigManager(ModuleManager modules) {
        this(modules, FabricLoader.getInstance().getConfigDir().resolve("aitraviswilson").resolve("config.json"));
    }

    public ConfigManager(ModuleManager modules, Path file) {
        this.modules = modules;
        this.file = file;
    }

    public Path getFile() { return file; }
    public int getGuiKey() { return guiKey; }
    public void setGuiKey(int key) { this.guiKey = key; }

    public JsonObject serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("guiKey", guiKey);
        JsonObject mods = new JsonObject();
        for (ClientModule m : modules.getModules()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("key", m.getKeyCode());
            JsonObject s = new JsonObject();
            for (Setting<?> setting : m.getSettings()) s.add(setting.getName(), setting.toJson());
            o.add("settings", s);
            mods.add(m.getName(), o);
        }
        root.add("modules", mods);
        return root;
    }

    public void apply(JsonObject root) {
        if (root != null && root.has("guiKey") && root.get("guiKey").isJsonPrimitive()
                && root.get("guiKey").getAsJsonPrimitive().isNumber()) {
            guiKey = root.get("guiKey").getAsInt();
        }
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) return;
        JsonObject mods = root.getAsJsonObject("modules");
        for (ClientModule m : modules.getModules()) {
            JsonElement el = mods.get(m.getName());
            if (el == null || !el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (o.has("settings") && o.get("settings").isJsonObject()) {
                JsonObject s = o.getAsJsonObject("settings");
                for (Setting<?> setting : m.getSettings()) {
                    if (s.has(setting.getName())) setting.fromJson(s.get(setting.getName()));
                }
            }
            if (o.has("key") && o.get("key").isJsonPrimitive() && o.get("key").getAsJsonPrimitive().isNumber()) {
                m.setKeyCode(o.get("key").getAsInt());
            }
            if (o.has("enabled") && o.get("enabled").isJsonPrimitive()
                    && o.get("enabled").getAsJsonPrimitive().isBoolean()) {
                m.setEnabled(o.get("enabled").getAsBoolean());
            }
        }
    }

    public void save() {
        writeTo(file);
    }

    public boolean writeTo(Path target) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(serialize(), w);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException ex) {
            System.err.println("[AITravisWilson] Could not save config: " + ex);
            return false;
        }
    }

    public void load() {
        readFrom(file, true);
    }

    /** Used for both normal load and import. Corrupt files are set aside, not fatal. */
    public boolean readFrom(Path source, boolean quarantineIfCorrupt) {
        if (!Files.isRegularFile(source)) return false;
        try (Reader r = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(r);
            if (!parsed.isJsonObject()) throw new IllegalStateException("root is not an object");
            apply(parsed.getAsJsonObject());
            return true;
        } catch (IOException | RuntimeException ex) {
            System.err.println("[AITravisWilson] Invalid config " + source + ": " + ex);
            if (quarantineIfCorrupt) {
                try {
                    Files.move(source, source.resolveSibling(source.getFileName() + ".corrupt"),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // Nothing more we can do; defaults stay in effect.
                }
            }
            return false;
        }
    }

    public boolean exportTo(Path target) { return writeTo(target); }
    public boolean importFrom(Path source) { return readFrom(source, false); }

    public void resetAll() {
        guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
        for (ClientModule m : modules.getModules()) {
            m.resetSettings();
            m.setKeyCode(-1);
        }
        save();
    }
}
