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

// the horizon's colour along horizontal direction toward (0 = away from the sun, 1 = under it), linear, from the day's
// colour day (display values): the sky's (SKY_HORIZON_DAY) or the air's (SKY_AIR_DAY)
vec3 skyHorizonFrom(vec3 day, float toward) {
    vec3 warm = mix(toLinear(SKY_SUNSET_DEEP), toLinear(SKY_SUNSET_GOLD), skyGolden());
    float share = skySunset() * mix(SKY_SUNSET_AWAY, 1.0, toward * toward);
    // the horizon away from the sun dims to a blue-grey as the sun sets (light pass 8: the dusk still read mid-afternoon)
    vec3 base = mix(toLinear(day), toLinear(SKY_HORIZON_DUSK), skySunset() * (1.0 - skyGolden()));
    return mix(base, warm, share);
}

vec3 skyHorizon(float toward) {
    return skyHorizonFrom(SKY_HORIZON_DAY, toward);
}

// the sky along view direction dir (view space, normalised), linear
vec3 skyGradient(vec3 dir) {
    vec3 up = normalize(upPosition);
    vec3 sun = normalize(sunPosition);
    float u = dot(dir, up);
    vec3 dh = dir - up * u;
    vec3 sh = sun - up * dot(sun, up);
    float toward = dot(dh, dh) > 1e-6 && dot(sh, sh) > 1e-6 ? dot(normalize(dh), normalize(sh)) * 0.5 + 0.5 : 0.5;
    vec3 zenith = mix(toLinear(SKY_ZENITH_DAY), toLinear(SKY_ZENITH_DUSK), skySunset() * (1.0 - 0.8 * skyGolden()));
    // the warm band climbs higher on the sun's side
    float span = SKY_GRADIENT_SPAN * (1.0 + 0.4 * skySunset() * toward)
        * mix(1.0, SKY_DUSK_SPAN, skySunset() * (1.0 - skyGolden()));
    // one soft ramp (light pass 8: pass 7's 0.10 span read as a hard two-band sky): a square root, so the 3/4 frame's
    // top edge (~5 degrees up) is already ~0.4 of the way to the zenith
    vec3 c = mix(skyHorizon(toward), zenith, sqrt(clamp(u / span, 0.0, 1.0)));
    // below the horizon (the void past the map's edge, seen from a 3/4 view): the horizon's colour turning a deeper
    // blue-grey further down (SKY_BELOW_TINT) - never the clear colour (light pass 6), never a flat cream (pass 7)
    return c * mix(vec3(1.0), SKY_BELOW_TINT, smoothstep(0.0, 0.4, -u));
}

// the sky with the sun's disc and halo and a glow round the moon (light passes 3-6), linear. Painted over every pixel
// nothing was drawn at by composite.fsh (light pass 6), so it shows whichever sky programs the loader runs
vec3 skyWithLights(vec3 dir) {
    vec3 c = skyGradient(dir);
    vec3 sun = normalize(sunPosition);
    float h = sunHeight();
    float u = dot(dir, normalize(upPosition));
    float toSun = max(dot(dir, sun), 0.0);
    float angle = acos(clamp(dot(dir, sun), -1.0, 1.0));
    // the sky brightens toward the sun within ~30 degrees of it (SKY_SUN_GLOW, light pass 8; pass 3's glow spread ~60),
    // warmer as it sinks
    c += mix(vec3(1.0, 0.96, 0.90), vec3(1.0, 0.72, 0.45), lowSun()) * (SKY_SUN_GLOW * exp(-angle / 0.25)
        * smoothstep(-0.12, 0.05, h));
    float disc = 1.0 - smoothstep(SUN_DISC_RADIUS * 0.75, SUN_DISC_RADIUS * 1.25, angle);
    float halo = SUN_HALO * exp(-angle / (SUN_DISC_RADIUS * 4.0));
    vec3 discColour = mix(SUN_DISC_COLOUR, SUN_DISC_LOW, lowSun());
    c += discColour * ((disc + halo) * smoothstep(-0.02, 0.01, h) * smoothstep(-0.02, 0.02, u));
    float toMoon = max(dot(dir, -sun), 0.0);
    float moonUp = smoothstep(-0.05, 0.10, -h);
    c += vec3(0.62, 0.70, 0.90) * (MOON_GLOW * moonUp * (pow(toMoon, 400.0) + 0.3 * pow(toMoon, 40.0)));
    // our own moon disc (light pass 8): vanilla's square moon sprite is dropped wherever it is drawn
    float moonAngle = acos(clamp(dot(dir, -sun), -1.0, 1.0));
    float moonDisc = 1.0 - smoothstep(MOON_DISC_RADIUS * 0.8, MOON_DISC_RADIUS * 1.2, moonAngle);
    c += MOON_DISC_COLOUR * (moonDisc * moonUp * smoothstep(-0.02, 0.02, u));
    return c;
}

// the air the aerial perspective fades into: the horizon seen across the sun's line (display values). Light pass 6:
// its own day colour (SKY_AIR_DAY, pass 4's horizon, judged ahead of the benchmark), now that the sky's horizon is
// lighter than its zenith
vec3 skyAir() {
    return toDisplay(skyHorizonFrom(SKY_AIR_DAY, 0.5));
}

#endif
