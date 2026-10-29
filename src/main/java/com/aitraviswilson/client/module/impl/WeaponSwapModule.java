package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.ActionGate;
import com.aitraviswilson.client.util.HotbarUtil;
import com.aitraviswilson.client.util.Targets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Shared "swap to a specific weapon, hit, swap back" state machine used by the attribute-swap
 * modules. Subclasses say when to act, which weapon to use and what the target is.
 */
public abstract class WeaponSwapModule extends ClientModule {
    protected final NumberSetting switchDelay = add(new NumberSetting("Switch Delay (ticks)", "Ticks to wait before switching to the weapon.", 1, 0, 10, 1));
    protected final NumberSetting cps = add(new NumberSetting("Attacks Per Second", "Maximum attack rate.", 8, 1, 20, 1));
    protected final NumberSetting restoreDelay = add(new NumberSetting("Restore Delay (ticks)", "Ticks after a hit before switching back.", 1, 0, 10, 1));
    protected final BoolSetting restore = add(new BoolSetting("Restore Previous Item", "Switch back to the item you were holding.", true));

    private int prevSlot = -1, waited, pendingRestore;
    private long lastAttack, readySince;

    protected WeaponSwapModule(String name, String desc, Category cat) {
        super(name, desc, cat);
    }

    protected abstract boolean wantsAction(MinecraftClient mc, ClientPlayerEntity me, Entity target);
    protected abstract int weaponSlot(ClientPlayerEntity me);
    protected float minCooldown() { return 0f; }
    protected long extraDelayMs() { return 0; }
    protected boolean restoreAfterEachHit() { return true; }
    protected int effectiveSwitchDelay() { return switchDelay.getInt(); }
    protected double effectiveCps() { return cps.get(); }
    protected boolean attackAllowed(MinecraftClient mc, ClientPlayerEntity me, Entity target) { return true; }

    protected Entity crosshairEntity(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof EntityHitResult ehr && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            return ehr.getEntity();
        }
        return null;
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) restoreNow(mc.player);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (pendingRestore > 0) {
            if (--pendingRestore == 0) restoreNow(me);
            return;
        }
        if (!ActionGate.ready(mc)) return;
        Entity target = crosshairEntity(mc);
        boolean want = target != null && Targets.isEnemy(mc, target) && wantsAction(mc, me, target);
        if (!want) {
            readySince = 0;
            if (prevSlot >= 0 && restore.get()) restoreNow(me);
            return;
        }
        int slot = weaponSlot(me);
        if (slot < 0) return;
        int sel = HotbarUtil.selected(me);
        if (sel != slot) {
            if (waited < effectiveSwitchDelay()) { waited++; return; }
            waited = 0;
            if (prevSlot < 0) prevSlot = sel;
            HotbarUtil.select(me, slot);
            ActionGate.lock(mc, 1);
            return;
        }
        if (me.getAttackCooldownProgress(0.5f) < minCooldown()) { readySince = 0; return; }
        long now = System.currentTimeMillis();
        if (readySince == 0) readySince = now;
        if (now - readySince < extraDelayMs()) return;
        if (now - lastAttack < 1000.0 / effectiveCps()) return;
        if (!attackAllowed(mc, me, target)) return;

        mc.interactionManager.attackEntity(me, target);
        me.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
        readySince = 0;
        if (target instanceof PlayerEntity p) Targets.set(p);
        if (restore.get() && restoreAfterEachHit()) pendingRestore = restoreDelay.getInt() + 1;
    }

    private void restoreNow(ClientPlayerEntity me) {
        if (prevSlot >= 0) {
            HotbarUtil.select(me, prevSlot);
            prevSlot = -1;
        }
        waited = 0;
    }
}
