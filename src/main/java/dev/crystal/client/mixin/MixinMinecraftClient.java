package dev.crystal.client.mixin;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method="tick",at=@At("HEAD"))
    private void onPre(CallbackInfo ci){if(CrystalClient.INSTANCE!=null)CrystalClient.INSTANCE.bus.post(new TickEvent(TickEvent.Phase.PRE));}
    @Inject(method="tick",at=@At("TAIL"))
    private void onPost(CallbackInfo ci){if(CrystalClient.INSTANCE!=null)CrystalClient.INSTANCE.bus.post(new TickEvent(TickEvent.Phase.POST));}
}
