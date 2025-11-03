package sh.tinywifi.canvasglsl.ide;

import java.nio.file.Path;

@FunctionalInterface
public interface ShaderChangeListener {
    void onShaderSaved(String source, Path file);
}
