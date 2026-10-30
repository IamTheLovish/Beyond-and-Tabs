// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The sky's colour by direction for the time of day (light pass 4): program/sky.fsh draws it, composite.fsh's aerial
// perspective fades far ground into its horizon. By day a pale horizon under a bluer zenith (SKY_*_DAY); as the sun
// sinks below SKY_SUNSET_HIGH the horizon warms toward the sun - golden first (SKY_SUNSET_GOLD, t11800), deep orange
// at the horizon's last light (SKY_SUNSET_DEEP, the dusk still) - and the zenith deepens (SKY_ZENITH_DUSK). It was the
// noon gradient at every hour: a blue sky over orange-lit ground, the critic's "fake dusk" tell. Linear light out.
// Needs lib/settings.glsl, lib/color.glsl and lib/light.glsl (sunHeight, sunPosition, upPosition) before it.

#ifndef SKY_GLSL
#define SKY_GLSL

// 0..1: how much our gradient draws the sky (vanilla's colours carry the night)
float skyOwn() {
    // light pass 5: through the afterglow too (the dusk still, 13000, sits about at the horizon: pass 4's -0.10..0.02
    // left half of it to vanilla's peach fog colour)
    return smoothstep(-0.25, -0.05, sunHeight());
}

// 0..1: how far into sunset the sky is (1 from SKY_SUNSET_HIGH down to the horizon), and 0..1 golden (1) to deep (0)
float skySunset() {
    float h = sunHeight();
    return 1.0 - smoothstep(SKY_SUNSET_HIGH, SKY_SUNSET_HIGH + 0.25, h);
}

float skyGolden() {
    return smoothstep(0.02, 0.22, sunHeight());
}

// the horizon's colour along horizontal direction toward (0 = away from the sun, 1 = under it), linear
vec3 skyHorizon(float toward) {
    vec3 warm = mix(toLinear(SKY_SUNSET_DEEP), toLinear(SKY_SUNSET_GOLD), skyGolden());
    float share = skySunset() * mix(SKY_SUNSET_AWAY, 1.0, toward * toward);
    return mix(toLinear(SKY_HORIZON_DAY), warm, share);
}

// the sky along view direction dir (view space, normalised), linear
vec3 skyGradient(vec3 dir) {
    vec3 up = normalize(upPosition);
    vec3 sun = normalize(sunPosition);
    float u = dot(dir, up);
    vec3 dh = dir - up * u;
    vec3 sh = sun - up * dot(sun, up);
    float toward = dot(dh, dh) > 1e-6 && dot(sh, sh) > 1e-6 ? dot(normalize(dh), normalize(sh)) * 0.5 + 0.5 : 0.5;
    vec3 zenith = mix(toLinear(SKY_ZENITH_DAY), toLinear(SKY_ZENITH_DUSK), skySunset() * (1.0 - 0.5 * skyGolden()));
    // the warm band climbs higher on the sun's side
    float span = SKY_GRADIENT_SPAN * (1.0 + 0.4 * skySunset() * toward);
    return mix(skyHorizon(toward), zenith, smoothstep(0.0, span, u));
}

// the air the aerial perspective fades into: the horizon seen across the sun's line (display values)
vec3 skyAir() {
    return toDisplay(skyHorizon(0.5));
}

#endif
