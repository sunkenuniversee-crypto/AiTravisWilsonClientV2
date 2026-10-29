package com.aitraviswilson.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class Targets {
    private static PlayerEntity current;
    private static long at;

    private Targets() {}

    public static void set(PlayerEntity p) {
        current = p;
        at = System.currentTimeMillis();
    }

    public static PlayerEntity get() {
        if (current != null && (System.currentTimeMillis() - at > 4000 || !current.isAlive())) current = null;
        return current;
    }

    /** A living entity we are allowed to treat as an enemy (not self, friend, teammate or dead). */
    public static boolean isEnemy(MinecraftClient mc, Entity e) {
        if (mc.player == null || e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
        if (Friends.isFriend(e)) return false;
        return !mc.player.isTeammate(e);
    }
}
