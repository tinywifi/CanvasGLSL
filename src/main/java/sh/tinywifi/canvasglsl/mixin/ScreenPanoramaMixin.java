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
        if (!((Object) this instanceof TitleScreen title)) return;

        ShaderBackground module = CanvasGLSL.SHADER_BACKGROUND;
        if (module == null || !module.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        double time = ((TitleScreenShaderAccess) title).canvasglsl$getShaderTime();
        long frame = ((TitleScreenShaderAccess) title).canvasglsl$getShaderFrame();

        Framebuffer fb = mc.getFramebuffer();
        int fbW = fb.textureWidth;
        int fbH = fb.textureHeight;

        // Render the shader background
        module.renderShader(context, fbW, fbH, 1.0f, time, frame);

        // Fallback black background if renderer not ready
        if (!module.isRendererReady()) {
            context.fill(0, 0, width, height, 0xFF000000);
        }

        ci.cancel();
    }

    @Inject(method = "allowRotatingPanorama", at = @At("HEAD"), cancellable = true)
    private void canvasglsl$disablePanorama(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof TitleScreen) {
            cir.setReturnValue(false);
        }
    }
}
