package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.KeySetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.Aim;
import com.aitraviswilson.client.util.ItemUtil;
import com.aitraviswilson.client.util.Targets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/** Attacks the entity under your crosshair when the attack is ready. */
public class TriggerBotModule extends ClientModule {
    private final NumberSetting cps = add(new NumberSetting("CPS", "Maximum attacks per second.", 10, 1, 20, 1));
    private final NumberSetting attackDelay = add(new NumberSetting("Attack Delay (ms)", "Extra wait after the crosshair lands on a target.", 0, 0, 500, 10));
    private final NumberSetting variation = add(new NumberSetting("Delay Variation %", "Random +/- variation on the CPS interval.", 10, 0, 50, 5));
    private final NumberSetting cooldown = add(new NumberSetting("Attack Cooldown", "Attack-strength required (1.0 = fully charged).", 0.9, 0, 1, 0.05));
    private final BoolSetting critOnly = add(new BoolSetting("Critical Hits Only", "Only attack when a vanilla critical hit is possible.", false));
    private final BoolSetting critTiming = add(new BoolSetting("Critical Timing", "Hold the attack while rising in a jump, until you fall.", false));
    private final ModeSetting targets = add(new ModeSetting("Targets", "What may be attacked.", "Players", "Players", "Hostile Mobs", "All Living"));
    private final BoolSetting weaponOnly = add(new BoolSetting("Require Weapon", "Only attack while holding a sword, axe, mace or spear.", true));
    private final BoolSetting ignoreInvisible = add(new BoolSetting("Ignore Invisible", "Skip invisible entities.", true));
    private final BoolSetting pauseUse = add(new BoolSetting("Pause While Using Items", "No attacks while eating, blocking, etc.", true));
    private final BoolSetting requireKey = add(new BoolSetting("Require Activation Key", "Only attack while the key is held.", false));
    private final KeySetting key = add(new KeySetting("Activation Key", "Hold to enable the trigger bot.", GLFW.GLFW_KEY_X));

    private long lastAttack, onTargetSince;

    public TriggerBotModule() {
        super("TriggerBot", "Auto-attacks the entity under your crosshair.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (requireKey.get() && !key.isDown()) { onTargetSince = 0; return; }
        if (pauseUse.get() && me.isUsingItem()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult ehr) || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            onTargetSince = 0;
            return;
        }
        Entity e = ehr.getEntity();
        if (!eligible(mc, e)) { onTargetSince = 0; return; }
        if (weaponOnly.get() && !ItemUtil.isMeleeWeapon(me.getMainHandStack())) return;

        long now = System.currentTimeMillis();
        if (onTargetSince == 0) onTargetSince = now;
        if (now - onTargetSince < attackDelay.getInt()) return;
        if (me.getAttackCooldownProgress(0.5f) < cooldown.getFloat()) return;
        boolean crit = Aim.canCrit(me);
        if (critOnly.get() && !crit) return;
        if (critTiming.get() && !me.isOnGround() && me.getVelocity().y > 0 && !crit) return;

        double interval = 1000.0 / cps.get();
        double v = variation.get() / 100.0;
        interval *= 1.0 + (Math.random() * 2 - 1) * v;
        if (now - lastAttack < interval) return;

        mc.interactionManager.attackEntity(me, e);
        me.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
        if (e instanceof PlayerEntity p) Targets.set(p);
    }

    private boolean eligible(MinecraftClient mc, Entity e) {
        if (!Targets.isEnemy(mc, e)) return false;
        if (ignoreInvisible.get() && e.isInvisible()) return false;
        return switch (targets.get()) {
            case "Players" -> e instanceof PlayerEntity;
            case "Hostile Mobs" -> e instanceof HostileEntity;
            default -> e instanceof LivingEntity;
        };
    }
}
