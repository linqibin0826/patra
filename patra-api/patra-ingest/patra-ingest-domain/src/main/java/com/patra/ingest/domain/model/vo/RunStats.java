package com.patra.ingest.domain.model.vo;

/** 任务运行统计。 */
public record RunStats(long fetched, long upserted, long failed, long pages) {
    public static RunStats empty() { return new RunStats(0,0,0,0); }
    public RunStats add(RunStats delta) {
        return new RunStats(fetched + delta.fetched, upserted + delta.upserted, failed + delta.failed, pages + delta.pages);
    }
}
