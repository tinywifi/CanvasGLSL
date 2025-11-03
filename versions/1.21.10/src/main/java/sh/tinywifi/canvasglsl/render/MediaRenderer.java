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
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.media.MediaEntry;
import sh.tinywifi.canvasglsl.media.MediaType;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.scale.AWTUtil;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;

/**
 * Lightweight renderer used to display static and animated media content in the title screen background using vanilla rendering.
 */
public final class MediaRenderer {
    private NativeImageBackedTexture texture;
    private Identifier textureId;

    private int imageWidth;
    private int imageHeight;
    private MediaType mediaType = MediaType.UNSUPPORTED;
    private Path currentSource;
    private AnimatedSequence animation;
    private long lastAnimationUpdateMs;

    public void load(MediaEntry entry) {
        unload();
        currentSource = entry.sourcePath();
        mediaType = entry.mediaType();
        animation = null;
        lastAnimationUpdateMs = System.currentTimeMillis();

        if (!Files.exists(currentSource)) {
            CanvasGLSL.LOG.error("Media file does not exist: {}", currentSource);
            return;
        }

        try {
            switch (mediaType) {
                case IMAGE -> loadStaticImage(currentSource);
                case GIF -> loadGifAnimation(currentSource);
                case VIDEO -> loadVideoAnimation(currentSource);
                default -> CanvasGLSL.LOG.error("Unsupported media type for file: {}", currentSource);
            }
        } catch (IOException | JCodecException ex) {
            CanvasGLSL.LOG.error("Failed to load media {}", currentSource, ex);
            unload();
        }
    }

