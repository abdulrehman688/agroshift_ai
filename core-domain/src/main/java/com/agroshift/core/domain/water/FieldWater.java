package com.agroshift.core.domain.water;

import java.time.LocalDate;

/** A field's saved watering date and optional canal turn. */
public record FieldWater(String fieldId, LocalDate lastWatered, CanalTurn turn) {
}
