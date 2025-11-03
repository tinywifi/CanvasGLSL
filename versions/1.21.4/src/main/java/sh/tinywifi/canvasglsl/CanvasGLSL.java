package sh.tinywifi.canvasglsl;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import sh.tinywifi.canvasglsl.ide.ShaderIDEController;
import sh.tinywifi.canvasglsl.modules.ShaderBackground;
import sh.tinywifi.canvasglsl.render.ResourcePackShaderLoader;

public class CanvasGLSL implements ClientModInitializer {
    public static final Logger LOG = LogUtils.getLogger();
    public static final ResourcePackShaderLoader SHADER_LOADER = new ResourcePackShaderLoader();
    public static final ShaderIDEController IDE = ShaderIDEController.get();
    public static final ShaderBackground SHADER_BACKGROUND = new ShaderBackground();

    @Override
    public void onInitializeClient() {
        LOG.info("Initializing CanvasGLSL (standalone)");

        SHADER_BACKGROUND.initialize();
        IDE.loadPersistedState();

        // Note: Don't compile shader here - it will be auto-compiled on first render
        // when the OpenGL context is fully initialized
    }
}
