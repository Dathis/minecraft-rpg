package com.dathis.rpg.core.input;

import java.util.OptionalDouble;

/** Разбор чисел, введённых в чат: «15», «15%», «2,5», «10-20». */
public final class Numbers {

    private Numbers() {
    }

    public static OptionalDouble parse(String text) {
        try {
            return OptionalDouble.of(Double.parseDouble(text.replace("%", "").replace(',', '.').trim()));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /** «10-20» → {10, 20}; «15» → {15, 15}. null, если не число или меньше нуля. */
    public static double[] parseRange(String text) {
        String[] parts = text.replace(" ", "").split("[-–]");
        if (parts.length == 0 || parts.length > 2) {
            return null;
        }
        OptionalDouble a = parse(parts[0]);
        OptionalDouble b = parts.length > 1 ? parse(parts[1]) : a;
        if (a.isEmpty() || b.isEmpty() || a.getAsDouble() < 0 || b.getAsDouble() < 0) {
            return null;
        }
        return new double[]{Math.min(a.getAsDouble(), b.getAsDouble()), Math.max(a.getAsDouble(), b.getAsDouble())};
    }

    /** 10.0 → "10", 2.5 → "2.5" */
    public static String format(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
