package com.example.trabalhocalculo3.util;

import static com.example.trabalhocalculo3.mathMotor.ConvertUnits.convertDensity;
import static com.example.trabalhocalculo3.mathMotor.ConvertUnits.convertVolume;
import com.example.trabalhocalculo3.mathMotor.CalculationResult;

// Reúne a lógica de conversão de unidades, tratamentos e formatação
// de strings de resultados
public class ResultFormatter {

    public static double tratarZero(double value) {
        return Math.abs(value) < 1e-9 ? 0.0 : value;
    }

    public static String formatDensityLabel(double density, Unit modelUnit, Unit displayUnit) {
        double displayedDensity = convertDensity(density, modelUnit, displayUnit);
        return String.format("Densidade (kg/%s): %.6f", displayUnit.getVolumeSymbol(), displayedDensity);
    }

    public static String formatResults(CalculationResult result, Unit modelUnit, Unit displayUnit) {
        if (result == null) return "";

        double displayedVolume = convertVolume(result.getRawVolume(), modelUnit, displayUnit);
        double factor = modelUnit.getMetersPerUnit() / displayUnit.getMetersPerUnit();

        double[] cm = result.getRawCenterOfMass();
        double cmX = cm[0] * factor;
        double cmY = cm[1] * factor;
        double cmZ = cm[2] * factor;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Volume: %.9f %s\nMassa: %.2f kg\nCentro de massa: (%.2f, %.2f, %.2f) %s",
                displayedVolume, displayUnit.getVolumeSymbol(), result.getMass(), cmX, cmY, cmZ, displayUnit.getSymbol()));
        sb.append("\nTensor de inércia: ");

        double[][] tensor = result.getInertiaTensor();
        for (int i = 0; i < 3; i++) {
            sb.append(String.format("\n| %.2f %.2f %.2f|",
                    tratarZero(tensor[i][0]), tratarZero(tensor[i][1]), tratarZero(tensor[i][2])));
        }

        return sb.toString();
    }
}