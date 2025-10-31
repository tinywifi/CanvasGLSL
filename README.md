# CanvasGLSL

A Fabric client mod for Minecraft 1.21 through 1.21.10 that replaces the main menu panorama with custom GLSL shaders - now backed by an in-game Dear ImGui IDE.

## Features

- **Dear ImGui shader IDE** with dedicated IDE & Settings tabs
- **Workspace-aware file manager** rooted at `.minecraft/canvasglsl`
- **Syntax highlighting + autocomplete** for GLSL, including line numbers and selection rendering
- **Theme picker & font scaling** to match your preferred editor look
- **Manual and auto compile paths** – “Compile now”, “Save + Compile”, and optional compile-on-save
- **Bundled presets** (Trippy & Grass Field) that can be loaded directly into the editor
- **Uniform support** for `time`, `resolution`, `mouse`, `frame`, `persistent_frame`, `speed`, and Shadertoy-style `iTime`, `iMouse`, `iResolution`, `iFrame`, `iChannel*`
- **Automatic legacy patching** to keep `gl_FragColor` and non-Shadertoy shaders working without edits

## Installation

1. Install the [Fabric loader](https://fabricmc.net/) for your target Minecraft version (any build from 1.21 to 1.21.10).
2. Download the latest CanvasGLSL release.
3. Drop the `.jar` into your `.minecraft/mods` folder.
4. Launch Minecraft — the panorama replacement is enabled by default.

## Usage

1. Press `Insert` at any time to toggle the ImGui IDE overlay (press `Insert` again or `Esc` to hide it).
2. The shader IDE presents:
   - **Left pane** - file explorer for `.minecraft/canvasglsl/`
   - **Right pane** - GLSL editor with syntax highlighting, autocomplete, and line numbers
3. Switch to the **Settings** tab to:
   - Pick editor themes and adjust zoom
   - Toggle the shader panorama on/off
   - Toggle auto-save / auto-compile
   - Load built-in presets
   - Trigger **Compile now** or **Save + Compile**
4. Save (`Ctrl+S`) or hit **Save + Compile** to rebuild and preview the shader on the main menu background.

### Shader Workspace

- Files live under `.minecraft/canvasglsl/`; subdirectories are supported.
- Creating a new shader seeds it with the Trippy preset for quick experimentation.
- Selecting an existing file loads it immediately and recompiles if auto-compile is enabled.
- When auto-compile is disabled, use **Compile now** to rebuild without saving.

### Writing Custom Shaders

Available uniforms:

- `uniform float time;` – Seconds since the shader started.
- `uniform vec2 resolution;` – Current screen resolution (pixels).
- `uniform vec2 mouse;` - Normalised mouse position (0.0 - 1.0).
- `uniform int frame;` - Frame counter (resets on compile).
- `uniform int persistent_frame;` - Global frame counter (never resets).
- `uniform float speed;` - Panorama speed slider from Minecraft options.
- Shadertoy-compatible aliases: `iTime`, `iResolution`, `iMouse`, `iFrame`, `iChannel0`..`iChannel3`, `iChannelTime[]`, `iChannelResolution[]`

Output to `fragColor` or `fragmentColor` (vec4). Example:

```glsl
#version 330 core
out vec4 fragColor;

uniform vec2 resolution;
uniform float time;

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec3 col = 0.5 + 0.5 * cos(time + uv.xyx + vec3(0, 2, 4));
    fragColor = vec4(col, 1.0);
}
```

## Performance Tips

- Prefer lighter shaders (fewer loops, cheaper math) for older hardware.
- Reduce iteration counts in raymarchers and noise-heavy effects.
- Start from the **Trippy** preset if you need a lightweight baseline.
- Avoid excessive texture lookups unless necessary.

### Editor Tips

- Enable auto-save + auto-compile for rapid iteration; disable when you prefer manual control.
- Use folders inside `.minecraft/canvasglsl` to organise experiments.
- Switch themes whenever you need a new vibe – colour palettes also update syntax highlighting.

## Building from Source

```bash
./gradlew build
```

The jar will end up in `build/libs/`.

## Credits

- Shader examples based on Trippy (Hazsi, modified) & a Shadertoy-inspired Grass Field.
- Built with [Fabric](https://fabricmc.net/) and [imgui-java](https://github.com/SpaiR/imgui-java).

## License

This project remains under CC0. Do whatever you like with it.
