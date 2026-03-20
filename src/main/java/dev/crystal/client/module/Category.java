package dev.crystal.client.module;

public enum Category {
    COMBAT  ("Combat",   0xFFE74C3C),
    MOVEMENT("Movement", 0xFF2ECC71),
    RENDER  ("Render",   0xFF3498DB),
    MISC    ("Misc",     0xFF9B59B6);

    public final String name;
    public final int    color;
    Category(String name, int color) { this.name = name; this.color = color; }
}
