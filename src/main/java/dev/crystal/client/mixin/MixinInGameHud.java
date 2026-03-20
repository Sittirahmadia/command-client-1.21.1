package dev.crystal.client.mixin;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.RenderHudEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public class MixinInGameHud {
    @Inject(method="render",at=@At("TAIL"))
    private void onRender(DrawContext ctx,RenderTickCounter counter,CallbackInfo ci){
        if(CrystalClient.INSTANCE!=null)
            CrystalClient.INSTANCE.bus.post(new RenderHudEvent(ctx,counter.getTickDelta(false)));
    }
}
