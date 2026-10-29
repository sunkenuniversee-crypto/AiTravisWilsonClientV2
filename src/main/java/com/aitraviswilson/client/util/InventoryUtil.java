package com.aitraviswilson.client.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

public final class InventoryUtil {
    private InventoryUtil() {}

    /** Player inventory index (0-8 hotbar, 9-35 main) to player screen-handler slot id. */
    public static int toHandlerSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
    }

    /** First hotbar/main inventory index holding the item, or -1. */
    public static int find(PlayerEntity player, Item item) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    public static int count(PlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            var stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) total += stack.getCount();
        }
        if (player.getOffHandStack().isOf(item)) total += player.getOffHandStack().getCount();
        return total;
    }
}
