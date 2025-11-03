package sh.tinywifi.canvasglsl.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.modules.ShaderBackground;
import sh.tinywifi.canvasglsl.bridge.TitleScreenShaderAccess;

@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {
    @Shadow protected int width;
    @Shadow protected int height;

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void canvasglsl$drawCustomPanorama(DrawContext context, float delta, CallbackInfo ci) {
        ShaderBackground module = CanvasGLSL.SHADER_BACKGROUND;
        if (module == null || !module.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        // Get time and frame from TitleScreen if available, otherwise use fallback
        double time = 0.0;
        long frame = 0;
        if ((Object) this instanceof TitleScreen title) {
            time = ((TitleScreenShaderAccess) title).canvasglsl$getShaderTime();
            frame = ((TitleScreenShaderAccess) title).canvasglsl$getShaderFrame();
        } else {
            // For non-title screens, use system time from renderer if available
            var renderer = module.getRenderer();
            if (renderer != null) {
                time = (System.nanoTime() - renderer.getStartTimeNanos()) / 1_000_000_000.0;
                frame = renderer.getFrameCounter();
            }
        }

        Framebuffer fb = mc.getFramebuffer();
        int fbW = fb.textureWidth;
        int fbH = fb.textureHeight;

        // Enable forced main framebuffer binding for panorama background rendering
        // This ensures the shader is visible even if Minecraft has bound an intermediate buffer
        var renderer = module.getRenderer();
        if (renderer != null && renderer.getCanvas() != null) {
            renderer.getCanvas().setForceMainFramebuffer(true);
        }

        // ALWAYS call renderShader() to allow compilation to happen on first frame
        // The method will handle showing default panorama if shader isn't ready yet
        module.renderShader(context, fbW, fbH, 1.0f, time, frame);

        // Disable forced framebuffer binding after rendering
        if (renderer != null && renderer.getCanvas() != null) {
            renderer.getCanvas().setForceMainFramebuffer(false);
        }

        // Only cancel default panorama if shader is actually ready to render
        // Otherwise let the default panorama show through
        if (module.isRendererReady()) {
            ci.cancel();
        }
    }

    // 1.21: allowRotatingPanorama method doesn't exist in this version
    // The panorama rendering is handled differently - no need for this mixin
}
