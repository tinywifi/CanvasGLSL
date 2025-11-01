package sh.tinywifi.canvasglsl.gui;

import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import imgui.type.ImBoolean;
import net.minecraft.client.MinecraftClient;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.ide.ShaderCodeEditor;
import sh.tinywifi.canvasglsl.ide.ShaderIDEController;
import sh.tinywifi.canvasglsl.ide.ShaderEditorState;
import sh.tinywifi.canvasglsl.ide.ShaderIDETheme;
import sh.tinywifi.canvasglsl.ide.ShaderWorkspace;
import sh.tinywifi.canvasglsl.media.MediaEntry;
import sh.tinywifi.canvasglsl.media.MediaType;
import sh.tinywifi.canvasglsl.modules.ShaderBackground;
import sh.tinywifi.canvasglsl.shader.ShaderPresets;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared Dear ImGui viewport used by the standalone screen and the overlay toggle.
 */
public final class ShaderIDEViewport {
    private static final String WINDOW_TITLE = "CanvasGLSL Shader IDE";
    private static final String POPUP_NEW_FILE = "New Shader";
    private static final String POPUP_SAVE_AS = "Save Shader As";
    private static final String POPUP_DELETE = "Delete Shader";

    private final ShaderIDEController controller;
    private final ShaderWorkspace workspace;
    private final ShaderEditorState editorState;
    private final ShaderCodeEditor codeEditor = new ShaderCodeEditor();

    private final ImString newFileName = new ImString("untitled.frag", 128);
    private final ImString saveAsName = new ImString("shader.frag", 128);
    private final ImBoolean shaderEnabledToggle = new ImBoolean(true);
    private final ImBoolean autoCompileToggle = new ImBoolean(true);
    private final ImBoolean autoSaveToggle = new ImBoolean(false);
    private final ImBoolean diagnosticLoggingToggle = new ImBoolean(false);
    private final ImBoolean framerateOverrideToggle = new ImBoolean(true);
    private final ImBoolean disableVsyncToggle = new ImBoolean(true);
    private final int[] framerateLimitBuffer = new int[]{120};
    private final float[] fontScale = new float[]{1.0f};
    private final ImString mediaPathInput = new ImString("", 512);

    private boolean openNewFilePopup;
    private boolean openSaveAsPopup;
    private boolean openDeletePopup;
    private Path pendingDeleteFile;
    private boolean themeApplied;
    private boolean showMediaPickerPopup;
    private Path mediaBrowserDirectory;
    private Path lastMediaDirectory;

    public ShaderIDEViewport(ShaderIDEController controller) {
        this.controller = controller;
        this.workspace = controller.getWorkspace();
        this.editorState = controller.getEditorState();
        this.mediaBrowserDirectory = resolveDefaultMediaDirectory();
        this.lastMediaDirectory = mediaBrowserDirectory;
    }

    public void ensureReady() {
        ImGuiManager gui = ImGuiManager.get();
        MinecraftClient client = MinecraftClient.getInstance();

        // Safety check: Don't initialize if still loading or no window
        if (client.getOverlay() != null || client.getWindow() == null) {
            return;
        }

        if (!gui.isInitialised()) {
            try {
                gui.init(client.getWindow().getHandle());
                themeApplied = false;
            } catch (Exception e) {
                CanvasGLSL.LOG.error("Failed to initialize ImGui - will retry later", e);
                return;
            }
        }
        if (!themeApplied) {
            editorState.getTheme().apply();
            themeApplied = true;
        }
    }

