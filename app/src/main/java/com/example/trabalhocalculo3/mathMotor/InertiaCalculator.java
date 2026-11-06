package com.example.trabalhocalculo3.mathMotor;

import com.example.trabalhocalculo3.mesh.Mesh3D;

// Retorna a matriz de inércia 3x3 em relação ao Centro de Massa
public class InertiaCalculator {
    public static double[][] computeInertiaTensor(Mesh3D mesh, double density, double[] centerOfMass) {
        float[] positions = mesh.getPositions();
        if (positions == null || positions.length == 0) {
            return new double[3][3];
        }

        double jxxSum = 0.0, jyySum = 0.0, jzzSum = 0.0;
        double jxySum = 0.0, jxzSum = 0.0, jyzSum = 0.0;
        double totalVolume = 0.0;

        // Decomposição em tetraedros
        for (int i = 0; i < positions.length; i += 9) {
            // Coordenadas dos 3 vértices da face do STL
            double x1 = positions[i],     y1 = positions[i + 1], z1 = positions[i + 2];
            double x2 = positions[i + 3], y2 = positions[i + 4], z2 = positions[i + 5];
            double x3 = positions[i + 6], y3 = positions[i + 7], z3 = positions[i + 8];

            // ===== Integração Local =====
            // Volume orientado do  por produto misto/6
            double v = (1.0 / 6.0) * (
                    x1 * (y2 * z3 - y3 * z2) -
                            y1 * (x2 * z3 - x3 * z2) +
                            z1 * (x2 * y3 - x3 * y2)
            );
            totalVolume += v;

            // Termos quadráticos simples
            double jxx = (v / 10.0) * (x1*x1 + x2*x2 + x3*x3 + x1*x2 + x1*x3 + x2*x3);
            double jyy = (v / 10.0) * (y1*y1 + y2*y2 + y3*y3 + y1*y2 + y1*y3 + y2*y3);
            double jzz = (v / 10.0) * (z1*z1 + z2*z2 + z3*z3 + z1*z2 + z1*z3 + z2*z3);

            // Termos mistos / produtos de inércia
            double jxy = (v / 20.0) * (2*x1*y1 + 2*x2*y2 + 2*x3*y3 + x1*y2 + x2*y1 + x1*y3 + x3*y1 + x2*y3 + x3*y2);
            double jxz = (v / 20.0) * (2*x1*z1 + 2*x2*z2 + 2*x3*z3 + x1*z2 + x2*z1 + x1*z3 + x3*z1 + x2*z3 + x3*z2);
            double jyz = (v / 20.0) * (2*y1*z1 + 2*y2*z2 + 2*y3*z3 + y1*z2 + y2*z1 + y1*z3 + y3*z1 + y2*z3 + y3*z2);
            // ===============================

            jxxSum += jxx;
            jyySum += jyy;
            jzzSum += jzz;
            jxySum += jxy;
            jxzSum += jxz;
            jyzSum += jyz;
        }

        // Cálculo dos momentos e produtos globais na Origem
        double ixxOrigin = density * (jyySum + jzzSum);
        double iyyOrigin = density * (jxxSum + jzzSum);
        double izzOrigin = density * (jxxSum + jyySum);
        double ixyOrigin = density * jxySum;
        double ixzOrigin = density * jxzSum;
        double iyzOrigin = density * jyzSum;

        // Massa total
        double mass = Math.abs(totalVolume) * density;
        double cx = centerOfMass[0];
        double cy = centerOfMass[1];
        double cz = centerOfMass[2];

        // Teorema dos Eixos Paralelos (Steiner) para alinhar ao Centro de Massa
        // subtrai-se o deslocamento do Centro de Massa em relação à origem
        double ixx = ixxOrigin - mass * (cy*cy + cz*cz);
        double iyy = iyyOrigin - mass * (cx*cx + cz*cz);
        double izz = izzOrigin - mass * (cx*cx + cy*cy);
        double ixy = ixyOrigin - mass * cx * cy;
        double ixz = ixzOrigin - mass * cx * cz;
        double iyz = iyzOrigin - mass * cy * cz;

        // Matriz de inércia 3x3
        /* Momentos de Inércia são as componentes da diagonal principal,
         * e representam a resistência escalar do objeto a girar em torno de cada um dos eixos ortogonais (X, Y, Z).
         * Os elementos fora da diagonal são os Produtos de Inércia e medem a assimetria geométrica da distribuição de massa.
         */
        return new double[][]{
                { ixx,  -ixy,  -ixz },
                { -ixy,  iyy,  -iyz },
                { -ixz, -iyz,   izz }
        };
    }
}