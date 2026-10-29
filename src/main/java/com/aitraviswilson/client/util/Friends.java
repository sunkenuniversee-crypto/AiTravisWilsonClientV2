package com.aitraviswilson.client.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** Friend list read from config/aitraviswilson/friends.txt (one name per line). */
public final class Friends {
    private static final Set<String> NAMES = new HashSet<>();

    private Friends() {}

    public static void load() {
        NAMES.clear();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("aitraviswilson").resolve("friends.txt");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "# One player name per line. Friends are ignored by combat modules.\n",
                        StandardCharsets.UTF_8);
                return;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String n = line.trim();
                if (!n.isEmpty() && !n.startsWith("#")) NAMES.add(n.toLowerCase(Locale.ROOT));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[AITravisWilson] Could not read friends.txt: " + e);
        }
    }

    public static boolean isFriend(Entity e) {
        return e instanceof PlayerEntity p && NAMES.contains(p.getName().getString().toLowerCase(Locale.ROOT));
    }
}
