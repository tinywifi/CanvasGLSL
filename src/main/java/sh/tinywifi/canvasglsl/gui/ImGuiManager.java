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
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
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
    public synchronized void init(long windowHandle) {
        if (initialised) return;

        try {
            // Save current OpenGL state to prevent conflicts with shader rendering
            int prevProgram = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            int prevVAO = GL30C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
            int prevArrayBuffer = GL20C.glGetInteger(GL20C.GL_ARRAY_BUFFER_BINDING);
            int prevTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);

            ImGui.createContext();
            final ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null); // Avoid writing imgui.ini into the user's folder
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable);

            final ImFontAtlas fonts = io.getFonts();
            fonts.addFontDefault();

            glfw.init(windowHandle, true);
            gl3.init();  // Let imgui-java auto-detect the GL version

            // Restore previous OpenGL state
            GL20C.glUseProgram(prevProgram);
            GL30C.glBindVertexArray(prevVAO);
            GL20C.glBindBuffer(GL20C.GL_ARRAY_BUFFER, prevArrayBuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTexture);

            initialised = true;
        } catch (Exception e) {
            // If initialization fails, ensure we can retry later
            initialised = false;
            throw new RuntimeException("Failed to initialize ImGui", e);
        }
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

        // Save OpenGL state before ImGui rendering
        boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
        int prevBlendSrcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);
        boolean depthTestEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean depthMaskEnabled = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        boolean scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        int prevProgram = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int prevVAO = GL30C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int prevArrayBuffer = GL20C.glGetInteger(GL20C.GL_ARRAY_BUFFER_BINDING);
        int prevElementArrayBuffer = GL11C.glGetInteger(GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int prevTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int prevActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);

        // Set up state for ImGui rendering
        GL20C.glUseProgram(0);
        GL30C.glBindVertexArray(0);
        GL11C.glColorMask(true, true, true, true);
        GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthMask(false);
        GL11C.glEnable(GL11C.GL_BLEND);
        GL11C.glBlendFunc(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
        GL20C.glBindBuffer(GL20C.GL_ARRAY_BUFFER, 0);
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, 0);

        // Render ImGui
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());

        // Restore OpenGL state for Minecraft UI
        if (blendEnabled) {
            GL11C.glEnable(GL11C.GL_BLEND);
        } else {
            GL11C.glDisable(GL11C.GL_BLEND);
        }
        GL14C.glBlendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);

        if (depthTestEnabled) {
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        } else {
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        }
        GL11C.glDepthMask(depthMaskEnabled);

        if (scissorEnabled) {
            GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
        } else {
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        }

        GL13C.glActiveTexture(prevActiveTexture);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, prevTexture);
        GL20C.glBindBuffer(GL20C.GL_ARRAY_BUFFER, prevArrayBuffer);
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, prevElementArrayBuffer);
        GL30C.glBindVertexArray(prevVAO);
        GL20C.glUseProgram(prevProgram);

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
