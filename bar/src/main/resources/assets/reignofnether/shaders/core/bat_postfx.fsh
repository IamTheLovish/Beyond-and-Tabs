#version 150

// The world look (postfx/WorldLookPass): aerial-perspective haze from the depth buffer, a parametric film grade and a
// faint vignette - in that order: the air first, then the "camera". No blur anywhere (no depth of field: blur plus a
// high camera is the toy-miniature look), so every edge, overlay ring and health bar stays pixel-sharp.

uniform sampler2D DiffuseSampler;   // the finished world colour
uniform sampler2D DepthSampler;     // the main depth texture (read only: the pass writes into its own target)

uniform mat4 InvProjMat;            // inverse of the projection the world was drawn with (RTS ortho or perspective)
uniform vec3 HazeColor;
uniform float HazeStart;            // blocks; perspective: from the eye, ortho: view depth of the screen's bottom edge
uniform float HazeInvScale;         // 1 / blocks per e-fold of the haze
uniform float HazeMax;              // the haze never covers more than this fraction (overlays stay readable)
uniform float Ortho;                // 1: distance along the view direction (ortho has no eye), 0: distance from the eye
uniform float Contrast;             // extra midtone slope of the S-curve on luma (0 = none)
uniform float SplitTone;            // cool shadows / warm highlights
uniform float Saturation;           // at or below 1, never boosted
uniform float Vignette;             // corner darkening
uniform float Aspect;               // width / height

in vec2 texCoord;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
// near-unit-luma tints: the split tone moves hue, not brightness
const vec3 SHADOW_TINT = vec3(0.96, 1.0, 1.06);
const vec3 HIGHLIGHT_TINT = vec3(1.04, 1.0, 0.94);
// scattering drifts distant colour cool before the in-scattered light veils it
const vec3 DISTANCE_COOL = vec3(0.93, 0.99, 1.07);

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    vec3 c = scene.rgb;
    float depth = texture(DepthSampler, texCoord).r;

    // depth 1.0 = nothing drawn there: the sky (or the RTS view's void tone) is never hazed
    if (depth < 1.0) {
        vec4 view = InvProjMat * vec4(vec3(texCoord, depth) * 2.0 - 1.0, 1.0);
        vec3 pos = view.xyz / view.w;
        float dist = Ortho > 0.5 ? -pos.z : length(pos);
        float h = HazeMax * (1.0 - exp(-max(dist - HazeStart, 0.0) * HazeInvScale));
        // haze lifts dark pixels most: the fog of war's unexplored ground would close in on the explored ground toward
        // the top of the RTS screen (3.0:1 -> ~1.9:1 fully zoomed out). Dark ground keeps 40% of the haze - the
        // seen / unseen read is gameplay; air comes second
        h *= mix(0.4, 1.0, smoothstep(0.05, 0.4, dot(c, LUMA)));
        c = mix(c, vec3(dot(c, LUMA)), h * 0.5);
        c *= mix(vec3(1.0), DISTANCE_COOL, h);
        c = mix(c, HazeColor, h);
    }

    // filmic S on luma: a quintic with slope 1 at black and white and 1 + Contrast through the midtones, so shadows
    // are not crushed (the fog of war's dark ground must stay readable) and highlights not flattened. Applied as a
    // ratio to rgb, so the curve leaves saturation (chroma / luma) unchanged.
    float l = dot(c, LUMA);
    float lc = l + Contrast * 8.0 * l * l * (1.0 - l) * (1.0 - l) * (2.0 * l - 1.0);
    c *= lc / max(l, 1e-4);
    float chroma = length(c - lc);
    // bell-shaped weights (peaks at 1/4 and 3/4 luma): black, white and the midtones stay neutral, so white rings and
    // damage numbers never turn cream and shadows never lift toward blue
    float shadows = smoothstep(0.0, 0.25, lc) - smoothstep(0.25, 0.5, lc);
    float highlights = smoothstep(0.5, 0.75, lc) - smoothstep(0.75, 1.0, lc);
    c *= mix(vec3(1.0), SHADOW_TINT, shadows * SplitTone) * mix(vec3(1.0), HIGHLIGHT_TINT, highlights * SplitTone);
    // the tint turns hue but never adds colour to what already had it (a warm tint on warm sand would): chroma is
    // capped at its value before the tint, except that near-greys may take the tint's own faint colour
    float lt = dot(c, LUMA);
    vec3 v = c - lt;
    c = lt + v * min(1.0, max(chroma, 0.1 * SplitTone * lt) / max(length(v), 1e-5));
    c = mix(vec3(lt), c, Saturation);

    // quartic falloff from the centre, 1 at the corners
    vec2 d = (texCoord - 0.5) * vec2(Aspect, 1.0);
    float r2 = dot(d, d) / (0.25 * (Aspect * Aspect + 1.0));
    c *= 1.0 - Vignette * r2 * r2;

    // one 8-bit step of interleaved-gradient dither: the curve and the faint vignette gradient never band
    c += (fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5) / 255.0;
    fragColor = vec4(clamp(c, 0.0, 1.0), scene.a);
}
