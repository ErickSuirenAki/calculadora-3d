package com.example.trabalhocalculo3.mathMotor;

// Reúne os resultados dos cálculos
public class CalculationResult {
    private double rawVolume;
    private double mass;
    private double[] rawCenterOfMass;
    private double[][] inertiaTensor;

    public CalculationResult(double rawVolume, double mass, double[] rawCenterOfMass, double[][] inertiaTensor) {
        this.rawVolume = rawVolume;
        this.mass = mass;
        this.rawCenterOfMass = rawCenterOfMass;
        this.inertiaTensor = inertiaTensor;
    }

    public CalculationResult(){
        this.rawVolume = 1.0;
        this.mass = 0.0;
        this.rawCenterOfMass = new double[]{0, 0, 0};
        this.inertiaTensor = new double[][]{{0, 0, 0}, {0, 0, 0}, {0, 0, 0}};
    }

    public double getRawVolume() { return rawVolume; }
    public double getMass() { return mass; }
    public double[] getRawCenterOfMass() { return rawCenterOfMass; }
    public double[][] getInertiaTensor() { return inertiaTensor; }

    public void setInertiaTensor(double[][] inertiaTensor) {
        this.inertiaTensor = inertiaTensor;
    }

    public void setMass(double mass) {
        this.mass = mass;
    }

    public void setRawCenterOfMass(double[] rawCenterOfMass) {
        this.rawCenterOfMass = rawCenterOfMass;
    }

    public void setRawVolume(double rawVolume) {
        this.rawVolume = rawVolume;
    }
}