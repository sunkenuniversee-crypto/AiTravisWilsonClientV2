package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.KeySetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.HotbarUtil;
import com.aitraviswilson.client.util.ItemUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.lwjgl.glfw.GLFW;

/**
 * Swaps to a spear and attacks the target under your crosshair, then swaps back. Lunge itself is
 * the vanilla spear/enchantment behaviour; this module only selects the spear and times the hit.
 * Movement is entirely decided by the game and server and is not modified here.
 */
public class SpearLungeModule extends WeaponSwapModule {
    private final KeySetting key = add(new KeySetting("Activation Key", "Hold to lunge at your target.", GLFW.GLFW_KEY_C));
    private final ModeSetting preset = add(new ModeSetting("Preset", "Fast / Slow override the timing sliders.", "Custom", "Fast", "Slow", "Custom"));
    private final NumberSetting range = add(new NumberSetting("Range", "Max target distance.", 4.5, 2, 6, 0.5));
    private final NumberSetting timing = add(new NumberSetting("Lunge Timing (ms)", "Extra wait once the weapon is ready.", 0, 0, 500, 10));
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", "Attack-strength needed before lunging.", 0.9, 0, 1, 0.05));
    private final BoolSetting rapid = add(new BoolSetting("Rapid Consecutive Lunges", "Stay on the spear and keep lunging while held.", false));

    public SpearLungeModule() {
        super("Spear Lunge", "Swaps to a spear and attacks your target.", Category.COMBAT);
        cps.set(4.0);
    }

    @Override
    protected boolean wantsAction(MinecraftClient mc, ClientPlayerEntity me, Entity target) {
        return key.isDown() && me.distanceTo(target) <= range.get();
    }

    @Override
    protected int weaponSlot(ClientPlayerEntity me) {
        if (ItemUtil.isSpear(me.getMainHandStack())) return HotbarUtil.selected(me);
        return HotbarUtil.findBest(me, s -> ItemUtil.isSpear(s) ? ItemUtil.materialScore(s) + 1 : 0);
    }

    @Override protected float minCooldown() { return preset.get().equals("Fast") ? 0.7f : cooldown.getFloat(); }
    @Override protected long extraDelayMs() { return preset.get().equals("Fast") ? 0 : preset.get().equals("Slow") ? 250 : timing.getInt(); }
    @Override protected boolean restoreAfterEachHit() { return !rapid.get(); }
    @Override protected int effectiveSwitchDelay() { return preset.get().equals("Fast") ? 0 : preset.get().equals("Slow") ? 3 : switchDelay.getInt(); }
    @Override protected double effectiveCps() { return preset.get().equals("Fast") ? 10 : preset.get().equals("Slow") ? 2 : cps.get(); }
}
