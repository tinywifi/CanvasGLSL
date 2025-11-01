package sh.tinywifi.canvasglsl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.media.MediaEntry;
import sh.tinywifi.canvasglsl.media.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight renderer used to display static media content in the title screen background using vanilla rendering.
 */
public final class MediaRenderer {
    private NativeImageBackedTexture texture;
    private Identifier textureId;

    private int imageWidth;
    private int imageHeight;
    private MediaType mediaType = MediaType.UNSUPPORTED;
    private Path currentSource;

    public void load(MediaEntry entry) {
        unload();
        currentSource = entry.sourcePath();
        mediaType = entry.mediaType();

        if (mediaType == MediaType.VIDEO) {
            CanvasGLSL.LOG.warn("Video playback is not supported yet. Selected file: {}", currentSource);
            return;
        }

        if (!Files.exists(currentSource)) {
            CanvasGLSL.LOG.error("Media file does not exist: {}", currentSource);
            return;
        }

        try (InputStream stream = Files.newInputStream(currentSource)) {
            NativeImage image = NativeImage.read(stream);
            if (image == null) {
                CanvasGLSL.LOG.error("Unsupported media format: {}", currentSource);
                return;
            }

            imageWidth = image.getWidth();
            imageHeight = image.getHeight();

            texture = new NativeImageBackedTexture(() -> "canvasglsl_media", image);

            MinecraftClient client = MinecraftClient.getInstance();
            if (textureId != null) {
                client.getTextureManager().destroyTexture(textureId);
            }
            String key = "media/" + Integer.toHexString(currentSource.toAbsolutePath().toString().hashCode());
            textureId = Identifier.of("canvasglsl", key);
            client.getTextureManager().registerTexture(textureId, texture);

            CanvasGLSL.LOG.info("Loaded media texture {}", currentSource);
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to load media texture {}", currentSource, ex);
            unload();
        }
    }

    public void render(DrawContext context, int screenWidth, int screenHeight, float alpha) {
        if (texture == null || textureId == null) {
            return;
        }

        float screenAspect = (float) screenWidth / (float) screenHeight;
        float imageAspect = imageWidth > 0 && imageHeight > 0 ? (float) imageWidth / (float) imageHeight : 1f;

        float drawWidth = screenWidth;
        float drawHeight = screenHeight;

        if (imageAspect > 0f) {
            if (imageAspect > screenAspect) {
                drawHeight = drawWidth / imageAspect;
            } else {
                drawWidth = drawHeight * imageAspect;
            }
        }

        int drawW = Math.max(1, Math.round(drawWidth));
        int drawH = Math.max(1, Math.round(drawHeight));
        int x = Math.round((screenWidth - drawWidth) * 0.5f);
        int y = Math.round((screenHeight - drawHeight) * 0.5f);

        RenderSystem.assertOnRenderThread();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float clampedAlpha = Math.max(0f, Math.min(1f, alpha));
        int alphaByte = Math.round(clampedAlpha * 255f) & 0xFF;
        int color = (alphaByte << 24) | 0x00FFFFFF;

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x,
            y,
            0f,
            0f,
            drawW,
            drawH,
            imageWidth,
            imageHeight,
            imageWidth,
            imageHeight,
            color
        );

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    public void unload() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (textureId != null) {
            client.getTextureManager().destroyTexture(textureId);
            textureId = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
        imageWidth = 0;
        imageHeight = 0;
        currentSource = null;
        mediaType = MediaType.UNSUPPORTED;
    }

    public boolean isReady() {
        return texture != null;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    @Nullable
    public Path getCurrentSource() {
        return currentSource;
    }
}
