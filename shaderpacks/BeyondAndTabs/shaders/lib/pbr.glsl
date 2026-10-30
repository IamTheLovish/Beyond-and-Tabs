// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Slice 2 (design/own_shader_pack.md): our blocks and units carry LabPBR 1.3 maps (tools/materials.py encodes both),
// and until this slice nothing read them - two art critics called the blocks flat and the metals "painted stone".
// This file turns the maps into material numbers and light:
//   _n  RG the tangent-space normal, green along +v (DOWN the texture), B material AO (255 = none), A height (unused:
//       parallax costs more than it shows at RTS distances, design);
//   _s  R perceptual smoothness, G F0 (0-229 linear, 230-237 the LabPBR named metals, 255 a metal with its albedo as
//       F0), B porosity / subsurface (not read yet), A emission (0-254, 255 = none).
// The sun gets a GGX highlight, the sky a cheap two-colour reflection, so a metal, a gem or obsidian keeps a sheen in
// the shade where no highlight can reach. No environment map: the sky and the lit ground stand in for it (screen-
// space reflections run on water only, where they hit; composite.fsh).
// Needs lib/color.glsl and lib/light.glsl (upPosition, the light table) before it.

const float PI = 3.14159265;

// green's direction in our maps relative to the bitangent lit.vsh builds (cross(N, T) * w, which points along +v
// when the loader's handedness follows the usual dP/dv rule). If an eyes still shows bumps lit from the wrong side
// (a ridge bright on the face turned from the sun), flip this to -1.0 - the one place the convention lives
const float NORMAL_MAP_Y = 1.0;
// the highlight's ceiling in linear light, after the sun's colour: a mirror-smooth blade under GGX peaks in the
// hundreds, and the tone curve's shoulder plus the grade clip anything far past it (design: clipped pixels under 0.5%
// of the frame). 1.2 tone-maps to ~0.94, well above the glow pass's threshold (final.fsh), so glints still bloom
const float SPEC_MAX = 1.2;
// the smallest GGX roughness: the sun is a disc, not a point, and a needle highlight only shimmers between frames
const float MIN_ROUGHNESS = 0.02;
// what is left of a metal's diffuse light (a metal has none; a little keeps a dark-albedo metal from going black where
// the sky reflection shows ground)
const float METAL_DIFFUSE = 0.10;
// the reflected sky, from the light table's fill: bluer overhead, paler and warmer at the horizon. Below the horizon
// the ground as the fill sees it (light pass 2): GROUND_ALBEDO of the sun and sky falling on open ground, tinted as
// grass and earth (GROUND_TINT, about the lawn's hue at luma 1) - one hemisphere for the fill and the reflections
const vec3 ZENITH_TINT = vec3(0.86, 0.96, 1.18);
const vec3 HORIZON_TINT = vec3(1.20, 1.15, 1.05);
const vec3 GROUND_TINT = vec3(1.05, 1.02, 0.80);

struct Material {
    vec3 normal;        // the mapped normal, view space
    float ao;           // _n B: the texture's own ambient occlusion, 1 = open
    float roughness;    // GGX alpha
    vec3 f0;            // reflectance straight on, linear
    float metal;        // 1 for 230-237 and 255
    float emission;     // 0..1
};

// the tangent-space normal from an _n texel. A texel of all zeros is a draw with no map bound (or a hole): flat
vec3 decodeNormal(vec4 raw) {
    if (raw.r + raw.g + raw.b + raw.a < 0.5 / 255.0)
        return vec3(0.0, 0.0, 1.0);
    vec2 xy = raw.rg * 2.0 - 1.0;
    xy.y *= NORMAL_MAP_Y;
    float d = dot(xy, xy);
    if (d > 1.0)
        xy *= inversesqrt(d);      // an 8-bit pair can land just past the unit circle: keep z real
    return vec3(xy, sqrt(max(1.0 - dot(xy, xy), 0.0)));
}

