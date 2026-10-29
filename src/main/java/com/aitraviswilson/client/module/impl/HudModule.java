package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.module.ModuleManager;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.InventoryUtil;
import com.aitraviswilson.client.util.ItemUtil;
import com.aitraviswilson.client.util.Targets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class HudModule extends ClientModule {
    private final ModuleManager manager;

    private final BoolSetting showModules = add(new BoolSetting("Active Modules", "Show enabled modules.", true));
    private final BoolSetting showFps = add(new BoolSetting("FPS", "Show FPS.", true));
    private final BoolSetting showPing = add(new BoolSetting("Ping", "Show ping.", true));
    private final BoolSetting showCoords = add(new BoolSetting("Coordinates", "Show coordinates.", true));
    private final BoolSetting showTotems = add(new BoolSetting("Totem Count", "Show totem count.", true));
    private final BoolSetting showPotions = add(new BoolSetting("Potion Count", "Show healing splash potion count.", true));
    private final BoolSetting showArmor = add(new BoolSetting("Armor Durability", "Show armour pieces with durability.", true));
    private final BoolSetting showTarget = add(new BoolSetting("Target Info", "Show the current target.", true));
    private final NumberSetting infoX = add(new NumberSetting("Info X", "Info block X.", 4, 0, 1000, 1));
    private final NumberSetting infoY = add(new NumberSetting("Info Y", "Info block Y.", 4, 0, 1000, 1));
    private final NumberSetting modulesY = add(new NumberSetting("Modules Y", "Module list Y.", 4, 0, 1000, 1));
    private final NumberSetting armorX = add(new NumberSetting("Armor X", "Armour block X.", 4, 0, 1000, 1));
    private final NumberSetting armorY = add(new NumberSetting("Armor Y", "Armour block Y.", 80, 0, 1000, 1));
    private final NumberSetting targetX = add(new NumberSetting("Target X", "Target block X.", 4, 0, 1000, 1));
    private final NumberSetting targetY = add(new NumberSetting("Target Y", "Target block Y.", 140, 0, 1000, 1));

    private static final int WHITE = 0xFFFFFFFF;

    public HudModule(ModuleManager manager) {
        super("HUD", "On-screen information.", Category.RENDER);
        this.manager = manager;
        setEnabled(true);
    }

    @Override
    public void onHud(DrawContext ctx, MinecraftClient mc, float td) {
        if (mc.player == null) return;
        var font = mc.textRenderer;

        List<String> lines = new ArrayList<>();
        if (showFps.get()) lines.add("FPS: " + mc.getCurrentFps());
        if (showPing.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) lines.add("Ping: " + entry.getLatency() + " ms");
        }
        if (showCoords.get()) {
            lines.add(String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }
        if (showTotems.get()) lines.add("Totems: " + InventoryUtil.count(mc.player, Items.TOTEM_OF_UNDYING));
        if (showPotions.get()) {
            int n = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (ItemUtil.isHealingSplash(s)) n += s.getCount();
            }
            lines.add("Health Pots: " + n);
        }
        int y = infoY.getInt();
        for (String line : lines) {
            ctx.drawTextWithShadow(font, line, infoX.getInt(), y, WHITE);
            y += font.fontHeight + 2;
        }

        if (showArmor.get()) {
            int ay = armorY.getInt();
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = mc.player.getEquippedStack(slot);
                if (st.isEmpty()) continue;
                ctx.drawItem(st, armorX.getInt(), ay);
                if (st.isDamageable()) {
                    int pct = 100 - st.getDamage() * 100 / Math.max(1, st.getMaxDamage());
                    int col = pct > 60 ? 0xFF55FF55 : pct > 30 ? 0xFFFFFF55 : 0xFFFF5555;
                    ctx.drawTextWithShadow(font, pct + "%", armorX.getInt() + 20, ay + 4, col);
                }
                ay += 18;
            }
        }

        if (showTarget.get()) {
            PlayerEntity t = Targets.get();
            if (t == null && mc.crosshairTarget instanceof EntityHitResult ehr
                    && mc.crosshairTarget.getType() == HitResult.Type.ENTITY && ehr.getEntity() instanceof PlayerEntity pe) t = pe;
            if (t != null && t != mc.player) {
                int ty = targetY.getInt(), tx = targetX.getInt();
                ctx.drawTextWithShadow(font, "Target: " + t.getName().getString(), tx, ty, WHITE);
                float abs = t.getAbsorptionAmount();
                ctx.drawTextWithShadow(font, String.format(Locale.ROOT, "HP: %.1f%s", t.getHealth(),
                        abs > 0 ? String.format(Locale.ROOT, " +%.1f", abs) : ""), tx, ty + 11, 0xFFFF6666);
                ctx.drawTextWithShadow(font, String.format(Locale.ROOT, "Dist: %.1f", mc.player.distanceTo(t)), tx, ty + 22, WHITE);
            }
        }

        if (showModules.get()) {
            List<String> names = new ArrayList<>();
            for (ClientModule m : manager.getModules()) {
                if (m.isEnabled() && m != this) names.add(m.getName());
            }
            names.sort(Comparator.comparingInt((String s) -> font.getWidth(s)).reversed());
            int ry = modulesY.getInt();
            int right = ctx.getScaledWindowWidth() - 4;
            for (String n : names) {
                ctx.drawTextWithShadow(font, n, right - font.getWidth(n), ry, WHITE);
                ry += font.fontHeight + 2;
            }
        }
    }
}
