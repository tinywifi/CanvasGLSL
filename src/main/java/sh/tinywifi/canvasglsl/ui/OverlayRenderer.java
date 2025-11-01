package sh.tinywifi.canvasglsl.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import sh.tinywifi.canvasglsl.CanvasGLSL;

/**
 * Called on title screen drawing.
 *
 * @param screen   Target screen
 * @param font     Screen font
 * @param graphics Drawing graphics
 */
@SuppressWarnings("ChainOfInstanceofChecks") // Minecraft screens are not easily abstracted.
public final class OverlayRenderer {
    private OverlayRenderer() {
    }

    public static void onDraw(Screen screen, TextRenderer font, DrawContext graphics) {
        if (!(screen instanceof TitleScreen)) {
            return;
        }

        var shaderBackground = CanvasGLSL.SHADER_BACKGROUND;
        if (shaderBackground == null || !shaderBackground.isEnabled()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }

        int fps = mc.getCurrentFps();
        String text = fps + " FPS";

        int color;
        if (fps < 30) {
            color = 0xFF5555; // Red
        } else if (fps < 60) {
            color = 0xFFFF55; // Yellow
        } else {
            color = 0x55FF55; // Green
        }

        int textWidth = font.getWidth(text);
        int textX = screen.width - textWidth - 4;
        int baseline = screen.height - font.fontHeight - 26; // aim slightly above Mojang copyright
        int textY = Math.max(4, baseline);

        graphics.drawText(font, text, textX, textY, color, true);
    }
}
