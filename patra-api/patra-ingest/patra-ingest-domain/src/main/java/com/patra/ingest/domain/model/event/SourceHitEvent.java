package com.patra.ingest.domain.model.event;

import com.patra.ingest.domain.model.enums.HitType;
import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

/**
 * 数据源命中事件
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
public class SourceHitEvent {
    
    /**
     * 命中记录ID
     */
    Long hitId;
    
    /**
     * 任务ID
     */
    Long jobId;
    
    /**
     * 运行ID
     */
    Long runId;
    
    /**
     * 批次ID
     */
    Long batchId;
    
    /**
     * 源系统标识
     */
    String sourceSystem;
    
    /**
     * 源记录ID
     */
    String sourceRecordId;
    
    /**
     * 命中类型
     */
    HitType hitType;
    
    /**
     * 数据哈希值
     */
    String dataHash;
    
    /**
     * 目标记录ID（处理成功时）
     */
    String targetRecordId;
    
    /**
     * 处理结果
     */
    String result;
    
    /**
     * 错误信息（处理失败时）
     */
    String error;
    
    /**
     * 是否处理成功
     */
    Boolean successful;
    
    /**
     * 窗口开始时间
     */
    LocalDateTime windowStart;
    
    /**
     * 窗口结束时间
     */
    LocalDateTime windowEnd;
    
    /**
     * 事件发生时间
     */
    LocalDateTime occurredAt;
}
