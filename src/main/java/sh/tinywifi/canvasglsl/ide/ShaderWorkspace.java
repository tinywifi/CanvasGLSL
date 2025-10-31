package sh.tinywifi.canvasglsl.ide;

import sh.tinywifi.canvasglsl.CanvasGLSL;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Owns the shader workspace located in {@code .minecraft/canvasglsl}.
 * Provides helper methods to load, save, and enumerate shader files.
 */
public final class ShaderWorkspace {
    public static final String WORKSPACE_FOLDER = "canvasglsl";
    private static final List<String> SUPPORTED_SHADER_EXTENSIONS = List.of(
        ".glsl", ".frag", ".fs", ".fsh", ".shader", ".txt"
    );
    private static final String MEDIA_DESCRIPTOR_EXTENSION = ".media.json";

    private final Path root;

    private ShaderWorkspace(Path root) {
        this.root = root;
        ensureExists();
    }

    public static ShaderWorkspace open() {
        Path mcDir = MinecraftClient.getInstance().runDirectory.toPath();
        Path root = mcDir.resolve(WORKSPACE_FOLDER);
        return new ShaderWorkspace(root);
    }

    private void ensureExists() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to create shader workspace at {}", root, ex);
        }
    }

    public Path getRoot() {
        return root;
    }

    public List<Entry> listEntries() {
        if (!Files.exists(root)) return Collections.emptyList();

        final List<Entry> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream
                .filter(path -> {
                    if (Files.isDirectory(path)) return true;
                    return hasSupportedExtension(path) || isMediaDescriptor(path);
                })
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> results.add(new Entry(path, Files.isDirectory(path))));
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Unable to enumerate shader workspace", ex);
        }
        return results;
    }

    public String readFile(Path path) {
        try {
            if (Files.notExists(path)) return "";
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to read shader file {}", path, ex);
            return "";
        }
    }

    public boolean writeFile(Path path, String contents) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, contents, StandardCharsets.UTF_8);
            return true;
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to write shader file {}", path, ex);
            return false;
        }
    }

    public boolean deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException ex) {
            CanvasGLSL.LOG.error("Failed to delete shader file {}", path, ex);
            return false;
        }
    }

    public Path resolve(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Attempted to escape workspace with path " + relative);
        }
        return resolved;
    }

    public boolean hasSupportedExtension(Path path) {
        if (Files.isDirectory(path)) return true;
        String name = path.getFileName().toString().toLowerCase();
        return SUPPORTED_SHADER_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    public boolean isMediaDescriptor(Path path) {
        if (Files.isDirectory(path)) return false;
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(MEDIA_DESCRIPTOR_EXTENSION);
    }

    public record Entry(Path path, boolean directory) {
        public String displayName(Path root) {
            return root.relativize(path).toString().replace('\\', '/');
        }
    }
}
