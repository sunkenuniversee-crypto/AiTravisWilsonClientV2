package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.ActionGate;
import com.aitraviswilson.client.util.HotbarUtil;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * After you place a respawn anchor, puts a glowstone block on the side of it facing you, when that
 * spot is valid. This is a shield block; it does not guarantee any damage reduction.
 */
public class AutoSafeAnchorModule extends ClientModule {
    private final NumberSetting range = add(new NumberSetting("Range", "Max distance to place.", 4.5, 1, 5, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Placement Delay (ticks)", "Ticks before placing after an anchor appears.", 2, 1, 20, 1));

    private final Set<BlockPos> known = new HashSet<>();
    private BlockPos pending;
    private int wait;
    private long lastHeldAnchorTick = -1000;

    public AutoSafeAnchorModule() {
        super("Auto Safe Anchor", "Places glowstone between a new anchor and you.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        known.clear();
        pending = null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        long now = mc.world.getTime();
        if (me.getMainHandStack().isOf(Items.RESPAWN_ANCHOR) || me.getOffHandStack().isOf(Items.RESPAWN_ANCHOR)) lastHeldAnchorTick = now;

        if (now % 4 == 0) scan(mc, me, now);
        if (pending == null) return;
        if (--wait > 0 || !ActionGate.ready(mc)) return;
        BlockPos anchor = pending;
        pending = null;
        place(mc, me, anchor);
    }

    private void scan(MinecraftClient mc, ClientPlayerEntity me, long now) {
        int r = (int) Math.ceil(range.get());
        Set<BlockPos> seen = new HashSet<>();
        BlockPos base = me.getBlockPos();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            BlockPos p = base.add(x, y, z);
            if (!mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR)) continue;
            seen.add(p);
            if (!known.contains(p) && now - lastHeldAnchorTick <= 20 && pending == null) {
                pending = p;
                wait = delay.getInt();
            }
        }
        known.clear();
        known.addAll(seen);
    }

    private void place(MinecraftClient mc, ClientPlayerEntity me, BlockPos anchor) {
        Vec3d d = me.getEntityPos().subtract(Vec3d.ofCenter(anchor));
        Direction dir = Math.abs(d.x) > Math.abs(d.z)
                ? (d.x > 0 ? Direction.EAST : Direction.WEST) : (d.z > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos spot = anchor.offset(dir);
        BlockPos floor = spot.down();
        if (!mc.world.isAir(spot)) return;
        if (!mc.world.getBlockState(floor).isSolidBlock(mc.world, floor)) return;
        Box box = new Box(spot);
        if (me.getBoundingBox().intersects(box) || !mc.world.getOtherEntities(null, box).isEmpty()) return;
        Vec3d hitPos = Vec3d.ofCenter(floor).add(0, 0.5, 0);
        if (me.getEyePos().distanceTo(hitPos) > range.get()) return;
        int slot = HotbarUtil.find(me, s -> s.isOf(Items.GLOWSTONE));
        Hand hand = Hand.MAIN_HAND;
        boolean offhand = me.getOffHandStack().isOf(Items.GLOWSTONE);
        if (offhand) hand = Hand.OFF_HAND;
        else if (slot < 0) return;
        int prev = HotbarUtil.selected(me);
        if (!offhand) HotbarUtil.select(me, slot);
        if (mc.interactionManager.interactBlock(me, hand, new BlockHitResult(hitPos, Direction.UP, floor, false)).isAccepted()) {
            me.swingHand(hand);
        }
        if (!offhand) {
            HotbarUtil.select(me, prev);
            ActionGate.lock(mc, 1);
        }
    }
}
