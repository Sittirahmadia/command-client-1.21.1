package dev.crystal.client.event.events;
import dev.crystal.client.event.Event;
public final class RenderWorldEvent extends Event {
    public final float delta;
    public RenderWorldEvent(float delta) { this.delta = delta; }
}
