package com.aitraviswilson.client.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Approximate end-crystal explosion damage (power 6). Uses the vanilla damage formula, a 12-point
 * line-of-sight exposure estimate and the armour-points reduction. It ignores armour toughness,
 * enchantments, potion effects and totems, so treat it as an estimate.
 */
public final class CombatMath {
    private CombatMath() {}

    public static float exposure(World w, Vec3d center, Box box, Entity ctx) {
        double lx = box.getLengthX(), ly = box.getLengthY(), lz = box.getLengthZ();
        double[] xs = {box.minX + lx * 0.2, box.maxX - lx * 0.2};
        double[] ys = {box.minY + 0.1, box.minY + ly * 0.5, box.maxY - 0.1};
        double[] zs = {box.minZ + lz * 0.2, box.maxZ - lz * 0.2};
        int clear = 0, total = 0;
        for (double x : xs) for (double y : ys) for (double z : zs) {
            total++;
            var hit = w.raycast(new RaycastContext(center, new Vec3d(x, y, z), RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, ctx));
            if (hit.getType() == HitResult.Type.MISS) clear++;
        }
        return clear / (float) total;
    }

    public static float crystalDamage(World w, Vec3d center, LivingEntity target, Box box) {
        return explosionDamage(w, center, target, box, 6.0);
    }

    /** Explosion power 6 = end crystal, 5 = respawn anchor. */
    public static float explosionDamage(World w, Vec3d center, LivingEntity target, Box box, double power) {
        double q = power * 2.0;
        double dist = Math.sqrt(box.getCenter().squaredDistanceTo(center)) / q;
        if (dist > 1.0) return 0f;
        double impact = (1.0 - dist) * exposure(w, center, box, target);
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0);
        switch (w.getDifficulty()) {
            case PEACEFUL -> { return 0f; }
            case EASY -> dmg = Math.min(dmg / 2f + 1f, dmg);
            case HARD -> dmg = dmg * 1.5f;
            default -> { }
        }
        float armor = target.getArmor();
        float reduction = Math.max(armor * 0.2f, Math.min(20f, armor - dmg / 2f)) / 25f;
        return Math.max(0f, dmg * (1f - reduction));
    }
}
