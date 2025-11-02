# CanvasGLSL

<img src="https://raw.githubusercontent.com/tinywifi/CanvasGLSL/main/logo.png" width="128" height="128" />

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/KMdCm9JIzO0" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

Replace Minecraft's menu backgrounds with custom GLSL shaders. Includes a built-in shader editor with syntax highlighting.

## Features

- **Live shader editor** (press `Insert` to open)
- **Works on all menu screens** (title, singleplayer, multiplayer, options, etc.)
- **Shadertoy compatible** - paste shaders directly from Shadertoy
- **Auto-compile** or manual compile modes
- **File manager** for organizing shaders
- **Syntax highlighting**
## Installation

1. Install [Fabric loader](https://fabricmc.net/) for Minecraft 1.21.10
2. Download the latest release JAR
3. Put it in your `.minecraft/mods` folder
4. Launch the game

## Usage

### Opening the Editor

Press `Insert` while in any menu to open the shader IDE. Press `Insert` or `Esc` to close it.

### Writing Shaders

Shaders are saved in `.minecraft/canvasglsl/`. You can create folders to organize them.

**Supported uniforms:**
- `uniform float iTime;` - Time in seconds
- `uniform vec3 iResolution;` - Screen resolution (width, height, aspect)
- `uniform vec4 iMouse;` - Mouse position (x, y, click x, click y)
- `uniform int iFrame;` - Frame counter
- `uniform sampler2D iChannel0;` - Procedural noise textures (iChannel0 through iChannel3)
- `uniform float iTimeDelta;` - Time since last frame
- `uniform vec4 iDate;` - Current date/time (year, month, day, seconds)
- `uniform float iSampleRate;` - Audio sample rate (44100)

**Example shader:**
```glsl
void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec3 col = 0.5 + 0.5 * cos(iTime + uv.xyx + vec3(0, 2, 4));
    fragColor = vec4(col, 1.0);
}
```

### Using Shadertoy Shaders

1. Copy shader code from [Shadertoy](https://www.shadertoy.com/)
2. Paste into the editor
3. Click "Compile" or enable auto-compile
4. Done! The shader will render as your menu background

## Controls

- `Insert` - Toggle shader editor
- `Esc` - Close editor
- `Ctrl+S` - Save current shader
- **Compile** button - Compile shader manually
- **Auto-compile** toggle - Auto-compile on save

## Building

```bash
./gradlew build
```

Output: `build/libs/canvasglsl-*.jar`

## License

CC0 - Public Domain
