package com.patra.ingest.adapter.in.job;

import com.patra.ingest.adapter.in.job.model.IngestWindow;

import java.time.*;

/**
 * 基于来源时区，计算“前一日增量”的 [D-1 00:00, D 00:00) 区间（左闭右开）。
 */
public final class TimeWindowCalculator {
    private TimeWindowCalculator() {
    }

    /**
     * 计算前一日的左闭右开时间窗口：[prevStart, todayStart)。
     *
     * @param zone  来源时区
     * @param clock 时钟（便于测试注入）
     * @return Window 窗口（fromInclusive, toExclusive）
     */
    public static IngestWindow previousFullDayClosedLeftOpenRight(ZoneId zone, Clock clock) {
        var now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        var todayStart = now.toLocalDate().atStartOfDay(zone);
        var prevStart = todayStart.minusDays(1);
        // 左闭右开：[prevStart, todayStart)
        return new IngestWindow(prevStart.toInstant(), todayStart.toInstant(), zone);
    }
}
