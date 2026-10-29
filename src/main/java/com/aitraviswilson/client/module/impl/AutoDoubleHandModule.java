package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.AIClient;
import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.ActionGate;
import com.aitraviswilson.client.util.InventoryUtil;
import com.aitraviswilson.client.util.Targets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Chooses what sits in your offhand depending on health and nearby enemies. */
public class AutoDoubleHandModule extends ClientModule {
    private static final String[] ITEMS = {"None", "Totem", "Shield", "Golden Apple", "End Crystal"};

    private final ModeSetting idle = add(new ModeSetting("Idle Item", "Offhand item when no enemy is near.", "Totem", ITEMS));
    private final ModeSetting combat = add(new ModeSetting("Combat Item", "Offhand item when an enemy is near.", "Shield", ITEMS));
    private final NumberSetting combatRange = add(new NumberSetting("Combat Range", "Enemy distance that counts as combat.", 8, 3, 20, 1));
    private final BoolSetting totemPriority = add(new BoolSetting("Totem Priority", "Switch to a totem at low health.", true));
    private final NumberSetting health = add(new NumberSetting("Totem Health", "Totem priority health threshold.", 10, 1, 20, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", "Ticks between swaps. Minimum 1.", 2, 1, 20, 1));

    private int cooldown;

    public AutoDoubleHandModule() {
        super("Auto Double Hand", "Manages your offhand by situation.", Category.COMBAT);
    }

    private static boolean matches(String mode, net.minecraft.item.ItemStack s) {
        return switch (mode) {
            case "Totem" -> s.isOf(Items.TOTEM_OF_UNDYING);
            case "Shield" -> s.isOf(Items.SHIELD);
            case "Golden Apple" -> s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE);
            case "End Crystal" -> s.isOf(Items.END_CRYSTAL);
            default -> false;
        };
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.interactionManager == null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (!ActionGate.ready(mc)) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!me.currentScreenHandler.getCursorStack().isEmpty()) return;

        // Auto Totem owns the offhand when it needs a totem.
        ClientModule at = AIClient.modules().byName("Auto Totem");
        if (at instanceof AutoTotemModule t && t.isHandling(me)) return;

        float hp = me.getHealth() + me.getAbsorptionAmount();
        boolean enemyNear = false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (Targets.isEnemy(mc, p) && me.distanceTo(p) <= combatRange.get()) { enemyNear = true; break; }
        }
        String want = totemPriority.get() && hp <= health.get() ? "Totem" : (enemyNear ? combat.get() : idle.get());
        if (want.equals("None") || matches(want, me.getOffHandStack())) return;

        int slot = -1;
        for (int i = 0; i < 36 && slot < 0; i++) if (matches(want, me.getInventory().getStack(i))) slot = i;
        if (slot < 0) return;
        mc.interactionManager.clickSlot(me.playerScreenHandler.syncId, InventoryUtil.toHandlerSlot(slot), 40,
                SlotActionType.SWAP, me);
        cooldown = delay.getInt();
        ActionGate.lock(mc, 2);
    }
}
