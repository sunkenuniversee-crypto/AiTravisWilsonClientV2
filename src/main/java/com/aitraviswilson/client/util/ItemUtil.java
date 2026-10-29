package com.aitraviswilson.client.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

public final class ItemUtil {
    private static final String[] MATERIALS = {"wooden", "stone", "golden", "copper", "iron", "diamond", "netherite"};

    private ItemUtil() {}

    /** 1..7 by tier from the item id prefix (wooden lowest, netherite highest); 0 if unknown. */
    public static int materialScore(ItemStack s) {
        String path = Registries.ITEM.getId(s.getItem()).getPath();
        int score = 0;
        for (int i = 0; i < MATERIALS.length; i++) if (path.startsWith(MATERIALS[i])) score = i + 1;
        return score;
    }

    public static boolean isSpear(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith("_spear");
    }

    public static boolean isAxe(ItemStack s) { return !s.isEmpty() && s.isIn(ItemTags.AXES); }

    public static boolean isMeleeWeapon(ItemStack s) {
        return !s.isEmpty() && (s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.isOf(Items.MACE) || isSpear(s));
    }

    public static boolean isHealingSplash(ItemStack s) {
        if (!s.isOf(Items.SPLASH_POTION)) return false;
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        for (StatusEffectInstance e : c.getEffects()) {
            if (e.getEffectType().equals(StatusEffects.INSTANT_HEALTH)) return true;
        }
        return false;
    }
}
