// Beyond and Tabs shader pack - GPL v2 or later (see LICENSE). Original work, written clean-room; not derived from
// any other shader pack.
//
// Which lit draws read the material maps (LIT_PBR; lib/pbr.glsl): terrain, entities, block entities and the hand, when
// the MATERIALS option is on. Translucent terrain and water (LIT_WATER) stay as slice 1 drew them: water gets its own
// surface in slice 4 (design/own_shader_pack.md). One switch for lit.vsh and lit.fsh, so the two stages agree on
// their varyings.

#if MATERIALS == 1 && !defined LIT_WATER
#define LIT_PBR
#endif
