package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.KeySetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.ActionGate;
import com.aitraviswilson.client.util.CombatMath;
import com.aitraviswilson.client.util.HotbarUtil;
import com.aitraviswilson.client.util.Targets;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * Fills respawn anchors with glowstone and, optionally, detonates them. Detonation only works
 * outside the Nether (in the Nether the anchor just sets a spawn point), so it is skipped there.
 */
public class AutoAnchorModule extends ClientModule {
    private final NumberSetting range = add(new NumberSetting("Range", "Only anchors within this distance.", 4.5, 1, 6, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Action Delay (ticks)", "Ticks between fill / detonate actions.", 2, 1, 20, 1));
    private final NumberSetting speed = add(new NumberSetting("Interactions / Cycle", "Glowstone uses per fill action.", 1, 1, 4, 1));
    private final NumberSetting maxCharges = add(new NumberSetting("Target Charges", "Fill to this charge level before detonating.", 4, 1, 4, 1));
    private final BoolSetting detonate = add(new BoolSetting("Detonate", "Explode the anchor once it is charged.", true));
    private final BoolSetting requireTarget = add(new BoolSetting("Require Enemy In Blast", "Only detonate if the estimate says it would hurt an enemy.", true));
    private final NumberSetting minDamage = add(new NumberSetting("Min Target Damage", "Minimum estimated damage to an enemy.", 4, 0, 36, 0.5));
    private final BoolSetting selfProtect = add(new BoolSetting("Self Damage Protection", "Never detonate if it could kill you.", true));
    private final NumberSetting maxSelf = add(new NumberSetting("Max Self Damage", "Never detonate above this estimated self damage.", 8, 0, 36, 0.5));
    private final BoolSetting requireKey = add(new BoolSetting("Require Activation Key", "Only act while the key is held.", true));
    private final KeySetting key = add(new KeySetting("Activation Key", "Hold to fill and detonate nearby anchors.", GLFW.GLFW_KEY_R));
    private final BoolSetting fillLooked = add(new BoolSetting("Fill Anchor You Look At", "Also fill the anchor under your crosshair without the key (never detonates).", false));
    private final BoolSetting switchBack = add(new BoolSetting("Switch Back", "Return to the previous slot.", true));

    private int timer;

    public AutoAnchorModule() {
        super("Auto Anchor", "Fills and detonates respawn anchors.", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (++timer < delay.getInt() || !ActionGate.ready(mc)) return;

        boolean keyActive = !requireKey.get() || key.isDown();
        BlockPos target = null;
        boolean allowDetonate = keyActive;
        if (keyActive) {
            target = nearestAnchor(mc, me);
        } else if (fillLooked.get() && mc.crosshairTarget instanceof BlockHitResult bhr
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockPos p = bhr.getBlockPos();
            if (mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR) && charge(mc, p) < maxCharges.getInt()
                    && me.getEyePos().distanceTo(Vec3d.ofCenter(p)) <= range.get()) target = p;
        }
        if (target == null) return;

        if (charge(mc, target) < maxCharges.getInt()) fill(mc, me, target);
        else if (allowDetonate && detonate.get()) explode(mc, me, target);
    }

    private int charge(MinecraftClient mc, BlockPos p) {
        return mc.world.getBlockState(p).get(RespawnAnchorBlock.CHARGES);
    }

    private BlockPos nearestAnchor(MinecraftClient mc, ClientPlayerEntity me) {
        int r = (int) Math.ceil(range.get());
        BlockPos base = me.getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            BlockPos p = base.add(x, y, z);
            if (!mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR)) continue;
            // Skip anchors that are charged but that we are not allowed to detonate.
            if (charge(mc, p) >= maxCharges.getInt() && (!detonate.get() || mc.world.getRegistryKey() == World.NETHER)) continue;
            double d = me.getEyePos().distanceTo(Vec3d.ofCenter(p));
            if (d <= range.get() && d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private void fill(MinecraftClient mc, ClientPlayerEntity me, BlockPos target) {
        Hand hand;
        int slot = -1;
        if (me.getOffHandStack().isOf(Items.GLOWSTONE)) hand = Hand.OFF_HAND;
        else if (me.getMainHandStack().isOf(Items.GLOWSTONE)) hand = Hand.MAIN_HAND;
        else {
            slot = HotbarUtil.find(me, s -> s.isOf(Items.GLOWSTONE));
            if (slot < 0) return;
            hand = Hand.MAIN_HAND;
        }
        int prev = HotbarUtil.selected(me);
        if (slot >= 0) HotbarUtil.select(me, slot);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(target).add(0, 0.5, 0), Direction.UP, target, false);
        int c = charge(mc, target);
        for (int i = 0; i < speed.getInt() && c < maxCharges.getInt(); i++, c++) {
            if (mc.interactionManager.interactBlock(me, hand, hit).isAccepted()) me.swingHand(hand);
        }
        if (slot >= 0 && switchBack.get()) HotbarUtil.select(me, prev);
        if (slot >= 0) ActionGate.lock(mc, 1);
        timer = 0;
    }

    private void explode(MinecraftClient mc, ClientPlayerEntity me, BlockPos target) {
        if (mc.world.getRegistryKey() == World.NETHER) return;
        Vec3d center = Vec3d.ofCenter(target);
        if (selfProtect.get()) {
            float self = CombatMath.explosionDamage(mc.world, center, me, me.getBoundingBox(), 5.0);
            if (self > maxSelf.get() || self >= me.getHealth() + me.getAbsorptionAmount() - 0.5f) return;
        }
        if (requireTarget.get()) {
            float best = 0;
            PlayerEntity who = null;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (!Targets.isEnemy(mc, p)) continue;
                float d = CombatMath.explosionDamage(mc.world, center, p, p.getBoundingBox(), 5.0);
                if (d > best) { best = d; who = p; }
            }
            if (who == null || best < minDamage.get()) return;
            Targets.set(who);
        }
        // Any item except glowstone triggers the explosion; keep the current slot when possible.
        int prev = HotbarUtil.selected(me);
        int slot = -1;
        if (me.getMainHandStack().isOf(Items.GLOWSTONE)) {
            slot = HotbarUtil.find(me, s -> !s.isOf(Items.GLOWSTONE));
            if (slot < 0) return;
            HotbarUtil.select(me, slot);
        }
        BlockHitResult hit = new BlockHitResult(center.add(0, 0.5, 0), Direction.UP, target, false);
        if (mc.interactionManager.interactBlock(me, Hand.MAIN_HAND, hit).isAccepted()) me.swingHand(Hand.MAIN_HAND);
        if (slot >= 0 && switchBack.get()) HotbarUtil.select(me, prev);
        if (slot >= 0) ActionGate.lock(mc, 1);
        timer = 0;
    }
}
