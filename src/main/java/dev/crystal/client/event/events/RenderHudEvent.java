package dev.crystal.client.event.events;
import dev.crystal.client.event.Event;
import net.minecraft.client.gui.DrawContext;
public final class RenderHudEvent extends Event {
    public final DrawContext ctx;
    public final float delta;
    public RenderHudEvent(DrawContext ctx, float delta) { this.ctx = ctx; this.delta = delta; }
}
