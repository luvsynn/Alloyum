#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GradeConfig {
    float Saturation;
    float Contrast;
    float Warmth;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 source = texture(InSampler, texCoord);
    float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    float saturation = clamp(Saturation, 0.0, 1.0) * 0.05;
    float contrast = clamp(Contrast, 0.0, 1.0) * 0.05;
    float warmth = clamp(Warmth, 0.0, 1.0) * 0.02;
    vec3 graded = mix(vec3(luminance), source.rgb, 1.0 + saturation);
    graded = (graded - vec3(0.5)) * (1.0 + contrast) + vec3(0.5);
    graded += vec3(warmth, 0.0, -warmth);
    fragColor = vec4(clamp(graded, 0.0, 1.0), source.a);
}
