package com.dogpound.railmap.cctv.client;

import com.dogpound.railmap.RailMap;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * The CCTV picture shader on the monitor glass: sensor noise, vignette, iris gain, IR monochrome (green for the night
 * camera, grey for day/night cameras in IR), the thermal false-colour palette (alpha = "hot" mask: living things), the
 * fisheye lens (equidistant projection of the 140° picture, or dewarped), soft focus and rain on the lens.
 * No GLSL (old GPU / a shader pack holding the pipeline) = the plain picture, tinted.
 */
public final class CctvShader {
    private CctvShader() {}

    private static int prog = -1;
    private static boolean failed;
    private static int uMode, uTime, uGain, uFish, uThmax, uNoise, uBlur, uTexel;

    private static final String VS = "#version 120\nvarying vec2 uv;\nvoid main(){ gl_Position = ftransform(); uv = gl_MultiTexCoord0.st; gl_FrontColor = gl_Color; }\n";
    private static final String FS = "#version 120\n"
            + "uniform sampler2D tex; uniform int mode; uniform float time; uniform float gain; uniform int fish; uniform float thmax; uniform float noise; uniform float blur; uniform vec2 texel;\n"
            + "varying vec2 uv;\n"
            + "float rnd(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233)) + time * 37.0) * 43758.5453); }\n"
            + "vec3 pal(float h){ h = clamp(h, 0.0, 1.0);\n"
            + "  vec3 a = vec3(0.02,0.0,0.10), b = vec3(0.35,0.0,0.55), c = vec3(0.85,0.10,0.25), d = vec3(1.0,0.55,0.0), e = vec3(1.0,1.0,0.75);\n"
            + "  if (h < 0.25) return mix(a, b, h / 0.25); if (h < 0.5) return mix(b, c, (h - 0.25) / 0.25); if (h < 0.75) return mix(c, d, (h - 0.5) / 0.25); return mix(d, e, (h - 0.75) / 0.25); }\n"
            + "void main(){\n"
            + "  vec2 t = uv;\n"
            + "  if (fish == 1) { vec2 c = t * 2.0 - 1.0; float r = length(c); if (r > 1.0) { gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0) * gl_Color; return; }\n"
            + "    float th = r * thmax; vec2 s = r < 1e-4 ? vec2(0.0) : c / r * tan(th) / tan(thmax); t = s * 0.5 + 0.5; }\n"
            + "  vec4 s = texture2D(tex, t);\n"
            + "  if (blur > 0.0) { s = (s * 2.0 + texture2D(tex, t + vec2(texel.x, 0.0) * blur) + texture2D(tex, t - vec2(texel.x, 0.0) * blur) + texture2D(tex, t + vec2(0.0, texel.y) * blur) + texture2D(tex, t - vec2(0.0, texel.y) * blur)) / 6.0; }\n"
            + "  vec3 c = s.rgb * gain;\n"
            + "  float l = dot(c, vec3(0.299, 0.587, 0.114));\n"
            + "  if (mode == 1) { l = clamp(l * 2.6 + 0.04, 0.0, 1.0); c = vec3(l * 0.45, l, l * 0.55); }\n"
            + "  else if (mode == 3) { l = clamp(l * 2.2 + 0.03, 0.0, 1.0); c = vec3(l); }\n"
            + "  else if (mode == 2) { float h = s.a > 0.5 ? 0.80 + 0.2 * l : l * 0.55; c = pal(h); }\n"
            + "  float n = noise + (mode == 1 || mode == 3 ? 0.08 : 0.035);\n"
            + "  c += (rnd(floor(uv * 256.0)) - 0.5) * n;\n"
            + "  vec2 d = uv - 0.5; c *= 1.0 - dot(d, d) * 0.55;\n"
            + "  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0) * gl_Color;\n"
            + "}\n";

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            RailMap.LOG.warn("[IR Extras] CCTV shader: {}", GL20.glGetShaderInfoLog(s, 2000));
            return -1;
        }
        return s;
    }

    private static boolean init() {
        if (prog > 0) return true;
        if (failed || !OpenGlHelper.shadersSupported) return false;
        try {
            int v = compile(GL20.GL_VERTEX_SHADER, VS), f = compile(GL20.GL_FRAGMENT_SHADER, FS);
            if (v < 0 || f < 0) { failed = true; return false; }
            int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, v);
            GL20.glAttachShader(p, f);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) { RailMap.LOG.warn("[IR Extras] CCTV shader link: {}", GL20.glGetProgramInfoLog(p, 2000)); failed = true; return false; }
            prog = p;
            uMode = GL20.glGetUniformLocation(p, "mode"); uTime = GL20.glGetUniformLocation(p, "time"); uGain = GL20.glGetUniformLocation(p, "gain");
            uFish = GL20.glGetUniformLocation(p, "fish"); uThmax = GL20.glGetUniformLocation(p, "thmax"); uNoise = GL20.glGetUniformLocation(p, "noise");
            uBlur = GL20.glGetUniformLocation(p, "blur"); uTexel = GL20.glGetUniformLocation(p, "texel");
            return true;
        } catch (RuntimeException | LinkageError e) {
            RailMap.LOG.warn("[IR Extras] CCTV shader unavailable: {}", e.toString());
            failed = true;
            return false;
        }
    }

    /** bind for one picture; returns the program that was bound before (to restore), or -2 if the shader isn't used */
    public static int begin(int mode, float gain, boolean fisheye, float noise, float blur, int texSize) {
        if (!init()) return -2;
        int old = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(prog);
        GL20.glUniform1i(GL20.glGetUniformLocation(prog, "tex"), 0);
        GL20.glUniform1i(uMode, mode);
        GL20.glUniform1f(uTime, (System.currentTimeMillis() % 100000) / 1000f);
        GL20.glUniform1f(uGain, gain);
        GL20.glUniform1i(uFish, fisheye ? 1 : 0);
        GL20.glUniform1f(uThmax, (float) Math.toRadians(70));
        GL20.glUniform1f(uNoise, noise);
        GL20.glUniform1f(uBlur, blur);
        GL20.glUniform2f(uTexel, 1f / Math.max(1, texSize), 1f / Math.max(1, texSize));
        return old;
    }

    public static void end(int old) { if (old != -2) GL20.glUseProgram(Math.max(0, old)); }

    public static boolean available() { return init(); }
}
