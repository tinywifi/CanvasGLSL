package sh.tinywifi.canvasglsl.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.bridge.TitleScreenShaderAccess;
import sh.tinywifi.canvasglsl.ui.OverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen implements TitleScreenShaderAccess {
    @Unique
    private double canvasglsl$time = 0.0;
    @Unique
    private long canvasglsl$frame = 0;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void CanvasGLSL$trackTime(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        canvasglsl$time += delta;
        canvasglsl$frame++;
    }

    // 1.21.4: Override renderBackground to ensure panorama shader is used instead
    // of default
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void CanvasGLSL$overrideBackground(DrawContext context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        // Call the parent Screen's renderPanoramaBackground which has our shader mixin
        this.renderPanoramaBackground(context, delta);
        // Cancel the default TitleScreen background rendering
        ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void canvasglsl$drawOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        OverlayRenderer.onDraw(this, mc.textRenderer, context);
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
