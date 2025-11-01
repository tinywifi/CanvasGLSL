package sh.tinywifi.canvasglsl.gui;

import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

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
    private int previousFramebufferBinding = -1;
    private boolean framebufferOverrideApplied;

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

        framebufferOverrideApplied = false;
        previousFramebufferBinding = -1;

        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();

        Framebuffer framebuffer = mc.getFramebuffer();
        if (framebuffer != null) {
            int currentBinding = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            previousFramebufferBinding = currentBinding;
            GpuTexture colorAttachment = framebuffer.getColorAttachment();
            if (colorAttachment instanceof GlTexture glTexture) {
                var device = RenderSystem.getDevice();
                if (device instanceof GlBackend backend) {
                    GlStateManager._glBindFramebuffer(
                        GL30C.GL_FRAMEBUFFER,
                        glTexture.getOrCreateFramebuffer(backend.getBufferManager(), framebuffer.getDepthAttachment())
                    );
                    framebufferOverrideApplied = true;
                }
            }
        }

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

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer lastViewport = stack.mallocInt(4);
            IntBuffer lastScissorBox = stack.mallocInt(4);
            IntBuffer lastPolygonMode = stack.mallocInt(2);
            ByteBuffer lastColorMask = stack.malloc(4);

            GLCapabilities caps = GL.getCapabilities();
            boolean samplerObjectsSupported = caps.OpenGL33 || caps.GL_ARB_sampler_objects;
            boolean primitiveRestartSupported = caps.OpenGL31 || caps.GL_NV_primitive_restart;

            int lastProgram = GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            int lastActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            int lastTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            int lastSampler = samplerObjectsSupported ? GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING) : 0;
            int lastArrayBuffer = GL15C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
            int lastElementArrayBuffer = GL15C.glGetInteger(GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            int lastVertexArray = GL30C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
            GL11C.glGetIntegerv(GL11C.GL_POLYGON_MODE, lastPolygonMode);
            int lastBlendSrcRgb = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB);
            int lastBlendDstRgb = GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB);
            int lastBlendSrcAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA);
            int lastBlendDstAlpha = GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA);
            int lastBlendEquationRgb = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB);
            int lastBlendEquationAlpha = GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA);
            boolean lastEnableBlend = GL11C.glIsEnabled(GL11C.GL_BLEND);
            boolean lastEnableCullFace = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
            boolean lastEnableDepthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
            boolean lastEnableStencilTest = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
            boolean lastEnableScissorTest = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
            boolean lastEnablePrimitiveRestart = primitiveRestartSupported && GL11C.glIsEnabled(GL31C.GL_PRIMITIVE_RESTART);
            boolean lastDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);

            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, lastViewport);
            GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, lastScissorBox);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, lastColorMask);

            int viewportX = lastViewport.get(0);
            int viewportY = lastViewport.get(1);
            int viewportWidth = lastViewport.get(2);
            int viewportHeight = lastViewport.get(3);
            int scissorX = lastScissorBox.get(0);
            int scissorY = lastScissorBox.get(1);
            int scissorWidth = lastScissorBox.get(2);
            int scissorHeight = lastScissorBox.get(3);
            int polygonModeFront = lastPolygonMode.get(0);
            int polygonModeBack = lastPolygonMode.get(1);
            boolean colorMaskRed = lastColorMask.get(0) != 0;
            boolean colorMaskGreen = lastColorMask.get(1) != 0;
            boolean colorMaskBlue = lastColorMask.get(2) != 0;
            boolean colorMaskAlpha = lastColorMask.get(3) != 0;

            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            if (primitiveRestartSupported) {
                GL11C.glDisable(GL31C.GL_PRIMITIVE_RESTART);
            }
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthMask(false);
            GL11C.glEnable(GL11C.GL_BLEND);
            GL14C.glBlendEquation(GL14C.GL_FUNC_ADD);
            GL14C.glBlendFuncSeparate(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA);
            GL11C.glColorMask(true, true, true, true);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            if (samplerObjectsSupported) {
                GL33C.glBindSampler(0, 0);
            }
            GL30C.glBindVertexArray(0);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, 0);
            GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);

            gl3.renderDrawData(ImGui.getDrawData());

            if (lastEnableBlend) {
                GL11C.glEnable(GL11C.GL_BLEND);
            } else {
                GL11C.glDisable(GL11C.GL_BLEND);
            }
            GL14C.glBlendFuncSeparate(lastBlendSrcRgb, lastBlendDstRgb, lastBlendSrcAlpha, lastBlendDstAlpha);
            GL20C.glBlendEquationSeparate(lastBlendEquationRgb, lastBlendEquationAlpha);

            if (lastEnableCullFace) {
                GL11C.glEnable(GL11C.GL_CULL_FACE);
            } else {
                GL11C.glDisable(GL11C.GL_CULL_FACE);
            }
            if (lastEnableDepthTest) {
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            }
            GL11C.glDepthMask(lastDepthMask);
            if (lastEnableStencilTest) {
                GL11C.glEnable(GL11C.GL_STENCIL_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            }
            if (lastEnableScissorTest) {
                GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            }
            if (primitiveRestartSupported) {
                if (lastEnablePrimitiveRestart) {
                    GL11C.glEnable(GL31C.GL_PRIMITIVE_RESTART);
                } else {
                    GL11C.glDisable(GL31C.GL_PRIMITIVE_RESTART);
                }
            }

            GL11C.glColorMask(colorMaskRed, colorMaskGreen, colorMaskBlue, colorMaskAlpha);
            GL11C.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            GL11C.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            GL11C.glPolygonMode(GL11C.GL_FRONT, polygonModeFront);
            GL11C.glPolygonMode(GL11C.GL_BACK, polygonModeBack);

            GL30C.glBindVertexArray(lastVertexArray);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, lastArrayBuffer);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, lastElementArrayBuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, lastTexture);
            if (samplerObjectsSupported) {
                GL33C.glBindSampler(0, lastSampler);
            }
            GL13C.glActiveTexture(lastActiveTexture);
            GL20C.glUseProgram(lastProgram);
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

        if (framebufferOverrideApplied) {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebufferBinding);
            framebufferOverrideApplied = false;
        }

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
