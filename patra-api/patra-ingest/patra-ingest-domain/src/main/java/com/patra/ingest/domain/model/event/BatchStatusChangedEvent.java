package com.patra.ingest.domain.model.event;

import com.patra.ingest.domain.model.enums.BatchStatus;
import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

/**
 * 批次状态变更事件
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
public class BatchStatusChangedEvent {
    
    /**
     * 批次ID
     */
    Long batchId;
    
    /**
     * 运行ID
     */
    Long runId;
    
    /**
     * 任务ID
     */
    Long jobId;
    
    /**
     * 批次序号
     */
    Integer batchNo;
    
    /**
     * 旧状态
     */
    BatchStatus oldStatus;
    
    /**
     * 新状态
     */
    BatchStatus newStatus;
    
    /**
     * 失败原因（如果失败）
     */
    String error;
    
    /**
     * 处理记录数
     */
    Long processedCount;
    
    /**
     * 成功记录数
     */
    Long successCount;
    
    /**
     * 失败记录数
     */
    Long failureCount;
    
    /**
     * 跳过记录数
     */
    Long skippedCount;
    
    /**
     * 状态变更时间
     */
    LocalDateTime changedAt;
    
    /**
     * 事件发生时间
     */
    LocalDateTime occurredAt;
}
