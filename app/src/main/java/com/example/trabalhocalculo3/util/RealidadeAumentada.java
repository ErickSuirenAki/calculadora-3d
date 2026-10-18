package com.example.trabalhocalculo3.util;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Plane;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class RealidadeAumentada extends AppCompatActivity {

    private static final int CAMERA_PERMISSION = 100;
    private Session session;
    private GLSurfaceView surfaceView;
    private ArRenderer renderer;
    private boolean installRequested = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);

        surfaceView = new GLSurfaceView(this);
        surfaceView.setEGLContextClientVersion(3);

        renderer = new ArRenderer(this);
        surfaceView.setRenderer(renderer);
        surfaceView.setRenderMode(
                GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        root.addView(surfaceView);

        TextView instructions = new TextView(this);
        instructions.setText(
                "Mova o celular para detectar uma superfície.\n"
                        + "Toque na superfície para posicionar o cilindro."
        );
        instructions.setTextColor(Color.WHITE);
        instructions.setTextSize(16);
        instructions.setGravity(Gravity.CENTER);
        instructions.setPadding(24, 20, 24, 20);
        instructions.setBackgroundColor(0x99000000);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                );
        root.addView(instructions, params);

        setContentView(root);

        surfaceView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                renderer.onTap(event.getX(), event.getY());
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION
            );
            return;
        }

        try {
            if (session == null) {
                ArCoreApk.InstallStatus status =
                        ArCoreApk.getInstance().requestInstall(
                                this, installRequested);

                if (status == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = false;
                    return;
                }

                session = new Session(this);

                Config config = new Config(session);

                config.setPlaneFindingMode(
                        Config.PlaneFindingMode.HORIZONTAL);

                // Desativa a API de profundidade para este teste.
                config.setDepthMode(
                        Config.DepthMode.DISABLED
                );

                session.configure(config);

                renderer.setSession(session);
            }

            session.resume();
            surfaceView.onResume();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Não foi possível iniciar a RA: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onPause() {
        if (session != null) {
            surfaceView.onPause();
            session.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length == 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "A permissão da câmera é necessária para a RA.",
                        Toast.LENGTH_LONG
                ).show();
                finish();
            }
            // Se concedida, o Android já chama onResume() sozinho.
        }
    }

    private static class ArRenderer
            implements GLSurfaceView.Renderer {

        private final Activity activity;
        private Session session;
        private int cameraTexture = -1;
        private boolean textureSet = false;
        private int cameraProgram;
        private int objectProgram;

        private int cameraPosition;
        private int cameraTexCoord;
        private int objectPosition;
        private int mvpLocation;

        private FloatBuffer quadBuffer;
        private FloatBuffer texBuffer;
        private FloatBuffer cylinderBuffer;

        private int cylinderVertexCount;
        private Anchor anchor;

        private int surfaceWidth;
        private int surfaceHeight;

        private final float[] projection = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] model = new float[16];
        private final float[] mv = new float[16];
        private final float[] mvp = new float[16];

        private volatile float tapX;
        private volatile float tapY;
        private volatile boolean tapPending;
        private volatile boolean geometryChanged = true;

        private static final float[] QUAD = {
                -1, -1,
                1, -1,
                -1,  1,
                1,  1
        };

        private static final float[] TEX = {
                0, 0, 1, 0, 0, 1, 1, 1
        };

        ArRenderer(Activity activity) {
            this.activity = activity;
        }

        void setSession(Session session) {
            this.session = session;
            this.textureSet = false;
            this.geometryChanged = true;
        }

        void onTap(float x, float y) {
            android.util.Log.d("AR_TAP", "onTap x=" + x + " y=" + y);
            tapX = x;
            tapY = y;
            tapPending = true;
        }

        @Override
        public void onSurfaceCreated(
                GL10 gl, EGLConfig config) {

            cameraProgram = createProgram(
                    "#version 300 es\n"
                            + "in vec2 aPosition;\n"
                            + "in vec2 aTexCoord;\n"
                            + "out vec2 vTexCoord;\n"
                            + "void main(){\n"
                            + " gl_Position=vec4(aPosition,0.0,1.0);\n"
                            + " vTexCoord=aTexCoord;\n"
                            + "}",

                    "#version 300 es\n"
                            + "#extension GL_OES_EGL_image_external_essl3 : require\n"
                            + "precision mediump float;\n"
                            + "uniform samplerExternalOES uTexture;\n"
                            + "in vec2 vTexCoord;\n"
                            + "out vec4 fragColor;\n"
                            + "void main(){fragColor=texture(uTexture,vTexCoord);}"
            );

            objectProgram = createProgram(
                    "#version 300 es\n"
                            + "in vec3 aPosition;\n"
                            + "uniform mat4 uMVP;\n"
                            + "void main(){gl_Position=uMVP*vec4(aPosition,1.0);}",

                    "#version 300 es\n"
                            + "precision mediump float;\n"
                            + "out vec4 fragColor;\n"
                            + "void main(){fragColor=vec4(0.1,0.8,0.3,1.0);}"
            );

            cameraPosition = GLES30.glGetAttribLocation(
                    cameraProgram, "aPosition");
            cameraTexCoord = GLES30.glGetAttribLocation(
                    cameraProgram, "aTexCoord");

            objectPosition = GLES30.glGetAttribLocation(
                    objectProgram, "aPosition");
            mvpLocation = GLES30.glGetUniformLocation(
                    objectProgram, "uMVP");

            quadBuffer = buffer(QUAD);
            texBuffer = buffer(TEX);

            float[] cyl = createCylinder();
            cylinderBuffer = buffer(cyl);
            cylinderVertexCount = cyl.length / 3;

            int[] textures = new int[1];
            GLES30.glGenTextures(1, textures, 0);
            cameraTexture = textures[0];

            GLES30.glBindTexture(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    cameraTexture);

            GLES30.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_LINEAR);
            GLES30.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_LINEAR);

            // Nova textura: precisa ser associada à sessão de novo.
            textureSet = false;

            GLES30.glClearColor(0, 0, 0, 1);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        }

        @Override
        public void onSurfaceChanged(
                GL10 gl, int width, int height) {

            GLES30.glViewport(0, 0, width, height);

            surfaceWidth = width;
            surfaceHeight = height;
            geometryChanged = true;
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES30.glClear(
                    GLES30.GL_COLOR_BUFFER_BIT |
                            GLES30.GL_DEPTH_BUFFER_BIT
            );

            Session currentSession = session;
            if (currentSession == null) {
                return;
            }

            try {
                if (!textureSet && cameraTexture >= 0) {
                    currentSession.setCameraTextureName(cameraTexture);
                    textureSet = true;
                }

                if (geometryChanged && surfaceWidth > 0 && surfaceHeight > 0) {
                    currentSession.setDisplayGeometry(
                            activity.getWindowManager()
                                    .getDefaultDisplay().getRotation(),
                            surfaceWidth,
                            surfaceHeight
                    );
                    geometryChanged = false;
                }

                Frame frame = currentSession.update();
                Camera camera = frame.getCamera();
                android.util.Log.d("AR_PLANES", "Planos: "
                        + currentSession.getAllTrackables(Plane.class).size()
                        + " | Tracking: " + camera.getTrackingState());


                drawCamera(frame);

                if (tapPending) {
                    android.util.Log.d("AR_TAP", "Toque pendente. Tracking = "
                            + camera.getTrackingState());

                    if (camera.getTrackingState() == TrackingState.TRACKING) {
                        tapPending = false;
                        placeAnchor(frame);
                    }
                }

                if (camera.getTrackingState() != TrackingState.TRACKING) {
                    return;
                }

                if (anchor != null
                        && anchor.getTrackingState() == TrackingState.TRACKING) {
                    drawCylinder(frame);
                }

            } catch (CameraNotAvailableException e) {
                android.util.Log.e("Calcula3D",
                        "Câmera indisponível", e);
                showMessage("Câmera indisponível. Reinicie a realidade aumentada.");

            } catch (Exception e) {
                android.util.Log.e("Calcula3D",
                        "Erro durante a renderização da RA", e);
            }
        }

        private void drawCamera(Frame frame) {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glUseProgram(cameraProgram);

            FloatBuffer input = buffer(QUAD);
            FloatBuffer output = buffer(new float[8]);

            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    input,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    output);

            GLES30.glEnableVertexAttribArray(cameraPosition);
            GLES30.glVertexAttribPointer(
                    cameraPosition, 2, GLES30.GL_FLOAT,
                    false, 0, quadBuffer);

            GLES30.glEnableVertexAttribArray(cameraTexCoord);
            GLES30.glVertexAttribPointer(
                    cameraTexCoord, 2, GLES30.GL_FLOAT,
                    false, 0, output);

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    cameraTexture);

            GLES30.glDrawArrays(
                    GLES30.GL_TRIANGLE_STRIP, 0, 4);

            GLES30.glDisableVertexAttribArray(cameraPosition);
            GLES30.glDisableVertexAttribArray(cameraTexCoord);

            GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        }

        private void placeAnchor(Frame frame) {
            List<HitResult> hits = frame.hitTest(tapX, tapY);

            for (HitResult hit : hits) {
                if (!(hit.getTrackable() instanceof Plane)) {
                    continue;
                }

                Plane plane = (Plane) hit.getTrackable();

                if (plane.getTrackingState() == TrackingState.TRACKING
                        && plane.isPoseInPolygon(hit.getHitPose())) {

                    if (anchor != null) {
                        anchor.detach();
                    }

                    anchor = hit.createAnchor();
                    showMessage("Cilindro posicionado!");
                    return;
                }
            }

            showMessage(
                    "Superfície não detectada. Mova o celular lentamente sobre uma mesa."
            );
        }

        private void showMessage(String message) {
            activity.runOnUiThread(() ->
                    Toast.makeText(
                            activity,
                            message,
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }

        private void drawCylinder(Frame frame) {
            anchor.getPose().toMatrix(model, 0);

            frame.getCamera().getViewMatrix(
                    viewMatrix, 0);

            frame.getCamera().getProjectionMatrix(
                    projection, 0, 0.1f, 100f);

            Matrix.multiplyMM(
                    mv, 0, viewMatrix, 0, model, 0);
            Matrix.multiplyMM(
                    mvp, 0, projection, 0, mv, 0);

            GLES30.glUseProgram(objectProgram);
            GLES30.glUniformMatrix4fv(
                    mvpLocation, 1, false, mvp, 0);

            GLES30.glEnableVertexAttribArray(objectPosition);
            GLES30.glVertexAttribPointer(
                    objectPosition, 3, GLES30.GL_FLOAT,
                    false, 0, cylinderBuffer);

            GLES30.glDrawArrays(
                    GLES30.GL_TRIANGLES, 0,
                    cylinderVertexCount);

            GLES30.glDisableVertexAttribArray(objectPosition);
        }

        private static FloatBuffer buffer(float[] data) {
            FloatBuffer result = ByteBuffer
                    .allocateDirect(data.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();

            result.put(data);
            result.position(0);
            return result;
        }

        private static int createProgram(
                String vertexSource, String fragmentSource) {

            int vertex = compileShader(
                    GLES30.GL_VERTEX_SHADER, vertexSource);
            int fragment = compileShader(
                    GLES30.GL_FRAGMENT_SHADER, fragmentSource);

            int program = GLES30.glCreateProgram();
            GLES30.glAttachShader(program, vertex);
            GLES30.glAttachShader(program, fragment);
            GLES30.glLinkProgram(program);

            int[] status = new int[1];
            GLES30.glGetProgramiv(
                    program, GLES30.GL_LINK_STATUS, status, 0);

            if (status[0] == 0) {
                String error = GLES30.glGetProgramInfoLog(program);
                GLES30.glDeleteProgram(program);
                throw new RuntimeException(
                        "Erro ao vincular shaders: " + error);
            }

            GLES30.glDeleteShader(vertex);
            GLES30.glDeleteShader(fragment);
            return program;
        }

        private static int compileShader(
                int type, String source) {

            int shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, source);
            GLES30.glCompileShader(shader);

            int[] status = new int[1];
            GLES30.glGetShaderiv(
                    shader, GLES30.GL_COMPILE_STATUS, status, 0);

            if (status[0] == 0) {
                String error = GLES30.glGetShaderInfoLog(shader);
                GLES30.glDeleteShader(shader);
                throw new RuntimeException(
                        "Erro ao compilar shader: " + error);
            }

            return shader;
        }

        private static float[] createCylinder() {
            java.util.ArrayList<Float> vertices =
                    new java.util.ArrayList<>();

            int segments = 32;
            float radius = 0.08f;
            float height = 0.18f;

            for (int i = 0; i < segments; i++) {
                double a1 = 2.0 * Math.PI * i / segments;
                double a2 = 2.0 * Math.PI * (i + 1) / segments;

                float x1 = (float) (radius * Math.cos(a1));
                float z1 = (float) (radius * Math.sin(a1));
                float x2 = (float) (radius * Math.cos(a2));
                float z2 = (float) (radius * Math.sin(a2));

                // Lateral
                addTriangle(vertices,
                        x1, 0, z1, x2, 0, z2, x2, height, z2);
                addTriangle(vertices,
                        x1, 0, z1, x2, height, z2, x1, height, z1);

                // Base
                addTriangle(vertices,
                        0, 0, 0, x2, 0, z2, x1, 0, z1);

                // Tampa
                addTriangle(vertices,
                        0, height, 0,
                        x1, height, z1,
                        x2, height, z2);
            }

            float[] result = new float[vertices.size()];
            for (int i = 0; i < vertices.size(); i++) {
                result[i] = vertices.get(i);
            }
            return result;
        }

        private static void addTriangle(
                java.util.ArrayList<Float> out,
                float x1, float y1, float z1,
                float x2, float y2, float z2,
                float x3, float y3, float z3) {

            out.add(x1); out.add(y1); out.add(z1);
            out.add(x2); out.add(y2); out.add(z2);
            out.add(x3); out.add(y3); out.add(z3);
        }
    }
}