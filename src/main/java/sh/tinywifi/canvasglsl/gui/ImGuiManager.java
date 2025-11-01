package sh.tinywifi.canvasglsl.gui;

import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

/**
 * Centralised Dear ImGui bootstrapper. Responsible for initialising the context,
 * driving the per-frame lifecycle, and cleaning up native resources when the game shuts down.
 */
public final class ImGuiManager {
    private static final ImGuiManager INSTANCE = new ImGuiManager();

    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();

    private boolean initialised;
    private boolean frameActive;

    private ImGuiManager() {}

    public static ImGuiManager get() {
        return INSTANCE;
    }

    /**
     * Lazy initialisation guard. ImGui is brought online the first time we need to render any GUI widgets.
     */
    public void init(long windowHandle) {
        if (initialised) return;

        ImGui.createContext();
        final ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null); // Avoid writing imgui.ini into the user's folder
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);

        final ImFontAtlas fonts = io.getFonts();
        fonts.addFontDefault();

        glfw.init(windowHandle, true);
        gl3.init();  // Let imgui-java auto-detect the GL version

        initialised = true;
    }

    public boolean beginFrame() {
        if (!initialised) {
            throw new IllegalStateException("ImGuiManager#init must be called before beginFrame().");
        }

        if (frameActive) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();

        int framebufferWidth = window.getFramebufferWidth();
        int framebufferHeight = window.getFramebufferHeight();
        GL11C.glViewport(0, 0, framebufferWidth, framebufferHeight);

        // Let GLFW backend handle DisplaySize and DisplayFramebufferScale
        // Don't overwrite them here or clip-space calculations will be wrong
        glfw.newFrame();
        gl3.newFrame();
        ImGui.newFrame();

        frameActive = true;
        return true;
    }

    public void endFrame() {
        if (!initialised || !frameActive) return;

        // Clear any residual OpenGL state from previous renders
        GL20C.glUseProgram(0);
        GL30C.glBindVertexArray(0);

        // Ensure color mask is fully enabled for all channels (RGBA)
        GL11C.glColorMask(true, true, true, true);

        GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthMask(false);
        GL11C.glEnable(GL11C.GL_BLEND);
        GL11C.glBlendFunc(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA);

        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());

        GL11C.glDisable(GL11C.GL_BLEND);
        GL11C.glDepthMask(true);
        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        GL30C.glBindVertexArray(0);
        GL20C.glUseProgram(0);

        frameActive = false;
    }

    public void shutdown() {
        if (!initialised) return;

        ImGui.destroyContext();
        initialised = false;
    }

    public boolean isInitialised() {
        return initialised;
    }
}

