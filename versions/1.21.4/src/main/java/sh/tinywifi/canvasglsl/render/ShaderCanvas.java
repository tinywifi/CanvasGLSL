package sh.tinywifi.canvasglsl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
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
 *
 * Adapted for 1.21.4 - uses SimpleFramebuffer directly without GlBackend/GlTexture/GpuTexture
 */
public final class ShaderCanvas implements Closeable {
    private final Framebuffer output;
    private SimpleFramebuffer input;
    private int previousFramebuffer = -1;
    private final FullscreenQuad blitQuad;
    private final int blitProgram;
    private final int blitAlphaUniform;
    private boolean forceMainFramebuffer = false;

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
        // 1.21.4: SimpleFramebuffer constructor takes only 3 parameters
        this.input = new SimpleFramebuffer(width, height, false);
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
        // 1.21.4: resize() takes only 2 parameters
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
        // 1.21: Direct beginWrite instead of getting framebuffer ID
        input.beginWrite(true);
    }

    public void read() {
        RenderSystem.assertOnRenderThread();
        // 1.21: Direct beginRead
        input.beginRead();
    }

    public void restore() {
        RenderSystem.assertOnRenderThread();
        // 1.21: Restore to output framebuffer
        output.beginWrite(true);
    }

    public void setForceMainFramebuffer(boolean force) {
        this.forceMainFramebuffer = force;
    }

    public void blit(float alpha) {
        RenderSystem.assertOnRenderThread();

        // Switch to output framebuffer
        output.beginWrite(true);

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
        // 1.21: Use getColorAttachment() directly - it returns the GL texture ID
        RenderSystem.setShaderTexture(0, input.getColorAttachment());
        GL20.glUseProgram(blitProgram);
        GL20.glUniform1f(blitAlphaUniform, alpha);

        blitQuad.bind();
        blitQuad.draw();
        FullscreenQuad.unbind();

        GL20.glUseProgram(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Restore blend state
        GL14.glBlendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcAlpha, prevDstAlpha);
        GL20.glBlendEquationSeparate(prevEqRgb, prevEqAlpha);
        if (blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }

        // Restore depth state
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
}
