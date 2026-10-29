package com.aitraviswilson.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double def, double min, double max, double step) {
        super(name, description, clamp(def, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public int getInt() { return (int) Math.round(value); }
    public float getFloat() { return value.floatValue(); }

    @Override
    public void set(Double v) {
        double c = clamp(v, min, max);
        if (step > 0) {
            c = Math.round(c / step) * step;
            c = clamp(c, min, max);
        }
        super.set(c);
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            double d = e.getAsDouble();
            if (!Double.isNaN(d) && !Double.isInfinite(d)) set(d);
        }
    }
}
