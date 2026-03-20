package dev.crystal.client.event.events;
import dev.crystal.client.event.CancellableEvent;
public final class ChatInputEvent extends CancellableEvent {
    public final String message;
    public ChatInputEvent(String msg) { this.message = msg; }
}
