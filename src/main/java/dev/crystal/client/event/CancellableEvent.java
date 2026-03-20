package dev.crystal.client.event;
public abstract class CancellableEvent extends Event {
    private boolean cancelled;
    public void cancel() { cancelled = true; }
    public boolean isCancelled() { return cancelled; }
}
