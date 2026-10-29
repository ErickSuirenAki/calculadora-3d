
package com.example.trabalhocalculo3.graphics;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class GraphSurfaceView extends GLSurfaceView {

    private final GraphRenderer renderer;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private float previousX;
    private float previousY;

    public GraphSurfaceView(Context context) {
        this(context, null);
    }

    public GraphSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Configura o OpenGL ES 3.
        setEGLContextClientVersion(3);

        // Cria o renderizador.
        renderer = new GraphRenderer();

        setRenderer(renderer);

        // Mantém a renderização contínua.
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Configura os gestos de zoom.
        scaleDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    @Override
                    public boolean onScale(
                            ScaleGestureDetector detector
                    ) {

                        renderer.zoomBy(
                                detector.getScaleFactor()
                        );

                        return true;
                    }
                }
        );

        // Configura o toque duplo para restaurar a visualização.
        gestureDetector = new GestureDetector(
                context,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent event) {

                        renderer.resetCamera();

                        return true;
                    }
                }
        );
    }

    public GraphRenderer getRenderer() {
        return renderer;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Encaminha os eventos para os detectores de gestos.
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                previousX = event.getX();
                previousY = event.getY();

                return true;

            case MotionEvent.ACTION_POINTER_DOWN:

                previousX = event.getX(0);
                previousY = event.getY(0);

                return true;

            case MotionEvent.ACTION_MOVE:

                // A rotação manual utiliza apenas um dedo.
                if (event.getPointerCount() == 1
                        && !scaleDetector.isInProgress()) {

                    float currentX = event.getX();
                    float currentY = event.getY();

                    float deltaX = currentX - previousX;
                    float deltaY = currentY - previousY;

                    // Movimento horizontal: gira para os lados.
                    // Movimento vertical: gira para cima e para baixo.
                    renderer.rotateBy(
                            deltaX * 0.5f,
                            deltaY * 0.5f
                    );

                    previousX = currentX;
                    previousY = currentY;
                }

                return true;

            case MotionEvent.ACTION_POINTER_UP:

                // Atualiza a posição de referência após
                // um dos dedos sair da tela.
                int remainingPointer =
                        event.getActionIndex() == 0 ? 1 : 0;

                if (remainingPointer < event.getPointerCount()) {

                    previousX = event.getX(remainingPointer);
                    previousY = event.getY(remainingPointer);
                }

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                return true;

            default:

                return true;
        }
    }
}