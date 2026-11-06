// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// The world uniforms several libraries share, declared in this one place: GLSL 120 rejects a uniform declared twice,
// and the water (lib/water.glsl via program/lit.fsh, composite.fsh), the wind (lib/wind.glsl) and the cloud shadows
// (lib/clouds.glsl) all read them in one program with the default options. Include it; never declare them yourself
// (tools/test_shader_pack.py checks every program at every option-screen variant)

#ifndef WORLD_GLSL
#define WORLD_GLSL

uniform vec3 cameraPosition;           // the camera's world position: world = player-space + cameraPosition
uniform float frameTimeCounter;        // seconds; the loader wraps it after an hour (one jump in the gusts then)

#endif
