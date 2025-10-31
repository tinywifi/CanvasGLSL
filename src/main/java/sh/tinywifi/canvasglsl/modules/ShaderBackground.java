package sh.tinywifi.canvasglsl.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.ide.MediaChangeListener;
import sh.tinywifi.canvasglsl.ide.ShaderChangeListener;
import sh.tinywifi.canvasglsl.ide.ShaderEditorState;
import sh.tinywifi.canvasglsl.ide.ShaderIDEController;
import sh.tinywifi.canvasglsl.media.MediaEntry;
import sh.tinywifi.canvasglsl.render.MediaRenderer;
import sh.tinywifi.canvasglsl.shader.ShaderPresets;
import sh.tinywifi.canvasglsl.shader.ShaderRenderer;

import java.nio.file.Path;

/**
 * Standalone background controller that swaps the main menu panorama with either static media or GLSL shaders.
 */
public class ShaderBackground implements ShaderChangeListener, MediaChangeListener {
    private final ShaderIDEController controller;
    private final ShaderEditorState editorState;
    private ShaderRenderer renderer;
    private final MediaRenderer mediaRenderer;

    private long lastFpsDiagnosticMs = 0L;

    private boolean needsCompile = true;
    private boolean enabled = true;
    private boolean compileQueued;
    private String queuedSource;

    public ShaderBackground() {
        this.controller = CanvasGLSL.IDE;
        this.editorState = controller.getEditorState();
        this.mediaRenderer = new MediaRenderer();
    }

    public void initialize() {
        controller.addListener(this);
        controller.addMediaListener(this);
        controller.getCurrentMediaEntry().ifPresent(mediaRenderer::load);
        compileQueued = false;
        needsCompile = true;
        logDiagnostic("Shader background initialized (enabled={})", enabled);
    }

