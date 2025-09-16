package com.patra.ingest.app.usecase.command;

import com.patra.common.enums.SortDirection;
import com.patra.ingest.domain.model.enums.CursorType;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 游标规范：定义增量/回填的边界与顺序。
 * 支持 TIME / ID / PAGE / HYBRID。
 */
public record CursorSpec(
        CursorType type,
        Optional<String> field,          // TIME 模式可为时间字段名，如 "PDAT"；若由配置提供则 empty
        SortDirection direction,         // API 返回排序 vs 我们增量扫描顺序要一致
        Optional<String> lastSeenId,     // 解决同时间戳并发/去重
        Optional<Instant> since,         // 起始锚点
        Optional<Instant> until,         // 结束锚点（通常 backfill 用）
        Optional<Duration> timeWindow,   // 滑动窗口（backfill 分片用）
        Optional<Long> idWindow          // ID 范围窗口（当 type=ID/HYBRID 时可用）
) {
    public CursorSpec {
        Objects.requireNonNull(type, "type cannot be null");
        field = field == null ? Optional.empty() : field;
        Objects.requireNonNull(direction, "direction cannot be null");
        lastSeenId = lastSeenId == null ? Optional.empty() : lastSeenId;
        since = since == null ? Optional.empty() : since;
        until = until == null ? Optional.empty() : until;
        timeWindow = timeWindow == null ? Optional.empty() : timeWindow;
        idWindow = idWindow == null ? Optional.empty() : idWindow;

        // 基本规则：窗口参数必须为正
        timeWindow.ifPresent(w -> {
            if (w.isZero() || w.isNegative()) throw new IllegalArgumentException("timeWindow 必须为正时长");
        });
        idWindow.ifPresent(w -> {
            if (w <= 0) throw new IllegalArgumentException("idWindow 必须为正数");
        });
    }

    // 便捷构造：时间游标（最常用）
    public static CursorSpec timeAsc(Optional<String> timeField, Optional<Instant> since, Optional<Instant> until) {
        return new CursorSpec(CursorType.TIME, timeField, SortDirection.ASC, Optional.empty(),
                since, until, Optional.empty(), Optional.empty());
    }

    public static CursorSpec timeDesc(Optional<String> timeField, Optional<Instant> since, Optional<Instant> until) {
        return new CursorSpec(CursorType.TIME, timeField, SortDirection.DESC, Optional.empty(),
                since, until, Optional.empty(), Optional.empty());
    }

    // 便捷构造：混合游标（时间+ID）
    public static CursorSpec hybridAsc(Optional<String> timeField, Optional<String> lastSeenId,
                                       Optional<Instant> since, Optional<Instant> until) {
        return new CursorSpec(CursorType.HYBRID, timeField, SortDirection.ASC, lastSeenId,
                since, until, Optional.empty(), Optional.empty());
    }
}
