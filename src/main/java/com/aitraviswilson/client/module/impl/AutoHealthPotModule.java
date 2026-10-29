package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.ActionGate;
import com.aitraviswilson.client.util.HotbarUtil;
import com.aitraviswilson.client.util.InventoryUtil;
import com.aitraviswilson.client.util.ItemUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/** Throws splash healing potions at your feet when low, and refills the hotbar from an open inventory. */
public class AutoHealthPotModule extends ClientModule {
    private final NumberSetting threshold = add(new NumberSetting("Health Threshold", "Throw when health is at or below this.", 8, 1, 19, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Throw Delay (ticks)", "Ticks between throws.", 3, 1, 20, 1));
    private final BoolSetting doublePot = add(new BoolSetting("Double Pot", "Throw two potions per trigger.", false));
    private final NumberSetting count = add(new NumberSetting("Potions Per Trigger", "Used when Double Pot is off.", 1, 1, 3, 1));
    private final NumberSetting pitch = add(new NumberSetting("Throw Pitch", "Look angle while throwing (90 = straight down).", 90, 60, 90, 1));
    private final NumberSetting hotbarSlot = add(new NumberSetting("Preferred Hotbar Slot", "0 = any slot, 1-9 = prefer that slot (also used by refill).", 0, 0, 9, 1));
    private final BoolSetting pauseUse = add(new BoolSetting("Pause While Using Items", "Do not throw while eating, blocking, etc.", true));
    private final BoolSetting refill = add(new BoolSetting("Inventory Refill", "Move potions to the hotbar, only while YOU have the inventory open.", true));
    private final NumberSetting refillDelay = add(new NumberSetting("Refill Delay (ticks)", "Ticks between refill moves.", 4, 1, 20, 1));

    private int cooldown, thrown, rest, refillTimer;

    public AutoHealthPotModule() {
        super("Auto Health Pot", "Throws healing splash potions when low.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.interactionManager == null) return;
        if (mc.currentScreen instanceof InventoryScreen) { refillTick(mc, me); return; }
        refillTimer = 0;
        if (mc.currentScreen != null || !ActionGate.ready(mc)) return;
        if (rest > 0) { rest--; return; }
        if (cooldown > 0) { cooldown--; return; }

        float hp = me.getHealth();
        if (hp > threshold.get() || hp >= me.getMaxHealth()) { thrown = 0; return; }
        if (pauseUse.get() && me.isUsingItem()) return;
        int slot = findPotion(me);
        if (slot < 0) return;

        int prev = HotbarUtil.selected(me);
        float yaw = me.getYaw(), pit = me.getPitch();
        var vel = me.getVelocity();
        if (Math.hypot(vel.x, vel.z) > 0.08) me.setYaw((float) Math.toDegrees(Math.atan2(vel.z, vel.x)) - 90f + 180f);
        me.setPitch(pitch.getFloat());
        HotbarUtil.select(me, slot);
        if (mc.interactionManager.interactItem(me, Hand.MAIN_HAND).isAccepted()) me.swingHand(Hand.MAIN_HAND);
        HotbarUtil.select(me, prev);
        me.setYaw(yaw);
        me.setPitch(pit);
        ActionGate.lock(mc, 1);

        thrown++;
        cooldown = delay.getInt();
        int want = doublePot.get() ? 2 : count.getInt();
        if (thrown >= want) { thrown = 0; rest = 20; }
    }

    private int findPotion(PlayerEntity me) {
        int pref = hotbarSlot.getInt() - 1;
        if (pref >= 0 && ItemUtil.isHealingSplash(me.getInventory().getStack(pref))) return pref;
        return HotbarUtil.find(me, ItemUtil::isHealingSplash);
    }

    private void refillTick(MinecraftClient mc, ClientPlayerEntity me) {
        if (!refill.get() || !ActionGate.ready(mc)) return;
        if (++refillTimer < refillDelay.getInt()) return;
        if (!me.currentScreenHandler.getCursorStack().isEmpty()) return;
        long h = mc.getWindow().getHandle();
        if (GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) return; // never fight manual clicks
        int from = -1;
        for (int i = 9; i < 36 && from < 0; i++) if (ItemUtil.isHealingSplash(me.getInventory().getStack(i))) from = i;
        if (from < 0) return;
        int pref = hotbarSlot.getInt() - 1;
        int sync = me.playerScreenHandler.syncId;
        if (pref >= 0 && me.getInventory().getStack(pref).isEmpty()) {
            mc.interactionManager.clickSlot(sync, InventoryUtil.toHandlerSlot(from), pref, SlotActionType.SWAP, me);
        } else {
            boolean free = false;
            for (int i = 0; i < 9; i++) if (me.getInventory().getStack(i).isEmpty()) free = true;
            if (!free) return;
            mc.interactionManager.clickSlot(sync, InventoryUtil.toHandlerSlot(from), 0, SlotActionType.QUICK_MOVE, me);
        }
        refillTimer = 0;
        ActionGate.lock(mc, 1);
    }
}
