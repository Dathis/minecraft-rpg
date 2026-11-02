package com.dathis.rpg.modules.region;

import java.util.Locale;

/** Размер участка. Сторона квадрата берётся из config.yml (plots.size-*), высота — вся высота мира. */
enum PlotSize {
    SMALL("малый", 15),
    MEDIUM("средний", 30),
    LARGE("большой", 60);

    final String title;
    final int defaultSide;

    PlotSize(String title, int defaultSide) {
        this.title = title;
        this.defaultSide = defaultSide;
    }

    String configKey() {
        return "plots.size-" + name().toLowerCase(Locale.ROOT);
    }

    static PlotSize parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
