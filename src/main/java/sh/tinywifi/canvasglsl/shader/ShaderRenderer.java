package sh.tinywifi.canvasglsl.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.render.GlobalState;
import sh.tinywifi.canvasglsl.render.ShaderPatcher;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Random;

public class ShaderRenderer {
    private int shaderProgram = -1;
    private int vertexShader = -1;
    private int fragmentShader = -1;

    private int timeUniform = -1;
    private int resolutionUniform = -1;
    private int mouseUniform = -1;
    private int imageUniform = -1;
    private int backbufferUniform = -1;
    private int frameUniform = -1;
    private int persistentFrameUniform = -1;
    private int speedUniform = -1;
    private int iTimeUniform = -1;
    private int iResolutionUniform = -1;
    private int iMouseUniform = -1;
    private int iFrameUniform = -1;
    private final int[] channelUniforms = new int[4];
    private final int[] channelResolutionUniforms = new int[4];
    private final int[] channelTimeUniforms = new int[4];
    private final int[] channelTextures = new int[4];
    private final int[] channelWidths = new int[4];
    private final int[] channelHeights = new int[4];

    private int vao = -1;
    private int vbo = -1;
    private boolean glResourcesInitialized;

    private final MinecraftClient mc;
    private long startTimeNanos;
    private long frameCounter = 0;
    private boolean panoramaSpeedChecked;
    private Method panoramaSpeedMethod;
    private double lastMouseClickX;
    private double lastMouseClickY;
    private boolean lastMouseDown;

    private static final String DEFAULT_VERTEX_SHADER = """
        #version 330 core
        layout(location = 0) in vec3 position;

        void main() {
            gl_Position = vec4(position, 1.0);
        }
        """;

    public ShaderRenderer() {
        this.mc = MinecraftClient.getInstance();
        this.startTimeNanos = System.nanoTime();
    }

    public void ensureInitialized() {
        if (glResourcesInitialized) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        initializeQuad();
        initializeChannelTextures();
        glResourcesInitialized = true;
    }

    private void initializeQuad() {
        // Create VAO
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        // Create VBO
        vbo = GL20.glGenBuffers();
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vbo);

