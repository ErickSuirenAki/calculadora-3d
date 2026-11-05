package com.example.trabalhocalculo3.graphics;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.example.trabalhocalculo3.mesh.Mesh3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GraphRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "GraphRenderer";

    private int shaderProgram;

    // Buffer de posições e buffer de normais.
    private int vertexBufferObject;
    private int normalBufferObject;

    private int vertexCount;

    // Rotação controlada pelo usuário.
    private volatile float rotationX = 0.0f;
    private volatile float rotationY = 0.0f;

    // Zoom controlado pelo usuário.
    private volatile float zoomFactor = 1.0f;

    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 4.0f;

    private Mesh3D mesh;

    private boolean drawLog = false;

    private final float[] mvpMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];

    /*
     * =========================================================
     * VERTEX SHADER
     * Calcula a posição dos vértices e transforma as normais.
     * =========================================================
     */

    private final String vertexShaderCode =
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

    private final String fragmentShaderCode =
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
    // CENTRO DE MASSA
    private double[] centerOfMass = null;
    private boolean showCenterOfMass = false;
    private Sphere centerOfMassSphere;
    private float sphereRadiusUsed = -1f;
    private int sphereVbo;
    private int sphereNormalVbo;

    public void setCenterOfMass(double[] centerOfMass) {
        this.centerOfMass = centerOfMass;
    }

    public void setShowCenterOfMass(boolean show) {
        this.showCenterOfMass = show;
    }

    /*
     * =========================================================
     * RECEBE A MALHA 3D
     * =========================================================
     */

    public void setMesh(Mesh3D mesh) {

        this.mesh = mesh;

        resetCamera();

        drawLog = false;

        if (mesh == null) {
            vertexCount = 0;
            return;
        }

        vertexCount = mesh.getVertexCount();

        Log.d(
                TAG,
                "Malha recebida: " +
                        vertexCount +
                        " vertices, " +
                        mesh.getTriangleCount() +
                        " triangulos"
        );
    }

    /*
     * =========================================================
     * CONTROLES DA VISUALIZAÇÃO
     * =========================================================
     */

    public void rotateBy(float deltaX, float deltaY) {

        rotationY += deltaX;
        rotationX += deltaY;
    }

    public void zoomBy(float scaleFactor) {

        if (scaleFactor <= 0.0f) {
            return;
        }

        float newZoom = zoomFactor * scaleFactor;

        zoomFactor = Math.max(
                MIN_ZOOM,
                Math.min(MAX_ZOOM, newZoom)
        );
    }

    public void resetCamera() {

        rotationX = 0.0f;
        rotationY = 0.0f;
        zoomFactor = 1.0f;
    }

    /*
     * =========================================================
     * INICIALIZAÇÃO DO OPENGL
     * =========================================================
     */

    @Override
    public void onSurfaceCreated(
            GL10 gl,
            EGLConfig config
    ) {

        GLES30.glClearColor(
                0.05f,
                0.05f,
                0.05f,
                1.0f
        );

        // Ativa o teste de profundidade.
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        GLES30.glDepthFunc(GLES30.GL_LESS);

        int vertexShader = compileShader(
                GLES30.GL_VERTEX_SHADER,
                vertexShaderCode
        );

        int fragmentShader = compileShader(
                GLES30.GL_FRAGMENT_SHADER,
                fragmentShaderCode
        );

        if (vertexShader == 0 || fragmentShader == 0) {

            if (vertexShader != 0) {
                GLES30.glDeleteShader(vertexShader);
            }

            if (fragmentShader != 0) {
                GLES30.glDeleteShader(fragmentShader);
            }

            Log.e(TAG, "Falha ao compilar os shaders.");

            shaderProgram = 0;

            return;
        }

        shaderProgram = GLES30.glCreateProgram();

        GLES30.glAttachShader(
                shaderProgram,
                vertexShader
        );

        GLES30.glAttachShader(
                shaderProgram,
                fragmentShader
        );

        GLES30.glLinkProgram(shaderProgram);

        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragmentShader);

        int[] linkStatus = new int[1];

        GLES30.glGetProgramiv(
                shaderProgram,
                GLES30.GL_LINK_STATUS,
                linkStatus,
                0
        );

        if (linkStatus[0] == 0) {

            Log.e(
                    TAG,
                    "Erro ao linkar o programa: " +
                            GLES30.glGetProgramInfoLog(shaderProgram)
            );

            GLES30.glDeleteProgram(shaderProgram);

            shaderProgram = 0;

            return;
        }

        // Cria os dois buffers da GPU.
        int[] buffers = new int[4];

        GLES30.glGenBuffers(4, buffers, 0);

        vertexBufferObject = buffers[0];
        normalBufferObject = buffers[1];
        sphereVbo = buffers[2];
        sphereNormalVbo = buffers[3];

        Matrix.setIdentityM(modelMatrix, 0);

        Log.d(TAG, "OpenGL inicializado com iluminacao 3D.");
    }

    /*
     * =========================================================
     * CONFIGURAÇÃO DA CÂMERA E DA TELA
     * =========================================================
     */

    @Override
    public void onSurfaceChanged(
            GL10 gl,
            int width,
            int height
    ) {

        GLES30.glViewport(0, 0, width, height);

        if (width <= 0 || height <= 0) {
            return;
        }

        float aspectRatio = (float) width / (float) height;

        Matrix.perspectiveM(
                projectionMatrix,
                0,
                45.0f,
                aspectRatio,
                0.1f,
                100.0f
        );

        Matrix.setLookAtM(
                viewMatrix,
                0,
                0.0f, 0.0f, 3.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        );
    }

    /*
     * =========================================================
     * DESENHO DO OBJETO 3D
     * =========================================================
     */

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES30.glClear(
                GLES30.GL_COLOR_BUFFER_BIT |
                        GLES30.GL_DEPTH_BUFFER_BIT
        );

        if (shaderProgram == 0 || mesh == null) {
            return;
        }

        float[] positions = mesh.getPositions();
        float[] normals = mesh.getNormals();

        if (positions == null || positions.length == 0) {
            return;
        }

        if (normals == null || normals.length != positions.length) {

            Log.e(
                    TAG,
                    "A malha nao possui uma normal para cada vertice. " +
                            "Verifique o MeshImporter."
            );

            return;
        }

        if (!drawLog) {

            Log.d(
                    TAG,
                    "Desenhando malha: " +
                            vertexCount +
                            " vertices."
            );

            drawLog = true;
        }

        /*
         * TRANSFORMAÇÕES DO OBJETO
         */

        Matrix.setIdentityM(modelMatrix, 0);

        // Rotação horizontal.
        Matrix.rotateM(
                modelMatrix,
                0,
                rotationY,
                0.0f,
                1.0f,
                0.0f
        );

        // Rotação vertical.
        Matrix.rotateM(
                modelMatrix,
                0,
                rotationX,
                1.0f,
                0.0f,
                0.0f
        );

        // Mantém a correção de orientação do modelo original.
        Matrix.rotateM(
                modelMatrix,
                0,
                -90.0f,
                1.0f,
                0.0f,
                0.0f
        );

        // Ajusta o tamanho inicial.
        float boundingRadius = mesh.getBoundingRadius();

        float targetSize = 1.5f;

        float baseScale = boundingRadius > 0.0f
                ? targetSize / boundingRadius
                : 1.0f;

        float finalScale = baseScale * zoomFactor;

        Matrix.scaleM(
                modelMatrix,
                0,
                finalScale,
                finalScale,
                finalScale
        );

        // Centraliza a malha.
        float[] center = mesh.getCenter();

        Matrix.translateM(
                modelMatrix,
                0,
                -center[0],
                -center[1],
                -center[2]
        );

        /*
         * PREPARA OS BUFFERS DE POSIÇÕES E NORMAIS
         */

        FloatBuffer vertexBuffer =
                ByteBuffer.allocateDirect(positions.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();

        vertexBuffer.put(positions);
        vertexBuffer.position(0);

        FloatBuffer normalBuffer =
                ByteBuffer.allocateDirect(normals.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();

        normalBuffer.put(normals);
        normalBuffer.position(0);

        /*
         * ENVIA AS POSIÇÕES PARA A GPU
         */

        GLES30.glBindBuffer(
                GLES30.GL_ARRAY_BUFFER,
                vertexBufferObject
        );

        GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                positions.length * 4,
                vertexBuffer,
                GLES30.GL_DYNAMIC_DRAW
        );

        /*
         * ENVIA AS NORMAIS PARA A GPU
         */

        GLES30.glBindBuffer(
                GLES30.GL_ARRAY_BUFFER,
                normalBufferObject
        );

        GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                normals.length * 4,
                normalBuffer,
                GLES30.GL_DYNAMIC_DRAW
        );

        /*
         * CALCULA AS MATRIZES
         */

        // ModelView = View × Model.
        Matrix.multiplyMM(
                modelViewMatrix,
                0,
                viewMatrix,
                0,
                modelMatrix,
                0
        );

        // MVP = Projection × ModelView.
        Matrix.multiplyMM(
                mvpMatrix,
                0,
                projectionMatrix,
                0,
                modelViewMatrix,
                0
        );

        /*
         * ATIVA O SHADER
         */

        GLES30.glUseProgram(shaderProgram);

        /*
         * ENVIA A MATRIZ MVP
         */

        int mvpHandle = GLES30.glGetUniformLocation(
                shaderProgram,
                "uMVPMatrix"
        );

        GLES30.glUniformMatrix4fv(
                mvpHandle,
                1,
                false,
                mvpMatrix,
                0
        );

        /*
         * ENVIA A MATRIZ MODELVIEW
         * Necessária para transformar as normais e as posições.
         */

        int mvHandle = GLES30.glGetUniformLocation(
                shaderProgram,
                "uMVMatrix"
        );

        GLES30.glUniformMatrix4fv(
                mvHandle,
                1,
                false,
                modelViewMatrix,
                0
        );

        /*
         * DESENHO DO MARCADOR DO CENTRO DE MASSA, SE ATIVADO
         */
        float markerRadius = mesh.getBoundingRadius() * 0.05f; // 5% do tamanho total do objeto
        if (centerOfMassSphere == null || sphereRadiusUsed != markerRadius) {
            createMarker(markerRadius);
        }
        int colorHandle = GLES30.glGetUniformLocation(shaderProgram, "uColor");
        int alphaHandle = GLES30.glGetUniformLocation(shaderProgram, "uAlpha");

        // Esfera vermelha opaca primeiro, para entrar no depth buffer
        if (showCenterOfMass && centerOfMass != null && centerOfMassSphere != null) {
            float[] mm = new float[16];
            System.arraycopy(modelMatrix, 0, mm, 0, 16);
            Matrix.translateM(mm, 0,
                    (float) centerOfMass[0],
                    (float) centerOfMass[1],
                    (float) centerOfMass[2]);

            float[] mmv = new float[16];
            float[] mmvp = new float[16];
            Matrix.multiplyMM(mmv, 0, viewMatrix, 0, mm, 0);
            Matrix.multiplyMM(mmvp, 0, projectionMatrix, 0, mmv, 0);

            GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mmvp, 0);
            GLES30.glUniformMatrix4fv(mvHandle, 1, false, mmv, 0);
            GLES30.glUniform3f(colorHandle, 1.0f, 0.1f, 0.1f);
            GLES30.glUniform1f(alphaHandle, 1.0f);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sphereVbo);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sphereNormalVbo);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 12, 0);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, centerOfMassSphere.getVertexCount());

            // Restaura as matrizes da malha
            GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
            GLES30.glUniformMatrix4fv(mvHandle, 1, false, modelViewMatrix, 0);
        }

        // Malha translúcida se o centro de massa está visível
        GLES30.glUniform3f(colorHandle, 0.20f, 0.62f, 0.95f);

        if (showCenterOfMass) {
            GLES30.glUniform1f(alphaHandle, 0.5f); // Controla a translucidez
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glDepthMask(false);
        } else {
            GLES30.glUniform1f(alphaHandle, 1.0f);
        }

        // Configurar vértices e normais
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferObject);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, normalBufferObject);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 12, 0);

        // Desenhar a malha
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0,
                Math.min(vertexCount, positions.length / 3));

        // Restaura o estado
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);

        // Verificação de erro OpenGL
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "Erro OpenGL ao desenhar: " + error);
        }

        // Limpeza
        GLES30.glDisableVertexAttribArray(0);
        GLES30.glDisableVertexAttribArray(1);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    /*
     * =========================================================
     * COMPILAÇÃO DOS SHADERS
     * =========================================================
     */

    private int compileShader(
            int type,
            String shaderCode
    ) {

        int shader = GLES30.glCreateShader(type);

        GLES30.glShaderSource(shader, shaderCode);

        GLES30.glCompileShader(shader);

        int[] compileStatus = new int[1];

        GLES30.glGetShaderiv(
                shader,
                GLES30.GL_COMPILE_STATUS,
                compileStatus,
                0
        );

        if (compileStatus[0] == 0) {

            Log.e(
                    TAG,
                    "Erro ao compilar shader: " +
                            GLES30.glGetShaderInfoLog(shader)
            );

            GLES30.glDeleteShader(shader);

            return 0;
        }

        return shader;
    }

    // Cria a esfera e envia posições e normais
    private void createMarker(float radius) {
        centerOfMassSphere = new Sphere(radius, 16, 16);
        sphereRadiusUsed = radius;

        FloatBuffer vb = centerOfMassSphere.getVertexBuffer();
        int n = centerOfMassSphere.getVertexCount();
        float[] sp = new float[n * 3];
        vb.position(0);
        vb.get(sp);
        vb.position(0);

        float[] sn = new float[n * 3];
        for (int i = 0; i < n; i++) {
            float x = sp[i * 3], y = sp[i * 3 + 1], z = sp[i * 3 + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len == 0) len = 1;
            sn[i * 3] = x / len;
            sn[i * 3 + 1] = y / len;
            sn[i * 3 + 2] = z / len;
        }
        FloatBuffer nb = ByteBuffer.allocateDirect(sn.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        nb.put(sn).position(0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sphereVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, n * 12, vb, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sphereNormalVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, sn.length * 4, nb, GLES30.GL_STATIC_DRAW);
    }
}