package sh.tinywifi.canvasglsl.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Simple fullscreen quad built on top of raw OpenGL vertex array objects.
 * This replaces the removed {@code net.minecraft.client.gl.VertexBuffer}.
 */
public final class FullscreenQuad implements AutoCloseable {
    private static final int VERTEX_COUNT = 4;

    private final int vao;
    private final int vbo;

    private FullscreenQuad(int vao, int vbo) {
        this.vao = vao;
        this.vbo = vbo;
    }

    public static FullscreenQuad create() {
        float[] vertices = {
            // position (xyz)   // uv
            -1f, -1f, 0f, 0f, 0f,
             1f, -1f, 0f, 1f, 0f,
            -1f,  1f, 0f, 0f, 1f,
             1f,  1f, 0f, 1f, 1f
        };

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices);
        buffer.flip();

        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        int stride = 5 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        return new FullscreenQuad(vao, vbo);
    }

    public void bind() {
        GL30.glBindVertexArray(vao);
    }

    public void draw() {
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);
    }

    public static void unbind() {
        GL30.glBindVertexArray(0);
    }

    @Override
    public void close() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}
