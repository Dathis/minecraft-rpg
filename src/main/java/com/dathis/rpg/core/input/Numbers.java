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

    /** «90» → 90, «5m» → 300, «1h» → 3600, «2м» → 120 (секунды). -1 если не понятно. */
    public static int parseTime(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        int multiplier = 1;
        if (t.endsWith("h") || t.endsWith("ч")) {
            multiplier = 3600;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m") || t.endsWith("м")) {
            multiplier = 60;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("s") || t.endsWith("с")) {
            t = t.substring(0, t.length() - 1);
        }
        OptionalDouble value = parse(t);
        return value.isPresent() ? (int) Math.round(value.getAsDouble() * multiplier) : -1;
    }

    /** 90 → «90 сек», 300 → «5 мин», 3600 → «1 ч». */
    public static String formatTime(int seconds) {
        if (seconds > 0 && seconds % 3600 == 0) {
            return (seconds / 3600) + " ч";
        }
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + " мин";
        }
        return seconds + " сек";
    }
}
