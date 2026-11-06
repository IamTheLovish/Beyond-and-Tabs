#version 150

// Instanced unit bodies, the light pass (a body's _glow texture, full-bright and additive - the game's "eyes" render
// type): the same bone transform as bat_unit_instanced.vsh, written the same way so the depth matches the body's to
// the bit (invariant), then the eyes shader's unlit colour.

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in int Bone;
in int BoneBase;
in vec4 Color;

uniform samplerBuffer Bones;

uniform mat4 ModelViewMat;
uniform mat4 BatViewProj;   // ProjMat * ModelViewMat, multiplied once on the CPU
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

invariant gl_Position;

float bat_fogDistance(vec3 p) {
    if (FogShape == 0)
        return length((ModelViewMat * vec4(p, 1.0)).xyz);
    float across = length((ModelViewMat * vec4(p.x, 0.0, p.z, 1.0)).xyz);
    float up = length((ModelViewMat * vec4(0.0, p.y, 0.0, 1.0)).xyz);
    return max(across, up);
}

void main() {
    int b = BoneBase + Bone * 6;
    vec4 at = vec4(Position, 1.0);
    vec3 pos = vec3(dot(texelFetch(Bones, b), at), dot(texelFetch(Bones, b + 1), at), dot(texelFetch(Bones, b + 2), at));
    vertexColor = Color;
    gl_Position = BatViewProj * vec4(pos, 1.0);
    vertexDistance = bat_fogDistance(IViewRotMat * pos);
    texCoord0 = UV0;
}
