package sh.tinywifi.canvasglsl.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Standalone screen wrapper that renders the shared Dear ImGui viewport.
 */
public final class ShaderIDEScreen extends Screen {
    private static final String WINDOW_TITLE = "CanvasGLSL Shader IDE";

    private final ShaderIDEViewport viewport;

    public ShaderIDEScreen(ShaderIDEViewport viewport) {
        super(Text.literal(WINDOW_TITLE));
        this.viewport = viewport;
    }

    @Override
    protected void init() {
        viewport.ensureReady();
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        renderBackground(drawContext, mouseX, mouseY, delta);
        viewport.ensureReady();
        ImGuiManager manager = ImGuiManager.get();
        if (!manager.beginFrame()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float scaledWidth = client.getWindow().getScaledWidth();
        float scaledHeight = client.getWindow().getScaledHeight();
        viewport.renderUI(scaledWidth, scaledHeight);
        manager.endFrame();
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}



