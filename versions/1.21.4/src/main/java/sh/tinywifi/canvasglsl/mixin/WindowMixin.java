package sh.tinywifi.canvasglsl.mixin;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.render.GlobalState;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "swapBuffers", at = @At("HEAD"))
    public void CanvasGLSL_swapBuffers(CallbackInfo ci) {
        GlobalState.swapFrame();
    }
}
