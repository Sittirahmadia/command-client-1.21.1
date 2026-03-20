package dev.crystal.client.mixin;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.RenderWorldEvent;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method="renderWorld",at=@At("TAIL"))
    private void onRender(RenderTickCounter counter,CallbackInfo ci){
        if(CrystalClient.INSTANCE!=null)
            CrystalClient.INSTANCE.bus.post(new RenderWorldEvent(counter.getTickDelta(false)));
    }
}
