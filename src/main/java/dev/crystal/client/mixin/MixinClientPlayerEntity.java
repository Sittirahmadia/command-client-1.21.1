package dev.crystal.client.mixin;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.PlayerMoveEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Inject(method="sendMovementPackets",at=@At("HEAD"),cancellable=true)
    private void onMove(CallbackInfo ci){
        if(CrystalClient.INSTANCE==null)return;
        var e=CrystalClient.INSTANCE.bus.post(new PlayerMoveEvent());
        if(e.isCancelled())ci.cancel();
    }
}
