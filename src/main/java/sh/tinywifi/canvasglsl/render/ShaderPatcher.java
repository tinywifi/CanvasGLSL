package sh.tinywifi.canvasglsl.render;

import sh.tinywifi.canvasglsl.CanvasGLSL;

import java.util.regex.Pattern;

public class ShaderPatcher {
    private static final String FRAGMENT_OLD = "gl_FragColor";
    private static final String FRAGMENT_NEW = "fragmentColor";

    public static String patch(String shader) {
        String working = shader;

        int insertPos;
        if (working.trim().startsWith("#version")) {
            int versionStart = working.indexOf("#version");
            int lineEnd = working.indexOf('\n', versionStart);
            insertPos = lineEnd >= 0 ? lineEnd + 1 : working.length();
        } else {
            working = "#version 330\n" + working;
            insertPos = "#version 330\n".length();
        }

        StringBuilder header = new StringBuilder();

        if (working.contains(FRAGMENT_OLD)) {
            working = working.replace(FRAGMENT_OLD, FRAGMENT_NEW);
            header.append("out vec4 ").append(FRAGMENT_NEW).append(";\n");
            CanvasGLSL.LOG.warn("Loaded shader uses outdated OpenGL keyword 'gl_FragColor'!");
        }

        // Note: Ensure your fragment shader outputs opaque alpha (e.g., vec4(color.rgb, 1.0))
        // to prevent UI flickering issues. Semi-transparent backgrounds can cause rendering artifacts.

        if (!containsUniform(working, "iTime")) {
            header.append("uniform float iTime;\n");
        }
        if (!containsUniform(working, "iResolution")) {
            header.append("uniform vec3 iResolution;\n");
        }
        if (!containsUniform(working, "iMouse")) {
            header.append("uniform vec4 iMouse;\n");
        }
        if (!containsUniform(working, "iFrame")) {
            header.append("uniform int iFrame;\n");
        }
        if (!containsUniform(working, "iChannelTime")) {
            header.append("uniform float iChannelTime[4];\n");
        }
        if (!containsUniform(working, "iChannelResolution")) {
            header.append("uniform vec3 iChannelResolution[4];\n");
        }
        for (int i = 0; i < 4; i++) {
            String sampler = "iChannel" + i;
            if (!containsUniform(working, sampler)) {
                header.append("uniform sampler2D ").append(sampler).append(";\n");
            }
        }

        if (header.length() > 0) {
            working = working.substring(0, insertPos) + header + working.substring(insertPos);
        }

        return working;
    }

    private static boolean containsUniform(String shader, String name) {
        Pattern pattern = Pattern.compile("\\buniform\\s+[\\w\\[\\]]+\\s+" + Pattern.quote(name) + "\\b");
        return pattern.matcher(shader).find();
    }
}
