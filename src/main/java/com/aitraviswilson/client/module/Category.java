package com.aitraviswilson.client.module;

public enum Category {
    COMBAT("Combat"),
    RENDER("Render"),
    PLAYER("Player"),
    WORLD("World"),
    MISC("Misc");

    private final String displayName;

    Category(String displayName) { this.displayName = displayName; }

    public String getDisplayName() { return displayName; }
}
