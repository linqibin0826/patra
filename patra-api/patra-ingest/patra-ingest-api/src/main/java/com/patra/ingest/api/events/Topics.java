package com.patra.ingest.api.events;

/**
 * 采集模块消息主题定义
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class Topics {
    
    // 计划相关主题
    public static final String PLAN_CREATED = "ingest.plan.created";
    public static final String PLAN_ACTIVATED = "ingest.plan.activated";
    public static final String PLAN_PAUSED = "ingest.plan.paused";
    public static final String PLAN_FINISHED = "ingest.plan.finished";
    
    // 任务相关主题
    public static final String JOB_SCHEDULED = "ingest.job.scheduled";
    public static final String JOB_STARTED = "ingest.job.started";
    public static final String JOB_SUCCEEDED = "ingest.job.succeeded";
    public static final String JOB_FAILED = "ingest.job.failed";
    public static final String JOB_CANCELLED = "ingest.job.cancelled";
    
    // 运行相关主题
    public static final String RUN_STARTED = "ingest.run.started";
    public static final String RUN_SUCCEEDED = "ingest.run.succeeded";
    public static final String RUN_FAILED = "ingest.run.failed";
    public static final String RUN_RETRIED = "ingest.run.retried";
    
    // 游标相关主题
    public static final String CURSOR_ADVANCED = "ingest.cursor.advanced";
    
    // 数据相关主题
    public static final String SOURCE_HIT_RECORDED = "ingest.source.hit.recorded";
    public static final String FIELD_PROVENANCE_DERIVED = "ingest.field.provenance.derived";
    public static final String METRIC_SNAPSHOT_UPDATED = "ingest.metric.snapshot.updated";
    
    private Topics() {
        throw new UnsupportedOperationException("Utility class");
    }
}
