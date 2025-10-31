package sh.tinywifi.canvasglsl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;
import sh.tinywifi.canvasglsl.CanvasGLSL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class ResourcePackShaderLoader {
    private static final Identifier VERTEX = Identifier.of("canvasglsl", "panorama/shader.vert");
    private static final Identifier FRAGMENT = Identifier.of("canvasglsl", "panorama/shader.frag");
    private static final Identifier TEXTURE = Identifier.of("canvasglsl", "panorama/texture.png");

    private ShaderData cachedData = null;
    private boolean hasResourcePackShader = false;

    public void reload() {
        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager manager = client.getResourceManager();

        String vertex = loadStringResource(manager, VERTEX);
        String fragment = loadStringResource(manager, FRAGMENT);
        Identifier texture = getTexture(manager);

        hasResourcePackShader = !vertex.isEmpty() && !fragment.isEmpty();
        cachedData = new ShaderData(vertex, fragment, texture);

        if (hasResourcePackShader) {
            CanvasGLSL.LOG.info("Loaded shader from resource pack");
        }
    }

    private String loadStringResource(ResourceManager manager, Identifier identifier) {
        Optional<Resource> resource = manager.getResource(identifier);

        if (resource.isPresent()) {
            try {
                return IOUtils.toString(resource.get().getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                CanvasGLSL.LOG.error("Failed to open input stream!", exception);
            }
        }

        return "";
    }

    private Identifier getTexture(ResourceManager manager) {
        return manager.getResource(TEXTURE).isPresent() ? TEXTURE : null;
    }

    public ShaderData getShaderData() {
        if (cachedData == null) {
            reload();
        }
        return cachedData;
    }

    public boolean hasResourcePackShader() {
        return hasResourcePackShader;
    }

    public static class ShaderData {
        public final String vertex;
        public final String fragment;
        public final Identifier texture;

        public ShaderData(String vertex, String fragment, Identifier texture) {
            this.vertex = vertex;
            this.fragment = fragment;
            this.texture = texture;
        }
    }
}
