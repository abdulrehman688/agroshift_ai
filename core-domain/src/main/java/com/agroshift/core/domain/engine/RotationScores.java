package com.agroshift.core.domain.engine;

/** Scores from 0 to 100. Higher is better, including climate risk. */
public record RotationScores(double overall, double waterFit, double climateRisk, double soilHealth,
                             double erosion, double pestBreak, double income) {
}
