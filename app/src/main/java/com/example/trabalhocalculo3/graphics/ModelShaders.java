package com.example.trabalhocalculo3.graphics;

/** Iluminação compartilhada entre a visualização 3D e a realidade aumentada. */
public final class ModelShaders {
    private ModelShaders() {}

    public static final String VERTEX =
            "#version 300 es\n" +
            "layout(location = 0) in vec3 aPosition;\n" +
            "layout(location = 1) in vec3 aNormal;\n" +

            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uMVMatrix;\n" +

            "out vec3 vNormal;\n" +
            "out vec3 vPosition;\n" +

            "void main() {\n" +
            "    vec4 positionView = uMVMatrix * vec4(aPosition, 1.0);\n" +
            "    vPosition = positionView.xyz;\n" +
            "    vNormal = normalize(mat3(uMVMatrix) * aNormal);\n" +
            "    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
            "}";

    /*
     * =========================================================
     * FRAGMENT SHADER
     * Calcula iluminação ambiente, difusa e especular.
     * =========================================================
     */

    public static final String FRAGMENT =
            "#version 300 es\n" +
                    "precision highp float;\n" +
                    "uniform vec3 uColor;\n" +
                    "uniform float uAlpha;\n" +
                    "in vec3 vNormal;\n" +
                    "in vec3 vPosition;\n" +

                    "out vec4 fragColor;\n" +

                    "void main() {\n" +

                    "    vec3 normal = normalize(vNormal);\n" +

                    // Direção da luz no espaço da câmera.
                    "    vec3 lightDirection = normalize(vec3(-0.5, 0.8, 1.0));\n" +

                    // Iluminação difusa: evidencia as diferenças entre as faces.
                    "    float diffuse = max(dot(normal, lightDirection), 0.0);\n" +
                    // Iluminação ambiente: evita que as áreas sem luz desapareçam.
                    "    float ambient = 0.28;\n" +
                    // Direção entre a superfície e a câmera.
                    "    vec3 viewDirection = normalize(-vPosition);\n" +
                    // Reflexo da luz para calcular o brilho.
                    "    vec3 reflectedLight = reflect(-lightDirection, normal);\n" +
                    "    float specular = pow(max(dot(viewDirection, reflectedLight), 0.0), 32.0);\n" +
                    // Combinação das três componentes de iluminação.
                    "    vec3 color = uColor * (ambient + 0.85 * diffuse);\n" +
                    "    color += vec3(1.0) * 0.25 * specular;\n" +
                    "    fragColor = vec4(color, uAlpha);\n" +
                    "}";
}