    public void renderUI(float width, float height) {
        ensureReady();

        applyKeyboardShortcuts();

        ImGui.setNextWindowSize(width * 0.75f, height * 0.8f, ImGuiCond.Once);
        ImGui.setNextWindowPos(width * 0.125f, height * 0.1f, ImGuiCond.Once);

        int windowFlags = ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoCollapse;
        String windowLabel = (editorState.hasUnsavedChanges() ? "* " : "") + WINDOW_TITLE + "###ShaderIDE";
        if (ImGui.begin(windowLabel, windowFlags)) {
            buildMenuBar();

            if (ImGui.beginTabBar("CanvasGLSL-ide-tabs")) {
                if (ImGui.beginTabItem("IDE")) {
                    drawIdeTab();
                    ImGui.endTabItem();
                }

                if (ImGui.beginTabItem("Settings")) {
                    drawSettingsTab();
                    ImGui.endTabItem();
                }

                ImGui.endTabBar();
            }

            drawStatusLine();
        }
        ImGui.end();

        handlePopups();
        renderMediaPickerPopup();
    }

    private void buildMenuBar() {
        if (!ImGui.beginMenuBar()) return;

        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("New Shader", "Ctrl+N")) openNewShaderPopup();

            boolean canSave = editorState.currentFile().isPresent();
            if (ImGui.menuItem("Save", "Ctrl+S", false, canSave)) {
                attemptSave();
            }

            if (ImGui.menuItem("Save As...", "Ctrl+Shift+S")) {
                openSaveAsPopup();
            }

            if (ImGui.menuItem("Reload", "Ctrl+R", false, canSave)) {
                editorState.currentFile().ifPresent(editorState::load);
            }

            ImGui.separator();

            if (ImGui.menuItem("Close", "Esc")) {
                closeScreen();
            }

