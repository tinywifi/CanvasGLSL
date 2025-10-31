package sh.tinywifi.canvasglsl.ide;

import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

/**
 * Predefined editor themes that configure both Dear ImGui styling and our syntax highlighting palette.
 */
public enum ShaderIDETheme {
    DARK("Dark", new SyntaxPalette(
        ImColor.rgba(220, 220, 220, 255),
        ImColor.rgba(102, 204, 255, 255),
        ImColor.rgba(255, 182, 108, 255),
        ImColor.rgba(226, 114, 151, 255),
        ImColor.rgba(166, 226, 46, 255),
        ImColor.rgba(117, 117, 117, 255),
        ImColor.rgba(255, 255, 102, 255),
        ImColor.rgba(32, 35, 36, 255),
        ImColor.rgba(90, 90, 90, 255)
    )) {
        @Override
        public void apply() {
            ImGui.styleColorsDark();
            ImGuiStyle style = ImGui.getStyle();
            style.setFrameRounding(4f);
            style.setWindowRounding(6f);
            style.setGrabRounding(4f);

            setColor(style, ImGuiCol.WindowBg, 0.12f, 0.13f, 0.14f, 1f);
            setColor(style, ImGuiCol.ChildBg, 0.11f, 0.12f, 0.13f, 1f);
            setColor(style, ImGuiCol.FrameBg, 0.18f, 0.19f, 0.20f, 1f);
            setColor(style, ImGuiCol.FrameBgHovered, 0.24f, 0.25f, 0.26f, 1f);
            setColor(style, ImGuiCol.FrameBgActive, 0.30f, 0.31f, 0.33f, 1f);
            setColor(style, ImGuiCol.TitleBg, 0.08f, 0.09f, 0.10f, 1f);
            setColor(style, ImGuiCol.TitleBgActive, 0.10f, 0.11f, 0.12f, 1f);
            setColor(style, ImGuiCol.Button, 0.20f, 0.21f, 0.22f, 1f);
            setColor(style, ImGuiCol.ButtonHovered, 0.25f, 0.26f, 0.27f, 1f);
            setColor(style, ImGuiCol.ButtonActive, 0.30f, 0.31f, 0.33f, 1f);
        }
    },
    MONOKAI("Monokai", new SyntaxPalette(
        ImColor.rgba(248, 248, 242, 255),
        ImColor.rgba(102, 217, 239, 255),
        ImColor.rgba(253, 151, 31, 255),
        ImColor.rgba(174, 129, 255, 255),
        ImColor.rgba(230, 219, 116, 255),
        ImColor.rgba(117, 113, 94, 255),
        ImColor.rgba(166, 226, 46, 255),
        ImColor.rgba(39, 40, 34, 255),
        ImColor.rgba(80, 81, 70, 255)
    )) {
        @Override
        public void apply() {
            ImGui.styleColorsDark();
            ImGuiStyle style = ImGui.getStyle();
            style.setFrameRounding(4f);
            style.setWindowRounding(4f);
            style.setGrabRounding(4f);

            setColor(style, ImGuiCol.WindowBg, 0.16f, 0.16f, 0.13f, 1f);
            setColor(style, ImGuiCol.ChildBg, 0.13f, 0.13f, 0.11f, 1f);
            setColor(style, ImGuiCol.FrameBg, 0.22f, 0.22f, 0.18f, 1f);
            setColor(style, ImGuiCol.FrameBgHovered, 0.30f, 0.30f, 0.24f, 1f);
            setColor(style, ImGuiCol.FrameBgActive, 0.35f, 0.35f, 0.29f, 1f);
            setColor(style, ImGuiCol.TitleBg, 0.09f, 0.09f, 0.07f, 1f);
            setColor(style, ImGuiCol.TitleBgActive, 0.11f, 0.11f, 0.09f, 1f);
            setColor(style, ImGuiCol.Button, 0.23f, 0.23f, 0.20f, 1f);
            setColor(style, ImGuiCol.ButtonHovered, 0.30f, 0.30f, 0.26f, 1f);
            setColor(style, ImGuiCol.ButtonActive, 0.36f, 0.36f, 0.30f, 1f);
        }
    },
    DRACULA("Dracula", new SyntaxPalette(
        ImColor.rgba(248, 248, 242, 255),
        ImColor.rgba(139, 233, 253, 255),
        ImColor.rgba(189, 147, 249, 255),
        ImColor.rgba(255, 121, 198, 255),
        ImColor.rgba(241, 250, 140, 255),
        ImColor.rgba(98, 114, 164, 255),
        ImColor.rgba(80, 250, 123, 255),
        ImColor.rgba(40, 42, 54, 255),
        ImColor.rgba(68, 71, 90, 255)
    )) {
        @Override
        public void apply() {
            ImGui.styleColorsDark();
            ImGuiStyle style = ImGui.getStyle();
            style.setWindowRounding(6f);
            style.setFrameRounding(4f);
            style.setGrabRounding(4f);

            setColor(style, ImGuiCol.WindowBg, 0.16f, 0.16f, 0.21f, 1f);
            setColor(style, ImGuiCol.ChildBg, 0.15f, 0.15f, 0.19f, 1f);
            setColor(style, ImGuiCol.FrameBg, 0.23f, 0.23f, 0.27f, 1f);
            setColor(style, ImGuiCol.FrameBgHovered, 0.30f, 0.30f, 0.35f, 1f);
            setColor(style, ImGuiCol.FrameBgActive, 0.37f, 0.37f, 0.42f, 1f);
            setColor(style, ImGuiCol.TitleBg, 0.11f, 0.11f, 0.15f, 1f);
            setColor(style, ImGuiCol.TitleBgActive, 0.13f, 0.13f, 0.17f, 1f);
            setColor(style, ImGuiCol.Button, 0.30f, 0.30f, 0.36f, 1f);
            setColor(style, ImGuiCol.ButtonHovered, 0.35f, 0.35f, 0.42f, 1f);
            setColor(style, ImGuiCol.ButtonActive, 0.40f, 0.40f, 0.48f, 1f);
        }
    },
    LIGHT("Solar Light", new SyntaxPalette(
        ImColor.rgba(30, 30, 30, 255),
        ImColor.rgba(38, 50, 56, 255),
        ImColor.rgba(0, 118, 190, 255),
        ImColor.rgba(173, 55, 24, 255),
        ImColor.rgba(191, 54, 12, 255),
        ImColor.rgba(93, 109, 126, 255),
        ImColor.rgba(56, 142, 60, 255),
        ImColor.rgba(244, 244, 244, 255),
        ImColor.rgba(180, 190, 200, 255)
    )) {
        @Override
        public void apply() {
            ImGui.styleColorsLight();
            ImGuiStyle style = ImGui.getStyle();
            style.setFrameRounding(4f);
            style.setWindowRounding(4f);
            style.setGrabRounding(4f);

            setColor(style, ImGuiCol.WindowBg, 0.96f, 0.96f, 0.97f, 1f);
            setColor(style, ImGuiCol.ChildBg, 0.94f, 0.94f, 0.95f, 1f);
            setColor(style, ImGuiCol.FrameBg, 0.88f, 0.88f, 0.90f, 1f);
            setColor(style, ImGuiCol.FrameBgHovered, 0.83f, 0.83f, 0.85f, 1f);
            setColor(style, ImGuiCol.FrameBgActive, 0.78f, 0.78f, 0.80f, 1f);
            setColor(style, ImGuiCol.TitleBg, 0.85f, 0.85f, 0.87f, 1f);
            setColor(style, ImGuiCol.TitleBgActive, 0.80f, 0.80f, 0.82f, 1f);
            setColor(style, ImGuiCol.Button, 0.78f, 0.78f, 0.80f, 1f);
            setColor(style, ImGuiCol.ButtonHovered, 0.70f, 0.70f, 0.72f, 1f);
            setColor(style, ImGuiCol.ButtonActive, 0.62f, 0.62f, 0.64f, 1f);
        }
    };

    private final String displayName;
    private final SyntaxPalette palette;

    ShaderIDETheme(String displayName, SyntaxPalette palette) {
        this.displayName = displayName;
        this.palette = palette;
    }

    public String displayName() {
        return displayName;
    }

    public SyntaxPalette palette() {
        return palette;
    }

    public abstract void apply();

    private static void setColor(ImGuiStyle style, int colorSlot, float r, float g, float b, float a) {
        style.setColor(colorSlot, r, g, b, a);
    }

    /**
     * High-level palette used by {@link sh.tinywifi.canvasglsl.ide.ShaderCodeEditor} when colouring tokens.
     */
    public record SyntaxPalette(
        int text,
        int keyword,
        int type,
        int number,
        int string,
        int comment,
        int builtin,
        int background,
        int lineNumber
    ) {}
}
