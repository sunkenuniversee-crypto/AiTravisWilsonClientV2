package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.ActionGate;
import com.aitraviswilson.client.util.Aim;
import com.aitraviswilson.client.util.CombatMath;
import com.aitraviswilson.client.util.HotbarUtil;
import com.aitraviswilson.client.util.Targets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Places and breaks end crystals around enemy players using an estimated damage model. */
public class FastCrystalModule extends ClientModule {
    private final NumberSetting placeDelay = add(new NumberSetting("Place Delay (ticks)", "Ticks between placements.", 2, 0, 10, 1));
    private final NumberSetting breakDelay = add(new NumberSetting("Break Delay (ticks)", "Ticks between breaks.", 1, 0, 10, 1));
    private final NumberSetting placeRange = add(new NumberSetting("Place Range", "Max distance to place.", 4.5, 1, 6, 0.5));
    private final NumberSetting breakRange = add(new NumberSetting("Attack Range", "Max distance to break.", 4.5, 1, 6, 0.5));
    private final NumberSetting targetRange = add(new NumberSetting("Target Range", "Max distance of enemies considered.", 10, 3, 20, 1));
    private final ModeSetting priority = add(new ModeSetting("Target Priority", "Which enemy to focus.", "Closest", "Closest", "Lowest Health"));
    private final BoolSetting calc = add(new BoolSetting("Damage Calculation", "Use the damage estimate; off = break any crystal in range.", true));
    private final NumberSetting minDamage = add(new NumberSetting("Min Target Damage", "Skip actions that deal less than this to the target.", 6, 0, 36, 0.5));
    private final NumberSetting maxSelf = add(new NumberSetting("Max Self Damage", "Skip actions that would hurt you more than this.", 8, 0, 36, 0.5));
    private final BoolSetting selfProtect = add(new BoolSetting("Self Damage Protection", "Never act if it could kill you.", true));
    private final BoolSetting predictive = add(new BoolSetting("Predictive Placement", "Estimate where the target will be.", true));
    private final NumberSetting predictTicks = add(new NumberSetting("Prediction Ticks", "How far ahead to predict.", 2, 1, 6, 1));
    private final ModeSetting rotate = add(new ModeSetting("Rotation", "Face the crystal / block before acting.", "None", "None", "Snap"));
    private final BoolSetting switchBack = add(new BoolSetting("Switch Back", "Return to the previous hotbar slot after placing.", true));
    private final BoolSetting pauseEat = add(new BoolSetting("Pause While Eating", "Do nothing while eating.", true));
    private final BoolSetting pauseUse = add(new BoolSetting("Pause While Using Items", "Do nothing while any item is being used.", false));

    private int breakTimer, placeTimer;
    private final Map<UUID, Vec3d> lastPos = new HashMap<>();
    private final Map<UUID, Vec3d> velocity = new HashMap<>();

    public FastCrystalModule() {
        super("Fast Crystal", "Automatically places and breaks end crystals.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        lastPos.clear();
        velocity.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null || mc.interactionManager == null || !me.isAlive()) return;
        updateVelocities(mc);
        breakTimer++;
        placeTimer++;
        if (mc.currentScreen != null) return;
        if (me.isUsingItem() && (pauseUse.get() || (pauseEat.get() && me.getActiveItem().contains(DataComponentTypes.FOOD)))) return;

        List<PlayerEntity> targets = findTargets(mc, me);
        if (!targets.isEmpty()) Targets.set(targets.get(0));
        if (breakTimer > breakDelay.getInt()) tryBreak(mc, me, targets);
        if (placeTimer > placeDelay.getInt() && !targets.isEmpty() && ActionGate.ready(mc)) tryPlace(mc, me, targets);
    }

