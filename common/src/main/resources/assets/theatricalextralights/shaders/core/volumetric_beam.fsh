#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 goboTex = texture(Sampler0, texCoord0);

    float goboLum = clamp(dot(goboTex.rgb, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    float goboAlpha = pow(goboTex.a * goboLum, 0.7);
    vec2 p = texCoord0 * 2.0 - 1.0;
    float radial = 1.0 - smoothstep(0.82, 1.0, length(p));

    float finalAlpha = goboAlpha * vertexColor.a * radial;
    vec3 finalRGB = goboTex.rgb * vertexColor.rgb;

    fragColor = vec4(finalRGB, finalAlpha);
}