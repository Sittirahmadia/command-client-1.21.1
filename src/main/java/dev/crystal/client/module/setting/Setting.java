package dev.crystal.client.module.setting;

public abstract class Setting<T> {
    protected final String name, description;
    protected T value;
    protected final T defaultValue;

    public Setting(String name, String description, T def) {
        this.name = name; this.description = description;
        this.value = def; this.defaultValue = def;
    }

    public String getName()        { return name; }
    public String getDescription() { return description; }
    public T      getValue()       { return value; }
    public void   setValue(T v)    { this.value = v; }
    public void   reset()          { this.value = defaultValue; }

    // ── Bool ─────────────────────────────────────────────────────────────────
    public static final class BoolSetting extends Setting<Boolean> {
        public BoolSetting(String name, String desc, boolean def) { super(name, desc, def); }
        public void toggle() { value = !value; }
    }

    // ── Number ────────────────────────────────────────────────────────────────
    public static final class NumberSetting extends Setting<Double> {
        private final double min, max, step;

        public NumberSetting(String name, String desc, double def, double min, double max, double step) {
            super(name, desc, def);
            this.min = min; this.max = max; this.step = step;
        }

        @Override
        public void setValue(Double v) {
            double p = step == 0 ? 100 : 1.0 / step;
            this.value = Math.max(min, Math.min(max, Math.round(v * p) / p));
        }

        public double getMin()     { return min; }
        public double getMax()     { return max; }
        public double getStep()    { return step; }
        public int    intValue()   { return (int) Math.round(value); }
        public float  floatValue() { return (float)(double) value; }
        public long   longValue()  { return Math.round(value); }
    }

    // ── Mode ──────────────────────────────────────────────────────────────────
    public static final class ModeSetting<E extends Enum<E>> extends Setting<E> {
        private final E[] values;

        @SuppressWarnings("unchecked")
        public ModeSetting(String name, String desc, E def) {
            super(name, desc, def);
            this.values = (E[]) def.getClass().getEnumConstants();
        }

        public void cycle() { value = values[(value.ordinal() + 1) % values.length]; }
        public E[]  getValues() { return values; }
    }
}
