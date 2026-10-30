// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Colour conversions. Textures and vertex colours are sRGB-ish display values; lighting happens in linear light; the
// tone curve brings it back to the screen.

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 toLinear(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(2.2));
}

vec3 toDisplay(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
}

// the tone curve: exactly linear below the knee, so everything lit like noon ground keeps its texture colour (and
// overlays drawn unlit come back as drawn); above it a soft shoulder that approaches white but never clips - the
// capitol's walls keep their shape instead of blowing out
const float TONE_KNEE = 0.70;

float toneCurve(float x) {
    if (x <= TONE_KNEE)
        return x;
    float r = 1.0 - TONE_KNEE;
    return TONE_KNEE + r * (1.0 - exp(-(x - TONE_KNEE) / r));
}

// applied to luminance and carried to rgb as a ratio, so the shoulder never shifts a hue (a bright orange stays orange)
vec3 toneMap(vec3 c) {
    float l = dot(c, LUMA);
    if (l <= TONE_KNEE)
        return min(c, vec3(1.0));
    vec3 t = c * (toneCurve(l) / l);
    // a channel past 1 would clip: blend it toward white instead (very bright light loses colour, as it does on film)
    float over = max(max(t.r, max(t.g, t.b)) - 1.0, 0.0);
    return min(mix(t, vec3(toneCurve(l)), clamp(over, 0.0, 1.0)), vec3(1.0));
}
