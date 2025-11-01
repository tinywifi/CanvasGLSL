package sh.tinywifi.canvasglsl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.BufferManager;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.render.FullscreenQuad;
import org.lwjgl.opengl.GL30;

import java.io.Closeable;

/**
 * Offscreen framebuffer used to render shaders at a configurable resolution
 * and then composite the result back onto Minecraft's main framebuffer.
 */
public final class ShaderCanvas implements Closeable {
    private final Framebuffer output;
    private SimpleFramebuffer input;
    private int previousFramebuffer = -1;
    private final FullscreenQuad blitQuad;
    private final int blitProgram;
    private final int blitAlphaUniform;

    private static final String BLIT_VERTEX_SHADER = """
        #version 330 core
        layout(location = 0) in vec3 position;
        layout(location = 1) in vec2 uv;
        out vec2 vUv;
        void main() {
            gl_Position = vec4(position, 1.0);
            vUv = uv;
        }
        """;

    private static final String BLIT_FRAGMENT_SHADER = """
        #version 330 core
        in vec2 vUv;
        uniform sampler2D uTexture;
        uniform float uAlpha;
        out vec4 fragColor;
        void main() {
            vec4 tex = texture(uTexture, vUv);
            // Force opaque alpha for background rendering to prevent UI flickering
            fragColor = vec4(tex.rgb * uAlpha, 1.0);
        }
        """;

    public ShaderCanvas() {
        MinecraftClient client = MinecraftClient.getInstance();
        this.output = client.getFramebuffer();
        int width = Math.max(1, output.textureWidth);
        int height = Math.max(1, output.textureHeight);
        this.input = new SimpleFramebuffer("canvasglsl_canvas", width, height, false);
        this.blitQuad = FullscreenQuad.create();
        this.blitProgram = createBlitProgram();
        GL20.glUseProgram(this.blitProgram);
        int textureUniform = GL20.glGetUniformLocation(this.blitProgram, "uTexture");
        GL20.glUniform1i(textureUniform, 0);
        this.blitAlphaUniform = GL20.glGetUniformLocation(this.blitProgram, "uAlpha");
        GL20.glUseProgram(0);
    }

    public void resize(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (width == input.textureWidth && height == input.textureHeight) {
            return;
        }
        input.resize(width, height);
    }

    public int width() {
        return input.textureWidth;
    }

    public int height() {
        return input.textureHeight;
    }

    public void write() {
        RenderSystem.assertOnRenderThread();
        previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getFramebufferId(input));
    }

    public void read() {
        RenderSystem.assertOnRenderThread();
        GpuTexture color = input.getColorAttachment();
        if (color instanceof GlTexture glTexture) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture.getGlId());
        }
    }

    public void restore() {
        RenderSystem.assertOnRenderThread();
        if (previousFramebuffer != -1) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            previousFramebuffer = -1;
        }
    }

    public void blit(float alpha) {
        RenderSystem.assertOnRenderThread();

        // CRITICAL FIX: Always blit to the main framebuffer (output), not whatever was previously bound
        // This ensures the shader background is visible even if an intermediate buffer was bound during rendering
        GpuTexture outputColor = output.getColorAttachment();
        if (!(outputColor instanceof GlTexture outputGlTexture)) {
            return;
        }
        var device = RenderSystem.getDevice();
        if (!(device instanceof GlBackend backend)) {
            return;
        }
        BufferManager buffers = backend.getBufferManager();
        int outputFboId = outputGlTexture.getOrCreateFramebuffer(buffers, output.getDepthAttachment());
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, outputFboId);

        GpuTexture colorAttachment = input.getColorAttachment();
        if (!(colorAttachment instanceof GlTexture glTexture)) {
            return;
        }

        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        int prevSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int prevEqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int prevEqAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture.getGlId());
        GL20.glUseProgram(blitProgram);
        GL20.glUniform1f(blitAlphaUniform, alpha);

        blitQuad.bind();
        blitQuad.draw();
        FullscreenQuad.unbind();

        GL20.glUseProgram(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        if (blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL14.glBlendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcAlpha, prevDstAlpha);
        GL20.glBlendEquationSeparate(prevEqRgb, prevEqAlpha);

        if (depthEnabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String info = GL20.glGetShaderInfoLog(shader);
            CanvasGLSL.LOG.error("Failed to compile blit shader: {}", info);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile blit shader: " + info);
        }
        return shader;
    }

    private static int createBlitProgram() {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, BLIT_VERTEX_SHADER);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, BLIT_FRAGMENT_SHADER);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glBindAttribLocation(program, 0, "position");
        GL20.glBindAttribLocation(program, 1, "uv");
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String info = GL20.glGetProgramInfoLog(program);
            CanvasGLSL.LOG.error("Failed to link blit program: {}", info);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            throw new IllegalStateException("Failed to link blit program: " + info);
        }
        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        return program;
    }

    @Override
    public void close() {
        input.delete();
        blitQuad.close();
        GL20.glDeleteProgram(blitProgram);
    }

    private static int getFramebufferId(SimpleFramebuffer framebuffer) {
        GpuTexture color = framebuffer.getColorAttachment();
        if (!(color instanceof GlTexture glTexture)) {
            throw new IllegalStateException("Expected GL texture attachment");
        }
        var device = RenderSystem.getDevice();
        if (!(device instanceof GlBackend backend)) {
            throw new IllegalStateException("Only OpenGL backend is supported");
        }
        BufferManager buffers = backend.getBufferManager();
        return glTexture.getOrCreateFramebuffer(buffers, framebuffer.getDepthAttachment());
    }
}
