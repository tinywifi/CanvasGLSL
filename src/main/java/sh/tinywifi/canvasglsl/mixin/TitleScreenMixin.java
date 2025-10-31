package sh.tinywifi.canvasglsl.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.modules.ShaderBackground;
import sh.tinywifi.canvasglsl.render.GlobalState;

@Mixin(RotatingCubeMapRenderer.class)
public class TitleScreenMixin {
    @Shadow private MinecraftClient client;

    @Unique private double time = 0f;
    @Unique private long frame = 0;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void increaseTime(DrawContext context, int mouseX, int mouseY, boolean hovered, CallbackInfo ci) {
        time += 0.016f; // Approximate delta time
        frame += 1;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void CanvasGLSL$renderOverlay(DrawContext context, int mouseX, int mouseY, boolean hovered, CallbackInfo ci) {
        ShaderBackground module = CanvasGLSL.SHADER_BACKGROUND;
        if (module != null && module.isEnabled()) {
            Window window = client.getWindow();
            int width = window.getWidth();
            int height = window.getHeight();

            module.renderShader(context, width, height, 1.0f, time / 60.0, frame);
        }

        GlobalState.nextFrame();
    }
}
