package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.Palette;
import com.aitraviswilson.client.util.Projection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Screen-space name tags. Because each tag is drawn at a fixed GUI scale at the projected head
 * position, its size never changes with distance.
 */
public class NameTagsModule extends ClientModule {
    private static final String[] HEART = {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};

    private final NumberSetting scale = add(new NumberSetting("Scale", "Tag size (constant at every distance).", 1.0, 0.5, 2.5, 0.1));
    private final NumberSetting maxDist = add(new NumberSetting("Max Distance", "Hide tags beyond this.", 64, 8, 256, 4));
    private final NumberSetting bgAlpha = add(new NumberSetting("Background Opacity", "0 = none, 255 = solid.", 120, 0, 255, 5));
    private final ModeSetting textColor = add(new ModeSetting("Text Color", "Name colour.", "White", Palette.NAMES));
    private final ModeSetting outlineColor = add(new ModeSetting("Outline Color", "Text outline colour.", "Black", Palette.NAMES));
    private final BoolSetting outline = add(new BoolSetting("Text Outline", "Draw an outline around text.", true));
    private final BoolSetting showPing = add(new BoolSetting("Ping", "Show ping.", true));
    private final BoolSetting showDist = add(new BoolSetting("Distance", "Show distance.", true));
    private final BoolSetting showHealth = add(new BoolSetting("Health", "Hearts and numbers.", true));
    private final BoolSetting showArmor = add(new BoolSetting("Armor", "Armour pieces.", true));
    private final BoolSetting showDura = add(new BoolSetting("Durability %", "Percent under each item.", true));
    private final BoolSetting showHeld = add(new BoolSetting("Held Items", "Main hand and offhand.", true));
    private final BoolSetting showEnch = add(new BoolSetting("Enchant Marker", "Mark enchanted items with an E.", false));

    public NameTagsModule() {
        super("NameTags", "Readable, constant-size player tags.", Category.RENDER);
    }

    @Override
    public void onHud(DrawContext ctx, MinecraftClient mc, float td) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            float dist = mc.player.distanceTo(p);
            if (dist > maxDist.get()) continue;
            Vec3d head = p.getLerpedPos(td).add(0, p.getHeight() + 0.4, 0);
            Projection.Point pt = Projection.project(mc, td, head, sw, sh);
            if (!pt.visible()) continue;
            draw(ctx, mc, p, pt.x(), pt.y(), dist);
        }
    }

    private void draw(DrawContext ctx, MinecraftClient mc, PlayerEntity p, double sx, double sy, float dist) {
        TextRenderer f = mc.textRenderer;
        StringBuilder sb = new StringBuilder(p.getName().getString());
        if (showPing.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (e != null) sb.append("  ").append(e.getLatency()).append("ms");
        }
        if (showDist.get()) sb.append(String.format(Locale.ROOT, "  %.1fm", dist));
        String title = sb.toString();

        float hp = p.getHealth(), abs = p.getAbsorptionAmount();
        String hpText = String.format(Locale.ROOT, "%.1f", hp);
        String absText = abs > 0 ? String.format(Locale.ROOT, "+%.1f", abs) : "";

        List<ItemStack> items = new ArrayList<>();
        if (showArmor.get()) {
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = p.getEquippedStack(s);
                if (!st.isEmpty()) items.add(st);
            }
        }
        if (showHeld.get()) {
            if (!p.getMainHandStack().isEmpty()) items.add(p.getMainHandStack());
            if (!p.getOffHandStack().isEmpty()) items.add(p.getOffHandStack());
        }

        int w = f.getWidth(title);
        if (showHealth.get()) w = Math.max(w, 10 * 8 + 6 + f.getWidth(hpText));
        if (!items.isEmpty()) w = Math.max(w, items.size() * 20);
        int y = 0;
        y -= 10;
        int nameY = y;
        int heartY = 0, absY = 0, itemY = 0;
        if (showHealth.get()) {
            y -= 10;
            heartY = y;
            if (abs > 0) { y -= 9; absY = y; }
        }
        if (!items.isEmpty()) {
            y -= 16 + (showDura.get() ? 10 : 0) + 2;
            itemY = y;
        }

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) sx, (float) sy);
        m.scale(scale.getFloat(), scale.getFloat());
        ctx.fill(-w / 2 - 4, y - 3, w / 2 + 4, 3, ((int) bgAlpha.get().doubleValue() << 24));

        text(ctx, f, title, -f.getWidth(title) / 2, nameY, Palette.get(textColor.get()));
        if (showHealth.get()) {
            int hx = -(10 * 8) / 2 - f.getWidth(hpText) / 2 - 3;
            for (int i = 0; i < 10; i++) {
                float v = hp - 2 * i;
                heart(ctx, hx + i * 8, heartY, 0xFFE53935, 0xFF3A3A3A, v >= 2 ? 2 : v >= 1 ? 1 : 0);
            }
            text(ctx, f, hpText, hx + 10 * 8 + 4, heartY, 0xFFFFFFFF);
            if (abs > 0) {
                for (int i = 0; i < 10; i++) {
                    float v = abs - 2 * i;
                    if (v <= 0) break;
                    heart(ctx, hx + i * 8, absY, 0xFFFFC107, 0xFF3A3A3A, v >= 2 ? 2 : 1);
                }
                text(ctx, f, absText, hx + 10 * 8 + 4, absY, 0xFFFFC107);
            }
        }
        int ix = -items.size() * 20 / 2 + 2;
        for (ItemStack st : items) {
            ctx.drawItem(st, ix, itemY);
            int ty = itemY + 17;
            if (showDura.get() && st.isDamageable()) {
                int pct = 100 - st.getDamage() * 100 / Math.max(1, st.getMaxDamage());
                String s = pct + "%";
                int col = pct > 60 ? 0xFF55FF55 : pct > 30 ? 0xFFFFFF55 : 0xFFFF5555;
                text(ctx, f, s, ix + 8 - f.getWidth(s) / 2, ty, col);
            }
            if (showEnch.get() && st.hasEnchantments()) text(ctx, f, "E", ix + 12, itemY - 1, 0xFF55FFFF);
            ix += 20;
        }
        m.popMatrix();
    }

    private void text(DrawContext ctx, TextRenderer f, String s, int x, int y, int color) {
        if (outline.get()) {
            int oc = Palette.get(outlineColor.get());
            ctx.drawText(f, s, x + 1, y, oc, false);
            ctx.drawText(f, s, x - 1, y, oc, false);
            ctx.drawText(f, s, x, y + 1, oc, false);
            ctx.drawText(f, s, x, y - 1, oc, false);
        }
        ctx.drawText(f, s, x, y, color, false);
    }

    /** fill: 0 empty, 1 half, 2 full. */
    private void heart(DrawContext ctx, int x, int y, int full, int empty, int fill) {
        for (int r = 0; r < HEART.length; r++) {
            for (int c = 0; c < 7; c++) {
                if (HEART[r].charAt(c) != 'X') continue;
                int col = fill == 2 || (fill == 1 && c < 4) ? full : empty;
                ctx.fill(x + c, y + r, x + c + 1, y + r + 1, col);
            }
        }
    }
}
