package dev.crystal.client.module;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String   name;
    private final String   description;
    private final Category category;
    private       int      key;
    private       boolean  enabled;

    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category, int key) {
        this.name = name; this.description = description;
        this.category = category; this.key = key;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    public void onEnable()  { CrystalClient.INSTANCE.bus.subscribe(this); }
    public void onDisable() { CrystalClient.INSTANCE.bus.unsubscribe(this); }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable(); else onDisable();
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    protected <T extends Setting<?>> T register(T s) { settings.add(s); return s; }
    public List<Setting<?>> getSettings() { return settings; }

    /** Find a setting by name (case-insensitive). */
    public Setting<?> getSetting(String name) {
        return settings.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────
    public String   getName()        { return name; }
    public String   getDescription() { return description; }
    public Category getCategory()    { return category; }
    public int      getKey()         { return key; }
    public boolean  isEnabled()      { return enabled; }
    public void     setKey(int k)    { this.key = k; }
    public void     setEnabled(boolean v) { if (v != enabled) toggle(); }
}
