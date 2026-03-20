package dev.crystal.client.event.events;
import dev.crystal.client.event.CancellableEvent;
import net.minecraft.network.packet.Packet;
public final class ReceivePacketEvent extends CancellableEvent {
    public final Packet<?> packet;
    public ReceivePacketEvent(Packet<?> p) { this.packet = p; }
}
