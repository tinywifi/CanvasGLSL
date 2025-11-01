package sh.tinywifi.canvasglsl.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.bridge.TitleScreenShaderAccess;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen implements TitleScreenShaderAccess {
    @Unique private double canvasglsl$time = 0.0;
    @Unique private long canvasglsl$frame = 0;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void CanvasGLSL$trackTime(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        canvasglsl$time += delta;
        canvasglsl$frame++;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void canvasglsl$renderFpsCounter(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Only show FPS counter when shader background is enabled and rendering
        if (CanvasGLSL.SHADER_BACKGROUND != null && CanvasGLSL.SHADER_BACKGROUND.isEnabled() && CanvasGLSL.SHADER_BACKGROUND.isRendererReady()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int fps = mc.getCurrentFps();
            String fpsText = fps + " FPS";

            // Calculate position for top right corner
            int textWidth = mc.textRenderer.getWidth(fpsText);
            int x = this.width - textWidth - 2;  // 2 pixels from right edge
            int y = 2;  // 2 pixels from top edge

            // Draw FPS text with bright color and shadow for visibility
            int color;
            if (fps >= 60) {
                color = 0x55FF55; // Bright green
            } else if (fps >= 30) {
                color = 0xFFFF55; // Bright yellow
            } else {
                color = 0xFF5555; // Bright red
            }

            // Draw with shadow for better visibility
            context.drawText(mc.textRenderer, fpsText, x, y, color, true);
        }
    }

    @Unique
    public double canvasglsl$getShaderTime() {
        return canvasglsl$time;
    }

    @Unique
    public long canvasglsl$getShaderFrame() {
        return canvasglsl$frame;
    }
}
