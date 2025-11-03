package sh.tinywifi.canvasglsl.shader;

public enum ShaderPresets {
    TRIPPY("Rise", """
        #version 330 core
        out vec4 fragColor;

        uniform vec2 resolution;
        uniform float time;

        mat2 m(float a) {
            float c = cos(a), s = sin(a);
            return mat2(c, -s, s, c);
        }

        float map(vec3 p) {
            p.xz *= m(time * 0.4);
            p.xy *= m(time * 0.1);
            vec3 q = p * 2.0 + time;
            return length(p + vec3(sin(time * 0.7))) * log(length(p) + 1.0) +
                   sin(q.x + sin(q.z + sin(q.y))) * 0.5 - 1.0;
        }

        void main() {
            vec2 a = gl_FragCoord.xy / resolution.y - vec2(0.9, 0.5);
            vec3 cl = vec3(1.0);
            float d = 2.5;

            for (int i = 0; i <= 5; i++) {
                vec3 p = vec3(0, 0, 4.0) + normalize(vec3(a, -1.0)) * d;
                float rz = map(p);
                float f = clamp((rz - map(p + 0.1)) * 0.5, -0.1, 1.0);
                vec3 l = vec3(0.1, 0.3, 0.4) + vec3(5.0, 2.5, 3.0) * f;
                cl = cl * l + smoothstep(2.5, 0.0, rz) * 0.6 * l;
                d += min(rz, 1.0);
            }

            fragColor = vec4(cl, 1.0);
        }
    """),

