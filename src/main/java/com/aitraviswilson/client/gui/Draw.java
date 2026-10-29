package com.aitraviswilson.client.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Small drawing helpers built only on DrawContext.fill and drawTexture. */
public final class Draw {
    public static final Identifier PFP = Identifier.of("aitraviswilson", "textures/gui/pfp.png");

    private Draw() {}

    private static int rowInset(int i, int h, int r) {
        int d = Math.min(i, h - 1 - i);
        if (d >= r) return 0;
        double dy = r - d - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** Rounded rectangle with a vertical colour gradient (top == bottom for a flat fill). */
    public static void rectV(DrawContext c, int x1, int y1, int x2, int y2, int r, int top, int bottom) {
        int h = y2 - y1;
        if (h <= 0 || x2 <= x1) return;
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, h / 2)));
        for (int i = 0; i < h; i++) {
            int inset = r == 0 ? 0 : rowInset(i, h, r);
            int col = top == bottom ? top : ColorUtil.mix(top, bottom, h <= 1 ? 0 : i / (float) (h - 1));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, col);
        }
    }

    public static void rect(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        rectV(c, x1, y1, x2, y2, r, color, color);
    }

    public static void outlined(DrawContext c, int x1, int y1, int x2, int y2, int r, int border, int fill) {
        rect(c, x1, y1, x2, y2, r, border);
        rect(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), fill);
    }

    public static void pfp(DrawContext c, int x, int y, int size) {
        float s = size / 128f;
        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(s, s);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, PFP, 0, 0, 0f, 0f, 128, 128, 128, 128);
        c.getMatrices().popMatrix();
    }

    /** 10x10 hand-drawn category icons. Index matches the sidebar order. */
    public static void icon(DrawContext c, int id, int x, int y, int col) {
        switch (id) {
            case 0 -> { // combat: sword
                for (int i = 0; i < 8; i++) c.fill(x + i + 1, y + 8 - i, x + i + 3, y + 9 - i, col);
                c.fill(x + 1, y + 6, x + 5, y + 7, col);
                c.fill(x + 2, y + 5, x + 3, y + 9, col);
            }
            case 1 -> { // render: eye
                c.fill(x + 1, y + 3, x + 9, y + 4, col);
                c.fill(x + 1, y + 7, x + 9, y + 8, col);
                c.fill(x, y + 4, x + 1, y + 7, col);
                c.fill(x + 9, y + 4, x + 10, y + 7, col);
                c.fill(x + 4, y + 4, x + 6, y + 7, col);
            }
            case 2 -> { // player
                c.fill(x + 3, y, x + 7, y + 4, col);
                c.fill(x + 1, y + 5, x + 9, y + 10, col);
            }
            case 3 -> { // world
                c.fill(x, y, x + 10, y + 1, col);
                c.fill(x, y + 9, x + 10, y + 10, col);
                c.fill(x, y, x + 1, y + 10, col);
                c.fill(x + 9, y, x + 10, y + 10, col);
                c.fill(x + 4, y, x + 6, y + 10, col);
                c.fill(x, y + 4, x + 10, y + 6, col);
            }
            case 4 -> { // misc: three dots
                c.fill(x, y + 4, x + 2, y + 6, col);
                c.fill(x + 4, y + 4, x + 6, y + 6, col);
                c.fill(x + 8, y + 4, x + 10, y + 6, col);
            }
            case 5 -> { // appearance: palette
                c.fill(x + 2, y, x + 8, y + 10, col);
                c.fill(x, y + 2, x + 10, y + 8, col);
                c.fill(x + 6, y + 6, x + 8, y + 8, 0xFF000000);
            }
            case 6 -> { // configs: sliders
                c.fill(x, y + 1, x + 10, y + 2, col);
                c.fill(x, y + 5, x + 10, y + 6, col);
                c.fill(x, y + 9, x + 10, y + 10, col);
                c.fill(x + 6, y, x + 8, y + 3, col);
                c.fill(x + 2, y + 4, x + 4, y + 7, col);
                c.fill(x + 6, y + 8, x + 8, y + 10, col);
            }
            default -> { // keybinds: keyboard
                c.fill(x, y + 2, x + 10, y + 3, col);
                c.fill(x, y + 8, x + 10, y + 9, col);
                c.fill(x, y + 2, x + 1, y + 9, col);
                c.fill(x + 9, y + 2, x + 10, y + 9, col);
                c.fill(x + 2, y + 4, x + 4, y + 5, col);
                c.fill(x + 6, y + 4, x + 8, y + 5, col);
                c.fill(x + 3, y + 6, x + 7, y + 7, col);
            }
        }
    }

    public static String keyName(int k) {
        if (k < 0) return "None";
        switch (k) {
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R-Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L-Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R-Alt";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            case GLFW.GLFW_KEY_INSERT: return "Insert";
            case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End";
            default: break;
        }
        if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F12) return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(k, 0);
        return n != null ? n.toUpperCase() : "Key " + k;
    }
}
