package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.module.ModuleManager;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.util.InventoryUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Adds totem count and slot locations to the tooltip of a Totem of Undying. */
public class HoverTotemModule extends ClientModule {
    private final BoolSetting enhanced = add(new BoolSetting("Enhanced Tooltip", "Also list where every totem is.", true));
    private final BoolSetting uses = add(new BoolSetting("Show Pops Left", "Show how many pops you have in reserve.", true));

    public HoverTotemModule() {
        super("Hover Totem", "Totem info when hovering a totem.", Category.PLAYER);
    }

    public static void registerTooltip(ModuleManager manager) {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!stack.isOf(Items.TOTEM_OF_UNDYING)) return;
            ClientModule m = manager.byName("Hover Totem");
            PlayerEntity p = MinecraftClient.getInstance().player;
            if (!(m instanceof HoverTotemModule h) || !h.isEnabled() || p == null) return;
            int total = InventoryUtil.count(p, Items.TOTEM_OF_UNDYING);
            lines.add(Text.literal("Totems owned: " + total).formatted(Formatting.GOLD));
            if (h.uses.get()) lines.add(Text.literal("Pops in reserve: " + Math.max(0, total - 1)).formatted(Formatting.GRAY));
            if (h.enhanced.get()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 36; i++) {
                    if (!p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(i < 9 ? "Hotbar " + (i + 1) : "Inv " + (i - 8));
                }
                if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) sb.append(sb.length() > 0 ? ", Offhand" : "Offhand");
                if (sb.length() > 0) lines.add(Text.literal("Locations: " + sb).formatted(Formatting.YELLOW));
            }
        });
    }
}