    GRASS("Grass Field", """
        #version 330 core
        out vec4 fragColor;

        uniform float time;
        uniform vec2 mouse;
        uniform vec2 resolution;

        vec3 sunLight = normalize(vec3(0.35, 0.2, 0.3));
        vec3 sunColour = vec3(1.0, 0.75, 0.6);

        float hash(float n) {
            return fract(sin(n) * 43758.5453123);
        }

        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
        }

        float noise(in vec2 x) {
            vec2 p = floor(x);
            vec2 f = fract(x);
            f = f * f * (3.0 - 2.0 * f);
            float n = p.x + p.y * 57.0;
            return mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
                      mix(hash(n + 57.0), hash(n + 58.0), f.x), f.y);
        }

        vec2 voronoi(in vec2 x) {
            vec2 p = floor(x);
            vec2 f = fract(x);
            float res = 100.0, id = 0.0;

            for(int j = -1; j <= 1; j++)
            for(int i = -1; i <= 1; i++) {
                vec2 b = vec2(float(i), float(j));
                vec2 r = b - f + hash(p + b);
                float d = dot(r, r);
                if(d < res) {
                    res = d;
                    id = hash(p + b);
                }
            }
            return vec2(max(0.4 - sqrt(res), 0.0), id);
        }

        vec2 terrain(in vec2 p) {
            vec2 pos = p * 0.003;
            float w = 50.0;
            float f = 0.0;

            for (int i = 0; i < 3; i++) {
                f += noise(pos) * w;
                w *= 0.62;
                pos *= 2.5;
            }

            return vec2(f, 0.0);
        }

        vec2 map(in vec3 p) {
            vec2 h = terrain(p.xz);
            return vec2(p.y - h.x, h.y);
        }

        float fractalNoise(in vec2 xy) {
            float w = 0.7;
            float f = 0.0;

            for (int i = 0; i < 3; i++) {
                f += noise(xy) * w;
                w *= 0.6;
                xy = 2.0 * xy;
            }
            return f;
        }

        vec3 getSky(in vec3 rd) {
            float sunAmount = max(dot(rd, sunLight), 0.0);
            float v = pow(1.0 - max(rd.y, 0.0), 6.0);
            vec3 sky = mix(vec3(0.1, 0.2, 0.3), vec3(0.32, 0.32, 0.32), v);
            sky += sunColour * sunAmount * sunAmount * 0.25;
            sky += sunColour * min(pow(sunAmount, 800.0) * 1.5, 0.3);
            return clamp(sky, 0.0, 1.0);
        }

        vec3 applyFog(in vec3 rgb, in float dis, in vec3 dir) {
            float fogAmount = clamp(dis * dis * 0.0000012, 0.0, 1.0);
            return mix(rgb, getSky(dir), fogAmount);
        }

        vec3 grassDE(vec3 p) {
            float base = terrain(p.xz).x - 1.9;
            float height = noise(p.xz * 2.0) * 0.75 + noise(p.xz) * 0.35 + noise(p.xz * 0.5) * 0.2;
            float y = p.y - base - height;
            y = y * y;
            vec2 ret = voronoi((p.xz * 2.5 + sin(y * 4.0 + p.zx * 12.3) * 0.12 +
                              vec2(sin(time * 2.3 + 1.5 * p.z), sin(time * 3.6 + 1.5 * p.x)) * y * 0.5));
            float f = ret.x * 0.6 + y * 0.58;
            return vec3(y - f * 1.4, clamp(f * 1.5, 0.0, 1.0), ret.y);
        }

        float circleOfConfusion(float t) {
            return max(t * 0.04, (2.0 / resolution.y) * (1.0 + t));
        }

        float linstep(float a, float b, float t) {
            return clamp((t - a) / (b - a), 0.0, 1.0);
        }

        vec3 grassBlades(in vec3 rO, in vec3 rD, in vec3 mat, in float dist) {
            float d = 0.0;
            float rCoC = circleOfConfusion(dist * 0.3);
            vec4 col = vec4(mat * 0.15, 0.0);

            for (int i = 0; i < 10; i++) {
                if (col.w > 0.99) break;
                vec3 p = rO + rD * d;
                vec3 ret = grassDE(p);
                ret.x += 0.5 * rCoC;

                if (ret.x < rCoC) {
                    float alpha = (1.0 - col.y) * linstep(-rCoC, rCoC, -ret.x);
                    float f = clamp(ret.y, 0.0, 1.0);
                    vec3 gra = mix(mat, vec3(0.35, 0.35, min(pow(ret.z, 4.0) * 35.0, 0.35)),
                                  pow(ret.y, 9.0) * 0.7) * ret.y;
                    col += vec4(gra * alpha, alpha);
                }
                d += max(ret.x * 0.7, 0.1);
            }

            if(col.w < 0.2) col.xyz = vec3(0.1, 0.15, 0.05);
            return col.xyz;
        }

        void doLighting(inout vec3 mat, in vec3 normal) {
            float h = dot(sunLight, normal);
            mat = mat * sunColour * (max(h, 0.0) + 0.2);
        }

        vec3 terrainColour(vec3 pos, vec3 dir, vec3 normal, float dis) {
            vec3 mat = mix(vec3(0.0, 0.3, 0.0), vec3(0.2, 0.3, 0.0), noise(pos.xz * 0.025));
            float t = fractalNoise(pos.xz * 0.1) + 0.5;
            mat = grassBlades(pos, dir, mat, dis) * t;
            doLighting(mat, normal);
            mat = applyFog(mat, dis, dir);
            return mat;
        }

        float binarySubdivision(in vec3 rO, in vec3 rD, float t, float oldT) {
            for (int n = 0; n < 5; n++) {
                float halfwayT = (oldT + t) * 0.5;
                if (map(rO + halfwayT * rD).x < 0.05) {
                    t = halfwayT;
                } else {
                    oldT = halfwayT;
                }
            }
            return t;
        }

        bool scene(in vec3 rO, in vec3 rD, out float resT) {
            float t = 5.0;
            float oldT = 0.0;
            bool hit = false;

            for(int j = 0; j < 60; j++) {
                vec3 p = rO + t * rD;
                if (p.y < 105.0 && !hit) {
                    vec2 h = map(p);
                    if(h.x < 0.05) {
                        resT = binarySubdivision(rO, rD, t, oldT);
                        hit = true;
                    } else {
                        float delta = max(0.04, 0.35 * h.x) + (t * 0.04);
                        oldT = t;
                        t += delta;
                    }
                }
            }
            return hit;
        }

        vec3 cameraPath(float t) {
            vec2 p = vec2(200.0 * sin(3.54 * t), 200.0 * cos(2.0 * t));
            return vec3(p.x + 55.0, 12.0 + sin(t * 0.3) * 6.5, -94.0 + p.y);
        }

        vec3 postEffects(vec3 rgb, vec2 xy) {
            rgb = pow(rgb, vec3(0.45));
            rgb = mix(vec3(0.5), mix(vec3(dot(vec3(0.2125, 0.7154, 0.0721), rgb * 1.3)),
                     rgb * 1.3, 1.3), 1.1);
            rgb *= 0.4 + 0.5 * pow(40.0 * xy.x * xy.y * (1.0 - xy.x) * (1.0 - xy.y), 0.2);
            return rgb;
        }

        void main() {
            float gTime = (time * 5.0 + 2352.0) * 0.006;
            vec2 xy = gl_FragCoord.xy / resolution.xy;
            vec2 uv = (-1.0 + 2.0 * xy) * vec2(resolution.x / resolution.y, 1.0);

            vec3 cameraPos = cameraPath(gTime);
            vec3 camTar = cameraPath(gTime + 0.009);
            cameraPos.y += terrain(cameraPath(gTime + 0.009).xz).x;
            camTar.y = cameraPos.y;

            float roll = 0.4 * sin(gTime + 0.5);
            vec3 cw = normalize(camTar - cameraPos);
            vec3 cp = vec3(sin(roll), cos(roll), 0.0);
            vec3 cu = cross(cw, cp);
            vec3 cv = cross(cu, cw);
            vec3 dir = normalize(uv.x * cu + uv.y * cv + 1.3 * cw);

            vec3 col;
            float distance;

            if(!scene(cameraPos, dir, distance)) {
                col = getSky(dir);
            } else {
                vec3 pos = cameraPos + distance * dir;
                vec2 p = vec2(0.1, 0.0);
                vec3 nor = vec3(0.0, terrain(pos.xz).x, 0.0);
                vec3 v2 = nor - vec3(p.x, terrain(pos.xz + p).x, 0.0);
                vec3 v3 = nor - vec3(0.0, terrain(pos.xz - p.yx).x, -p.x);
                nor = normalize(cross(v2, v3));
                col = terrainColour(pos, dir, nor, distance);
            }

            float bri = dot(cw, sunLight) * 0.75;
            if (bri > 0.0) {
                vec2 sunPos = vec2(dot(sunLight, cu), dot(sunLight, cv));
                vec2 uvT = uv - sunPos;
                uvT = uvT * length(uvT);
                bri = pow(bri, 6.0) * 0.8;

                float glare1 = max(dot(normalize(vec3(dir.x, dir.y + 0.3, dir.z)), sunLight), 0.0) * 1.4;
                float glare2 = max(1.0 - length(uvT + sunPos * 0.5) * 4.0, 0.0);
                uvT = mix(uvT, uv, -2.3);
                float glare3 = max(1.0 - length(uvT + sunPos * 5.0) * 1.2, 0.0);

                col += bri * vec3(1.0, 0.0, 0.0) * pow(glare1, 12.5) * 0.05;
                col += bri * vec3(1.0, 1.0, 0.2) * pow(glare2, 2.0) * 2.5;
                col += bri * sunColour * pow(glare3, 2.0) * 3.0;
            }

            col = postEffects(col, xy);
            fragColor = vec4(col, 1.0);
        }
    """),

    CUSTOM("Custom", "");

    private final String name;
    private final String shaderCode;

    ShaderPresets(String name, String shaderCode) {
        this.name = name;
        this.shaderCode = shaderCode;
    }

    public String getShaderCode() {
        return shaderCode;
    }

    @Override
    public String toString() {
        return name;
    }
}
