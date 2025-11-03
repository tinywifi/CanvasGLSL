package sh.tinywifi.canvasglsl.ide;

import imgui.type.ImString;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.modules.ShaderBackground;
import sh.tinywifi.canvasglsl.shader.ShaderPresets;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime state bundle for the shader IDE. Keeps track of the current file, editor contents,
 * dirty flagging, active theme, and various toggles influencing behaviour.
 */
public final class ShaderEditorState {
    private static final int DEFAULT_BUFFER_CAPACITY = 256 * 1024; // 256 KiB

    private final ShaderWorkspace workspace;
    private final ImString buffer;

    private Path currentFile;
    private boolean dirty;
    private boolean requestFocus;
    private ShaderIDETheme theme;
    private boolean autoCompile = true;
    private boolean autoSave = false;
    private boolean diagnosticLogging = false;
    private boolean framerateOverrideEnabled = true;
    private int framerateLimit = 120;
    private boolean disableVsyncDuringOverride = true;
    private float fontScale = 1.0f;
    private ShaderPresets activePreset = ShaderPresets.TRIPPY;

    private String statusMessage = "";
    private Instant statusSince = Instant.now();

    public ShaderEditorState(ShaderWorkspace workspace) {
        this.workspace = workspace;
        this.buffer = new ImString(DEFAULT_BUFFER_CAPACITY);
        this.theme = ShaderIDETheme.MONOKAI;
        resetToPreset(ShaderPresets.TRIPPY.getShaderCode());
    }

    public ShaderWorkspace getWorkspace() {
        return workspace;
    }

    public ImString buffer() {
        return buffer;
    }

    public Optional<Path> currentFile() {
        return Optional.ofNullable(currentFile);
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public void markDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void resetToPreset(String source) {
        buffer.set(source);
        dirty = true;
        currentFile = null;
        requestFocus = true;
        activePreset = ShaderPresets.TRIPPY;
        setStatus("New shader seeded from preset.");
    }

    public void resetToEmpty() {
        buffer.set("");
        dirty = true;
        currentFile = null;
        requestFocus = true;
        activePreset = null;
        setStatus("New blank shader.");
    }

    public void applyPreset(ShaderPresets preset) {
        if (preset == null) return;
        activePreset = preset;
        buffer.set(preset.getShaderCode());
        dirty = true;
        currentFile = null;
        requestFocus = true;
        setStatus("Loaded preset " + preset.name());
    }

    public boolean load(Path file) {
        Objects.requireNonNull(file, "file");

        String contents = workspace.readFile(file);
        if (contents.isEmpty() && workspace.hasSupportedExtension(file) && !file.toFile().exists()) {
            CanvasGLSL.LOG.warn("Requested shader {} does not exist yet", file);
            return false;
        }

        buffer.set(contents);
        currentFile = file;
        dirty = false;
        requestFocus = true;
        setStatus("Loaded " + workspace.getRoot().relativize(file));
        return true;
    }

    public boolean save() {
        if (currentFile == null) return false;
        return saveAs(currentFile);
    }

    public boolean saveAs(Path file) {
        Objects.requireNonNull(file, "file");
        boolean ok = workspace.writeFile(file, buffer.get());
        if (ok) {
            currentFile = file;
            dirty = false;
            setStatus("Saved " + workspace.getRoot().relativize(file));
        }
        return ok;
    }

    public void setTheme(ShaderIDETheme theme) {
        if (theme == null) return;
        this.theme = theme;
        theme.apply();
        setStatus("Applied theme " + theme.displayName());
    }

    public ShaderIDETheme getTheme() {
        return theme;
    }

    public ShaderPresets getActivePreset() {
        return activePreset;
    }

    public boolean shouldFocusEditor() {
        if (requestFocus) {
            requestFocus = false;
            return true;
        }
        return false;
    }

    public void requestEditorFocus() {
        requestFocus = true;
    }

    public boolean isAutoCompileEnabled() {
        return autoCompile;
    }

    public void setAutoCompile(boolean autoCompile) {
        this.autoCompile = autoCompile;
    }

    public boolean isAutoSaveEnabled() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean isDiagnosticLoggingEnabled() {
        return diagnosticLogging;
    }

    public void setDiagnosticLogging(boolean enabled) {
        this.diagnosticLogging = enabled;
    }

    public boolean isFramerateOverrideEnabled() {
        return framerateOverrideEnabled;
    }

    public void setFramerateOverrideEnabled(boolean enabled) {
        this.framerateOverrideEnabled = enabled;
    }

    public int getFramerateLimit() {
        return framerateLimit;
    }

    public void setFramerateLimit(int limit) {
        this.framerateLimit = Math.max(30, Math.min(ShaderBackground.FPS_UNLOCK_VALUE, limit));
    }

    public boolean isDisableVsyncDuringOverride() {
        return disableVsyncDuringOverride;
    }

    public void setDisableVsyncDuringOverride(boolean disable) {
        this.disableVsyncDuringOverride = disable;
    }

    public float getFontScale() {
        return fontScale;
    }

    public void setFontScale(float fontScale) {
        this.fontScale = Math.max(0.8f, Math.min(2.0f, fontScale));
    }

    public void setStatus(String message) {
        this.statusMessage = message;
        this.statusSince = Instant.now();
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Duration statusAge() {
        return Duration.between(statusSince, Instant.now());
    }
}
