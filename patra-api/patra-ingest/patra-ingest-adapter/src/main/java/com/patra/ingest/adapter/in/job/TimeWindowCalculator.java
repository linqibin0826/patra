package com.patra.ingest.adapter.in.job;

import java.awt.*;
import java.time.*;

/**
 * 基于来源时区，计算“前一日增量”的 (D-1 00:00, D 00:00] 区间。
 */
public final class TimeWindowCalculator {
    private TimeWindowCalculator() {
    }

    public static Window previousFullDayOpenLeftClosedRight(ZoneId zone, Clock clock) {
        var now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        var todayStart = now.toLocalDate().atStartOfDay(zone);
        var prevStart = todayStart.minusDays(1);
        // 左开右闭：(prevStart, todayStart]
        return new Window(prevStart.toInstant(), todayStart.toInstant(), zone);
    }
}
