package dev.crystal.client.mixin;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.modules.combat.Hitboxes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(method="getBoundingBox",at=@At("RETURN"),cancellable=true)
    private void onBBox(CallbackInfoReturnable<Box> cir){
        if(CrystalClient.INSTANCE==null)return;
        Hitboxes hb=CrystalClient.INSTANCE.modules.get(Hitboxes.class);
        if(hb==null||!hb.isEnabled())return;
        Entity self=(Entity)(Object)this;
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||self==mc.player)return;
        double exp=hb.getExpansion(self);
        if(exp<=0)return;
        cir.setReturnValue(cir.getReturnValue().expand(exp,0,exp));
    }
}
