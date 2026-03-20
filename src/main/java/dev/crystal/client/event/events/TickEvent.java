package dev.crystal.client.event.events;
import dev.crystal.client.event.Event;
public final class TickEvent extends Event {
    public enum Phase { PRE, POST }
    public final Phase phase;
    public TickEvent(Phase p) { this.phase = p; }
}
