package com.aitraviswilson.client.module.impl;

import com.aitraviswilson.client.gui.ColorUtil;
import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.util.Wire;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleToIntFunction;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** RGB world visuals: block highlight, crystal / anchor outlines and an animated screen-edge glow. */
public class WorldVisualsModule extends ClientModule {
    private final BoolSetting blockHighlight = add(new BoolSetting("Block Highlight", "RGB box on the block you look at.", true));
    private final BoolSetting crystalEsp = add(new BoolSetting("Crystal Outline", "RGB box around end crystals.", true));
    private final BoolSetting anchorEsp = add(new BoolSetting("Anchor Outline", "RGB box around respawn anchors.", true));
    private final BoolSetting glow = add(new BoolSetting("Screen Glow", "Animated RGB glow around the screen edges.", true));
    private final NumberSetting glowSize = add(new NumberSetting("Glow Size", "Edge glow thickness.", 28, 8, 80, 2));
    private final NumberSetting glowStrength = add(new NumberSetting("Glow Strength", "Edge glow opacity.", 70, 10, 200, 5));
    private final NumberSetting speed = add(new NumberSetting("RGB Speed", "Animation speed.", 1.0, 0.1, 4, 0.1));
    private final NumberSetting thickness = add(new NumberSetting("Line Thickness", "Outline width.", 1, 1, 3, 1));
    private final NumberSetting range = add(new NumberSetting("Outline Range", "How far to look for crystals and anchors.", 16, 4, 32, 2));

    private final List<BlockPos> anchors = new ArrayList<>();

    public WorldVisualsModule() {
        super("World Visuals", "RGB highlights and screen glow.", Category.RENDER);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (!anchorEsp.get() || mc.player == null || mc.world == null) { anchors.clear(); return; }
        if (mc.world.getTime() % 20 != 0) return;
        anchors.clear();
        int r = range.getInt();
        BlockPos base = mc.player.getBlockPos();
        for (int x = -r; x <= r; x++) for (int y = -r / 2; y <= r / 2; y++) for (int z = -r; z <= r; z++) {
            BlockPos p = base.add(x, y, z);
            if (mc.world.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR)) anchors.add(p);
        }
    }

    private DoubleToIntFunction rainbow(double offset) {
        double time = System.currentTimeMillis() / 4000.0 * speed.get();
        return t -> 0xFF000000 | ColorUtil.hsvToRgb((float) ((time + offset + t * 0.5) % 1.0), 0.75f, 1f);
    }

    @Override
    public void onHud(DrawContext ctx, MinecraftClient mc, float td) {
        int th = thickness.getInt();
        if (blockHighlight.get() && mc.crosshairTarget instanceof BlockHitResult bhr
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            Wire.box(ctx, mc, td, new Box(bhr.getBlockPos()).expand(0.003), th, rainbow(0));
        }
        if (crystalEsp.get()) {
            Box area = mc.player.getBoundingBox().expand(range.get());
            int n = 0;
            for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, area, e -> e.isAlive())) {
                if (n++ >= 12) break;
                Wire.box(ctx, mc, td, c.getBoundingBox().expand(0.05), th, rainbow(0.3));
            }
        }
        if (anchorEsp.get()) {
            int n = 0;
            for (BlockPos p : anchors) {
                if (n++ >= 12) break;
                Wire.box(ctx, mc, td, new Box(p).expand(0.003), th, rainbow(0.6));
            }
        }
        if (glow.get()) drawGlow(ctx);
    }

    private void drawGlow(DrawContext ctx) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int size = glowSize.getInt();
        double time = System.currentTimeMillis() / 4000.0 * speed.get();
        for (int i = 0; i < size; i += 2) {
            float falloff = 1f - i / (float) size;
            int a = Math.round(glowStrength.get().floatValue() * falloff * falloff);
            if (a <= 0) continue;
            int top = argb(a, time + i * 0.004);
            int right = argb(a, time + 0.25 + i * 0.004);
            int bottom = argb(a, time + 0.5 + i * 0.004);
            int left = argb(a, time + 0.75 + i * 0.004);
            ctx.fill(i, i, w - i, i + 2, top);
            ctx.fill(w - i - 2, i + 2, w - i, h - i - 2, right);
            ctx.fill(i, h - i - 2, w - i, h - i, bottom);
            ctx.fill(i, i + 2, i + 2, h - i - 2, left);
        }
    }

    private static int argb(int a, double hue) {
        return (Math.min(255, a) << 24) | ColorUtil.hsvToRgb((float) (hue % 1.0), 0.8f, 1f);
    }
}
