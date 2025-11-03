package sh.tinywifi.canvasglsl.ide;

import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiInputTextCallbackData;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.callback.ImGuiInputTextCallback;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ImGui-powered shader editor widget featuring lightweight syntax highlighting and completions.
 */
public final class ShaderCodeEditor {
    private static final int INPUT_FLAGS = ImGuiInputTextFlags.AllowTabInput
        | ImGuiInputTextFlags.CallbackAlways;

    private static final float GUTTER_WIDTH = 52f;
    private static final float POPUP_WIDTH = 220f;
    private static final float POPUP_HEIGHT = 180f;

    private final Set<String> keywords = new HashSet<>();
    private final Set<String> types = new HashSet<>();
    private final Set<String> builtins = new HashSet<>();
    private final List<String> dictionary = new ArrayList<>();

    private final ImGuiInputTextCallback callback = new ImGuiInputTextCallback() {
        @Override
        public void accept(ImGuiInputTextCallbackData data) {
            onInputEvent(data);
        }
    };

    private int cursorPos;
    private int selectionStart;
    private int selectionEnd;
    private boolean pendingSetCursor;
    private int pendingCursorPos;

    private boolean requestPopupOpen;
    private final ImVec2 popupPos = new ImVec2();
    private final List<String> popupMatches = new ArrayList<>();
    private String popupPrefix = "";
    private int popupWordStart = -1;
    private int popupSelection = 0;

    private float caretX;
    private float caretY;
    private float lineHeight;
    private boolean caretVisible;

    public ShaderCodeEditor() {
        seedKeywords();
        dictionary.addAll(keywords);
        dictionary.addAll(types);
        dictionary.addAll(builtins);
        Collections.sort(dictionary);
    }

    private void onInputEvent(ImGuiInputTextCallbackData data) {
        cursorPos = data.getCursorPos();
        selectionStart = data.getSelectionStart();
        selectionEnd = data.getSelectionEnd();

        if (pendingSetCursor) {
            data.setCursorPos(pendingCursorPos);
            data.setSelectionStart(pendingCursorPos);
            data.setSelectionEnd(pendingCursorPos);
            cursorPos = pendingCursorPos;
            selectionStart = pendingCursorPos;
            selectionEnd = pendingCursorPos;
            pendingSetCursor = false;
        }

    }

    private void seedKeywords() {
        Collections.addAll(keywords,
            "uniform", "varying", "attribute", "const", "precision",
            "in", "out", "inout", "layout",
            "if", "else", "for", "while", "do", "switch", "case", "default",
            "break", "continue", "return", "discard", "struct"
        );

        Collections.addAll(types,
            "void", "bool", "int", "uint", "float", "double",
            "vec2", "vec3", "vec4",
            "ivec2", "ivec3", "ivec4",
            "bvec2", "bvec3", "bvec4",
            "mat2", "mat3", "mat4",
            "sampler2D", "sampler3D", "samplerCube", "sampler2DArray",
            "isampler2D", "usampler2D", "sampler2DShadow"
        );

        Collections.addAll(builtins,
            "abs", "acos", "asin", "atan", "atanh",
            "ceil", "clamp", "cos", "cosh", "cross",
            "degrees", "distance", "dot", "faceforward",
            "floor", "fract", "inverse", "length", "log",
            "max", "min", "mix", "mod", "normalize",
            "pow", "reflect", "refract", "round", "sign",
            "sin", "sinh", "smoothstep", "sqrt", "step", "tan", "tanh",
            "texture", "texture2D", "textureCube", "textureLod", "textureProj",
            "gl_FragCoord", "gl_FragColor", "gl_FragData", "gl_Position", "gl_Time"
        );
    }

    public boolean render(ShaderEditorState state) {
        ShaderIDETheme.SyntaxPalette palette = state.getTheme().palette();
        boolean changed = false;

        ImGui.pushID("shader-code-editor");
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0f);

        ImGui.beginChild(
            "shader-editor-scroll",
            0f,
            0f,
            false,
            ImGuiWindowFlags.HorizontalScrollbar | ImGuiWindowFlags.AlwaysVerticalScrollbar
        );

