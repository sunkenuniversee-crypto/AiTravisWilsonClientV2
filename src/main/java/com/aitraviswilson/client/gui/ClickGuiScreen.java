package com.aitraviswilson.client.gui;

import com.aitraviswilson.client.AIClient;
import com.aitraviswilson.client.module.Category;
import com.aitraviswilson.client.module.ClientModule;
import com.aitraviswilson.client.setting.BoolSetting;
import com.aitraviswilson.client.setting.KeySetting;
import com.aitraviswilson.client.setting.ModeSetting;
import com.aitraviswilson.client.setting.NumberSetting;
import com.aitraviswilson.client.setting.Setting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Native immediate-mode ClickGUI. Every frame the layout is recomputed and clickable regions are
 * recorded in {@link #hits}; mouse events are then dispatched against that list.
 */
public class ClickGuiScreen extends Screen {
    private static final String[] PAGES = {"Combat", "Render", "Player", "World", "Misc",
        "Appearance", "Configs", "Keybinds"};
    private static final int SIDEBAR_W = 140;
    private static final int F_NONE = 0, F_SEARCH = 1, F_HEX = 2, F_NAME = 3;
    private static final int WHITE = 0xFFFFFFFF;

    private record Hit(int x1, int y1, int x2, int y2, Runnable click, BiConsumer<Double, Double> drag,
                       boolean clipped) {}

    private record AKey(Object o, String s) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Object, Float> anims = new HashMap<>();
    private final Set<ClientModule> expanded = new HashSet<>();
    private final Set<Object> openDropdowns = new HashSet<>();
    private final float[] scroll = new float[PAGES.length];
    private final float[] contentH = new float[PAGES.length];

    private int page = 0;
    private int focus = F_NONE;
    private String search = "";
    private String hexText = "";
    private String themeName = "";
    private int selectedColor = 0;
    private float hue, sat, val;
    private boolean hsvInit = false;
    private ClientModule listeningModule;
    private boolean listeningGui;
    private KeySetting listeningSetting;
    private BiConsumer<Double, Double> activeDrag;
    private int clickButton;
    private String status = "";
    private long statusUntil;
    private String tooltip;
    private float selY = -1;
    private long lastNs;
    private float frameDt, openAnim, pageAnim = 1f, soundPitch = 1f, renderScale = 1f, scaleCx, scaleCy, lastSliderSound;
    private int contentTop;
    private final float[] scrollTarget = new float[PAGES.length];

    // per-frame state
    private int mx, my;
    private int x0, y0, winW, winH;
    private int vx1, vy1, vx2, vy2;
    private boolean hitsEnabled = true;

    public ClickGuiScreen() {
        super(Text.literal("AI Travis Wilson Client"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        ui(0.8f);
        ThemeManager.saveCurrent();
        AIClient.config().save();
        super.close();
    }

    private TextRenderer font() { return MinecraftClient.getInstance().textRenderer; }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void init() {
        ui(1.4f);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        frameDt = dt;
        Theme t = ThemeManager.current();
        if (t.animations) {
            openAnim = Math.min(1f, openAnim + dt * 5f);
            pageAnim = Math.min(1f, pageAnim + dt * 6f);
        } else {
            openAnim = 1f;
            pageAnim = 1f;
        }
        float e = ease(openAnim);

        winW = Math.min(580, width - 16);
        winH = Math.min(380, height - 16);
        x0 = (width - winW) / 2;
        y0 = (height - winH) / 2;
        scaleCx = x0 + winW / 2f;
        scaleCy = y0 + winH / 2f;
        renderScale = 0.96f + 0.04f * e;
        mx = (int) invX(mouseX);
        my = (int) invY(mouseY);
        hits.clear();
        tooltip = null;
        hitsEnabled = true;
        if (!hsvInit) selectColor(0);

        if (t.blur) applyBlur(ctx);
        ctx.fill(0, 0, width, height, ((int) (0x66 * e)) << 24);

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(scaleCx, scaleCy);
        m.scale(renderScale, renderScale);
        m.translate(-scaleCx, -scaleCy);

        int r = t.radius + 2;
        // soft shadow
        for (int i = 8; i >= 1; i--) {
            Draw.rect(ctx, x0 - i, y0 - i + 3, x0 + winW + i, y0 + winH + i + 3, r + i, (Math.max(1, 7 - i) * 3) << 24);
        }
        Draw.rect(ctx, x0 - 1, y0 - 1, x0 + winW + 1, y0 + winH + 1, r + 1, alpha(t.c(Theme.BORDER), t));
        int bgTop = alpha(t.c(Theme.BG), t);
        int bgBot = t.gradient ? alpha(ColorUtil.mix(t.c(Theme.BG), t.fx(t.c(Theme.ACCENT)), 0.14f), t) : bgTop;
        Draw.rectV(ctx, x0, y0, x0 + winW, y0 + winH, r, bgTop, bgBot);

        renderSidebar(ctx, t, dt);
        renderHeader(ctx, t);

        vx1 = x0 + SIDEBAR_W + 12;
        vy1 = y0 + 44;
        vx2 = x0 + winW - 12;
        vy2 = y0 + winH - 10;
        renderContent(ctx, t, dt);
        renderTooltip(ctx, t);
        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            text(ctx, status, vx1, y0 + winH - 8 - font().fontHeight, t.fx(t.c(Theme.ACCENT)));
        }
        m.popMatrix();
    }

    private static float ease(float x) {
        float k = 1f - Math.max(0f, Math.min(1f, x));
        return 1f - k * k * k;
    }

    private double invX(double px) { return (px - scaleCx) / renderScale + scaleCx; }
    private double invY(double py) { return (py - scaleCy) / renderScale + scaleCy; }

    private void setPage(int p) {
        if (p != page || !search.isEmpty()) pageAnim = 0f;
        page = p;
        search = "";
        focus = F_NONE;
    }

    private void ui(float pitch) {
        if (!ThemeManager.current().sounds) return;
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private void renderSidebar(DrawContext ctx, Theme t, float dt) {
        int sx = x0 + 6, sw = SIDEBAR_W - 8;
        int fill = t.gradient
                ? ColorUtil.mix(t.c(Theme.SIDEBAR), t.fx(t.c(Theme.ACCENT)), 0.06f) : t.c(Theme.SIDEBAR);
        Draw.rect(ctx, sx, y0 + 6, sx + sw, y0 + winH - 6, t.radius + 1, alpha(fill, t));

        // branding
        Draw.pfp(ctx, sx + 8, y0 + 14, 28);
        text(ctx, "AI Travis Wilson", sx + 42, y0 + 16, t.c(Theme.TEXT));
        text(ctx, "Client", sx + 42, y0 + 27, t.fx(t.c(Theme.ACCENT)));

        int dim = dimText(t);
        text(ctx, "MODULES", sx + 10, y0 + 54, dim);
        text(ctx, "GENERAL", sx + 10, y0 + 176, dim);

        boolean searching = !search.isEmpty();
        float targetY = sideY(page);
        selY = selY < 0 ? targetY : selY + (targetY - selY) * Math.min(1f, dt * 16f);
        if (!searching) {
            int py = Math.round(selY);
            Draw.rect(ctx, sx + 4, py, sx + sw - 4, py + 18, Math.min(t.radius, 9),
                    ColorUtil.withAlpha(t.fx(t.c(Theme.ACCENT)), 0.22f));
            ctx.fill(sx + 4, py + 4, sx + 6, py + 14, t.fx(t.c(Theme.ACCENT)));
        }
        for (int i = 0; i < PAGES.length; i++) {
            int iy = sideY(i);
            boolean sel = i == page && !searching;
            boolean hov = over(sx + 4, iy, sx + sw - 4, iy + 18, false);
            float ha = animTo(new AKey("side", "h" + i), hov && !sel ? 1f : 0f, frameDt, 0.01f);
            if (ha > 0f) {
                Draw.rect(ctx, sx + 4, iy, sx + sw - 4, iy + 18, Math.min(t.radius, 9),
                        ColorUtil.withAlpha(t.c(Theme.TEXT), 0.07f * ha));
            }
            int col = sel ? t.fx(t.c(Theme.ACCENT)) : (hov ? t.c(Theme.TEXT) : ColorUtil.mix(t.c(Theme.TEXT),
                    t.c(Theme.BG), 0.25f));
            Draw.icon(ctx, i, sx + 12, iy + 4, col);
            text(ctx, PAGES[i], sx + 29, iy + 5, col);
            final int idx = i;
            hit(sx + 4, iy, sx + sw - 4, iy + 18, () -> {
                setPage(idx);
            }, null, false);
        }
    }

    private int sideY(int i) { return i < 5 ? y0 + 66 + i * 20 : y0 + 188 + (i - 5) * 20; }

    private void renderHeader(DrawContext ctx, Theme t) {
        int hx = x0 + SIDEBAR_W + 12;
        boolean searching = !search.isEmpty();
        text(ctx, searching ? "Search results" : PAGES[page], hx, y0 + 15, t.c(Theme.TEXT));
        ctx.fill(hx, y0 + 28, hx + 24, y0 + 30, t.fx(t.c(Theme.ACCENT)));
        fieldBox(ctx, t, F_SEARCH, search, "Search modules...", x0 + winW - 12 - 150, y0 + 10, 150, 18);
    }

    private void renderContent(DrawContext ctx, Theme t, float dt) {
        int viewH = vy2 - vy1;
        float maxScroll = Math.max(0, contentH[page] - viewH);
        scrollTarget[page] = Math.max(0, Math.min(scrollTarget[page], maxScroll));
        scroll[page] = t.animations ? scroll[page] + (scrollTarget[page] - scroll[page]) * Math.min(1f, dt * 14f) : scrollTarget[page];
        if (Math.abs(scrollTarget[page] - scroll[page]) < 0.3f) scroll[page] = scrollTarget[page];
        int top = vy1 - Math.round(scroll[page]) + Math.round((1f - ease(pageAnim)) * 14f);
        contentTop = top;
        ctx.enableScissor(vx1, vy1, vx2, vy2);
        int end;
        if (!search.isEmpty()) end = renderModules(ctx, t, searchResults(), top, dt);
        else if (page < 5) end = renderModules(ctx, t, AIClient.modules().byCategory(Category.values()[page]), top, dt);
        else if (page == 5) end = renderAppearance(ctx, t, top, dt);
        else if (page == 6) end = renderConfigs(ctx, t, top);
        else end = renderKeybinds(ctx, t, top);
        ctx.disableScissor();
        contentH[page] = end - top + 24;
        if (contentH[page] > viewH) {
            float frac = viewH / contentH[page];
            int th = Math.max(16, (int) (viewH * frac));
            int ty = vy1 + (int) ((viewH - th) * (scroll[page] / (contentH[page] - viewH)));
            Draw.rect(ctx, vx2 - 3, ty, vx2, ty + th, 1, ColorUtil.withAlpha(t.fx(t.c(Theme.ACCENT)), 0.6f));
        }
    }

    private List<ClientModule> searchResults() {
        String q = search.toLowerCase(Locale.ROOT);
        List<ClientModule> out = new ArrayList<>();
        for (ClientModule m : AIClient.modules().getModules()) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)
                    || m.getDescription().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------ module pages

    private int renderModules(DrawContext ctx, Theme t, List<ClientModule> list, int y, float dt) {
        int x1 = vx1, x2 = vx2 - 8;
        if (list.isEmpty()) {
            text(ctx, search.isEmpty() ? "No modules in this category yet." : "No modules match your search.",
                    x1 + 2, y + 4, dimText(t));
            return y + 20;
        }
        for (ClientModule m : list) y = moduleCard(ctx, t, m, x1, y, x2, dt) + 6;
        return y;
    }

    private int settingsHeight(ClientModule m) {
        int h = 4 + 20 + 22 + 4;
        for (Setting<?> s : m.getSettings()) {
            if (s instanceof NumberSetting) h += 26;
            else if (s instanceof ModeSetting ms) h += 18 + (openDropdowns.contains(s) ? ms.getModes().size() * 14 : 0);
            else if (s instanceof KeySetting) h += 20;
            else h += 18;
        }
        return h;
    }

    private int moduleCard(DrawContext ctx, Theme t, ClientModule m, int x1, int y, int x2, float dt) {
        final int hh = 24;
        boolean open = expanded.contains(m);
        float expAnim = animTo(new AKey(m, "exp"), open ? settingsHeight(m) : 0, dt, 0.5f);
        float onAnim = animTo(new AKey(m, "on"), m.isEnabled() ? 1f : 0f, dt, 0.01f);
        int total = hh + Math.round(expAnim);
        int rad = Math.min(t.radius, 10);

        Draw.rect(ctx, x1, y, x2, y + total, rad, alpha(t.c(Theme.PANEL), t));
        boolean hov = over(x1, y, x2, y + hh, true);
        float cardHover = animTo(new AKey(m, "hov"), hov ? 1f : 0f, dt, 0.01f);
        if (cardHover > 0f) Draw.rect(ctx, x1, y, x2, y + total, rad, ColorUtil.withAlpha(WHITE, 0.05f * cardHover));
        if (hov) tooltip = m.getDescription();
        if (m.isEnabled()) ctx.fill(x1 + 1, y + 6, x1 + 3, y + hh - 6, t.fx(t.c(Theme.ACCENT)));

        int nameCol = ColorUtil.mix(ColorUtil.mix(t.c(Theme.TEXT), t.c(Theme.BG), 0.2f), t.c(Theme.TEXT), onAnim);
        text(ctx, m.getName(), x1 + 10, y + 8, nameCol);
        if (m.getKeyCode() >= 0) {
            String k = "[" + Draw.keyName(m.getKeyCode()) + "]";
            text(ctx, k, x2 - 56 - font().getWidth(k), y + 8, dimText(t));
        }
        // header click: left toggles, right expands
        hit(x1, y, x2, y + hh, () -> {
            if (clickButton == 1) toggleExpanded(m);
            else {
                m.toggle();
                soundPitch = m.isEnabled() ? 1.3f : 0.85f;
            }
        }, null, true);
        // expand arrow
        text(ctx, open ? "v" : ">", x2 - 48, y + 8, dimText(t));
        hit(x2 - 54, y, x2 - 40, y + hh, () -> toggleExpanded(m), null, true);
        switchAt(ctx, t, x2 - 36, y + 6, 26, 12, onAnim);

        if (expAnim > 1f) {
            ctx.enableScissor(x1, y + hh, x2, y + total);
            hitsEnabled = open && Math.abs(expAnim - settingsHeight(m)) < 1.5f;
            int ry = y + hh + 4, sx1 = x1 + 10, sx2 = x2 - 10;
            for (Setting<?> s : m.getSettings()) ry = settingRow(ctx, t, s, sx1, ry, sx2, dt);
            ry = keyRow(ctx, t, m, sx1, ry, sx2);
            button(ctx, t, sx1, ry + 2, 110, 16, "Reset Settings", () -> {
                m.resetSettings();
                flash("Reset " + m.getName());
            });
            hitsEnabled = true;
            ctx.disableScissor();
        }
        return y + total;
    }

    private void toggleExpanded(ClientModule m) {
        if (!expanded.remove(m)) expanded.add(m);
    }

    private int settingRow(DrawContext ctx, Theme t, Setting<?> s, int x1, int y, int x2, float dt) {
        if (s instanceof BoolSetting b) {
            return boolRow(ctx, t, b, b.getName(), x1, y, x2, b.get(), () -> b.set(!b.get()), b.getDescription(), dt);
        }
        if (s instanceof NumberSetting n) {
            double range = n.getMax() - n.getMin();
            String vt = n.getStep() >= 1 ? String.valueOf(n.getInt()) : String.format(Locale.ROOT, "%.1f", n.get());
            return slider(ctx, t, n.getName(), vt, x1, y, x2, (n.get() - n.getMin()) / range,
                    f -> n.set(n.getMin() + range * f), n.getDescription());
        }
        if (s instanceof ModeSetting md) {
            boolean open = openDropdowns.contains(s);
            text(ctx, md.getName(), x1, y + 5, t.c(Theme.TEXT));
            if (over(x1, y, x2, y + 18, true)) tooltip = md.getDescription();
            int px1 = x2 - 96;
            Draw.outlined(ctx, px1, y + 1, x2, y + 17, Math.min(t.radius, 6), alpha(t.c(Theme.BORDER), t),
                    t.c(Theme.BUTTON));
            text(ctx, md.get(), px1 + 6, y + 5, t.c(Theme.TEXT));
            text(ctx, open ? "^" : "v", x2 - 12, y + 5, dimText(t));
            hit(px1, y + 1, x2, y + 17, () -> {
                if (!openDropdowns.remove(s)) openDropdowns.add(s);
            }, null, true);
            int ny = y + 18;
            if (open) {
                for (String opt : md.getModes()) {
                    boolean sel = opt.equals(md.get());
                    boolean hv = over(px1, ny, x2, ny + 14, true);
                    Draw.rect(ctx, px1, ny, x2, ny + 14, Math.min(t.radius, 4),
                            hv ? ColorUtil.mix(t.c(Theme.BUTTON), t.fx(t.c(Theme.ACCENT)), 0.3f) : t.c(Theme.BUTTON));
                    text(ctx, opt, px1 + 6, ny + 3, sel ? t.fx(t.c(Theme.ACCENT)) : t.c(Theme.TEXT));
                    hit(px1, ny, x2, ny + 14, () -> {
                        md.set(opt);
                        openDropdowns.remove(s);
                    }, null, true);
                    ny += 14;
                }
            }
            return ny;
        }
        if (s instanceof KeySetting ks) {
            text(ctx, ks.getName(), x1, y + 6, t.c(Theme.TEXT));
            if (over(x1, y, x2, y + 20, true)) tooltip = ks.getDescription();
            boolean listening = listeningSetting == ks;
            keyButton(ctx, t, x2 - 90, y + 2, 90, 16, listening ? "Press a key..." : Draw.keyName(ks.get()),
                    listening, () -> {
                        listeningSetting = ks;
                        listeningModule = null;
                        listeningGui = false;
                    });
            return y + 20;
        }
        return y;
    }

    private int keyRow(DrawContext ctx, Theme t, ClientModule m, int x1, int y, int x2) {
        text(ctx, "Keybind", x1, y + 6, t.c(Theme.TEXT));
        boolean listening = listeningModule == m;
        keyButton(ctx, t, x2 - 90, y + 2, 90, 16, listening ? "Press a key..." : Draw.keyName(m.getKeyCode()),
                listening, () -> {
                    listeningModule = m;
                    listeningGui = false;
                });
        return y + 20;
    }

    // ------------------------------------------------------------------ appearance page

    private int renderAppearance(DrawContext ctx, Theme t, int y, float dt) {
        int x1 = vx1, x2 = vx2 - 8;
        y = label(ctx, t, "PRESETS", x1, y);
        int bx = x1, by = y;
        for (String name : ThemeManager.presetNames()) {
            int w = font().getWidth(name) + 26;
            if (bx + w > x2) { bx = x1; by += 22; }
            final String n = name;
            int acc = ThemeManager.preset(name).c(Theme.ACCENT);
            button(ctx, t, bx, by, w, 18, "", () -> {
                ThemeManager.applyPreset(n);
                selectColor(selectedColor);
            });
            ctx.fill(bx + 6, by + 6, bx + 12, by + 12, acc);
            text(ctx, name, bx + 16, by + 5, t.c(Theme.TEXT));
            bx += w + 6;
        }
        y = by + 28;

        y = label(ctx, t, "MY THEMES", x1, y);
        fieldBox(ctx, t, F_NAME, themeName, "Theme name", x1, y, 130, 18);
        button(ctx, t, x1 + 136, y, 46, 18, "Save", () -> flash(ThemeManager.saveCustom(themeName)
                ? "Saved theme \"" + ThemeManager.sanitize(themeName) + "\"" : "Enter a theme name first"));
        button(ctx, t, x1 + 188, y, 96, 18, "Reset Default", () -> {
            ThemeManager.resetToDefault();
            selectColor(selectedColor);
            flash("Theme reset");
        });
        y += 26;
        List<String> custom = ThemeManager.customNames();
        if (custom.isEmpty()) {
            text(ctx, "No saved themes yet.", x1 + 2, y + 2, dimText(t));
            y += 18;
        }
        for (String n : custom) {
            Draw.outlined(ctx, x1, y, x2, y + 20, Math.min(t.radius, 8), alpha(t.c(Theme.BORDER), t),
                    alpha(t.c(Theme.PANEL), t));
            text(ctx, n, x1 + 8, y + 6, t.c(Theme.TEXT));
            button(ctx, t, x2 - 112, y + 2, 50, 16, "Load", () -> {
                flash(ThemeManager.loadCustom(n) ? "Loaded \"" + n + "\"" : "Could not load theme");
                selectColor(selectedColor);
            });
            button(ctx, t, x2 - 58, y + 2, 54, 16, "Delete", () -> {
                ThemeManager.deleteCustom(n);
                flash("Deleted \"" + n + "\"");
            });
            y += 24;
        }
        y += 6;

        y = label(ctx, t, "COLORS", x1, y);
        int colW = (x2 - x1 - 6) / 2;
        for (int i = 0; i < Theme.COLOR_NAMES.length; i++) {
            int cx = x1 + (i % 2) * (colW + 6);
            int cy = y + (i / 2) * 22;
            boolean sel = i == selectedColor;
            Draw.outlined(ctx, cx, cy, cx + colW, cy + 20, Math.min(t.radius, 8),
                    sel ? t.fx(t.c(Theme.ACCENT)) : alpha(t.c(Theme.BORDER), t), alpha(t.c(Theme.PANEL), t));
            ctx.fill(cx + 6, cy + 4, cx + 18, cy + 16, 0xFF000000 | t.colors[i]);
            text(ctx, Theme.COLOR_NAMES[i], cx + 24, cy + 6, t.c(Theme.TEXT));
            String hx = "#" + ColorUtil.hex(t.colors[i]);
            text(ctx, hx, cx + colW - 6 - font().getWidth(hx), cy + 6, dimText(t));
            final int idx = i;
            hit(cx, cy, cx + colW, cy + 20, () -> selectColor(idx), null, true);
        }
        y += ((Theme.COLOR_NAMES.length + 1) / 2) * 22 + 8;

        // colour editor: HSV square + hue bar, RGB sliders, HEX field
        int ex = x1, ey = y, svW = 130, svH = 90;
        for (int i = 0; i < svW; i++) {
            float s = i / (float) (svW - 1);
            ctx.fillGradient(ex + i, ey, ex + i + 1, ey + svH, 0xFF000000 | ColorUtil.hsvToRgb(hue, s, 1f),
                    0xFF000000 | ColorUtil.hsvToRgb(hue, s, 0f));
        }
        int mxp = ex + Math.round(sat * (svW - 1)), myp = ey + Math.round((1f - val) * (svH - 1));
        ctx.fill(mxp - 3, myp - 3, mxp + 4, myp + 4, WHITE);
        ctx.fill(mxp - 2, myp - 2, mxp + 3, myp + 3, 0xFF000000 | ColorUtil.hsvToRgb(hue, sat, val));
        hit(ex, ey, ex + svW, ey + svH, null, (px, py) -> {
            sat = (float) Math.max(0, Math.min(1, (px - ex) / (svW - 1)));
            val = 1f - (float) Math.max(0, Math.min(1, (py - ey) / (svH - 1)));
            applyHsv();
        }, true);
        int hy = ey + svH + 6;
        for (int i = 0; i < svW; i++) ctx.fill(ex + i, hy, ex + i + 1, hy + 10, 0xFF000000 | ColorUtil.hsvToRgb(i / (float) (svW - 1), 1f, 1f));
        int hxp = ex + Math.round(hue * (svW - 1));
        ctx.fill(hxp - 1, hy - 2, hxp + 2, hy + 12, WHITE);
        hit(ex, hy - 2, ex + svW, hy + 12, null, (px, py) -> {
            hue = (float) Math.max(0, Math.min(0.9999, (px - ex) / (svW - 1)));
            applyHsv();
        }, true);

        int rx = ex + svW + 16, rx2 = Math.max(rx + 100, x2);
        int cur = t.colors[selectedColor];
        int ry = ey;
        final int sel = selectedColor;
        ry = slider(ctx, t, "Red", String.valueOf((cur >> 16) & 255), rx, ry, rx2, ((cur >> 16) & 255) / 255.0,
                f -> setChannel(sel, 16, f), null);
        ry = slider(ctx, t, "Green", String.valueOf((cur >> 8) & 255), rx, ry, rx2, ((cur >> 8) & 255) / 255.0,
                f -> setChannel(sel, 8, f), null);
        ry = slider(ctx, t, "Blue", String.valueOf(cur & 255), rx, ry, rx2, (cur & 255) / 255.0,
                f -> setChannel(sel, 0, f), null);
        text(ctx, "HEX  #", rx, ry + 6, t.c(Theme.TEXT));
        if (focus != F_HEX) hexText = ColorUtil.hex(cur);
        fieldBox(ctx, t, F_HEX, hexText, "RRGGBB", rx + font().getWidth("HEX  #"), ry + 2, 70, 16);
        y = Math.max(hy + 18, ry + 26);

        y = label(ctx, t, "STYLE", x1, y);
        y = slider(ctx, t, "Transparency (panel opacity)", Math.round(t.opacity * 100) + "%", x1, y, x2,
                (t.opacity - 0.2) / 0.8, f -> t.opacity = (float) (0.2 + 0.8 * f), null);
        y = slider(ctx, t, "Corner radius", String.valueOf(t.radius), x1, y, x2, t.radius / 14.0,
                f -> t.radius = (int) Math.round(f * 14), null);
        y = boolRow(ctx, t, "gradient", "Gradients", x1, y, x2, t.gradient, () -> t.gradient = !t.gradient,
                "Subtle accent gradient on the window and sidebar.", dt);
        y = boolRow(ctx, t, "rainbow", "Rainbow effect", x1, y, x2, t.rainbow, () -> t.rainbow = !t.rainbow,
                "Cycles accent, toggle and slider colours.", dt);
        y = boolRow(ctx, t, "blur", "Background blur", x1, y, x2, t.blur, () -> t.blur = !t.blur,
                "Blur the game behind the GUI.", dt);
        y = boolRow(ctx, t, "animations", "GUI animations", x1, y, x2, t.animations, () -> t.animations = !t.animations,
                "Open, page, hover and scroll animations.", dt);
        y = boolRow(ctx, t, "sounds", "GUI sounds", x1, y, x2, t.sounds, () -> t.sounds = !t.sounds,
                "Click and toggle sounds.", dt);
        y += 6;

        y = label(ctx, t, "LIVE PREVIEW", x1, y);
        Draw.outlined(ctx, x1, y, x2, y + 34, Math.min(t.radius, 10), alpha(t.c(Theme.BORDER), t), alpha(t.c(Theme.PANEL), t));
        text(ctx, "Sample module", x1 + 10, y + 12, t.c(Theme.TEXT));
        switchAt(ctx, t, x1 + 110, y + 11, 26, 12, 1f);
        switchAt(ctx, t, x1 + 142, y + 11, 26, 12, 0f);
        Draw.rect(ctx, x1 + 180, y + 15, x1 + 260, y + 19, 2, t.c(Theme.BUTTON));
        Draw.rect(ctx, x1 + 180, y + 15, x1 + 228, y + 19, 2, t.fx(t.c(Theme.SLIDER)));
        Draw.outlined(ctx, x1 + 276, y + 8, x1 + 336, y + 26, Math.min(t.radius, 8), alpha(t.c(Theme.BORDER), t), t.c(Theme.BUTTON));
        text(ctx, "Button", x1 + 292, y + 14, t.c(Theme.TEXT));
        return y + 40;
    }

    private void selectColor(int i) {
        selectedColor = i;
        float[] hsv = ColorUtil.rgbToHsv(ThemeManager.current().colors[i]);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        hsvInit = true;
    }

    private void applyHsv() {
        ThemeManager.current().colors[selectedColor] = ColorUtil.hsvToRgb(hue, sat, val);
    }

    private void setChannel(int slot, int shift, double f) {
        Theme t = ThemeManager.current();
        int c = t.colors[slot];
        int v = (int) Math.round(Math.max(0, Math.min(1, f)) * 255);
        t.colors[slot] = (c & ~(255 << shift)) | (v << shift);
        if (slot == selectedColor) {
            float[] hsv = ColorUtil.rgbToHsv(t.colors[slot]);
            if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
        }
    }

    // ------------------------------------------------------------------ configs & keybinds pages

    private int renderConfigs(DrawContext ctx, Theme t, int y) {
        int x1 = vx1;
        y = label(ctx, t, "CONFIGURATION", x1, y);
        var cfg = AIClient.config();
        var export = cfg.getFile().resolveSibling("export.json");
        y = wideButton(ctx, t, x1, y, "Save Config", () -> {
            cfg.save();
            ThemeManager.saveCurrent();
            flash("Config saved");
        });
        y = wideButton(ctx, t, x1, y, "Load Config", () -> flash(cfg.readFrom(cfg.getFile(), false)
                ? "Config loaded" : "No valid config found"));
        y = wideButton(ctx, t, x1, y, "Export Config", () -> flash(cfg.exportTo(export)
                ? "Exported to config/aitraviswilson/export.json" : "Export failed"));
        y = wideButton(ctx, t, x1, y, "Import Config", () -> flash(cfg.importFrom(export)
                ? "Imported export.json" : "No valid export.json found"));
        y = wideButton(ctx, t, x1, y, "Reset All Module Settings", () -> {
            cfg.resetAll();
            flash("All module settings reset");
        });
        y = wideButton(ctx, t, x1, y, "Reset Theme", () -> {
            ThemeManager.resetToDefault();
            selectColor(selectedColor);
            flash("Theme reset");
        });
        text(ctx, "Files live in .minecraft/config/aitraviswilson/", x1 + 2, y + 6, dimText(t));
        return y + 20;
    }

    private int wideButton(DrawContext ctx, Theme t, int x, int y, String label, Runnable r) {
        button(ctx, t, x, y, 190, 20, label, r);
        return y + 26;
    }

    private int renderKeybinds(DrawContext ctx, Theme t, int y) {
        int x1 = vx1, x2 = vx2 - 8;
        y = label(ctx, t, "KEYBINDS  (click a key, then press the new key; Backspace clears)", x1, y);
        Draw.outlined(ctx, x1, y, x2, y + 22, Math.min(t.radius, 8), alpha(t.c(Theme.BORDER), t), alpha(t.c(Theme.PANEL), t));
        text(ctx, "Open ClickGUI", x1 + 10, y + 7, t.c(Theme.TEXT));
        keyButton(ctx, t, x2 - 100, y + 3, 92, 16, listeningGui ? "Press a key..." : Draw.keyName(AIClient.config().getGuiKey()),
                listeningGui, () -> {
                    listeningGui = true;
                    listeningModule = null;
                });
        y += 26;
        for (ClientModule m : AIClient.modules().getModules()) {
            Draw.outlined(ctx, x1, y, x2, y + 22, Math.min(t.radius, 8), alpha(t.c(Theme.BORDER), t), alpha(t.c(Theme.PANEL), t));
            text(ctx, m.getName(), x1 + 10, y + 7, t.c(Theme.TEXT));
            boolean listening = listeningModule == m;
            keyButton(ctx, t, x2 - 100, y + 3, 92, 16, listening ? "Press a key..." : Draw.keyName(m.getKeyCode()),
                    listening, () -> {
                        listeningModule = m;
                        listeningGui = false;
                    });
            y += 26;
        }
        return y;
    }

    // ------------------------------------------------------------------ widgets

    private int label(DrawContext ctx, Theme t, String s, int x, int y) {
        text(ctx, s, x + 2, y + 2, dimText(t));
        return y + 16;
    }

    private void button(DrawContext ctx, Theme t, int x, int y, int w, int h, String label, Runnable r) {
        boolean hov = over(x, y, x + w, y + h, true);
        float ha = animTo(new AKey("btn" + label + (x - vx1), "h" + (y - contentTop)), hov ? 1f : 0f, frameDt, 0.01f);
        int fill = ColorUtil.mix(t.c(Theme.BUTTON), t.fx(t.c(Theme.ACCENT)), 0.25f * ha);
        Draw.outlined(ctx, x, y, x + w, y + h, Math.min(t.radius, 8), alpha(t.c(Theme.BORDER), t), fill);
        if (!label.isEmpty()) {
            text(ctx, label, x + (w - font().getWidth(label)) / 2, y + (h - font().fontHeight) / 2 + 1, t.c(Theme.TEXT));
        }
        hit(x, y, x + w, y + h, r, null, true);
    }

    private void keyButton(DrawContext ctx, Theme t, int x, int y, int w, int h, String label, boolean active, Runnable r) {
        boolean hov = over(x, y, x + w, y + h, true);
        Draw.outlined(ctx, x, y, x + w, y + h, Math.min(t.radius, 8),
                active ? t.fx(t.c(Theme.ACCENT)) : alpha(t.c(Theme.BORDER), t),
                ColorUtil.mix(t.c(Theme.BUTTON), t.fx(t.c(Theme.ACCENT)), 0.2f * animTo(new AKey("key" + label + (x - vx1), "h" + (y - contentTop)), hov ? 1f : 0f, frameDt, 0.01f)));
        text(ctx, label, x + (w - font().getWidth(label)) / 2, y + (h - font().fontHeight) / 2 + 1, t.c(Theme.TEXT));
        hit(x, y, x + w, y + h, r, null, true);
    }

    private void fieldBox(DrawContext ctx, Theme t, int id, String value, String placeholder, int x, int y, int w, int h) {
        boolean focused = focus == id;
        Draw.outlined(ctx, x, y, x + w, y + h, Math.min(t.radius, 8),
                focused ? t.fx(t.c(Theme.ACCENT)) : alpha(t.c(Theme.BORDER), t), t.c(Theme.BUTTON));
        String shown = value;
        while (font().getWidth(shown) > w - 14 && !shown.isEmpty()) shown = shown.substring(1);
        int ty = y + (h - font().fontHeight) / 2 + 1;
        if (value.isEmpty() && !focused) text(ctx, placeholder, x + 6, ty, dimText(t));
        else text(ctx, shown + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""), x + 6, ty, t.c(Theme.TEXT));
        hit(x, y, x + w, y + h, () -> focus = id, null, id != F_SEARCH);
    }

    private void switchAt(DrawContext ctx, Theme t, int x, int y, int w, int h, float anim) {
        int track = ColorUtil.mix(t.c(Theme.TOGGLE_OFF), t.fx(t.c(Theme.TOGGLE_ON)), anim);
        Draw.rect(ctx, x, y, x + w, y + h, h / 2, track);
        int k = h - 4;
        int kx = x + 2 + Math.round((w - h) * anim);
        Draw.rect(ctx, kx, y + 2, kx + k, y + 2 + k, k / 2, WHITE);
    }

    private int boolRow(DrawContext ctx, Theme t, Object key, String label, int x1, int y, int x2, boolean value,
                        Runnable toggle, String tip, float dt) {
        float a = animTo(new AKey(key, "bool"), value ? 1f : 0f, dt, 0.01f);
        text(ctx, label, x1, y + 5, t.c(Theme.TEXT));
        switchAt(ctx, t, x2 - 26, y + 3, 26, 12, a);
        if (over(x1, y, x2, y + 18, true) && tip != null) tooltip = tip;
        hit(x1, y, x2, y + 18, () -> {
            toggle.run();
            soundPitch = value ? 0.85f : 1.3f;
        }, null, true);
        return y + 18;
    }

    private int slider(DrawContext ctx, Theme t, String label, String valueText, int x1, int y, int x2, double frac,
                       DoubleConsumer onFrac, String tip) {
        frac = Math.max(0, Math.min(1, frac));
        text(ctx, label, x1, y + 2, t.c(Theme.TEXT));
        text(ctx, valueText, x2 - font().getWidth(valueText), y + 2, dimText(t));
        int tx1 = x1, tx2 = x2, ty = y + 14;
        Draw.rect(ctx, tx1, ty, tx2, ty + 4, 2, t.c(Theme.BUTTON));
        int fx = tx1 + (int) Math.round((tx2 - tx1) * frac);
        if (fx > tx1) Draw.rect(ctx, tx1, ty, fx, ty + 4, 2, t.fx(t.c(Theme.SLIDER)));
        Draw.rect(ctx, fx - 4, ty - 2, fx + 4, ty + 6, 4, WHITE);
        if (over(x1, y, x2, y + 24, true) && tip != null) tooltip = tip;
        hit(x1, y + 8, x2, y + 24, null, (px, py) -> {
            double f = Math.max(0, Math.min(1, (px - tx1) / (double) (tx2 - tx1)));
            onFrac.accept(f);
            if (Math.abs(f - lastSliderSound) > 0.05) {
                lastSliderSound = (float) f;
                ui(1.5f);
            }
        }, true);
        return y + 26;
    }

    private void renderTooltip(DrawContext ctx, Theme t) {
        if (tooltip == null || tooltip.isEmpty()) return;
        int w = font().getWidth(tooltip) + 12;
        int tx = Math.min(mx + 12, width - w - 4), ty = Math.min(my + 14, height - 22);
        Draw.outlined(ctx, tx, ty, tx + w, ty + 16, Math.min(t.radius, 6), t.fx(t.c(Theme.ACCENT)), 0xF0000000 | t.colors[Theme.BG]);
        text(ctx, tooltip, tx + 6, ty + 4, t.c(Theme.TEXT));
    }

    // ------------------------------------------------------------------ helpers

    private void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(font(), s, x, y, color, false);
    }

    private int dimText(Theme t) { return ColorUtil.mix(t.c(Theme.TEXT), t.c(Theme.BG), 0.5f); }

    private int alpha(int argb, Theme t) { return ColorUtil.withAlpha(argb, t.opacity); }

    private boolean over(int x1, int y1, int x2, int y2, boolean clipped) {
        if (mx < x1 || mx >= x2 || my < y1 || my >= y2) return false;
        return !clipped || (mx >= vx1 && mx < vx2 && my >= vy1 && my < vy2);
    }

    private void hit(int x1, int y1, int x2, int y2, Runnable click, BiConsumer<Double, Double> drag, boolean clipped) {
        if (hitsEnabled) hits.add(new Hit(x1, y1, x2, y2, click, drag, clipped));
    }

    private float animTo(Object key, float target, float dt, float eps) {
        if (!ThemeManager.current().animations) {
            anims.put(key, target);
            return target;
        }
        Float cur = anims.get(key);
        float v = cur == null ? target : cur;
        v += (target - v) * Math.min(1f, dt * 14f);
        if (Math.abs(target - v) < eps) v = target;
        anims.put(key, v);
        return v;
    }

    private void flash(String msg) {
        status = msg;
        statusUntil = System.currentTimeMillis() + 3500;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        clickButton = click.button();
        double px = invX(click.x()), py = invY(click.y());
        soundPitch = 1f;
        focus = F_NONE;
        listeningModule = null;
        listeningGui = false;
        listeningSetting = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (px < h.x1 || px >= h.x2 || py < h.y1 || py >= h.y2) continue;
            if (h.clipped && (px < vx1 || px >= vx2 || py < vy1 || py >= vy2)) continue;
            if (h.click != null) {
                h.click.run();
                ui(soundPitch);
            }
            if (h.drag != null) {
                activeDrag = h.drag;
                h.drag.accept(px, py);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (activeDrag != null) {
            activeDrag.accept(invX(click.x()), invY(click.y()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        activeDrag = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double smx = invX(mouseX), smy = invY(mouseY);
        if (smx >= vx1 && smx < vx2 && smy >= vy1 && smy < vy2) {
            scrollTarget[page] -= (float) (verticalAmount * 28);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

        if (listeningModule != null || listeningGui || listeningSetting != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                boolean clear = key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
                if (listeningSetting != null) listeningSetting.set(clear ? -1 : key);
                else if (listeningModule != null) listeningModule.setKeyCode(clear ? -1 : key);
                else if (!clear) AIClient.config().setGuiKey(key);
            }
            listeningModule = null;
            listeningGui = false;
            listeningSetting = null;
            return true;
        }
        if (focus != F_NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                focus = F_NONE;
                return true;
            }
            String cur = focus == F_SEARCH ? search : focus == F_HEX ? hexText : themeName;
            int max = focus == F_SEARCH ? 24 : focus == F_HEX ? 6 : 24;
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!cur.isEmpty()) cur = cur.substring(0, cur.length() - 1);
            } else {
                char ch = keyToChar(key, shift);
                if (ch != 0 && cur.length() < max) {
                    if (focus == F_HEX) {
                        ch = Character.toUpperCase(ch);
                        if (Character.digit(ch, 16) < 0) ch = 0;
                    } else if (focus == F_SEARCH) {
                        ch = Character.toLowerCase(ch);
                    }
                    if (ch != 0) cur += ch;
                }
            }
            if (focus == F_SEARCH) { search = cur; scroll[page] = 0; scrollTarget[page] = 0; }
            else if (focus == F_HEX) {
                hexText = cur;
                int rgb = ColorUtil.parseHex(cur);
                if (rgb >= 0) {
                    ThemeManager.current().colors[selectedColor] = rgb;
                    float[] hsv = ColorUtil.rgbToHsv(rgb);
                    if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                }
            } else themeName = cur;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == AIClient.config().getGuiKey()) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || (key == GLFW.GLFW_KEY_TAB && !shift)) {
            setPage((page + 1) % PAGES.length);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || (key == GLFW.GLFW_KEY_TAB && shift)) {
            setPage((page + PAGES.length - 1) % PAGES.length);
            return true;
        }
        return super.keyPressed(input);
    }

    private static char keyToChar(int key, boolean shift) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char c = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return shift ? Character.toUpperCase(c) : c;
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return (char) ('0' + (key - GLFW.GLFW_KEY_0));
        if (key == GLFW.GLFW_KEY_SPACE) return ' ';
        if (key == GLFW.GLFW_KEY_MINUS) return shift ? '_' : '-';
        return 0;
    }
}
