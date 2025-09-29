package com.patra.ingest.domain.model.vo;

/**
 * 任务运行统计聚合值对象。
 * <p>语义：记录一次运行过程中的数据采集 + 写入指标。</p>
 * <ul>
 *   <li>fetched：抓取到的原始记录数</li>
 *   <li>upserted：成功写入（插入或更新）记录数</li>
 *   <li>failed：处理失败记录数</li>
 *   <li>pages：分页/批次数（调度或翻页次数）</li>
 * </ul>
 * 不变式：各字段为非负长整型（由调用方保证）。
 */
public record RunStats(long fetched, long upserted, long failed, long pages) {
    /**
     * 空指标（全部为 0）。
     */
    public static RunStats empty() {
        return new RunStats(0, 0, 0, 0);
    }

    /**
     * 累加两个统计量（不可变合成）。
     *
     * @param delta 增量统计
     * @return 新的统计实例
     */
    public RunStats add(RunStats delta) {
        return new RunStats(fetched + delta.fetched, upserted + delta.upserted, failed + delta.failed, pages + delta.pages);
    }
}
