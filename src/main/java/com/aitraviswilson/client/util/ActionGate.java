package com.aitraviswilson.client.util;

import net.minecraft.client.MinecraftClient;

/** Shared lock so two modules never swap slots / click the inventory in the same few ticks. */
public final class ActionGate {
    private static long lockedUntil;

    private ActionGate() {}

    public static boolean ready(MinecraftClient mc) {
        return mc.world != null && mc.world.getTime() >= lockedUntil;
    }

    public static void lock(MinecraftClient mc, int ticks) {
        if (mc.world != null) lockedUntil = Math.max(lockedUntil, mc.world.getTime() + ticks);
    }
}
