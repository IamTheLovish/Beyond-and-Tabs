// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The shadow map fitted to the RTS view. The loader centres the shadow map on the camera and spreads it over a box of
// +-shadowDistance blocks: right for an eye on the ground, wasteful for our camera, which looks down at a patch of
// ground 30-100 blocks below it (OrthoviewClientEvents). Here the map is cut down to the part of that box the view
// shows: the four screen corners, followed down the view direction to a band of heights below the camera, projected
// into the light's view; their bounding square is stretched over the whole map. A shadow caster off screen needs no
// margin: in the light's view it lands on the very spot of the shadow it throws.
// The shader cannot know the ground height, so the band covers it: the RTS eye sits about 30 blocks above the ground
// (orthoviewPlayerBaseY = average height + 30), and roofs and towers reach up toward the camera.
// Used identically by the shadow pass (shadow.vsh) and every lookup (lib/shadow.glsl): the same uniforms, the same
// arithmetic, so caster and receiver agree. A perspective view (first person) keeps the loader's whole box.

uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;

const float FIT_BAND_TOP = 0.0;        // blocks relative to the camera: the band the visible ground lies in
const float FIT_BAND_BOTTOM = -64.0;
const float FIT_MARGIN = 1.06;         // a little room round the view, so the screen edge never sits on the map's edge

bool orthoView() {
    // the projection's w row: 0, 0, 0, 1 for the RTS camera's orthographic projection, 0, 0, -1, 0 for perspective
    return gbufferProjection[2][3] == 0.0 && gbufferProjection[3][3] == 1.0;
}

void fitCorner(vec2 ndc, vec3 dir, inout vec2 lo, inout vec2 hi) {
    vec4 v = gbufferProjectionInverse * vec4(ndc, 0.0, 1.0);
    vec3 origin = (gbufferModelViewInverse * vec4(v.xyz / v.w, 1.0)).xyz;
    for (int i = 0; i < 2; i++) {
        float h = i == 0 ? FIT_BAND_TOP : FIT_BAND_BOTTOM;
        vec3 p = origin + dir * ((h - origin.y) / dir.y);
        vec2 q = (shadowProjection * (shadowModelView * vec4(p, 1.0))).xy;
        lo = min(lo, q);
        hi = max(hi, q);
    }
}

// xy: the centre of the fitted square in the loader's shadow clip space; z: the scale that stretches it over the map
vec3 shadowFit() {
    if (!orthoView())
        return vec3(0.0, 0.0, 1.0);
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * vec3(0.0, 0.0, -1.0));
    if (dir.y > -0.1)                  // the RTS tilt never gets this shallow (12 degrees at most); keep the whole box
        return vec3(0.0, 0.0, 1.0);
    vec2 lo = vec2(1e9), hi = vec2(-1e9);
    fitCorner(vec2(-1.0, -1.0), dir, lo, hi);
    fitCorner(vec2(1.0, -1.0), dir, lo, hi);
    fitCorner(vec2(-1.0, 1.0), dir, lo, hi);
    fitCorner(vec2(1.0, 1.0), dir, lo, hi);
    // nothing outside the loader's box was drawn into the map
    lo = max(lo, vec2(-1.0));
    hi = min(hi, vec2(1.0));
    float extent = max(max(hi.x - lo.x, hi.y - lo.y) * 0.5 * FIT_MARGIN, 1.0 / 64.0);
    return vec3((lo + hi) * 0.5, 1.0 / min(extent, 1.0));
}

vec2 shadowRemap(vec2 clip, vec3 fit) {
    return (clip - fit.xy) * fit.z;
}
