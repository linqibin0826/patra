package com.patra.ingest.domain.model.vo;

/** 单批次统计。 */
public record BatchStats(int recordCount) {
    public static BatchStats of(int count) { return new BatchStats(count); }
}
