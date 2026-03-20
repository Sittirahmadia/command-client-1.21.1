package dev.crystal.client.mixin;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.ClickGUI;
import dev.crystal.client.module.Module;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Keyboard.class)
public class MixinKeyboard {
    @Shadow @Final private MinecraftClient client;
    @Inject(method="onKey",at=@At("HEAD"))
    private void onKey(long window,int key,int scancode,int action,int modifiers,CallbackInfo ci){
        if(CrystalClient.INSTANCE==null||client.player==null||action!=GLFW.GLFW_PRESS)return;
        // Right Shift = open/close GUI
        if(key==GLFW.GLFW_KEY_RIGHT_SHIFT){
            if(client.currentScreen instanceof ClickGUI) client.setScreen(null);
            else client.setScreen(new ClickGUI());
            return;
        }
        // Module keybinds
        for(Module m:CrystalClient.INSTANCE.modules.getModules())
            if(m.getKey()==key)m.toggle();
    }
}
