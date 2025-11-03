package sh.tinywifi.canvasglsl.mixin;

import imgui.ImGui;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.CanvasGLSL;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "method_22676", at = @At("HEAD"), cancellable = true, require = 0)
    private void CanvasGLSL$consumeKeyLegacy(long window, int keyCode, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (CanvasGLSL.IDE.isOverlayVisible() && ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }

    // 1.21.4: onKey has different signature - (long window, int key, int scancode, int action, int modifiers)
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true, require = 0)
    private void CanvasGLSL$consumeKeyModern(long window, int keyCode, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (CanvasGLSL.IDE.isOverlayVisible() && ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "method_22675", at = @At("HEAD"), cancellable = true, require = 0)
    private void CanvasGLSL$consumeCharLegacy(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (CanvasGLSL.IDE.isOverlayVisible() && ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }

    // 1.21.4: onChar has different signature - (long window, int codePoint, int modifiers)
    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true, require = 0)
    private void CanvasGLSL$consumeCharModern(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (CanvasGLSL.IDE.isOverlayVisible() && ImGui.getIO().getWantCaptureKeyboard()) {
            ci.cancel();
        }
    }
}
