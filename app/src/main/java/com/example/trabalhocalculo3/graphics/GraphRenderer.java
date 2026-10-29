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
    private int vertexBufferObject;

    private int vertexCount;

    // Rotação controlada pelo usuário.
    private volatile float rotationX = 0.0f;
    private volatile float rotationY = 0.0f;

    // Zoom controlado pelo usuário.
    private volatile float zoomFactor = 1.0f;

    // Limites do zoom.
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 4.0f;

    private Mesh3D mesh;

    private boolean drawLog = false;

    // Matriz final enviada para o shader.
    private final float[] mvpMatrix = new float[16];

    // Matriz de projeção.
    private final float[] projectionMatrix = new float[16];

    // Matriz da câmera.
    private final float[] viewMatrix = new float[16];

    // Matriz do objeto.
    private final float[] modelMatrix = new float[16];

    private final String vertexShaderCode =
            "#version 300 es\n" +
            "layout(location = 0) in vec3 aPosition;\n" +
            "\n" +
            "uniform mat4 uMVPMatrix;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
            "}";

    private final String fragmentShaderCode =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "void main() {\n" +
            "    fragColor = vec4(0.2, 0.7, 1.0, 1.0);\n" +
            "}";

    /*
     * =========================================================
     * RECEBE A MALHA 3D
     * =========================================================
     */

    public void setMesh(Mesh3D mesh) {

        this.mesh = mesh;

        // Restaura os controles ao carregar outro objeto.
        resetCamera();

        if (mesh == null) {
            vertexCount = 0;
            return;
        }

        vertexCount = mesh.getVertexCount();

        Log.d(
                TAG,
                "Mesh recebido: " +
                        vertexCount +
                        " vertices"
        );
    }

    /*
     * =========================================================
     * CONTROLES DA VISUALIZAÇÃO
     * =========================================================
     */

    /**
     * Controla a rotação manual do objeto.
     *
     * deltaX: movimento horizontal do dedo.
     * deltaY: movimento vertical do dedo.
     */
    public void rotateBy(float deltaX, float deltaY) {

        rotationY += deltaX;
        rotationX += deltaY;
    }

    /**
     * Aplica o zoom recebido do gesto de pinça.
     *
     * scaleFactor maior que 1: aproxima.
     * scaleFactor menor que 1: afasta.
     */
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

    /**
     * Restaura a rotação e o zoom iniciais.
     */
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
        GLES30.glEnable(
                GLES30.GL_DEPTH_TEST
        );

        int vertexShader = compileShader(
                GLES30.GL_VERTEX_SHADER,
                vertexShaderCode
        );

        int fragmentShader = compileShader(
                GLES30.GL_FRAGMENT_SHADER,
                fragmentShaderCode
        );

        // Interrompe a inicialização se algum shader falhar.
        if (vertexShader == 0 || fragmentShader == 0) {

            if (vertexShader != 0) {
                GLES30.glDeleteShader(vertexShader);
            }

            if (fragmentShader != 0) {
                GLES30.glDeleteShader(fragmentShader);
            }

            Log.e(TAG, "Não foi possível compilar os shaders.");

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

        // Os shaders já foram anexados ao programa.
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
                    "Erro ao linkar programa: " +
                            GLES30.glGetProgramInfoLog(
                                    shaderProgram
                            )
            );

            GLES30.glDeleteProgram(shaderProgram);

            shaderProgram = 0;

            return;
        }

        // Cria o VBO.
        int[] buffers = new int[1];

        GLES30.glGenBuffers(
                1,
                buffers,
                0
        );

        vertexBufferObject = buffers[0];

        // Inicializa a matriz do objeto.
        Matrix.setIdentityM(
                modelMatrix,
                0
        );
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

        GLES30.glViewport(
                0,
                0,
                width,
                height
        );

        if (width <= 0 || height <= 0) {
            return;
        }

        float aspectRatio =
                (float) width / (float) height;

        // Configuração da perspectiva.
        Matrix.perspectiveM(
                projectionMatrix,
                0,
                45.0f,
                aspectRatio,
                0.1f,
                100.0f
        );

        // Configuração da câmera.
        Matrix.setLookAtM(
                viewMatrix,
                0,

                // Posição da câmera.
                0.0f,
                0.0f,
                3.0f,

                // Ponto para onde olha.
                0.0f,
                0.0f,
                0.0f,

                // Direção para cima.
                0.0f,
                1.0f,
                0.0f
        );
    }

    /*
     * =========================================================
     * DESENHO DO OBJETO 3D
     * =========================================================
     */

    @Override
    public void onDrawFrame(GL10 gl) {

        // Limpa a tela e o buffer de profundidade.
        GLES30.glClear(
                GLES30.GL_COLOR_BUFFER_BIT |
                        GLES30.GL_DEPTH_BUFFER_BIT
        );

        if (shaderProgram == 0) {
            return;
        }

        if (mesh == null) {
            return;
        }

        if (!drawLog) {

            Log.d(
                    TAG,
                    "Iniciando desenho. VertexCount = " +
                            vertexCount
            );

            drawLog = true;
        }

        float[] positions = mesh.getPositions();

        if (positions == null || positions.length == 0) {
            return;
        }

        /*
         * =========================================================
         * TRANSFORMAÇÕES DO OBJETO
         * =========================================================
         *
         * A rotação não é mais automática.
         * Os ângulos são definidos pelos gestos do usuário.
         */

        Matrix.setIdentityM(
                modelMatrix,
                0
        );

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

        // Correção da orientação original do modelo.
        Matrix.rotateM(
                modelMatrix,
                0,
                -90.0f,
                1.0f,
                0.0f,
                0.0f
        );

        // Calcula a escala para enquadrar o objeto.
        float boundingRadius = mesh.getBoundingRadius();

        float targetSize = 1.5f;

        float baseScale = boundingRadius > 0.0f
                ? targetSize / boundingRadius
                : 1.0f;

        // Aplica o zoom do usuário à escala inicial.
        float finalScale = baseScale * zoomFactor;

        Matrix.scaleM(
                modelMatrix,
                0,
                finalScale,
                finalScale,
                finalScale
        );

        // Centraliza o objeto na origem.
        float[] center = mesh.getCenter();

        Matrix.translateM(
                modelMatrix,
                0,
                -center[0],
                -center[1],
                -center[2]
        );

        /*
         * =========================================================
         * CRIA BUFFER DOS VÉRTICES
         * =========================================================
         */

        FloatBuffer vertexBuffer =
                ByteBuffer
                        .allocateDirect(
                                positions.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        vertexBuffer.put(positions);

        vertexBuffer.position(0);

        /*
         * =========================================================
         * ENVIA VÉRTICES PARA A GPU
         * =========================================================
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
         * =========================================================
         * CALCULA A MATRIZ MVP
         * =========================================================
         */

        float[] tempMatrix = new float[16];

        // View × Model.
        Matrix.multiplyMM(
                tempMatrix,
                0,
                viewMatrix,
                0,
                modelMatrix,
                0
        );

        // Projection × View × Model.
        Matrix.multiplyMM(
                mvpMatrix,
                0,
                projectionMatrix,
                0,
                tempMatrix,
                0
        );

        /*
         * =========================================================
         * ATIVA O SHADER
         * =========================================================
         */

        GLES30.glUseProgram(shaderProgram);

        /*
         * =========================================================
         * ENVIA A MATRIZ PARA O SHADER
         * =========================================================
         */

        int matrixHandle =
                GLES30.glGetUniformLocation(
                        shaderProgram,
                        "uMVPMatrix"
                );

        GLES30.glUniformMatrix4fv(
                matrixHandle,
                1,
                false,
                mvpMatrix,
                0
        );

        /*
         * =========================================================
         * CONFIGURA A POSIÇÃO DOS VÉRTICES
         * =========================================================
         */

        GLES30.glEnableVertexAttribArray(0);

        GLES30.glVertexAttribPointer(
                0,
                3,
                GLES30.GL_FLOAT,
                false,
                3 * 4,
                0
        );

        /*
         * =========================================================
         * DESENHA O OBJETO
         * =========================================================
         */

        GLES30.glDrawArrays(
                GLES30.GL_TRIANGLES,
                0,
                vertexCount
        );

        /*
         * =========================================================
         * VERIFICA ERROS OPENGL
         * =========================================================
         */

        int error = GLES30.glGetError();

        if (error != GLES30.GL_NO_ERROR) {

            Log.e(
                    TAG,
                    "Erro OpenGL ao desenhar: " + error
            );
        }

        /*
         * =========================================================
         * LIMPEZA
         * =========================================================
         */

        GLES30.glDisableVertexAttribArray(0);

        GLES30.glBindBuffer(
                GLES30.GL_ARRAY_BUFFER,
                0
        );
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

        GLES30.glShaderSource(
                shader,
                shaderCode
        );

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
}