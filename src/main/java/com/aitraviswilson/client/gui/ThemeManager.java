package com.aitraviswilson.client.gui;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/** Holds the active theme, built-in presets and user-saved themes. Persists the active theme. */
public final class ThemeManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Theme> PRESETS = new LinkedHashMap<>();
    private static Theme current = Theme.defaults();
    private static Path root;
    private static List<String> customNames = new ArrayList<>();

    static {
        PRESETS.put("Violet", Theme.defaults());
        PRESETS.put("Ocean", Theme.make(0x38BDF8, 0x0B1218, 0x0F1820, 0x15212B, 0xE8F1F8));
        PRESETS.put("Crimson", Theme.make(0xEF4444, 0x140C0C, 0x1A1010, 0x241616, 0xF6EAEA));
        PRESETS.put("Mint", Theme.make(0x34D399, 0x0B1411, 0x0F1A16, 0x15241E, 0xE8F6F0));
        PRESETS.put("Sunset", Theme.make(0xF97316, 0x15100B, 0x1B140E, 0x261C13, 0xF8EFE6));
        PRESETS.put("Mono", Theme.make(0xE5E7EB, 0x0E0E0E, 0x131313, 0x1C1C1C, 0xF2F2F2));
        PRESETS.put("Light", Theme.make(0x3B82F6, 0xF3F4F6, 0xE5E7EB, 0xFFFFFF, 0x111827));
    }

    private ThemeManager() {}

    public static void init() {
        root = FabricLoader.getInstance().getConfigDir().resolve("aitraviswilson");
        loadCurrent();
        refreshCustom();
    }

    public static Theme current() { return current; }
    public static List<String> presetNames() { return new ArrayList<>(PRESETS.keySet()); }
    public static Theme preset(String name) { return PRESETS.get(name); }
    public static List<String> customNames() { return customNames; }

    public static void applyPreset(String name) {
        Theme t = PRESETS.get(name);
        if (t != null) current = t.copy();
    }

    public static void resetToDefault() { current = Theme.defaults(); }

    public static String sanitize(String name) {
        String s = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return s.length() > 24 ? s.substring(0, 24).trim() : s;
    }

    public static boolean saveCustom(String rawName) {
        String name = sanitize(rawName);
        if (name.isEmpty() || root == null) return false;
        boolean ok = write(root.resolve("themes").resolve(name + ".json"), current);
        refreshCustom();
        return ok;
    }

    public static boolean loadCustom(String name) {
        if (root == null) return false;
        Theme t = read(root.resolve("themes").resolve(sanitize(name) + ".json"));
        if (t == null) return false;
        current = t;
        return true;
    }

    public static boolean deleteCustom(String name) {
        if (root == null) return false;
        try {
            boolean r = Files.deleteIfExists(root.resolve("themes").resolve(sanitize(name) + ".json"));
            refreshCustom();
            return r;
        } catch (IOException e) {
            return false;
        }
    }

    public static void saveCurrent() {
        if (root != null) write(root.resolve("theme.json"), current);
    }

    private static void loadCurrent() {
        Theme t = read(root.resolve("theme.json"));
        if (t != null) current = t;
    }

    private static void refreshCustom() {
        List<String> names = new ArrayList<>();
        Path dir = root == null ? null : root.resolve("themes");
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - 5))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(names::add);
            } catch (IOException ignored) {
                // Leave the list empty; themes simply will not be listed.
            }
        }
        customNames = names;
    }

    private static boolean write(Path file, Theme t) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(t.toJson(), w);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("[AITravisWilson] Could not save theme: " + e);
            return false;
        }
    }

    private static Theme read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement e = JsonParser.parseReader(r);
            return e.isJsonObject() ? Theme.fromJson((JsonObject) e) : null;
        } catch (IOException | RuntimeException ex) {
            System.err.println("[AITravisWilson] Ignoring invalid theme " + file + ": " + ex);
            return null;
        }
    }
}
