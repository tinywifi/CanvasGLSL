package sh.tinywifi.canvasglsl.ide;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.gui.ShaderIDEScreen;
import sh.tinywifi.canvasglsl.gui.ShaderIDEViewport;
import sh.tinywifi.canvasglsl.media.MediaEntry;
import sh.tinywifi.canvasglsl.media.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Coordinates the shader IDE state between the GUI and the shader module. Acts as the single source of truth
 * for the currently active workspace, editor buffer, and last saved shader source.
 */
public final class ShaderIDEController {
    public enum ContentType {
        SHADER,
        MEDIA
    }

    private static final ShaderIDEController INSTANCE = new ShaderIDEController();

    private final ShaderWorkspace workspace = ShaderWorkspace.open();
    private final ShaderEditorState editorState = new ShaderEditorState(workspace);
    private final ShaderIDEViewport viewport = new ShaderIDEViewport(this);
    private final List<ShaderChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final List<MediaChangeListener> mediaListeners = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();
    private final Path stateFile = workspace.getRoot().resolve(".canvasglsl-state.json");

    private volatile String lastSavedSource = editorState.buffer().get();
    private volatile Path lastSavedFile = null;
    private volatile boolean overlayVisible;
    private volatile ContentType contentType = ContentType.SHADER;
    private volatile MediaEntry currentMediaEntry;

    private ShaderIDEController() {}

    public static ShaderIDEController get() {
        return INSTANCE;
    }

    public ShaderWorkspace getWorkspace() {
        return workspace;
    }

    public ShaderEditorState getEditorState() {
        return editorState;
    }

    public ShaderIDEViewport getViewport() {
        return viewport;
    }

    public ContentType getActiveContentType() {
        return contentType;
    }

    public Optional<MediaEntry> getCurrentMediaEntry() {
        return Optional.ofNullable(currentMediaEntry);
    }

    public void toggleOverlayVisible() {
        setOverlayVisible(!overlayVisible);
    }

    public void setOverlayVisible(boolean visible) {
        overlayVisible = visible;
        if (visible) {
            viewport.ensureReady();
        }
    }

    public boolean isOverlayVisible() {
        return overlayVisible;
    }

    public ShaderIDEScreen createScreen() {
        return new ShaderIDEScreen(viewport);
    }

    public void addListener(ShaderChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void addMediaListener(MediaChangeListener listener) {
        if (listener != null && !mediaListeners.contains(listener)) {
            mediaListeners.add(listener);
        }
    }

    public void removeListener(ShaderChangeListener listener) {
        listeners.remove(listener);
    }

    public void removeMediaListener(MediaChangeListener listener) {
        mediaListeners.remove(listener);
    }

    public void notifyShaderSaved() {
        String snapshot = editorState.buffer().get();
        Path file = editorState.currentFile().orElse(null);
        lastSavedSource = snapshot;
        lastSavedFile = file;
        contentType = ContentType.SHADER;
        currentMediaEntry = null;
        saveState();

        for (ShaderChangeListener listener : listeners) {
            listener.onShaderSaved(snapshot, file);
        }
    }

    public void notifyMediaSelected(MediaEntry entry) {
        currentMediaEntry = entry;
        contentType = ContentType.MEDIA;
        lastSavedSource = null;
        lastSavedFile = null;
        saveState();

        for (MediaChangeListener listener : mediaListeners) {
            listener.onMediaSelected(entry);
        }
    }

    public void clearSelection() {
        currentMediaEntry = null;
        contentType = ContentType.SHADER;
        saveState();
    }

    public MediaType classifyMedia(Path path) {
        return detectMediaType(path);
    }

    public boolean loadMediaDescriptor(Path descriptor) {
        if (!workspace.isMediaDescriptor(descriptor)) {
            return false;
        }

        try {
            String json = Files.readString(descriptor, StandardCharsets.UTF_8);
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("path")) {
                editorState.setStatus("Invalid media descriptor: " + descriptor.getFileName());
                return false;
            }

            String sourcePath = obj.get("path").getAsString();
            Path source = Path.of(sourcePath);
            MediaType type = detectMediaType(source);

            MediaEntry entry = new MediaEntry(descriptor, source, type);
            currentMediaEntry = entry;
            contentType = ContentType.MEDIA;

            editorState.resetToEmpty();
            editorState.markDirty(false);
            editorState.setStatus("Media selected: " + source);

            notifyMediaSelected(entry);
            return true;
        } catch (IOException | JsonSyntaxException ex) {
            CanvasGLSL.LOG.error("Failed to load media descriptor {}", descriptor, ex);
            editorState.setStatus("Failed to load media descriptor");
            return false;
        }
    }

    public void saveMediaDescriptor(Path descriptor, Path source) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("path", source.toAbsolutePath().toString());
        obj.addProperty("type", detectMediaType(source).name());
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, gson.toJson(obj), StandardCharsets.UTF_8);
    }

    public void loadPersistedState() {
        if (!Files.exists(stateFile)) return;

        try {
            String json = Files.readString(stateFile, StandardCharsets.UTF_8);
            State state = gson.fromJson(json, State.class);
            if (state == null) return;

            if ("MEDIA".equalsIgnoreCase(state.lastType) && state.lastMedia != null) {
                Path descriptor = workspace.resolve(state.lastMedia);
                if (Files.exists(descriptor)) {
                    loadMediaDescriptor(descriptor);
                    return;
                }
            }

            if (state.lastShader != null) {
                Path shaderPath = workspace.resolve(state.lastShader);
                if (Files.exists(shaderPath) && editorState.load(shaderPath)) {
                    notifyShaderSaved();
                }
            }
        } catch (IOException | JsonSyntaxException ex) {
            CanvasGLSL.LOG.error("Failed to read CanvasGLSL state file", ex);
        }
    }

    private void saveState() {
        try {
            State state = new State();
            state.lastType = contentType.name();
            if (lastSavedFile != null && Files.exists(lastSavedFile)) {
                state.lastShader = workspace.getRoot().relativize(lastSavedFile).toString();
            }
            if (currentMediaEntry != null) {
                Path descriptor = currentMediaEntry.descriptorFile();
                if (descriptor != null && descriptor.startsWith(workspace.getRoot())) {
                    state.lastMedia = workspace.getRoot().relativize(descriptor).toString();
                }
            }

            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, gson.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to save CanvasGLSL state file", ex);
        }
    }

    private MediaType detectMediaType(Path source) {
        String name = source.getFileName().toString().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp")) {
            return MediaType.IMAGE;
        }
        if (name.endsWith(".gif")) {
            return MediaType.GIF;
        }
        if (name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".mkv")) {
            return MediaType.VIDEO;
        }
        return MediaType.UNSUPPORTED;
    }

    public String getCurrentSource() {
        return editorState.buffer().get();
    }

    public Optional<Path> getCurrentFile() {
        return editorState.currentFile();
    }

    public Optional<String> getLastSavedSource() {
        return Optional.ofNullable(lastSavedSource);
    }

    public Optional<Path> getLastSavedFile() {
        return Optional.ofNullable(lastSavedFile);
    }

    private static final class State {
        String lastType;
        String lastShader;
        String lastMedia;
    }
}
