#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GradeConfig {
    float Strength;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 source = texture(InSampler, texCoord);
    float strength = clamp(Strength, 0.0, 1.0);
    float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 saturated = mix(vec3(luminance), source.rgb, 1.1);
    vec3 contrasted = (saturated - vec3(0.5)) * 1.1 + vec3(0.5);
    contrasted += vec3(0.04, 0.0, -0.04);
    fragColor = vec4(mix(source.rgb, clamp(contrasted, 0.0, 1.0), strength), source.a);
}
