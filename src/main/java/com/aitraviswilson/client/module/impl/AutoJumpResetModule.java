package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Presses the jump key shortly after you take a hit while standing on the ground. */
public class AutoJumpResetModule extends ClientModule {
    private final NumberSetting delay = add(new NumberSetting("Reaction Delay (ms)", "Wait before jumping. Always above zero.", 60, 20, 500, 10));
    private final NumberSetting chance = add(new NumberSetting("Chance %", "Probability of jumping after a hit.", 100, 0, 100, 5));
    private final BoolSetting projectiles = add(new BoolSetting("Include Projectiles", "Also react to hits from range.", false));
    private final BoolSetting needSprint = add(new BoolSetting("Only While Sprinting", "Only react while sprinting.", false));
    private final BoolSetting pauseUse = add(new BoolSetting("Pause While Using Items", "Do not jump while using an item.", true));

    private int lastHurt;
    private long fireAt;
    private boolean pressed;

    public AutoJumpResetModule() {
        super("Auto Jump Reset", "Jumps shortly after being hit.", Category.COMBAT);
    }

    @Override
    protected void onDisable() { release(MinecraftClient.getInstance()); }

    private void release(MinecraftClient mc) {
        if (pressed && mc.options != null) mc.options.jumpKey.setPressed(false);
        pressed = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null) return;
        if (pressed) { release(mc); return; }
        int hurt = me.hurtTime;
        boolean newHit = hurt > lastHurt && hurt > 0;
        lastHurt = hurt;
        if (newHit && fireAt == 0 && mc.currentScreen == null) {
            LivingEntity attacker = me.getAttacker();
            boolean melee = attacker instanceof PlayerEntity && me.distanceTo(attacker) <= 6.0f;
            boolean ranged = projectiles.get() && attacker != null;
            if ((melee || ranged) && Math.random() * 100.0 < chance.get()) {
                fireAt = System.currentTimeMillis() + Math.max(20, delay.getInt());
            }
        }
        if (fireAt != 0 && System.currentTimeMillis() >= fireAt) {
            fireAt = 0;
            boolean ok = me.isOnGround() && mc.currentScreen == null && !(pauseUse.get() && me.isUsingItem())
                    && (!needSprint.get() || me.isSprinting());
            if (ok) {
                mc.options.jumpKey.setPressed(true);
                pressed = true;
            }
        }
    }
}