    public void render(DrawContext context, int screenWidth, int screenHeight, float alpha) {
        if (texture == null || textureId == null) {
            return;
        }

        updateAnimation();

        float scale = 1.0f;
        if (imageWidth > 0 && imageHeight > 0) {
            float scaleX = (float) screenWidth / (float) imageWidth;
            float scaleY = (float) screenHeight / (float) imageHeight;
            scale = Math.max(scaleX, scaleY);
        }

        int drawW = Math.max(1, Math.round(imageWidth * scale));
        int drawH = Math.max(1, Math.round(imageHeight * scale));
        int x = (screenWidth - drawW) / 2;
        int y = (screenHeight - drawH) / 2;

        RenderSystem.assertOnRenderThread();

        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevTexture2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

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

        if (blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL14.glBlendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);

        if (depthEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }

        if (scissorEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        GL13.glActiveTexture(prevActiveTexture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture2D);
        GL20.glUseProgram(prevProgram);
    }

    private void loadStaticImage(Path source) throws IOException {
        try (java.io.InputStream stream = Files.newInputStream(source)) {
            NativeImage image = NativeImage.read(stream);
            if (image == null) {
                throw new IOException("Unsupported image format");
            }
            initializeTexture(image);
            animation = null;
            lastAnimationUpdateMs = 0L;
            CanvasGLSL.LOG.info("Loaded media texture {}", currentSource);
        }
    }

    private void loadGifAnimation(Path source) throws IOException {
        List<AnimationFrame> frames = new ArrayList<>();
        try (java.io.InputStream fileStream = Files.newInputStream(source);
             ImageInputStream input = ImageIO.createImageInputStream(fileStream)) {
            if (input == null) {
                throw new IOException("Unable to create GIF stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IOException("No GIF reader available");
            }
            ImageReader reader = readers.next();
            reader.setInput(input, false, false);

            int frameCount = reader.getNumImages(true);
            for (int i = 0; i < frameCount; i++) {
                BufferedImage frameImage = reader.read(i);
                IIOMetadata metadata = reader.getImageMetadata(i);
                long delayMs = Math.max(20L, extractGifDelay(metadata));
                frames.add(new AnimationFrame(convertBufferedImage(frameImage), delayMs));
            }
            reader.dispose();

            if (frames.isEmpty()) {
                throw new IOException("GIF contained no frames");
            }

            initializeAnimation(frames);
            CanvasGLSL.LOG.info("Loaded animated GIF {} ({} frames)", currentSource, frames.size());
        } catch (IOException ex) {
            frames.forEach(frame -> frame.image.close());
            throw ex;
        }
    }

    private void loadVideoAnimation(Path source) throws IOException, JCodecException {
        List<AnimationFrame> frames = new ArrayList<>();
        try (SeekableByteChannel channel = NIOUtils.readableChannel(source.toFile())) {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            double fps = 0.0;
            if (grab.getVideoTrack() != null && grab.getVideoTrack().getMeta() != null) {
                var meta = grab.getVideoTrack().getMeta();
                if (meta.getTotalDuration() > 0 && meta.getTotalFrames() > 0) {
                    fps = meta.getTotalFrames() / meta.getTotalDuration();
                }
            }
            if (fps <= 0.0 || Double.isNaN(fps) || Double.isInfinite(fps)) {
                fps = 24.0;
            }

            int maxFrames = 600;
            int frameIndex = 0;
            Picture picture;
            while ((picture = grab.getNativeFrame()) != null && frameIndex < maxFrames) {
                BufferedImage buffered = AWTUtil.toBufferedImage(picture);
                NativeImage frameImage = convertBufferedImage(buffered);
                long durationMs = Math.max(15L, Math.round(1000.0 / fps));
                frames.add(new AnimationFrame(frameImage, durationMs));
                frameIndex++;
            }

            if (frames.isEmpty()) {
                throw new IOException("Video contained no decodable frames");
            }

            initializeAnimation(frames);
            CanvasGLSL.LOG.info("Loaded video {} ({} frames)", currentSource, frames.size());
        } catch (IOException | JCodecException ex) {
            frames.forEach(frame -> frame.image.close());
            throw ex;
        }
    }

    private void initializeTexture(NativeImage image) {
        imageWidth = image.getWidth();
        imageHeight = image.getHeight();
        texture = new NativeImageBackedTexture(() -> "canvasglsl_media", image);
        registerTexture();
        texture.upload();
    }

    private void initializeAnimation(List<AnimationFrame> frames) {
        AnimatedSequence sequence = new AnimatedSequence(frames);
        NativeImage baseFrame = copyNativeImage(sequence.currentFrame().image);
        initializeTexture(baseFrame);
        animation = sequence;
        lastAnimationUpdateMs = System.currentTimeMillis();
    }

    private void registerTexture() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (textureId != null) {
            client.getTextureManager().destroyTexture(textureId);
        }
        String keySource = currentSource != null ? currentSource.toAbsolutePath().toString() : "media";
        String key = "media/" + Integer.toHexString(keySource.hashCode());
        textureId = Identifier.of("canvasglsl", key);
        client.getTextureManager().registerTexture(textureId, texture);
    }

    private void updateAnimation() {
        if (animation == null || texture == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastAnimationUpdateMs == 0L) {
            lastAnimationUpdateMs = now;
        }
        long delta = now - lastAnimationUpdateMs;
        if (delta <= 0L) {
            return;
        }
        lastAnimationUpdateMs = now;
        if (animation.advance(delta)) {
            texture.getImage().copyFrom(animation.currentFrame().image);
            texture.upload();
        }
    }

    private NativeImage convertBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                nativeImage.setColorArgb(x, y, argb);
            }
        }
        return nativeImage;
    }

    private NativeImage copyNativeImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getFormat(), source.getWidth(), source.getHeight(), false);
        copy.copyFrom(source);
        return copy;
    }

    private long extractGifDelay(IIOMetadata metadata) {
        if (metadata == null) {
            return 100L;
        }
        try {
            String formatName = metadata.getNativeMetadataFormatName();
            if (formatName == null) {
                return 100L;
            }
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);
            IIOMetadataNode node = findMetadataNode(root, "GraphicControlExtension");
            if (node != null) {
                String delayTime = node.getAttribute("delayTime");
                int hundredths = Integer.parseInt(delayTime);
                return Math.max(1, hundredths) * 10L;
            }
        } catch (Exception ignored) {
        }
        return 100L;
    }

    private IIOMetadataNode findMetadataNode(IIOMetadataNode parent, String target) {
        if (target.equals(parent.getNodeName())) {
            return parent;
        }
        for (int i = 0; i < parent.getLength(); i++) {
            IIOMetadataNode child = (IIOMetadataNode) parent.item(i);
            IIOMetadataNode result = findMetadataNode(child, target);
            if (result != null) {
                return result;
            }
        }
        return null;
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
        if (animation != null) {
            animation.close();
            animation = null;
        }
        lastAnimationUpdateMs = 0L;
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

    private static final class AnimationFrame {
        private final NativeImage image;
        private final long durationMs;

        private AnimationFrame(NativeImage image, long durationMs) {
            this.image = image;
            this.durationMs = durationMs;
        }
    }

    private static final class AnimatedSequence implements AutoCloseable {
        private final List<AnimationFrame> frames;
        private int frameIndex;
        private long frameTimerMs;

        private AnimatedSequence(List<AnimationFrame> frames) {
            this.frames = frames;
        }

        private AnimationFrame currentFrame() {
            return frames.get(frameIndex);
        }

        private boolean advance(long deltaMs) {
            if (frames.size() <= 1) {
                return false;
            }
            frameTimerMs += deltaMs;
            boolean advanced = false;
            while (frameTimerMs >= currentFrame().durationMs) {
                frameTimerMs -= currentFrame().durationMs;
                frameIndex = (frameIndex + 1) % frames.size();
                advanced = true;
            }
            return advanced;
        }

        @Override
        public void close() {
            for (AnimationFrame frame : frames) {
                frame.image.close();
            }
            frames.clear();
        }
    }
}