        if (state.shouldFocusEditor()) {
            ImGui.setKeyboardFocusHere();
        }

        ImGuiStyle style = ImGui.getStyle();
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, style.getItemSpacingY());
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, style.getFramePaddingX(), style.getFramePaddingY());

        ImGui.setWindowFontScale(state.getFontScale());
        lineHeight = ImGui.getTextLineHeightWithSpacing();
        int initialLineCount = countLines(state.buffer().get());
        float contentHeight = Math.max(
            lineHeight * Math.max(1, initialLineCount) + style.getFramePaddingY() * 2f,
            ImGui.getContentRegionAvailY()
        );

        // Temporarily hide ImGui's own text so we can paint custom colouring on top.
        ImGui.pushStyleColor(ImGuiCol.Text, ImColor.rgba(0, 0, 0, 0));
        ImGui.pushStyleColor(ImGuiCol.TextSelectedBg, ImColor.rgba(100, 130, 255, 80));

        boolean localChanged = ImGui.inputTextMultiline(
            "##shader-editor-input",
            state.buffer(),
            -1f,
            contentHeight,
            INPUT_FLAGS,
            callback
        );

        changed |= localChanged;

        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);

        float rectMinX = ImGui.getItemRectMinX();
        float rectMinY = ImGui.getItemRectMinY();
        float rectMaxX = ImGui.getItemRectMaxX();
        float rectMaxY = ImGui.getItemRectMaxY();
        float scrollX = ImGui.getScrollX();
        float scrollY = ImGui.getScrollY();
        float scrollMaxY = ImGui.getScrollMaxY();
        String rawText = state.buffer().get().replace("\r", "");
        int overlayLineCount = countLines(rawText);
        float overlayContentHeight = Math.max(
            lineHeight * Math.max(1, overlayLineCount) + style.getFramePaddingY() * 2f,
            contentHeight
        );

        renderOverlay(rawText, palette, rectMinX, rectMinY, rectMaxX, rectMaxY, scrollX, scrollY);
        ensureCaretVisible(rectMinY, scrollY, scrollMaxY, overlayContentHeight);

        if (ImGui.isItemFocused()) {
            handleKeyboardShortcuts(state);
        }

        renderAutocompletePopup(state);

        ImGui.setWindowFontScale(1f);
        ImGui.endChild();
        ImGui.popStyleVar(2);
        ImGui.popID();

        return changed;
    }

    private void renderOverlay(String rawText, ShaderIDETheme.SyntaxPalette palette,
                               float rectMinX, float rectMinY, float rectMaxX, float rectMaxY,
                               float scrollX, float scrollY) {
        final List<LineInfo> lines = tokenize(rawText);
        final ImDrawList drawList = ImGui.getWindowDrawList();

        float gutterX = rectMinX;
        float gutterRight = gutterX + GUTTER_WIDTH;

        // Draw gutter background.
        drawList.addRectFilled(gutterX, rectMinY, gutterRight, rectMaxY, palette.background());
        drawList.addLine(gutterRight, rectMinY, gutterRight, rectMaxY, ImColor.rgba(60, 60, 60, 255));

        caretVisible = false;

        ImGui.pushClipRect(rectMinX, rectMinY, rectMaxX, rectMaxY, true);

        int caretLineIndex = lineForIndex(lines, cursorPos);

        float textStartBaseX = gutterRight + ImGui.getStyle().getFramePaddingX();
        float lineNumberBaseX = gutterX + 8f;

        int selectionA = Math.min(selectionStart, selectionEnd);
        int selectionB = Math.max(selectionStart, selectionEnd);
        boolean hasSelection = selectionA != selectionB;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            LineInfo line = lines.get(lineIndex);

            float y = rectMinY + ImGui.getStyle().getFramePaddingY() - scrollY + lineIndex * lineHeight;

            if (y > rectMaxY || y + lineHeight < rectMinY) continue;

            boolean isCaretLine = lineIndex == caretLineIndex;

            // Current line highlight
            if (isCaretLine) {
                drawList.addRectFilled(
                    textStartBaseX - scrollX - 4f,
                    y,
                    rectMaxX,
                    y + lineHeight,
                    ImColor.rgba(60, 60, 90, 70)
                );
            }

            // Selection highlight
            if (hasSelection && selectionA < line.endIndex && selectionB > line.startIndex) {
                int lineSelStart = Math.max(selectionA, line.startIndex) - line.startIndex;
                int lineSelEnd = Math.min(selectionB, line.endIndex) - line.startIndex;

                float selStartX = textStartBaseX - scrollX + measureTextWidth(line.text, 0, lineSelStart);
                float selEndX = textStartBaseX - scrollX + measureTextWidth(line.text, 0, lineSelEnd);

                drawList.addRectFilled(
                    selStartX,
                    y,
                    selEndX,
                    y + lineHeight,
                    ImColor.rgba(100, 130, 255, 60)
                );
            }

            // Line numbers
            drawList.addText(lineNumberBaseX, y, palette.lineNumber(), Integer.toString(lineIndex + 1));

            float textX = textStartBaseX - scrollX;
            if (line.segments.isEmpty()) {
                caretIfNeeded(drawList, line, lineIndex, textX, y);
                continue;
            }

            for (Segment segment : line.segments) {
                String display = segment.display;
                int color = paletteFor(palette, segment.type);
                if (!display.isEmpty()) {
                    drawList.addText(textX, y, color, display);
                    textX += ImGui.calcTextSize(display).x;
                }

                if (segment.endsAtCursor && isCaretLine) {
                    caretX = textX;
                    caretY = y;
                }
            }

            caretIfNeeded(drawList, line, lineIndex, textStartBaseX - scrollX, y);
        }

        ImGui.popClipRect();

        // Draw the caret overlay after clipping so it stays visible.
        if (caretVisible) {
            drawCaret(drawList, rectMinY, rectMaxY);
        }

        // Prepare popup anchor position.
        popupPos.set(caretX, caretY + lineHeight);
    }

    private void caretIfNeeded(ImDrawList drawList, LineInfo line, int lineIndex, float baseX, float y) {
        if (cursorPos < line.startIndex || cursorPos > line.endIndex) return;
        float caretOffset = measureTextWidth(line.text, 0, cursorPos - line.startIndex);
        caretX = baseX + caretOffset;
        caretY = y;
        caretVisible = true;
    }

    private void drawCaret(ImDrawList drawList, float rectMinY, float rectMaxY) {
        float caretTop = caretY + 2f;
        float caretBottom = caretY + lineHeight - 2f;
        caretTop = Math.max(caretTop, rectMinY);
        caretBottom = Math.min(caretBottom, rectMaxY);
        drawList.addLine(caretX, caretTop, caretX, caretBottom, ImColor.rgba(220, 220, 255, 255), 1.6f);
    }

    private float measureTextWidth(String text, int start, int end) {
        if (start >= end) return 0f;
        String slice = text.substring(start, end).replace("\t", "    ");
        return ImGui.calcTextSize(slice).x;
    }

    private int paletteFor(ShaderIDETheme.SyntaxPalette palette, TokenType type) {
        return switch (type) {
            case KEYWORD -> palette.keyword();
            case TYPE -> palette.type();
            case BUILTIN -> palette.builtin();
            case NUMBER -> palette.number();
            case STRING -> palette.string();
            case COMMENT -> palette.comment();
            case PLAIN -> palette.text();
        };
    }

    private void handleKeyboardShortcuts(ShaderEditorState state) {
        if (ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(ImGuiKey.Space, false)) {
            requestCompletionPopup(state);
        }
    }

    private void requestCompletionPopup(ShaderEditorState state) {
        CompletionContext context = buildCompletionContext(state.buffer().get(), cursorPos);
        if (context == null) return;

        popupMatches.clear();
        popupPrefix = context.prefix();
        popupWordStart = context.wordStart();

        for (String entry : dictionary) {
            if (!entry.equals(popupPrefix) && entry.toLowerCase(Locale.ROOT).startsWith(popupPrefix.toLowerCase(Locale.ROOT))) {
                popupMatches.add(entry);
            }
        }

        if (!popupMatches.isEmpty()) {
            popupSelection = 0;
            requestPopupOpen = true;
        }
    }

    private void renderAutocompletePopup(ShaderEditorState state) {
        final String popupName = "shader-editor-autocomplete";
        if (requestPopupOpen) {
            ImGui.setNextWindowPos(popupPos.x + 6f, popupPos.y + lineHeight);
            ImGui.setNextWindowSize(POPUP_WIDTH, POPUP_HEIGHT, 0);
            ImGui.openPopup(popupName);
            requestPopupOpen = false;
        }

        if (ImGui.beginPopup(popupName)) {
            boolean inputHandled = false;

            if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
                popupSelection = (popupSelection + 1) % popupMatches.size();
                inputHandled = true;
            } else if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
                popupSelection = (popupSelection - 1 + popupMatches.size()) % popupMatches.size();
                inputHandled = true;
            } else if (ImGui.isKeyPressed(ImGuiKey.Enter) || ImGui.isKeyPressed(ImGuiKey.Tab)) {
                applyPopupSelection(state, popupMatches.get(popupSelection));
                ImGui.closeCurrentPopup();
                inputHandled = true;
            } else if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
                inputHandled = true;
            }

            ImGui.beginChild("autocomplete-scroll", 0f, 0f, false);
            for (int i = 0; i < popupMatches.size(); i++) {
                final boolean selected = i == popupSelection;
                if (selected && !inputHandled) {
                    ImGui.setScrollHereY();
                }

                if (ImGui.selectable(popupMatches.get(i), selected)) {
                    applyPopupSelection(state, popupMatches.get(i));
                    ImGui.closeCurrentPopup();
                    break;
                }
            }
            ImGui.endChild();
            ImGui.endPopup();
        }
    }

    private void applyPopupSelection(ShaderEditorState state, String value) {
        if (popupWordStart < 0) return;

        String text = state.buffer().get();
        int end = cursorPos;
        if (end < popupWordStart) end = popupWordStart;

        String completed = value;
        String updated = text.substring(0, popupWordStart) + completed + text.substring(end);

        state.buffer().set(updated);
        cursorPos = popupWordStart + completed.length();
        selectionStart = cursorPos;
        selectionEnd = cursorPos;
        pendingCursorPos = cursorPos;
        pendingSetCursor = true;
        state.markDirty(true);
        state.requestEditorFocus();
    }

    private CompletionContext buildCompletionContext(String buffer, int cursor) {
        if (buffer == null || buffer.isEmpty() || cursor == 0) {
            return null;
        }

        int wordStart = cursor;
        while (wordStart > 0) {
            char c = buffer.charAt(wordStart - 1);
            if (!isWordChar(c)) break;
            wordStart--;
        }

        if (wordStart == cursor) return null;

        String prefix = buffer.substring(wordStart, cursor);
        return new CompletionContext(wordStart, prefix);
    }

    private String findBestCompletion(String prefix) {
        if (prefix == null || prefix.isEmpty()) return null;
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String entry : dictionary) {
            if (entry.toLowerCase(Locale.ROOT).startsWith(lower)) {
                return entry;
            }
        }
        return null;
    }

    private List<LineInfo> tokenize(String text) {
        List<LineInfo> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add(new LineInfo("", 0));
            return lines;
        }

        int index = 0;
        int length = text.length();

        StringBuilder current = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                LineInfo line = new LineInfo(current.toString(), index);
                line.buildSegments();
                lines.add(line);
                current.setLength(0);
                index = i + 1;
            } else {
                current.append(c);
            }
        }

        LineInfo tail = new LineInfo(current.toString(), index);
        tail.buildSegments();
        lines.add(tail);
        return lines;
    }

    private void ensureCaretVisible(float rectMinY, float currentScrollY, float scrollMaxY, float contentHeight) {
        if (!caretVisible) return;
        float viewportHeight = Math.max(1f, contentHeight - scrollMaxY);
        float caretContentTop = caretY - rectMinY + currentScrollY;
        float caretContentBottom = caretContentTop + lineHeight;

        float viewportTop = currentScrollY;
        float viewportBottom = currentScrollY + viewportHeight;
        float desiredScroll = currentScrollY;
        float margin = lineHeight * 0.5f;

        if (caretContentTop < viewportTop) {
            desiredScroll = caretContentTop - margin;
        } else if (caretContentBottom > viewportBottom) {
            desiredScroll = caretContentBottom - viewportHeight + margin;
        }

        desiredScroll = Math.max(0f, Math.min(scrollMaxY, desiredScroll));
        if (Math.abs(desiredScroll - currentScrollY) > 0.5f) {
            ImGui.setScrollY(desiredScroll);
        }
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }

        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private int lineForIndex(List<LineInfo> lines, int idx) {
        for (int i = 0; i < lines.size(); i++) {
            LineInfo line = lines.get(i);
            if (idx >= line.startIndex && idx <= line.endIndex) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private boolean isWordChar(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }

    private enum TokenType {
        KEYWORD,
        TYPE,
        BUILTIN,
        NUMBER,
        STRING,
        COMMENT,
        PLAIN
    }

    private static final class Segment {
        private final String text;
        private final String display;
        private final TokenType type;
        private final boolean endsAtCursor;

        private Segment(String text, TokenType type, boolean endsAtCursor) {
            this.text = text;
            this.display = text.replace("\t", "    ");
            this.type = type;
            this.endsAtCursor = endsAtCursor;
        }
    }

    private final class LineInfo {
        private final String text;
        private final int startIndex;
        private int endIndex;
        private final List<Segment> segments = new ArrayList<>();

        private LineInfo(String text, int startIndex) {
            this.text = text;
            this.startIndex = startIndex;
            this.endIndex = startIndex + text.length();
        }

        private void buildSegments() {
            if (text.isEmpty()) {
                segments.clear();
                return;
            }

            int pos = 0;
            boolean inString = false;

            while (pos < text.length()) {
                char c = text.charAt(pos);

                // Line comment
                if (!inString && c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                    String rest = text.substring(pos);
                    segments.add(new Segment(rest, TokenType.COMMENT, cursorPos == startIndex + pos + rest.length()));
                    return;
                }

                if (c == '"' && (pos == 0 || text.charAt(pos - 1) != '\\')) {
                    int start = pos++;
                    inString = true;
                    while (pos < text.length()) {
                        char sc = text.charAt(pos);
                        if (sc == '"' && text.charAt(pos - 1) != '\\') {
                            pos++;
                            break;
                        }
                        pos++;
                    }
                    String slice = text.substring(start, Math.min(pos, text.length()));
                    segments.add(new Segment(slice, TokenType.STRING, cursorPos == startIndex + pos));
                    inString = false;
                    continue;
                }

                if (Character.isDigit(c)) {
                    int start = pos++;
                    while (pos < text.length()) {
                        char nc = text.charAt(pos);
                        if (!(Character.isDigit(nc) || nc == '.' || nc == 'f' || nc == 'F')) break;
                        pos++;
                    }
                    String slice = text.substring(start, pos);
                    segments.add(new Segment(slice, TokenType.NUMBER, cursorPos == startIndex + pos));
                    continue;
                }

                if (isWordChar(c)) {
                    int start = pos++;
                    while (pos < text.length() && isWordChar(text.charAt(pos))) {
                        pos++;
                    }
                    String slice = text.substring(start, pos);
                    TokenType type = classify(slice);
                    segments.add(new Segment(slice, type, cursorPos == startIndex + pos));
                    continue;
                }

                segments.add(new Segment(Character.toString(c), TokenType.PLAIN, cursorPos == startIndex + pos + 1));
                pos++;
            }
        }

        private TokenType classify(String slice) {
            String key = slice.toLowerCase(Locale.ROOT);
            if (keywords.contains(key)) return TokenType.KEYWORD;
            if (types.contains(key)) return TokenType.TYPE;
            if (builtins.contains(slice) || builtins.contains(key)) return TokenType.BUILTIN;
            return TokenType.PLAIN;
        }
    }

    private record CompletionContext(int wordStart, String prefix) {}
}