    private void updateVelocities(MinecraftClient mc) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            Vec3d now = new Vec3d(p.getX(), p.getY(), p.getZ());
            Vec3d prev = lastPos.put(p.getUuid(), now);
            velocity.put(p.getUuid(), prev == null ? Vec3d.ZERO : now.subtract(prev));
        }
    }

    private List<PlayerEntity> findTargets(MinecraftClient mc, ClientPlayerEntity me) {
        List<PlayerEntity> out = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (Targets.isEnemy(mc, p) && me.distanceTo(p) <= targetRange.get()) out.add(p);
        }
        Comparator<PlayerEntity> cmp = priority.get().equals("Lowest Health")
                ? Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount())
                : Comparator.comparingDouble(me::distanceTo);
        out.sort(cmp);
        return out.size() > 3 ? new ArrayList<>(out.subList(0, 3)) : out;
    }

    private Box predicted(PlayerEntity t) {
        Box b = t.getBoundingBox();
        if (!predictive.get()) return b;
        Vec3d v = velocity.getOrDefault(t.getUuid(), Vec3d.ZERO);
        double n = predictTicks.getInt();
        return b.offset(v.x * n, 0, v.z * n);
    }

    private boolean selfSafe(MinecraftClient mc, ClientPlayerEntity me, Vec3d center) {
        if (!selfProtect.get()) return true;
        float self = CombatMath.crystalDamage(mc.world, center, me, me.getBoundingBox());
        return self <= maxSelf.get() && self < me.getHealth() + me.getAbsorptionAmount() - 0.5f;
    }

    private float targetDamage(MinecraftClient mc, Vec3d center, List<PlayerEntity> targets) {
        float best = 0;
        for (PlayerEntity t : targets) best = Math.max(best, CombatMath.crystalDamage(mc.world, center, t, predicted(t)));
        return best;
    }

    private void tryBreak(MinecraftClient mc, ClientPlayerEntity me, List<PlayerEntity> targets) {
        if (calc.get() && targets.isEmpty()) return;
        Box area = me.getBoundingBox().expand(breakRange.get() + 1);
        EndCrystalEntity best = null;
        float bestDmg = -1;
        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, area, e -> e.isAlive())) {
            if (me.getEyePos().distanceTo(c.getEntityPos()) > breakRange.get()) continue;
            Vec3d center = new Vec3d(c.getX(), c.getY(), c.getZ());
            if (!selfSafe(mc, me, center)) continue;
            float dmg = calc.get() ? targetDamage(mc, center, targets) : 1f;
            if (calc.get() && dmg < minDamage.get()) continue;
            if (dmg > bestDmg) { bestDmg = dmg; best = c; }
        }
        if (best == null) return;
        if (rotate.get().equals("Snap")) Aim.lookAt(me, best.getEntityPos());
        mc.interactionManager.attackEntity(me, best);
        me.swingHand(Hand.MAIN_HAND);
        breakTimer = 0;
    }

    private void tryPlace(MinecraftClient mc, ClientPlayerEntity me, List<PlayerEntity> targets) {
        Hand hand;
        int slot = -1;
        if (me.getOffHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.OFF_HAND;
        else if (me.getMainHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.MAIN_HAND;
        else {
            slot = HotbarUtil.find(me, s -> s.isOf(Items.END_CRYSTAL));
            if (slot < 0) return;
            hand = Hand.MAIN_HAND;
        }

        BlockPos best = null;
        float bestScore = -1000;
        for (PlayerEntity t : targets) {
            BlockPos base = t.getBlockPos();
            for (int dx = -4; dx <= 4; dx++) for (int dy = -3; dy <= 1; dy++) for (int dz = -4; dz <= 4; dz++) {
                BlockPos pos = base.add(dx, dy, dz);
                var state = mc.world.getBlockState(pos);
                if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) continue;
                Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                if (me.getEyePos().distanceTo(center) > placeRange.get()) continue;
                if (!mc.world.isAir(pos.up())) continue;
                Box space = new Box(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
                if (!mc.world.getOtherEntities(null, space).isEmpty()) continue;
                if (!selfSafe(mc, me, center)) continue;
                float dmg = targetDamage(mc, center, targets);
                if (calc.get() && dmg < minDamage.get()) continue;
                float score = calc.get() ? dmg : -(float) center.distanceTo(t.getEntityPos());
                if (score > bestScore) { bestScore = score; best = pos; }
            }
        }
        if (best == null) return;

        int prev = HotbarUtil.selected(me);
        if (slot >= 0) HotbarUtil.select(me, slot);
        Vec3d hitPos = new Vec3d(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5);
        if (rotate.get().equals("Snap")) Aim.lookAt(me, hitPos);
        ActionResult res = mc.interactionManager.interactBlock(me, hand, new BlockHitResult(hitPos, Direction.UP, best, false));
        if (res.isAccepted()) me.swingHand(hand);
        if (slot >= 0 && switchBack.get()) HotbarUtil.select(me, prev);
        if (slot >= 0) ActionGate.lock(mc, 1);
        placeTimer = 0;
    }
}