    public void shutdown() {
        destroyRenderer();
        mediaRenderer.unload();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            logDiagnostic("Shader background enabled");
            needsCompile = true;
            compileQueued = false;
        } else {
            logDiagnostic("Shader background disabled; destroying renderer");
            destroyRenderer();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onShaderSaved(String source, Path file) {
        needsCompile = true;
        mediaRenderer.unload();
        logDiagnostic("Shader saved to {}; queued for compile (autoCompile={})",
            file != null ? file.getFileName() : "<unsaved>", editorState.isAutoCompileEnabled());
        if (enabled && editorState.isAutoCompileEnabled()) {
            queueCompile(source);
        }
    }

    @Override
    public void onMediaSelected(MediaEntry entry) {
        mediaRenderer.load(entry);
        logDiagnostic("Media selected {}", entry != null ? entry.sourcePath() : "<null>");
    }

    public void compileCurrentShader() {
        if (!enabled) return;
        if (controller.getActiveContentType() != ShaderIDEController.ContentType.SHADER) {
            return;
        }
        logDiagnostic("compileCurrentShader invoked (autoCompile={})", editorState.isAutoCompileEnabled());
        queueCompile(controller.getCurrentSource());
    }

    public void requestManualCompile() {
        if (!enabled) return;
        if (controller.getActiveContentType() != ShaderIDEController.ContentType.SHADER) {
            return;
        }
        logDiagnostic("Manual compile requested");
        queueCompile(controller.getCurrentSource());
    }

    private void queueCompile(String fragmentSource) {
        queuedSource = fragmentSource;
        compileQueued = true;
        needsCompile = true;
        int length = fragmentSource != null ? fragmentSource.length() : -1;
        logDiagnostic("Queued shader compile (length={} autoCompile={})",
            length, editorState.isAutoCompileEnabled());
        logDiagnostic("Queued shader source: {}", describeCurrentShader());

        if (RenderSystem.isOnRenderThread()) {
            ShaderRenderer shaderRenderer = getOrCreateRenderer();
            logDiagnostic("Immediate compile flush (on render thread) rendererPresent={}", shaderRenderer != null);
            flushQueuedCompile(shaderRenderer);
        } else {
            logDiagnostic("Render thread not available yet; scheduling compile flush");
            RenderSystem.recordRenderCall(() -> {
                ShaderRenderer shaderRenderer = getOrCreateRenderer();
                logDiagnostic("Render thread compile flush scheduled (rendererPresent={})", shaderRenderer != null);
                flushQueuedCompile(shaderRenderer);
            });
        }
    }

    private void flushQueuedCompile(ShaderRenderer renderer) {
        if (!compileQueued) {
            logDiagnostic("flushQueuedCompile: nothing queued (needsCompile={})", needsCompile);
            return;
        }
        compileQueued = false;
        String shaderCode = queuedSource;
        queuedSource = null;

        if (shaderCode == null || shaderCode.isBlank()) {
            CanvasGLSL.LOG.warn("Current shader buffer empty; using TRIPPY preset as fallback");
            shaderCode = ShaderPresets.TRIPPY.getShaderCode();
        }

        CanvasGLSL.LOG.info("Compiling shader ({} characters)", shaderCode.length());
        boolean success = renderer.compileShader(shaderCode);

        if (success) {
            needsCompile = false;
            renderer.resetTime();
            CanvasGLSL.LOG.info("Shader compiled successfully");
        } else {
            needsCompile = true;
            CanvasGLSL.LOG.error("Shader compilation failed");
        }
    }

    private ShaderRenderer getOrCreateRenderer() {
        if (renderer == null) {
            renderer = new ShaderRenderer();
        }
        return renderer;
    }

    private void destroyRenderer() {
        if (renderer == null) return;
        ShaderRenderer toCleanup = renderer;
        renderer = null;
        compileQueued = false;
        queuedSource = null;
        needsCompile = true;

        logDiagnostic("Destroying shader renderer; scheduling cleanup on render thread");

        if (RenderSystem.isOnRenderThread()) {
            toCleanup.cleanup();
        } else {
            RenderSystem.recordRenderCall(toCleanup::cleanup);
        }
    }

    public void renderShader(DrawContext context, int width, int height, float alpha, double time, long frame) {
        if (!enabled) return;

        logDiagnostic(
            "renderShader frame={} contentType={} needsCompile={} compileQueued={} rendererPresent={} compiled={}",
            frame,
            controller.getActiveContentType(),
            needsCompile,
            compileQueued,
            renderer != null,
            renderer != null && renderer.isCompiled()
        );

        if (controller.getActiveContentType() == ShaderIDEController.ContentType.MEDIA) {
            if (mediaRenderer.isReady()) {
                logDiagnostic("Rendering media background ({}x{})", width, height);
                mediaRenderer.render(context, width, height, alpha);
            } else {
                logDiagnostic("Media background selected but not ready yet");
            }
            return;
        }

        if (needsCompile && editorState.isAutoCompileEnabled() && !compileQueued) {
            logDiagnostic("Auto-compiling shader during render pass");
            queueCompile(controller.getCurrentSource());
        } else if (needsCompile && !editorState.isAutoCompileEnabled()) {
            logDiagnostic("Shader requires manual compile; skipping render");
        }

        ShaderRenderer shaderRenderer = getOrCreateRenderer();
        flushQueuedCompile(shaderRenderer);

        if (shaderRenderer.isCompiled()) {
            shaderRenderer.render(width, height, alpha, 1.0);
            logDiagnostic("Shader draw completed for frame {}", frame);
            logFpsDiagnostics(frame);
        } else {
            logDiagnostic("Shader renderer not compiled yet; nothing drawn this frame");
        }
    }

    public ShaderRenderer getRenderer() {
        return renderer;
    }

    public boolean isRendererReady() {
        if (!enabled) return false;
        if (controller.getActiveContentType() == ShaderIDEController.ContentType.MEDIA) {
            return mediaRenderer.isReady();
        }
        return renderer != null && renderer.isCompiled();
    }

    private void logDiagnostic(String message, Object... args) {
        if (editorState.isDiagnosticLoggingEnabled()) {
            CanvasGLSL.LOG.info("[CanvasGLSL] " + message, args);
        } else if (CanvasGLSL.LOG.isDebugEnabled()) {
            CanvasGLSL.LOG.debug(message, args);
        }
    }

    private void logFpsDiagnostics(long frame) {
        if (!editorState.isDiagnosticLoggingEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFpsDiagnosticMs < 1000) {
            return;
        }
        lastFpsDiagnosticMs = now;
        int fps = MinecraftClient.getInstance().getCurrentFps();
        logDiagnostic("FPS={} frame={} shader={}", fps, frame, describeCurrentShader());
    }

    private String describeCurrentShader() {
        return controller.getCurrentFile()
            .map(Path::getFileName)
            .map(Path::toString)
            .orElseGet(() -> {
                ShaderPresets preset = editorState.getActivePreset();
                return preset != null ? "Preset:" + preset.name() : "<unsaved>";
            });
    }
}


