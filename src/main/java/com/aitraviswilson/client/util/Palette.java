package com.aitraviswilson.client.util;

public final class Palette {
    public static final String[] NAMES = {"White", "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta", "Orange", "Gray", "Black"};
    private static final int[] COLORS = {0xFFFFFFFF, 0xFFFF5555, 0xFF55FF55, 0xFF5599FF, 0xFFFFFF55, 0xFF55FFFF,
        0xFFFF55FF, 0xFFFFAA00, 0xFFAAAAAA, 0xFF000000};

    private Palette() {}

    public static int get(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return COLORS[i];
        return 0xFFFFFFFF;
    }
}
