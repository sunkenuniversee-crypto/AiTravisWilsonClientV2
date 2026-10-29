package com.aitraviswilson.client.util;

import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class HotbarUtil {
    private HotbarUtil() {}

    public static int find(PlayerEntity p, Predicate<ItemStack> test) {
        for (int i = 0; i < 9; i++) if (test.test(p.getInventory().getStack(i))) return i;
        return -1;
    }

    /** Best hotbar slot by score (higher wins); -1 if none scores above 0. */
    public static int findBest(PlayerEntity p, java.util.function.ToIntFunction<ItemStack> score) {
        int best = -1, bestScore = 0;
        for (int i = 0; i < 9; i++) {
            int s = score.applyAsInt(p.getInventory().getStack(i));
            if (s > bestScore) { bestScore = s; best = i; }
        }
        return best;
    }

    public static int selected(PlayerEntity p) { return p.getInventory().getSelectedSlot(); }

    public static void select(PlayerEntity p, int slot) {
        if (slot >= 0 && slot < 9) p.getInventory().setSelectedSlot(slot);
    }
}
