package com.aitraviswilson.client.setting;

import com.google.gson.JsonElement;

/** Base class for every persisted module setting. */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    protected T value;
    private final T defaultValue;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
    public T getDefault() { return defaultValue; }
    public void reset() { this.value = defaultValue; }

    public abstract JsonElement toJson();

    /** Must never throw on bad data; invalid input keeps the current value. */
    public abstract void fromJson(JsonElement element);
}
