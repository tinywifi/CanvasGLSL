package sh.tinywifi.canvasglsl.mixin;

import imgui.ImGui;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.CanvasGLSL;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "method_22684", at = @At("HEAD"), cancellable = true, require = 0)
    private void CanvasGLSL$consumeMouseButtonLegacy(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (CanvasGLSL.IDE.isOverlayVisible() && ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void CanvasGLSL$consumeMouseButtonModern(long window, @Coerce Object input, int phase, CallbackInfo ci) {
        if (CanvasGLSL.IDE.isOverlayVisible() && ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void CanvasGLSL$consumeScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (CanvasGLSL.IDE.isOverlayVisible() && ImGui.getIO().getWantCaptureMouse()) {
            ci.cancel();
        }
    }
}
