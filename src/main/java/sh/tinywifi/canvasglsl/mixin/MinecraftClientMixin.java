package sh.tinywifi.canvasglsl.mixin;

import java.lang.ReflectiveOperationException;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.gui.ImGuiManager;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow @Final private Window window;

    @Unique private static final java.lang.reflect.Method canvasglsl$isKeyPressedWindowMethod;
    @Unique private static final java.lang.reflect.Method canvasglsl$isKeyPressedHandleMethod;

    private boolean CanvasGLSL$insertDown;
    private boolean CanvasGLSL$escapeDown;

    static {
        canvasglsl$isKeyPressedWindowMethod = canvasglsl$resolveIsKeyPressed(Window.class);
        canvasglsl$isKeyPressedHandleMethod = canvasglsl$resolveIsKeyPressed(Long.TYPE);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void CanvasGLSL$initImGui(RunArgs args, CallbackInfo ci) {
        ImGuiManager.get().init(window.getHandle());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void CanvasGLSL$shutdownImGui(CallbackInfo ci) {
        ImGuiManager.get().shutdown();
        CanvasGLSL.SHADER_BACKGROUND.shutdown();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void CanvasGLSL$tick(CallbackInfo ci) {
        boolean insertPressed = canvasglsl$isKeyPressed(window, GLFW.GLFW_KEY_INSERT);
        if (insertPressed && !CanvasGLSL$insertDown) {
            CanvasGLSL.IDE.toggleOverlayVisible();
        }
        CanvasGLSL$insertDown = insertPressed;

        boolean escapePressed = canvasglsl$isKeyPressed(window, GLFW.GLFW_KEY_ESCAPE);
        if (escapePressed && !CanvasGLSL$escapeDown && CanvasGLSL.IDE.isOverlayVisible()) {
            CanvasGLSL.IDE.setOverlayVisible(false);
        }
        CanvasGLSL$escapeDown = escapePressed;
    }

    @Unique
    private boolean canvasglsl$isKeyPressed(Window window, int key) {
        if (canvasglsl$isKeyPressedWindowMethod != null) {
            try {
                return (boolean) canvasglsl$isKeyPressedWindowMethod.invoke(null, window, key);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (canvasglsl$isKeyPressedHandleMethod != null) {
            try {
                long handle = window.getHandle();
                return (boolean) canvasglsl$isKeyPressedHandleMethod.invoke(null, handle, key);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        long handle = window.getHandle();
        return GLFW.glfwGetKey(handle, key) != GLFW.GLFW_RELEASE;
    }

    @Unique
    private static java.lang.reflect.Method canvasglsl$resolveIsKeyPressed(Class<?> parameterType) {
        try {
            return InputUtil.class.getMethod("isKeyPressed", parameterType, int.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

