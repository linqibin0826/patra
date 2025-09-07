package com.patra.ingest.domain.model.event;

import com.patra.ingest.domain.model.vo.RunStats;
import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

/**
 * 运行完成事件
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
public class RunCompletedEvent {
    
    /**
     * 运行ID
     */
    Long runId;
    
    /**
     * 任务ID
     */
    Long jobId;
    
    /**
     * 游标键值
     */
    String cursorKey;
    
    /**
     * 窗口开始时间
     */
    LocalDateTime windowStart;
    
    /**
     * 窗口结束时间
     */
    LocalDateTime windowEnd;
    
    /**
     * 尝试次数
     */
    Integer attemptNo;
    
    /**
     * 是否成功
     */
    Boolean successful;
    
    /**
     * 失败原因（如果失败）
     */
    String error;
    
    /**
     * 统计信息
     */
    RunStats stats;
    
    /**
     * 开始时间
     */
    LocalDateTime startedAt;
    
    /**
     * 结束时间
     */
    LocalDateTime finishedAt;
    
    /**
     * 执行耗时（毫秒）
     */
    Long durationMs;
    
    /**
     * 调度运行ID
     */
    String schedulerRunId;
    
    /**
     * 关联ID
     */
    String correlationId;
    
    /**
     * 事件发生时间
     */
    LocalDateTime occurredAt;
}
