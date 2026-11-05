package com.example.trabalhocalculo3.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Sphere {
    private final FloatBuffer vertexBuffer;
    private final int vertexCount;

    public Sphere(float radius, int rings, int sectors) {
        float R = 1.0f / (float) (rings - 1);
        float S = 1.0f / (float) (sectors - 1);

        float[] tempVertices = new float[rings * sectors * 3];
        int vertexIdx = 0;

        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                float y = (float) Math.sin(-Math.PI / 2 + Math.PI * r * R);
                float x = (float) Math.cos(2 * Math.PI * s * S) * (float) Math.sin(Math.PI * r * R);
                float z = (float) Math.sin(2 * Math.PI * s * S) * (float) Math.sin(Math.PI * r * R);

                tempVertices[vertexIdx++] = x * radius;
                tempVertices[vertexIdx++] = y * radius;
                tempVertices[vertexIdx++] = z * radius;
            }
        }

        float[] vertices = new float[(rings - 1) * (sectors - 1) * 6 * 3];
        int idx = 0;

        for (int r = 0; r < rings - 1; r++) {
            for (int s = 0; s < sectors - 1; s++) {
                int current = r * sectors + s;
                int next = (r + 1) * sectors + s;

                // Triângulo 1
                vertices[idx++] = tempVertices[current * 3];
                vertices[idx++] = tempVertices[current * 3 + 1];
                vertices[idx++] = tempVertices[current * 3 + 2];

                vertices[idx++] = tempVertices[next * 3];
                vertices[idx++] = tempVertices[next * 3 + 1];
                vertices[idx++] = tempVertices[next * 3 + 2];

                vertices[idx++] = tempVertices[(current + 1) * 3];
                vertices[idx++] = tempVertices[(current + 1) * 3 + 1];
                vertices[idx++] = tempVertices[(current + 1) * 3 + 2];

                // Triângulo 2
                vertices[idx++] = tempVertices[(current + 1) * 3];
                vertices[idx++] = tempVertices[(current + 1) * 3 + 1];
                vertices[idx++] = tempVertices[(current + 1) * 3 + 2];

                vertices[idx++] = tempVertices[next * 3];
                vertices[idx++] = tempVertices[next * 3 + 1];
                vertices[idx++] = tempVertices[next * 3 + 2];

                vertices[idx++] = tempVertices[(next + 1) * 3];
                vertices[idx++] = tempVertices[(next + 1) * 3 + 1];
                vertices[idx++] = tempVertices[(next + 1) * 3 + 2];
            }
        }

        vertexCount = vertices.length / 3;
        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);
    }

    public FloatBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public int getVertexCount() {
        return vertexCount;
    }
}