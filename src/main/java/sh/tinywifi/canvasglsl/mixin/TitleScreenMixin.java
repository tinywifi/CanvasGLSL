package sh.tinywifi.canvasglsl.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.modules.ShaderBackground;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique private double canvasglsl$time = 0.0;
    @Unique private long canvasglsl$frame = 0;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void CanvasGLSL$incrementTimer(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        canvasglsl$time += delta;
        canvasglsl$frame++;
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void CanvasGLSL$renderShader(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ShaderBackground module = CanvasGLSL.SHADER_BACKGROUND;
        if (module == null || !module.isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        if (CanvasGLSL.IDE.getEditorState().isDiagnosticLoggingEnabled()) {
            CanvasGLSL.LOG.info("[CanvasGLSL] TitleScreen renderBackground invoked (frame={})", canvasglsl$frame);
        }

        module.renderShader(context, width, height, 1.0f, canvasglsl$time, canvasglsl$frame);
    }
}
