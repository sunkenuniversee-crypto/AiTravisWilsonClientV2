package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.ActionGate;
import com.aitraviswilson.client.util.InventoryUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Keeps a Totem of Undying in the offhand when health is at or below the threshold. */
public class AutoTotemModule extends ClientModule {
    private final NumberSetting health = add(new NumberSetting("Health Threshold",
            "Equip a totem when health (incl. absorption) is at or below this.", 20, 1, 20, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay (ticks)",
            "Ticks between inventory swaps. Minimum 1.", 2, 1, 20, 1));
    private final BoolSetting restore = add(new BoolSetting("Restore Offhand",
            "Put the previous offhand item back once health is safe.", false));
    private final NumberSetting restoreAbove = add(new NumberSetting("Restore Above",
            "Health required before the previous offhand item is restored.", 20, 1, 20, 0.5));
    private final BoolSetting pauseInScreens = add(new BoolSetting("Pause In Other Screens",
            "Do nothing while a screen other than the inventory is open.", true));

    private int cooldown;
    private Item previousOffhand;

    public AutoTotemModule() {
        super("Auto Totem", "Keeps a Totem of Undying in your offhand.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        previousOffhand = null;
        cooldown = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (!ActionGate.ready(client)) return;
        if (pauseInScreens.get() && client.currentScreen != null
                && !(client.currentScreen instanceof InventoryScreen)) return;
        // Never touch the slots while the player is holding a cursor item.
        if (!player.currentScreenHandler.getCursorStack().isEmpty()) return;

        float effective = player.getHealth() + player.getAbsorptionAmount();
        boolean offhandHasTotem = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

        if (effective <= health.get() && !offhandHasTotem) {
            int slot = InventoryUtil.find(player, Items.TOTEM_OF_UNDYING);
            if (slot < 0) return;
            Item before = player.getOffHandStack().isEmpty() ? null : player.getOffHandStack().getItem();
            swapToOffhand(client, player, slot);
            if (before != null) previousOffhand = before;
            cooldown = delay.getInt();
            ActionGate.lock(client, 2);
        } else if (restore.get() && offhandHasTotem && previousOffhand != null
                && effective >= restoreAbove.get() && effective > health.get()) {
            int slot = InventoryUtil.find(player, previousOffhand);
            if (slot >= 0) {
                swapToOffhand(client, player, slot);
                cooldown = delay.getInt();
                ActionGate.lock(client, 2);
            }
            previousOffhand = null;
        }
    }

    /** True when this module wants a totem in the offhand right now (used by Auto Double Hand). */
    public boolean isHandling(PlayerEntity player) {
        return isEnabled() && player.getHealth() + player.getAbsorptionAmount() <= health.get();
    }

    private void swapToOffhand(MinecraftClient client, PlayerEntity player, int inventoryIndex) {
        // Button 40 on SWAP exchanges the clicked slot with the offhand.
        client.interactionManager.clickSlot(player.playerScreenHandler.syncId,
                InventoryUtil.toHandlerSlot(inventoryIndex), 40, SlotActionType.SWAP, player);
    }
}