            ImGui.endMenu();
        }

        if (ImGui.beginMenu("View")) {
            if (ImGui.menuItem("Toggle Auto Compile", "", editorState.isAutoCompileEnabled())) {
                editorState.setAutoCompile(!editorState.isAutoCompileEnabled());
            }
            if (ImGui.menuItem("Toggle Auto Save", "", editorState.isAutoSaveEnabled())) {
                editorState.setAutoSave(!editorState.isAutoSaveEnabled());
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Help")) {
            ImGui.menuItem("Workspace: " + workspace.getRoot(), "", false, false);
            ImGui.menuItem("Preset seed based on bundled TRIPPY shader", "", false, false);
            ImGui.endMenu();
        }

        ImGui.endMenuBar();
    }

    private void drawIdeTab() {
        final float leftPaneWidth = Math.max(220f, ImGui.getContentRegionAvailX() * 0.25f);

        ImGui.beginChild("ide-left-pane", leftPaneWidth, 0f, true);
        drawFileManager(leftPaneWidth);
        ImGui.endChild();

        ImGui.sameLine();

        ImGui.beginChild("ide-editor-pane", 0f, 0f, false);
        boolean changed = codeEditor.render(editorState);
        if (changed) {
            editorState.markDirty(true);
            if (editorState.isAutoSaveEnabled()) {
                attemptSave();
            }
        }
        ImGui.endChild();
    }

    private void drawSettingsTab() {
        ShaderIDETheme currentTheme = editorState.getTheme();
        ShaderBackground background = CanvasGLSL.SHADER_BACKGROUND;

        shaderEnabledToggle.set(background.isEnabled());
        if (ImGui.checkbox("Enable shader panorama", shaderEnabledToggle)) {
            background.setEnabled(shaderEnabledToggle.get());
            if (shaderEnabledToggle.get()) {
                background.requestManualCompile();
            }
        }

        ImGui.separator();

        if (ImGui.beginCombo("IDE Theme", currentTheme.displayName())) {
            for (ShaderIDETheme theme : ShaderIDETheme.values()) {
                boolean selected = theme == currentTheme;
                if (ImGui.selectable(theme.displayName(), selected)) {
                    editorState.setTheme(theme);
                    themeApplied = true;
                }
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        autoCompileToggle.set(editorState.isAutoCompileEnabled());
        if (ImGui.checkbox("Auto compile on save", autoCompileToggle)) {
            editorState.setAutoCompile(autoCompileToggle.get());
        }

        autoSaveToggle.set(editorState.isAutoSaveEnabled());
        if (ImGui.checkbox("Auto save", autoSaveToggle)) {
            editorState.setAutoSave(autoSaveToggle.get());
        }

        diagnosticLoggingToggle.set(editorState.isDiagnosticLoggingEnabled());
        if (ImGui.checkbox("Verbose shader diagnostics", diagnosticLoggingToggle)) {
            editorState.setDiagnosticLogging(diagnosticLoggingToggle.get());
            CanvasGLSL.LOG.info("CanvasGLSL diagnostic logging {}", diagnosticLoggingToggle.get() ? "enabled" : "disabled");
            editorState.setStatus("Diagnostic logging " + (diagnosticLoggingToggle.get() ? "enabled" : "disabled"));
        }

        framerateOverrideToggle.set(editorState.isFramerateOverrideEnabled());
        if (ImGui.checkbox("Override menu frame rate", framerateOverrideToggle)) {
            editorState.setFramerateOverrideEnabled(framerateOverrideToggle.get());
            editorState.setStatus(framerateOverrideToggle.get()
                ? "Menu frame rate override enabled"
                : "Menu frame rate override disabled");
        }

        if (framerateOverrideToggle.get()) {
            framerateLimitBuffer[0] = editorState.getFramerateLimit();
            if (ImGui.sliderInt("Menu FPS limit", framerateLimitBuffer, 30, ShaderBackground.FPS_UNLOCK_VALUE, "%d FPS")) {
                editorState.setFramerateLimit(framerateLimitBuffer[0]);
                editorState.setStatus("Menu FPS limit set to " + framerateLimitBuffer[0]);
            }

            disableVsyncToggle.set(editorState.isDisableVsyncDuringOverride());
            if (ImGui.checkbox("Disable VSync while shader runs", disableVsyncToggle)) {
                editorState.setDisableVsyncDuringOverride(disableVsyncToggle.get());
                editorState.setStatus(disableVsyncToggle.get()
                    ? "VSync disabled for menu shader"
                    : "VSync enabled for menu shader");
            }
        }

        fontScale[0] = editorState.getFontScale();
        if (ImGui.sliderFloat("Editor zoom", fontScale, 0.8f, 1.8f, "%.2fx")) {
            editorState.setFontScale(fontScale[0]);
        }

        ImGui.separator();

        ShaderPresets preset = editorState.getActivePreset();
        String presetLabel = preset != null ? preset.name() : "Custom";
        if (ImGui.beginCombo("Load preset", presetLabel)) {
            for (ShaderPresets value : ShaderPresets.values()) {
                boolean selected = value == preset;
                if (ImGui.selectable(value.name(), selected)) {
                    editorState.applyPreset(value);
                }
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        if (ImGui.button("Reset to preset")) {
            ShaderPresets active = editorState.getActivePreset();
            editorState.applyPreset(active != null ? active : ShaderPresets.TRIPPY);
        }

        ImGui.separator();

        if (ImGui.button("Compile now")) {
            background.requestManualCompile();
        }
        ImGui.sameLine();
        if (ImGui.button("Open workspace in explorer")) {
            openWorkspaceInExplorer();
        }
        ImGui.sameLine();
        if (ImGui.button("Save + Compile")) {
            attemptSave();
            background.requestManualCompile();
        }

        ImGui.separator();

        ImGui.text("Workspace directory:");
        ImGui.textColored(ImColor.rgba(170, 170, 170, 255), workspace.getRoot().toString());

        ImGui.spacing();
    }

    private void drawStatusLine() {
        ImGui.separator();
        ImGui.textDisabled(editorState.getStatusMessage());
    }

    private void drawFileManager(float width) {
        ImGui.text("Workspace");
        ImGui.separator();

        if (ImGui.button("New")) openNewShaderPopup();
        ImGui.sameLine();
        if (ImGui.button("Save")) attemptSave();
        ImGui.sameLine();
        if (ImGui.button("Save As")) openSaveAsPopup();
        ImGui.sameLine();
        if (ImGui.button("New media")) {
            openMediaChooser();
        }

        ImGui.sameLine();
        if (ImGui.button("Reload")) {
            editorState.currentFile().ifPresent(editorState::load);
        }

        ImGui.spacing();

        renderDirectory(workspace.getRoot());
    }

    private void renderDirectory(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> children = stream
                .sorted((a, b) -> {
                    boolean dirA = Files.isDirectory(a);
                    boolean dirB = Files.isDirectory(b);
                    if (dirA != dirB) return dirA ? -1 : 1;
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                })
                .collect(Collectors.toList());

            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    String label = child.getFileName().toString();
                    boolean open = ImGui.treeNodeEx(label, ImGuiTreeNodeFlags.SpanFullWidth | ImGuiTreeNodeFlags.DefaultOpen);
                    if (open) {
                        renderDirectory(child);
                        ImGui.treePop();
                    }
                } else if (workspace.isMediaDescriptor(child)) {
                    boolean selected = controller.getCurrentMediaEntry().map(entry -> entry.descriptorFile().equals(child)).orElse(false);
                    if (ImGui.selectable(child.getFileName().toString(), selected)) {
                        if (!selected) {
                            if (!controller.loadMediaDescriptor(child)) {
                                editorState.setStatus("Failed to load media descriptor");
                            }
                        }
                    }

                    if (ImGui.beginPopupContextItem()) {
                        if (ImGui.menuItem("Delete")) {
                            pendingDeleteFile = child;
                            openDeletePopup = true;
                        }
                        ImGui.endPopup();
                    }
                } else if (workspace.hasSupportedExtension(child)) {
                    boolean selected = editorState.currentFile().map(child::equals).orElse(false);
                    if (ImGui.selectable(child.getFileName().toString(), selected)) {
                        if (!selected) {
                            if (editorState.load(child)) {
                                controller.notifyShaderSaved();
                            }
                        }
                    }

                    if (ImGui.beginPopupContextItem()) {
                        if (ImGui.menuItem("Delete")) {
                            pendingDeleteFile = child;
                            openDeletePopup = true;
                        }
                        ImGui.endPopup();
                    }
                }
            }
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to enumerate shader workspace", ex);
            ImGui.textColored(ImColor.rgba(255, 85, 85, 255), "Failed to list directory " + directory.getFileName());
        }
    }

    private void handlePopups() {
        if (openNewFilePopup) {
            ImGui.openPopup(POPUP_NEW_FILE);
            openNewFilePopup = false;
        }

        if (openSaveAsPopup) {
            ImGui.openPopup(POPUP_SAVE_AS);
            openSaveAsPopup = false;
        }

        if (openDeletePopup) {
            ImGui.openPopup(POPUP_DELETE);
            openDeletePopup = false;
        }

        if (ImGui.beginPopupModal(POPUP_NEW_FILE)) {
            ImGui.text("Create a new shader file inside the workspace.");
            ImGui.inputText("File name", newFileName);

            if (ImGui.button("Create")) {
                createNewShader(newFileName.get());
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }

        if (ImGui.beginPopupModal(POPUP_SAVE_AS)) {
            ImGui.text("Choose a destination under " + workspace.getRoot().getFileName());
            ImGui.inputText("File name", saveAsName);

            if (ImGui.button("Save")) {
                saveShaderAs(saveAsName.get());
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }

        if (ImGui.beginPopupModal(POPUP_DELETE)) {
            ImGui.textColored(ImColor.rgba(255, 102, 102, 255), "This will delete the selected shader file.");
            if (pendingDeleteFile != null) {
                ImGui.text(pendingDeleteFile.getFileName().toString());
            }

            if (ImGui.button("Delete")) {
                if (pendingDeleteFile != null) {
                    workspace.deleteFile(pendingDeleteFile);
                    if (editorState.currentFile().map(pendingDeleteFile::equals).orElse(false)) {
                        editorState.resetToEmpty();
                    }
                    editorState.setStatus("Deleted " + pendingDeleteFile.getFileName());
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void applyKeyboardShortcuts() {
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.S, false)) {
            attemptSave();
        }
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.O, false)) {
            openSaveAsPopup();
        }
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.N, false)) {
            openNewShaderPopup();
        }
    }

    private void attemptSave() {
        Optional<Path> current = editorState.currentFile();
        if (current.isPresent()) {
            if (editorState.save()) {
                editorState.setStatus("Saved " + workspace.getRoot().relativize(current.get()));
                controller.notifyShaderSaved();
            }
        } else {
            openSaveAsPopup();
        }
    }

    private void createNewShader(String fileName) {
        try {
            Path path = workspace.resolve(fileName);
            if (Files.exists(path)) {
                editorState.setStatus("File already exists: " + fileName);
                editorState.load(path);
                return;
            }
            editorState.resetToEmpty();
            if (editorState.saveAs(path)) {
                controller.notifyShaderSaved();
                editorState.load(path);
                editorState.setStatus("Created " + fileName);
            }
        } catch (IllegalArgumentException ex) {
            editorState.setStatus("Invalid file path");
        }
    }

    private void saveShaderAs(String name) {
        try {
            Path destination = workspace.resolve(name);
            if (editorState.saveAs(destination)) {
                controller.notifyShaderSaved();
            }
        } catch (IllegalArgumentException ex) {
            editorState.setStatus("Invalid save path");
        }
    }

    private void openNewShaderPopup() {
        newFileName.set("untitled.frag");
        openNewFilePopup = true;
    }

    private void openSaveAsPopup() {
        saveAsName.set(editorState.currentFile()
            .map(path -> workspace.getRoot().relativize(path).toString())
            .orElse("shader.frag"));
        openSaveAsPopup = true;
    }

    private void openMediaChooser() {
        if (showMediaPickerPopup) return;
        if (lastMediaDirectory != null && Files.isDirectory(lastMediaDirectory)) {
            mediaBrowserDirectory = lastMediaDirectory;
        } else {
            mediaBrowserDirectory = resolveDefaultMediaDirectory();
        }
        mediaPathInput.set("");
        showMediaPickerPopup = true;
    }

    private void handleMediaSelection(Path source) {
        if (source == null) return;
        if (!Files.exists(source)) {
            editorState.setStatus("File not found: " + source);
            return;
        }

        lastMediaDirectory = source.getParent();

        MediaType type = controller.classifyMedia(source);
        if (type == MediaType.UNSUPPORTED) {
            editorState.setStatus("Unsupported media type: " + source.getFileName());
            return;
        }

        try {
            Path descriptor = allocateMediaDescriptor(source);
            controller.saveMediaDescriptor(descriptor, source);
            if (controller.loadMediaDescriptor(descriptor)) {
                editorState.setStatus("Created media entry " + workspace.getRoot().relativize(descriptor));
            } else {
                editorState.setStatus("Saved media descriptor but failed to load it");
            }
        } catch (IOException ex) {
            editorState.setStatus("Failed to save media descriptor");
            CanvasGLSL.LOG.error("Failed to save media descriptor for {}", source, ex);
        }
    }

    private void renderMediaPickerPopup() {
        if (showMediaPickerPopup) {
            ImGui.openPopup("Select Media Source");
            showMediaPickerPopup = false;
        }

        if (ImGui.beginPopupModal("Select Media Source", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textWrapped("Choose an image, GIF, or video to use for the menu background.");
            ImGui.spacing();

            ImGui.textWrapped("Directory:");
            ImGui.textColored(ImColor.rgba(170, 170, 170, 255), mediaBrowserDirectory.toAbsolutePath().toString());

            if (ImGui.button("Up one level")) {
                Path parent = mediaBrowserDirectory.getParent();
                if (parent != null && Files.isDirectory(parent)) {
                    mediaBrowserDirectory = parent;
                }
            }
            ImGui.sameLine();
            if (ImGui.button("Workspace")) {
                Path mediaDir = workspace.resolve("media");
                if (!Files.isDirectory(mediaDir)) {
                    mediaDir = workspace.getRoot();
                }
                mediaBrowserDirectory = mediaDir;
            }
            ImGui.sameLine();
            if (ImGui.button("Home")) {
                mediaBrowserDirectory = Paths.get(System.getProperty("user.home", "."));
            }

            ImGui.inputText("Selected file", mediaPathInput);

            ImGui.separator();

            ImGui.beginChild("MediaBrowser", 520, 260, true);
            List<Path> entries = listDirectoryEntries(mediaBrowserDirectory);
            if (entries.isEmpty()) {
                ImGui.textDisabled("(Directory is empty)");
            } else {
                for (Path entry : entries) {
                    boolean isDirectory = Files.isDirectory(entry);
                    MediaType mediaType = isDirectory ? MediaType.UNSUPPORTED : controller.classifyMedia(entry);
                    boolean supported = mediaType != MediaType.UNSUPPORTED;
                    String label = (isDirectory ? "[Dir] " : "") + entry.getFileName().toString();

                    if (!isDirectory && !supported) {
                        ImGui.beginDisabled();
                    }

                    if (ImGui.selectable(label, false)) {
                        if (isDirectory) {
                            mediaBrowserDirectory = entry;
                        } else {
                            mediaPathInput.set(entry.toAbsolutePath().toString());
                        }
                    }

                    if (!isDirectory && !supported) {
                        ImGui.endDisabled();
                    }
                }
            }
            ImGui.endChild();

            boolean hasSelection = mediaPathInput.get().length() > 0;
            Path selectedPath = null;
            boolean selectionValid = false;
            if (hasSelection) {
                try {
                    selectedPath = Paths.get(mediaPathInput.get());
                    selectionValid = Files.exists(selectedPath) && controller.classifyMedia(selectedPath) != MediaType.UNSUPPORTED;
                } catch (java.nio.file.InvalidPathException | SecurityException ignored) {
                    selectionValid = false;
                }
            }

            if (!selectionValid) {
                ImGui.beginDisabled();
            }
            if (ImGui.button("Load media")) {
                if (selectedPath != null) {
                    handleMediaSelection(selectedPath);
                }
                ImGui.closeCurrentPopup();
            }
            if (!selectionValid) {
                ImGui.endDisabled();
            }

            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private List<Path> listDirectoryEntries(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .sorted(Comparator
                    .comparing((Path path) -> !Files.isDirectory(path))
                    .thenComparing(path -> path.getFileName().toString().toLowerCase()))
                .limit(500)
                .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to list media directory {}", directory, ex);
            editorState.setStatus("Failed to list directory " + directory.getFileName());
            return List.of();
        }
    }

    private Path resolveDefaultMediaDirectory() {
        if (workspace == null) {
            return Paths.get(System.getProperty("user.home", "."));
        }
        Path mediaDir = workspace.resolve("media");
        if (Files.isDirectory(mediaDir)) {
            return mediaDir;
        }
        Path root = workspace.getRoot();
        if (Files.isDirectory(root)) {
            return root;
        }
        return Paths.get(System.getProperty("user.home", "."));
    }

    private Path allocateMediaDescriptor(Path source) {
        String baseName = source.getFileName().toString();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }

        String sanitized = baseName.replaceAll("[^a-zA-Z0-9-_]", "_");
        if (sanitized.isBlank()) {
            sanitized = "media";
        }

        Path mediaDir = workspace.resolve("media");
        int counter = 0;
        Path candidate;

        do {
            String suffix = counter == 0 ? "" : "-" + counter;
            String fileName = sanitized + suffix + ".media.json";
            candidate = mediaDir.resolve(fileName);
            counter++;
        } while (Files.exists(candidate));

        return candidate;
    }

    private void closeScreen() {
        CanvasGLSL.IDE.setOverlayVisible(false);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ShaderIDEScreen) {
            client.setScreen(null);
        }
    }

    private void openWorkspaceInExplorer() {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                editorState.setStatus("Desktop browsing not supported on this platform");
                return;
            }
            Desktop.getDesktop().open(workspace.getRoot().toFile());
        } catch (IOException | UnsupportedOperationException ex) {
            editorState.setStatus("Unable to open explorer");
            CanvasGLSL.LOG.error("Failed to open workspace directory", ex);
        }
    }
}
