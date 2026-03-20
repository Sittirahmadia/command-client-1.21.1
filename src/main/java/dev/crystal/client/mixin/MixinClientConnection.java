package dev.crystal.client.mixin;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.ReceivePacketEvent;
import dev.crystal.client.event.events.SendPacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientConnection.class)
public class MixinClientConnection {
    @Inject(method="send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",at=@At("HEAD"),cancellable=true)
    private void onSend(Packet<?> packet,PacketCallbacks cb,boolean flush,CallbackInfo ci){
        if(CrystalClient.INSTANCE==null)return;
        var e=CrystalClient.INSTANCE.bus.post(new SendPacketEvent(packet));
        if(e.isCancelled())ci.cancel();
    }
    @Inject(method="channelRead0",at=@At("HEAD"),cancellable=true)
    private void onReceive(ChannelHandlerContext ctx,Packet<?> packet,CallbackInfo ci){
        if(CrystalClient.INSTANCE==null)return;
        var e=CrystalClient.INSTANCE.bus.post(new ReceivePacketEvent(packet));
        if(e.isCancelled())ci.cancel();
    }
}
