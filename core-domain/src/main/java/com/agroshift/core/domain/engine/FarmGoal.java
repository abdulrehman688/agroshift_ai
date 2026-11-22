package com.agroshift.core.domain.engine;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

/** Immutable farmer priorities. Selected priorities weigh more than the others. */
public final class FarmGoal {
    public enum Priority { WATER, SOIL, INCOME, EROSION, PEST }

    public static final FarmGoal BALANCED = of(Set.of());
    public static final FarmGoal SAVE_WATER = of(Set.of(Priority.WATER));
    public static final FarmGoal BUILD_SOIL = of(Set.of(Priority.SOIL));
    public static final FarmGoal INCOME = of(Set.of(Priority.INCOME));

    private static final double NEUTRAL = 3;
    private static final double SELECTED = 5;
    private static final double OTHER = 2;

    private final Set<Priority> selected;
    final double water;
    final double soil;
    final double erosion;
    final double pest;
    final double income;

    private FarmGoal(Set<Priority> priorities) {
        EnumSet<Priority> copy = EnumSet.noneOf(Priority.class);
        copy.addAll(priorities);
        selected = copy;
        water = weight(Priority.WATER);
        soil = weight(Priority.SOIL);
        erosion = weight(Priority.EROSION);
        pest = weight(Priority.PEST);
        income = weight(Priority.INCOME);
    }

    public static FarmGoal of(Collection<Priority> priorities) {
        return new FarmGoal(priorities.isEmpty() ? Set.of() : EnumSet.copyOf(priorities));
    }

    private double weight(Priority priority) {
        if (selected.isEmpty()) {
            return NEUTRAL;
        }
        return selected.contains(priority) ? SELECTED : OTHER;
    }

    public boolean has(Priority priority) {
        return selected.contains(priority);
    }

    public Set<Priority> priorities() {
        return EnumSet.copyOf(selected.isEmpty() ? EnumSet.noneOf(Priority.class) : selected);
    }

    public String name() {
        if (selected.isEmpty()) {
            return "BALANCED";
        }
        StringJoiner joined = new StringJoiner(",");
        for (Priority priority : selected) {
            joined.add(priority.name());
        }
        return joined.toString();
    }

    public static FarmGoal parse(String value) {
        if (value == null || value.isBlank() || "BALANCED".equals(value)) {
            return BALANCED;
        }
        if ("SAVE_WATER".equals(value)) {
            return SAVE_WATER;
        }
        if ("BUILD_SOIL".equals(value)) {
            return BUILD_SOIL;
        }
        EnumSet<Priority> parsed = EnumSet.noneOf(Priority.class);
        for (String part : value.split(",")) {
            try {
                parsed.add(Priority.valueOf(part.strip()));
            } catch (IllegalArgumentException ignored) {
                return BALANCED;
            }
        }
        return of(parsed);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FarmGoal goal && selected.equals(goal.selected);
    }

    @Override
    public int hashCode() {
        return selected.hashCode();
    }

    @Override
    public String toString() {
        return name();
    }
}
