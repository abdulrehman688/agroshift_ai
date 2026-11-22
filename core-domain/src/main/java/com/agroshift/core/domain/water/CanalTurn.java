package com.agroshift.core.domain.water;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/** A weekly warabandi water turn. */
public record CanalTurn(DayOfWeek day, int startMinute, int durationMinutes, int remindMinutes) {
    private static final int MINUTES_PER_DAY = 24 * 60;

    public CanalTurn {
        if (day == null || startMinute < 0 || startMinute >= MINUTES_PER_DAY
                || durationMinutes <= 0 || durationMinutes > MINUTES_PER_DAY
                || remindMinutes < 0 || remindMinutes > MINUTES_PER_DAY) {
            throw new IllegalArgumentException("Invalid canal turn");
        }
    }

    /** First start strictly after the given time. */
    public LocalDateTime nextStart(LocalDateTime now) {
        LocalDateTime start = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atStartOfDay()
                .plusMinutes(startMinute);
        return start.isAfter(now) ? start : start.plusWeeks(1);
    }

    /** Next reminder, or the start itself if the reminder time has already passed. */
    public LocalDateTime nextAlarm(LocalDateTime now) {
        LocalDateTime start = nextStart(now);
        LocalDateTime reminder = start.minusMinutes(remindMinutes);
        return reminder.isAfter(now) ? reminder : start;
    }

    public boolean isActive(LocalDateTime now) {
        LocalDateTime previous = nextStart(now).minusWeeks(1);
        return !now.isBefore(previous) && now.isBefore(previous.plusMinutes(durationMinutes));
    }
}
