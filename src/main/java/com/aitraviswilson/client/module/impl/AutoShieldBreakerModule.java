package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.KeySetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.Aim;
import com.aitraviswilson.client.util.HotbarUtil;
import com.aitraviswilson.client.util.ItemUtil;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

/**
 * Attribute swap for shields: when you aim at a player who is blocking, switch to an axe (axes
 * disable shields in vanilla), hit, then switch back.
 */
public class AutoShieldBreakerModule extends WeaponSwapModule {
    private final ModeSetting weapon = add(new ModeSetting("Weapon Selection", "Which axe to use.", "Best Axe", "Best Axe", "Cheapest Axe"));
    private final BoolSetting requireKey = add(new BoolSetting("Require Activation Key", "Only act while the key is held.", false));
    private final KeySetting key = add(new KeySetting("Activation Key", "Hold to enable shield breaking.", GLFW.GLFW_KEY_V));
    private final BoolSetting stun = add(new BoolSetting("Stun Attempt", "Only attempt a swap-hit with the probability below.", false));
    private final NumberSetting stunChance = add(new NumberSetting("Stun Probability %", "Chance to attempt on each new blocking target.", 100, 0, 100, 5));
    private final BoolSetting critFirst = add(new BoolSetting("Prioritise Critical Hits", "Wait out the rising part of a jump before hitting.", false));

    private UUID rolledFor;
    private boolean rolledOk = true;
    private long rolledAt;

    public AutoShieldBreakerModule() {
        super("Auto Shield Breaker", "Swaps to an axe to disable shields.", Category.COMBAT);
        cps.set(6.0);
    }

    @Override
    protected boolean wantsAction(MinecraftClient mc, ClientPlayerEntity me, Entity target) {
        if (requireKey.get() && !key.isDown()) return false;
        if (!(target instanceof LivingEntity le) || !le.isBlocking()) return false;
        if (stun.get()) {
            long now = System.currentTimeMillis();
            if (!target.getUuid().equals(rolledFor) || now - rolledAt > 2000) {
                rolledFor = target.getUuid();
                rolledAt = now;
                rolledOk = Math.random() * 100.0 < stunChance.get();
            }
            if (!rolledOk) return false;
        }
        return true;
    }

    @Override
    protected int weaponSlot(ClientPlayerEntity me) {
        if (ItemUtil.isAxe(me.getMainHandStack()) && !weapon.get().equals("Best Axe")) return HotbarUtil.selected(me);
        boolean best = weapon.get().equals("Best Axe");
        return HotbarUtil.findBest(me, s -> ItemUtil.isAxe(s) ? (best ? ItemUtil.materialScore(s) + 1 : 9 - ItemUtil.materialScore(s)) : 0);
    }

    @Override
    protected boolean attackAllowed(MinecraftClient mc, ClientPlayerEntity me, Entity target) {
        return !(critFirst.get() && !me.isOnGround() && me.getVelocity().y > 0 && !Aim.canCrit(me));
    }
}