        // Fullscreen quad vertices (covering NDC space -1 to 1)
        float[] vertices = {
            -1.0f, -1.0f, 0.0f,  // Bottom-left
             1.0f, -1.0f, 0.0f,  // Bottom-right
             1.0f,  1.0f, 0.0f,  // Top-right
            -1.0f,  1.0f, 0.0f   // Top-left
        };

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length);
        vertexBuffer.put(vertices).flip();

        GL20.glBufferData(GL20.GL_ARRAY_BUFFER, vertexBuffer, GL20.GL_STATIC_DRAW);

        // Set vertex attribute pointer
        GL20.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        // Unbind
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        CanvasGLSL.LOG.info("Initialized shader quad with VAO={} VBO={}", vao, vbo);
    }

    private void initializeChannelTextures() {
        RenderSystem.assertOnRenderThread();
        final int size = 256;

        for (int i = 0; i < channelTextures.length; i++) {
            channelTextures[i] = GL11.glGenTextures();
            channelWidths[i] = size;
            channelHeights[i] = size;

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, channelTextures[i]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_REPEAT);

            ByteBuffer data = BufferUtils.createByteBuffer(size * size * 4);
            Random random = new Random(0xC0FFEE + i * 997);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float fx = x / (float) (size - 1);
                    float fy = y / (float) (size - 1);
                    int r;
                    int g;
                    int b;

                    switch (i) {
                        case 0 -> {
                            int value = random.nextInt(256);
                            r = value;
                            g = value;
                            b = value;
                        }
                        case 1 -> {
                            r = Math.min(255, Math.round(fx * 255f));
                            g = Math.min(255, Math.round(fy * 255f));
                            b = Math.min(255, Math.round(((fx + fy) * 0.5f) * 255f));
                        }
                        case 2 -> {
                            int stripe = ((x ^ y) & 15) * 16;
                            r = stripe;
                            g = 255 - stripe;
                            b = stripe / 2 + 64;
                        }
                        default -> {
                            r = random.nextInt(256);
                            g = random.nextInt(256);
                            b = random.nextInt(256);
                        }
                    }

                    data.put((byte) r);
                    data.put((byte) g);
                    data.put((byte) b);
                    data.put((byte) 255);
                }
            }
            data.flip();

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        CanvasGLSL.LOG.info("Initialized {} fallback shader channels ({}x{})", channelTextures.length, size, size);
    }

    public boolean compileShader(String fragmentSource) {
        return compileShader(DEFAULT_VERTEX_SHADER, fragmentSource, null);
    }

    public boolean compileShader(String vertexSource, String fragmentSource, Identifier textureId) {
        RenderSystem.assertOnRenderThread();
        ensureInitialized();
        cleanupShader();

        try {
            CanvasGLSL.LOG.info("=== COMPILING SHADER ===");
            CanvasGLSL.LOG.info("Vertex shader source:\n{}", vertexSource);
            CanvasGLSL.LOG.info("Fragment shader source:\n{}", fragmentSource);

            // Patch shaders for compatibility
            String processedVertex = ShaderPatcher.patch(vertexSource);
            String processedFragment = ShaderPatcher.patch(fragmentSource);

            CanvasGLSL.LOG.info("Processed vertex shader:\n{}", processedVertex);
            CanvasGLSL.LOG.info("Processed fragment shader:\n{}", processedFragment);

            // Compile vertex shader
            vertexShader = compileShaderPart(processedVertex, GL20.GL_VERTEX_SHADER);
            if (vertexShader == -1) return false;
            CanvasGLSL.LOG.info("Vertex shader compiled, ID: {}", vertexShader);

            // Compile fragment shader
            fragmentShader = compileShaderPart(processedFragment, GL20.GL_FRAGMENT_SHADER);
            if (fragmentShader == -1) return false;
            CanvasGLSL.LOG.info("Fragment shader compiled, ID: {}", fragmentShader);

            // Link program
            shaderProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shaderProgram, vertexShader);
            GL20.glAttachShader(shaderProgram, fragmentShader);
            GL20.glLinkProgram(shaderProgram);

            if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(shaderProgram, 1024);
                CanvasGLSL.LOG.error("Failed to link shader program! Caused by: {}", log);
                return false;
            }

            CanvasGLSL.LOG.info("Shader program linked, ID: {}", shaderProgram);

            // Free now unused resources
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            vertexShader = -1;
            fragmentShader = -1;

            // Get uniform locations
            timeUniform = GL20.glGetUniformLocation(shaderProgram, "time");
            resolutionUniform = GL20.glGetUniformLocation(shaderProgram, "resolution");
            mouseUniform = GL20.glGetUniformLocation(shaderProgram, "mouse");
            frameUniform = GL20.glGetUniformLocation(shaderProgram, "frame");
            persistentFrameUniform = GL20.glGetUniformLocation(shaderProgram, "persistent_frame");
            speedUniform = GL20.glGetUniformLocation(shaderProgram, "speed");
            iTimeUniform = GL20.glGetUniformLocation(shaderProgram, "iTime");
            iResolutionUniform = GL20.glGetUniformLocation(shaderProgram, "iResolution");
            iMouseUniform = GL20.glGetUniformLocation(shaderProgram, "iMouse");
            iFrameUniform = GL20.glGetUniformLocation(shaderProgram, "iFrame");
            for (int i = 0; i < channelUniforms.length; i++) {
                channelUniforms[i] = -1;
                channelResolutionUniforms[i] = -1;
                channelTimeUniforms[i] = -1;
            }
            for (int i = 0; i < channelUniforms.length; i++) {
                channelUniforms[i] = GL20.glGetUniformLocation(shaderProgram, "iChannel" + i);
                channelResolutionUniforms[i] = GL20.glGetUniformLocation(shaderProgram, "iChannelResolution[" + i + "]");
                channelTimeUniforms[i] = GL20.glGetUniformLocation(shaderProgram, "iChannelTime[" + i + "]");
            }

            CanvasGLSL.LOG.info("Uniform locations:");
            CanvasGLSL.LOG.info("  time: {}", timeUniform);
            CanvasGLSL.LOG.info("  resolution: {}", resolutionUniform);
            CanvasGLSL.LOG.info("  mouse: {}", mouseUniform);
            CanvasGLSL.LOG.info("  frame: {}", frameUniform);
            CanvasGLSL.LOG.info("  persistent_frame: {}", persistentFrameUniform);
            CanvasGLSL.LOG.info("  speed: {}", speedUniform);
            CanvasGLSL.LOG.info("  iTime: {}", iTimeUniform);
            CanvasGLSL.LOG.info("  iResolution: {}", iResolutionUniform);
            CanvasGLSL.LOG.info("  iMouse: {}", iMouseUniform);
            CanvasGLSL.LOG.info("  iFrame: {}", iFrameUniform);
            for (int i = 0; i < channelUniforms.length; i++) {
                CanvasGLSL.LOG.info("  iChannel{}: {} (resolution {} time {})",
                    i,
                    channelUniforms[i],
                    channelResolutionUniforms[i],
                    channelTimeUniforms[i]);
            }

            CanvasGLSL.LOG.info("Shader compiled successfully");
            CanvasGLSL.LOG.info("=== END COMPILATION ===");
            return true;

        } catch (Exception e) {
            CanvasGLSL.LOG.error("Failed to compile shader", e);
            cleanupShader();
            return false;
        }
    }

    private int compileShaderPart(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 1024);
            String shaderType = (type == GL20.GL_VERTEX_SHADER) ? "vertex" : "fragment";
            CanvasGLSL.LOG.error("Failed to compile {} shader! Caused by: {}", shaderType, log);
            return -1;
        }

        return shader;
    }

    public void render(int width, int height, float alpha, double quality) {
        RenderSystem.assertOnRenderThread();
        ensureInitialized();
        if (shaderProgram == -1) {
            CanvasGLSL.LOG.error("Render called but shader program is not compiled!");
            return;
        }

        if (vao == -1) {
            CanvasGLSL.LOG.error("Render called but VAO is not initialized!");
            return;
        }

        // Log detailed info only once per second
        if (frameCounter % 60 == 0) {
            CanvasGLSL.LOG.info("=== RENDER DEBUG (Frame {}) ===", frameCounter);
            CanvasGLSL.LOG.info("Viewport: {}x{}, Alpha: {}", width, height, alpha);
            CanvasGLSL.LOG.info("Shader Program ID: {}", shaderProgram);
            CanvasGLSL.LOG.info("VAO: {}, VBO: {}", vao, vbo);
        }

        try {
            // Check for OpenGL errors before rendering
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                CanvasGLSL.LOG.warn("OpenGL error before render: {}", error);
            }

            // Disable depth test and enable blending
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Use program
            GL20.glUseProgram(shaderProgram);

            // Set uniforms
            long nowNanos = System.nanoTime();
            float currentTime = (nowNanos - startTimeNanos) / 1_000_000_000f;
            if (frameCounter % 60 == 0) {
                CanvasGLSL.LOG.info("Timing debug: now={}ns start={}ns elapsed={}s", nowNanos, startTimeNanos, currentTime);
            }
            if (timeUniform != -1) {
                GL20.glUniform1f(timeUniform, currentTime);
            }
            if (iTimeUniform != -1) {
                GL20.glUniform1f(iTimeUniform, currentTime);
            }

            if (resolutionUniform != -1) {
                GL20.glUniform2f(resolutionUniform, (float) width, (float) height);
            }
            if (iResolutionUniform != -1) {
                GL20.glUniform3f(iResolutionUniform, (float) width, (float) height, 1.0f);
            }

            Window window = mc.getWindow();

            if (mc.mouse != null && window != null) {
                float normalizedX = (float) mc.mouse.getX() / Math.max(width, 1);
                float normalizedY = (float) mc.mouse.getY() / Math.max(height, 1);

                if (mouseUniform != -1) {
                    GL20.glUniform2f(mouseUniform, normalizedX, normalizedY);
                }

                float pixelX = (float) mc.mouse.getX();
                float pixelY = (float) (height - mc.mouse.getY());
                boolean leftDown = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                if (leftDown && !lastMouseDown) {
                    lastMouseClickX = pixelX;
                    lastMouseClickY = pixelY;
                }
                lastMouseDown = leftDown;

                if (iMouseUniform != -1) {
                    GL20.glUniform4f(
                        iMouseUniform,
                        pixelX,
                        pixelY,
                        (float) lastMouseClickX,
                        (float) lastMouseClickY
                    );
                }
            } else {
                lastMouseDown = false;
                if (mouseUniform != -1) {
                    GL20.glUniform2f(mouseUniform, 0f, 0f);
                }
                if (iMouseUniform != -1) {
                    GL20.glUniform4f(iMouseUniform, 0f, 0f, (float) lastMouseClickX, (float) lastMouseClickY);
                }
            }

            if (frameUniform != -1) {
                GL20.glUniform1i(frameUniform, (int) frameCounter);
            }
            if (iFrameUniform != -1) {
                GL20.glUniform1i(iFrameUniform, (int) frameCounter);
            }

            if (persistentFrameUniform != -1) {
                GL20.glUniform1i(persistentFrameUniform, GlobalState.getFrame());
            }

            if (speedUniform != -1) {
                GL20.glUniform1f(speedUniform, resolvePanoramaSpeed());
            }

            for (int channel = 0; channel < channelUniforms.length; channel++) {
                if (channelUniforms[channel] != -1 && channelTextures[channel] != 0) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + channel);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, channelTextures[channel]);
                    GL20.glUniform1i(channelUniforms[channel], channel);
                }

                if (channelResolutionUniforms[channel] != -1) {
                    GL20.glUniform3f(
                        channelResolutionUniforms[channel],
                        channelWidths[channel],
                        channelHeights[channel],
                        0f
                    );
                }

                if (channelTimeUniforms[channel] != -1) {
                    GL20.glUniform1f(channelTimeUniforms[channel], currentTime);
                }
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            // Log uniform values once per second
            if (frameCounter % 60 == 0) {
                CanvasGLSL.LOG.info("Uniform values:");
                CanvasGLSL.LOG.info("  time: {}", currentTime);
                CanvasGLSL.LOG.info("  resolution: {}x{}", width, height);
                CanvasGLSL.LOG.info("  frame: {}", frameCounter);
            }

            // Draw fullscreen quad
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
            GL30.glBindVertexArray(0);

            GL20.glUseProgram(0);

            // Check for OpenGL errors after rendering
            error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                CanvasGLSL.LOG.error("OpenGL error after render: {}", error);
            }

            if (frameCounter % 60 == 0) {
                CanvasGLSL.LOG.info("=== END RENDER DEBUG ===");
            }

            frameCounter++;
        } catch (Exception e) {
            CanvasGLSL.LOG.error("Error during shader rendering", e);
        }
    }

    private float resolvePanoramaSpeed() {
        if (!panoramaSpeedChecked) {
            panoramaSpeedChecked = true;
            try {
                panoramaSpeedMethod = mc.options.getClass().getMethod("getPanoramaSpeed");
            } catch (NoSuchMethodException e) {
                panoramaSpeedMethod = null;
                CanvasGLSL.LOG.debug("Panorama speed option unavailable on this version; using default speed.");
            }
        }

        if (panoramaSpeedMethod != null) {
            try {
                Object option = panoramaSpeedMethod.invoke(mc.options);
                if (option instanceof SimpleOption<?> simpleOption) {
                    Object value = simpleOption.getValue();
                    if (value instanceof Number number) {
                        return number.floatValue();
                    }
                } else if (option instanceof Number number) {
                    return number.floatValue();
                }
            } catch (ReflectiveOperationException e) {
                CanvasGLSL.LOG.debug("Failed to resolve panorama speed via reflection; falling back to default.", e);
                panoramaSpeedMethod = null;
            }
        }

        return 1.0f;
    }


    private void cleanupShader() {
        if (fragmentShader != -1) {
            GL20.glDeleteShader(fragmentShader);
            fragmentShader = -1;
        }
        if (vertexShader != -1) {
            GL20.glDeleteShader(vertexShader);
            vertexShader = -1;
        }
        if (shaderProgram != -1) {
            GL20.glDeleteProgram(shaderProgram);
            shaderProgram = -1;
        }
    }

    public void cleanup() {
        RenderSystem.assertOnRenderThread();
        cleanupShader();
        for (int i = 0; i < channelTextures.length; i++) {
            if (channelTextures[i] != 0) {
                GL11.glDeleteTextures(channelTextures[i]);
                channelTextures[i] = 0;
            }
        }
        if (vbo != -1) {
            GL20.glDeleteBuffers(vbo);
            vbo = -1;
        }
        if (vao != -1) {
            GL30.glDeleteVertexArrays(vao);
            vao = -1;
        }
        glResourcesInitialized = false;
    }

    public boolean isCompiled() {
        return shaderProgram != -1;
    }

    public void resetTime() {
        this.startTimeNanos = System.nanoTime();
        CanvasGLSL.LOG.info("ShaderRenderer time reset; new start={}ns", startTimeNanos);
        this.frameCounter = 0;
    }
}
