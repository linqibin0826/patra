package com.patra.ingest.domain.model.vo;

/**
 * 单批次处理统计。
 * <p>recordCount：本批处理（抓取或写入）记录数，调用方保证非负。</p>
 */
public record BatchStats(int recordCount) {
    /**
     * 快捷构造。
     */
    public static BatchStats of(int count) {
        return new BatchStats(count);
    }
}