// albedo is linear. geomNormal and the tangent frame are view space; a zero tangent (a draw the loader gave none)
// leaves the vertex normal as it is
Material readMaterial(vec4 nTex, vec4 sTex, vec3 albedo, vec3 geomNormal, vec3 tangent, vec3 bitangent, bool mapNormal) {
    Material m;
    bool noMap = nTex.r + nTex.g + nTex.b + nTex.a < 0.5 / 255.0;
    m.ao = noMap ? 1.0 : nTex.b;
    m.normal = geomNormal;
    if (mapNormal && dot(tangent, tangent) > 1e-6 && dot(bitangent, bitangent) > 1e-6) {
        // re-orthogonalise against the interpolated normal: on a smooth entity the three drift apart across a face
        vec3 t = normalize(tangent - geomNormal * dot(geomNormal, tangent));
        vec3 b = normalize(bitangent - geomNormal * dot(geomNormal, bitangent) - t * dot(t, bitangent));
        vec3 tn = decodeNormal(nTex);
        m.normal = normalize(t * tn.x + b * tn.y + geomNormal * tn.z);
    }

    float smoothness = sTex.r;
    float rough = (1.0 - smoothness) * (1.0 - smoothness);     // LabPBR: perceptual smoothness to linear roughness
    m.roughness = max(rough, MIN_ROUGHNESS);
    float code = floor(sTex.g * 255.0 + 0.5);
    // 230-237 named metals and 255 albedo-as-F0: the albedo's own colour as F0, so a gold trim stays gold and a
    // team-tinted plate keeps its tint (the named metals' fixed values would turn every iron unit the same grey).
    // 238-254 only come from a mipmap blending those two, so they stay metal. A code between DIELECTRIC_F0_MAX_CODE
    // and 230 is a mip's blend of a metal texel with a dielectric one: that share of metal (light pass 2's audit;
    // lib/settings.glsl), so a barrel's hoop seen from afar is never a dielectric with a near-mirror F0
    m.metal = clamp((code - DIELECTRIC_F0_MAX_CODE) / (229.5 - DIELECTRIC_F0_MAX_CODE), 0.0, 1.0);
    m.f0 = mix(vec3(min(code, DIELECTRIC_F0_MAX_CODE) / 255.0), albedo, m.metal);
    m.emission = sTex.a < 254.5 / 255.0 ? sTex.a * (255.0 / 254.0) : 0.0;
    return m;
}

vec3 fresnelSchlick(vec3 f0, float cosTheta) {
    float k = pow(1.0 - clamp(cosTheta, 0.0, 1.0), 5.0);
    return f0 + (1.0 - f0) * k;
}

// the sun's highlight per unit of the sun's light (cap it with SPEC_MAX once the light is applied), in this pack's units (diffuse is albedo x light, no 1/pi, so the
// BRDF's specular lobe is scaled by pi to sit on the same scale): GGX distribution x Schlick Fresnel x a
// Schlick-Smith visibility x N.L
vec3 sunSpecular(Material m, vec3 v, vec3 l) {
    vec3 n = m.normal;
    float nl = dot(n, l);
    if (nl <= 0.0)
        return vec3(0.0);
    float nv = max(dot(n, v), 1e-3);
    vec3 h = normalize(v + l);
    float nh = max(dot(n, h), 0.0);
    float a2 = m.roughness * m.roughness;
    float q = nh * nh * (a2 - 1.0) + 1.0;
    float d = a2 / (PI * q * q);
    float k = m.roughness * 0.5;
    float vis = 0.25 / ((nl * (1.0 - k) + k) * (nv * (1.0 - k) + k));
    vec3 f = fresnelSchlick(m.f0, dot(v, h));
    return PI * d * vis * nl * f;
}

// the sky seen in a reflection along r (view space), from this frame's sky fill
// the reflected sky keeps pass 12's brightness under a high sun (light pass 13): the fill's share fell 0.62 -> 0.34 for
// deeper shadows, which is how much of the sky a diffuse surface gathers, not how bright the sky is - a metal or the
// water would have lost ~45% of its reflection with it (light pass 1: iron read near black)
const float REFLECTED_SKY_GAIN = 0.62 / SHADOW_FILL_SHARE;

vec3 skyInDirection(vec3 r, vec3 skyColour, vec3 sunColour) {
    skyColour *= mix(1.0, REFLECTED_SKY_GAIN, smoothstep(0.10, 0.55, sunHeight()));
    float u = dot(r, normalize(upPosition));
    vec3 horizon = skyColour * HORIZON_TINT + sunColour * 0.08;
    vec3 zenith = skyColour * ZENITH_TINT;
    if (u >= 0.0)
        return mix(horizon, zenith, sqrt(u));
    vec3 up = normalize(upPosition);
    vec3 ground = GROUND_TINT * GROUND_ALBEDO
        * (sunColour * sunFacing(up, normalize(shadowLightPosition)) + skyColour);
    return mix(horizon, ground, smoothstep(0.0, 0.3, -u));
}

// the reflected environment (sky above the horizon, lit ground below), weighted by Fresnel and by smoothness (a rough
// surface scatters it into its diffuse look), and by the sky light level as the fill is - down to SKY_FILL_FLOOR, never
// to nothing (light pass 2: a metal face with a stale or low sky light reflected black; lib/light.glsl has the floor)
vec3 skyReflection(Material m, vec3 v, float skyLevel, vec3 skyColour, vec3 sunColour) {
    vec3 r = reflect(-v, m.normal);
    vec3 f = fresnelSchlick(m.f0, dot(m.normal, v));
    return skyInDirection(r, skyColour, sunColour) * f * ((1.0 - m.roughness) * mix(SKY_FILL_FLOOR, 1.0, skyLevel));
}
