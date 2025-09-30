package com.patra.ingest.app.usecase.relay.support;

/**
 * Outbox 频道常量集合。
 * <p>命名约定：使用层次化点分结构（模块.语义.状态），便于 MQ / Topic / 指标标签统一管理。</p>
 */
public final class OutboxChannels {

    /** 任务已准备就绪（调度生成的 Task 入队等待执行）。 */
    public static final String INGEST_TASK_READY = "ingest.task.ready";

    private OutboxChannels() { }
}
