#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec2 vUv;
flat layout(location = 1) in vec2 vHalfExtent;
layout(location = 2) in vec4 vColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float dist = length(max(abs(vUv) - vHalfExtent, 0.0)) - 1.0;

    float aa = max(fwidth(dist), 1e-5);
    float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);

    if (alpha <= 0.0) {
        discard;
    }

    vec4 color = vec4(vColor.rgb, vColor.a * alpha);
    if (color.a == 0.0) {
        discard;
    }

    fragColor = color * ColorModulator;
}
