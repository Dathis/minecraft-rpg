package com.dathis.rpg.modules.quest;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Время сброса повторяемых квестов: ежедневные — каждый день в quests.daily-reset,
 * еженедельные — в quests.weekly-reset-day в то же время. Часовой пояс — сервера или quests.timezone.
 */
final class QuestClock {

    private final ZoneId zone;
    private final LocalTime resetTime;
    private final DayOfWeek weeklyDay;

    QuestClock(FileConfiguration config, Logger log) {
        ZoneId z = ZoneId.systemDefault();
        String tz = config.getString("quests.timezone", "");
        if (tz != null && !tz.isBlank()) {
            try {
                z = ZoneId.of(tz.trim());
            } catch (Exception e) {
                log.warning("quests.timezone: неизвестный часовой пояс «" + tz + "», беру часовой пояс сервера.");
            }
        }
        zone = z;
        LocalTime time = LocalTime.MIDNIGHT;
        try {
            time = LocalTime.parse(config.getString("quests.daily-reset", "00:00").trim());
        } catch (Exception e) {
            log.warning("quests.daily-reset: нужно время вида 00:00, беру 00:00.");
        }
        resetTime = time;
        DayOfWeek day = DayOfWeek.MONDAY;
        try {
            day = DayOfWeek.valueOf(config.getString("quests.weekly-reset-day", "MONDAY").trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            log.warning("quests.weekly-reset-day: нужен день недели по-английски (MONDAY), беру MONDAY.");
        }
        weeklyDay = day;
    }

    /** Ближайший ежедневный сброс строго после момента after (мс). */
    long nextDaily(long after) {
        ZonedDateTime t = Instant.ofEpochMilli(after).atZone(zone);
        ZonedDateTime candidate = t.toLocalDate().atTime(resetTime).atZone(zone);
        if (!candidate.isAfter(t)) {
            candidate = t.toLocalDate().plusDays(1).atTime(resetTime).atZone(zone);
        }
        return candidate.toInstant().toEpochMilli();
    }

    /** Ближайший еженедельный сброс строго после момента after (мс). */
    long nextWeekly(long after) {
        ZonedDateTime t = Instant.ofEpochMilli(after).atZone(zone);
        ZonedDateTime candidate = t.toLocalDate().with(TemporalAdjusters.nextOrSame(weeklyDay)).atTime(resetTime).atZone(zone);
        if (!candidate.isAfter(t)) {
            candidate = t.toLocalDate().plusWeeks(1).with(TemporalAdjusters.nextOrSame(weeklyDay))
                    .atTime(resetTime).atZone(zone);
        }
        return candidate.toInstant().toEpochMilli();
    }

    /** Номер «игрового дня»: меняется в момент ежедневного сброса. По нему выбираются задания доски. */
    long dayIndex(long now) {
        ZonedDateTime t = Instant.ofEpochMilli(now).atZone(zone);
        return t.minusSeconds(resetTime.toSecondOfDay()).toLocalDate().toEpochDay();
    }

    /** Когда квест, сданный в момент last, снова можно взять. */
    long availableAgain(Quest quest, long last) {
        return switch (quest.mode) {
            case ONCE -> Long.MAX_VALUE;
            case REPEAT -> last + quest.cooldown * 1000L;
            case DAILY -> nextDaily(last);
            case WEEKLY -> nextWeekly(last);
        };
    }

    /** «2 д 3 ч», «3 ч 20 мин», «15 мин». */
    static String duration(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        long days = minutes / (60 * 24);
        long hours = minutes / 60 % 24;
        long mins = minutes % 60;
        if (days > 0) {
            return days + " д" + (hours > 0 ? " " + hours + " ч" : "");
        }
        if (hours > 0) {
            return hours + " ч" + (mins > 0 ? " " + mins + " мин" : "");
        }
        return mins + " мин";
    }
}
