package com.patra.ingest.domain.model.vo;

import lombok.Value;

import java.time.LocalDateTime;

/**
 * 时间窗口值对象。
 * <p>
 * 表示一个时间范围，用于切片和调度。
 * 支持开区间、闭区间和半开区间的语义。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
public class TimeWindow {

    /** 窗口起始时间（包含） */
    LocalDateTime from;

    /** 窗口结束时间（不包含） */
    LocalDateTime to;

    /**
     * 创建时间窗口。
     *
     * @param from 起始时间
     * @param to   结束时间
     * @return 时间窗口
     * @throws IllegalArgumentException 如果 from 晚于 to
     */
    public static TimeWindow of(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("窗口起始时间不能晚于结束时间");
        }
        return new TimeWindow(from, to);
    }

    /**
     * 检查窗口是否有效。
     *
     * @return 如果起始和结束时间都不为空且起始时间早于结束时间则返回 true
     */
    public boolean isValid() {
        return from != null && to != null && !from.isAfter(to);
    }

    /**
     * 获取窗口持续时间（毫秒）。
     *
     * @return 持续时间毫秒数，如果窗口无效则返回 0
     */
    public long getDurationMillis() {
        if (!isValid()) {
            return 0;
        }
        return java.time.Duration.between(from, to).toMillis();
    }
}
