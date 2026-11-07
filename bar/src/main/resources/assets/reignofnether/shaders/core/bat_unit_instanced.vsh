#version 150

// Instanced unit bodies (unit/modelling/instancing): one draw for every unit of a body and pass. A vertex is baked in
// its bone's frame; the bone matrices (six RGBA texels a bone: the pose's three rows, the normal matrix's three rows)
// come from a texture buffer, and the instance's bone block, colour, light-map texel and overlay texel are per-instance
// attributes (the two texels read on the CPU from the very images the game uploads, so no per-vertex fetch). After the bone transform the vertex is
// exactly what the old path wrote on the CPU, and from there this does what the game's entity cutout shader does with
// it: the two-light shading, the light map, the hurt overlay, the fog distance.

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in int Bone;
in int BoneBase;
in vec4 Color;
in vec4 LightColor;
in vec4 OverlayColor;

uniform samplerBuffer Bones;

uniform mat4 ModelViewMat;
uniform mat4 BatViewProj;   // ProjMat * ModelViewMat, multiplied once on the CPU instead of once a vertex
uniform mat3 IViewRotMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
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
    vec3 n = vec3(dot(texelFetch(Bones, b + 3).xyz, Normal), dot(texelFetch(Bones, b + 4).xyz, Normal),
                  dot(texelFetch(Bones, b + 5).xyz, Normal));
    // the old path stores a normal in bytes, (int) (clamp(c) * 127), and the shader reads c / 127 back
    n = trunc(clamp(n, -1.0, 1.0) * 127.0) / 127.0;

    gl_Position = BatViewProj * vec4(pos, 1.0);
    vertexDistance = bat_fogDistance(IViewRotMat * pos);
    // two directional lights over an ambient floor, as every lit entity
    float l0 = max(0.0, dot(normalize(Light0_Direction), n));
    float l1 = max(0.0, dot(normalize(Light1_Direction), n));
    vertexColor = vec4(Color.rgb * min(1.0, (l0 + l1) * 0.6 + 0.4), Color.a);
    lightMapColor = LightColor;
    overlayColor = OverlayColor;
    texCoord0 = UV0;
}
