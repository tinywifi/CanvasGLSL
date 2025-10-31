package sh.tinywifi.canvasglsl.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.gui.ImGuiManager;
import sh.tinywifi.canvasglsl.gui.ShaderIDEViewport;
import sh.tinywifi.canvasglsl.ide.ShaderIDEController;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void CanvasGLSL$render(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ShaderIDEController controller = CanvasGLSL.IDE;
        if (!controller.isOverlayVisible()) return;

        ShaderIDEViewport viewport = controller.getViewport();
        viewport.ensureReady();

        ImGuiManager manager = ImGuiManager.get();
        if (!manager.beginFrame()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        float scaledWidth = window.getScaledWidth();
        float scaledHeight = window.getScaledHeight();
        viewport.renderUI(scaledWidth, scaledHeight);

        manager.endFrame();
    }
}



