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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.net.Uri;
import android.view.ViewConfiguration;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.trabalhocalculo3.mesh.Mesh3D;
import com.example.trabalhocalculo3.mesh.MeshImporter;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import java.util.EnumSet;
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

    private final ExecutorService importer = Executors.newSingleThreadExecutor();
    private TextView instructions;
    private TextView trackingStatus;
    private Button importButton;
    private boolean hasModel;
    private boolean importing;
    private float touchStartX, touchStartY;
    private boolean moved;
    private SeekBar sizeControl, rotationControl;
    private final ActivityResultLauncher<String[]> openStl = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                String[] units = {"Milímetros (mm)", "Centímetros (cm)", "Metros (m)"};
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Unidade do arquivo STL")
                        .setSingleChoiceItems(units, 0, null)
                        .setPositiveButton("Importar", (dialog, which) -> {
                            int selected = ((androidx.appcompat.app.AlertDialog) dialog)
                                    .getListView().getCheckedItemPosition();
                            importModel(uri, new double[]{0.001, 0.01, 1.0}[selected]);
                        })
                        .setNegativeButton("Cancelar", null).show();
            });

    private void importModel(Uri uri, double metersPerUnit) {
        importing = true;
        importButton.setEnabled(false);
        instructions.setText("Importando STL…");
        importer.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                Mesh3D mesh = MeshImporter.importStl(input);
                float[] vertices = mesh.getPositions();
                if (vertices == null || vertices.length == 0 || vertices.length % 9 != 0)
                    throw new IllegalArgumentException("Arquivo STL vazio ou inválido.");
                float[] center = mesh.getCenter();
                float minZ = Float.POSITIVE_INFINITY;
                for (int i = 2; i < vertices.length; i += 3) minZ = Math.min(minZ, vertices[i]);
                float[] positions = new float[vertices.length];
                for (int i = 0; i < vertices.length; i += 3) {
                    positions[i] = (float) ((vertices[i] - center[0]) * metersPerUnit);
                    positions[i + 1] = (float) ((vertices[i + 2] - minZ) * metersPerUnit);
                    positions[i + 2] = (float) (-(vertices[i + 1] - center[1]) * metersPerUnit);
                }
                if (!(mesh.getBoundingRadius() > 0))
                    throw new IllegalArgumentException("O modelo não possui dimensões válidas.");
                FloatBuffer data = ArRenderer.buffer(positions);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    surfaceView.queueEvent(() -> renderer.setModel(data, positions.length / 3));
                    hasModel = true;
                    importing = false;
                    importButton.setEnabled(true);
                    sizeControl.setProgress(100);
                    rotationControl.setProgress(0);
                    instructions.setText("STL importado em tamanho real. Mova o celular e toque em uma superfície para posicionar.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    importing = false;
                    importButton.setEnabled(true);
                    instructions.setText("Não foi possível importar o STL. " + e.getMessage());
                });
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);

        surfaceView = new GLSurfaceView(this);
        surfaceView.setEGLContextClientVersion(3);
        surfaceView.setPreserveEGLContextOnPause(true);

        renderer = new ArRenderer(this);
        renderer.statusListener = message -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) trackingStatus.setText(message);
        });
        surfaceView.setRenderer(renderer);
        surfaceView.setRenderMode(
                GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        root.addView(surfaceView);

        instructions = new TextView(this);
        instructions.setText(
                "Importe um arquivo STL para visualizar no ambiente."
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

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(20, 8, 20, 12);
        controls.setBackgroundColor(0xCC000000);
        importButton = new Button(this);
        importButton.setText("Importar STL");
        importButton.setOnClickListener(v -> openStl.launch(new String[]{"*/*"}));
        trackingStatus = new TextView(this);
        trackingStatus.setTextColor(Color.WHITE);
        trackingStatus.setText("Iniciando rastreamento da câmera…");
        trackingStatus.setPadding(8, 8, 8, 8);
        controls.addView(trackingStatus);
        controls.addView(importButton);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setTextColor(Color.WHITE);
        sizeLabel.setText("Tamanho: 100% (escala real)");
        controls.addView(sizeLabel);
        sizeControl = new SeekBar(this);
        sizeControl.setMax(200);
        sizeControl.setProgress(100);
        controls.addView(sizeControl);
        TextView rotationLabel = new TextView(this);
        rotationLabel.setTextColor(Color.WHITE);
        rotationLabel.setText("Rotação: 0°");
        controls.addView(rotationLabel);
        rotationControl = new SeekBar(this);
        rotationControl.setMax(360);
        controls.addView(rotationControl);
        sizeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                float scale = (float) Math.pow(10, (value - 100) / 100.0);
                renderer.modelScale = scale;
                sizeLabel.setText(String.format(java.util.Locale.getDefault(), "Tamanho: %.0f%%", scale * 100));
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        rotationControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                renderer.modelRotation = value;
                rotationLabel.setText("Rotação: " + value + "°");
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        root.addView(controls, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);

        surfaceView.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                    moved = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    moved = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(event.getX() - touchStartX, event.getY() - touchStartY)
                            > ViewConfiguration.get(this).getScaledTouchSlop()) moved = true;
                    break;
                case MotionEvent.ACTION_UP:
                    if (!moved && hasModel && !importing) {
                        float x = event.getX(), y = event.getY();
                        surfaceView.queueEvent(() -> renderer.onTap(x, y));
                    }
                    view.performClick();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    moved = true;
                    break;
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

        startSession();
    }

    private void startSession() {
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
                CameraConfigFilter cameraFilter = new CameraConfigFilter(session);
                cameraFilter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30));
                List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraFilter);
                if (!cameraConfigs.isEmpty()) session.setCameraConfig(cameraConfigs.get(0));

                Config config = new Config(session);

                config.setPlaneFindingMode(
                        Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);

                config.setFocusMode(Config.FocusMode.AUTO);
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);

                config.setDepthMode(
                        Config.DepthMode.DISABLED
                );

                session.configure(config);

                renderer.setSession(session);
            }

            session.resume();
            renderer.cameraFailed = false;
            surfaceView.queueEvent(renderer::onSessionResumed);
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
        importer.shutdownNow();
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
            } else if (session == null) {
                startSession();
            }
        }
    }

    private static class ArRenderer
            implements GLSurfaceView.Renderer {

        private final Activity activity;
        private volatile Session session;
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
        private FloatBuffer modelBuffer;
        volatile float modelScale = 1f;
        volatile float modelRotation;
        private volatile boolean cameraFailed;

        interface StatusListener { void onStatus(String message); }
        private StatusListener statusListener;
        private String lastStatus;

        private void reportStatus(String message) {
            if (message.equals(lastStatus)) return;
            lastStatus = message;
            android.util.Log.i("AR_TRACKING", message);
            if (statusListener != null) statusListener.onStatus(message);
        }

        void onSessionResumed() {
            textureSet = false;
            geometryChanged = true;
            tapPending = false;
            lastStatus = null;
        }

        private String trackingMessage(Camera camera) {
            if (camera.getTrackingState() == TrackingState.STOPPED)
                return "Rastreamento encerrado. Saia e abra a realidade aumentada novamente.";
            switch (camera.getTrackingFailureReason()) {
                case INSUFFICIENT_LIGHT:
                    return "Pouca luz. Aponte para uma área mais iluminada e confira se a câmera está descoberta.";
                case INSUFFICIENT_FEATURES:
                    return "Faltam detalhes visuais. Aponte para uma mesa com objetos; evite paredes lisas e reflexos.";
                case EXCESSIVE_MOTION:
                    return "Movimento rápido. Mova o celular devagar, deslocando-o um pouco para os lados.";
                case CAMERA_UNAVAILABLE:
                    return "Câmera indisponível. Feche outros aplicativos que usam a câmera.";
                case BAD_STATE:
                    return "Falha interna do rastreamento. Saia e abra a realidade aumentada novamente.";
                default:
                    return "Inicializando posição. Aponte para uma área iluminada com detalhes e mova o celular para os lados.";
            }
        }

        private int modelVertexCount;
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

        void setModel(FloatBuffer data, int count) {
            modelBuffer = data;
            modelVertexCount = count;
            tapPending = false;
            if (anchor != null) anchor.detach();
            anchor = null;
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
                            + "out vec3 vPosition;\n"
                            + "void main(){vPosition=aPosition;gl_Position=uMVP*vec4(aPosition,1.0);}",

                    "#version 300 es\n"
                            + "precision mediump float;\n"
                            + "out vec4 fragColor;\n"
                            + "in vec3 vPosition;\n"
                            + "void main(){vec3 n=normalize(cross(dFdx(vPosition),dFdy(vPosition)));"
                            + "float light=0.35+0.65*abs(dot(n,normalize(vec3(0.3,0.8,0.5))));"
                            + "fragColor=vec4(vec3(0.1,0.8,0.3)*light,1.0);}"
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
            if (currentSession == null || cameraFailed) {
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



                drawCamera(frame);
                if (camera.getTrackingState() == TrackingState.TRACKING) {
                    reportStatus(anchor == null
                            ? "Rastreamento pronto. Toque na imagem para posicionar o STL."
                            : "Objeto posicionado. Toque para mover; use os controles para ajustar.");
                } else {
                    reportStatus(trackingMessage(camera));
                }

                if (tapPending) {
                    android.util.Log.d("AR_TAP", "Toque pendente. Tracking = "
                            + camera.getTrackingState());

                    tapPending = false;
                    if (camera.getTrackingState() == TrackingState.TRACKING && modelBuffer != null) {
                        placeAnchor(frame);
                    } else {
                        showMessage(trackingMessage(camera));
                    }
                }

                if (camera.getTrackingState() != TrackingState.TRACKING) {
                    return;
                }

                if (anchor != null
                        && anchor.getTrackingState() == TrackingState.TRACKING) {
                    drawModel(frame);
                }

            } catch (CameraNotAvailableException e) {
                cameraFailed = true;
                android.util.Log.e("Calcula3D",
                        "Câmera indisponível", e);
                showMessage("Câmera indisponível. Reinicie a realidade aumentada.");

            } catch (Exception e) {
                reportStatus("Falha na RA: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                android.util.Log.e("Calcula3D",
                        "Erro durante a renderização da RA", e);
            }
        }

        private void drawCamera(Frame frame) {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glUseProgram(cameraProgram);

            quadBuffer.position(0);
            texBuffer.position(0);

            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    quadBuffer,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    texBuffer);

            GLES30.glEnableVertexAttribArray(cameraPosition);
            GLES30.glVertexAttribPointer(
                    cameraPosition, 2, GLES30.GL_FLOAT,
                    false, 0, quadBuffer);

            GLES30.glEnableVertexAttribArray(cameraTexCoord);
            GLES30.glVertexAttribPointer(
                    cameraTexCoord, 2, GLES30.GL_FLOAT,
                    false, 0, texBuffer);

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

                    Anchor nextAnchor = hit.createAnchor();
                    if (anchor != null) anchor.detach();
                    anchor = nextAnchor;
                    showMessage("Objeto posicionado! Toque em outra superfície para mover.");
                    return;
                }
            }

            List<HitResult> approximateHits = frame.hitTestInstantPlacement(tapX, tapY, 1.0f);
            for (HitResult hit : approximateHits) {
                if (hit.getTrackable().getTrackingState() != TrackingState.TRACKING) continue;
                Anchor nextAnchor = hit.createAnchor();
                if (anchor != null) anchor.detach();
                anchor = nextAnchor;
                showMessage("Objeto posicionado com distância aproximada. A posição pode se ajustar ao ambiente.");
                return;
            }
            showMessage("Não foi possível posicionar neste ponto. Aponte para uma área com detalhes e toque novamente.");
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

        private void drawModel(Frame frame) {
            anchor.getPose().toMatrix(model, 0);
            Matrix.rotateM(model, 0, modelRotation, 0, 1, 0);
            Matrix.scaleM(model, 0, modelScale, modelScale, modelScale);

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
                    false, 0, modelBuffer);

            GLES30.glDrawArrays(
                    GLES30.GL_TRIANGLES, 0,
                    modelVertexCount);

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

    }
}
