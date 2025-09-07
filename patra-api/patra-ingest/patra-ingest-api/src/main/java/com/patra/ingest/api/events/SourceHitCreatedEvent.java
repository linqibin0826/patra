package com.patra.ingest.api.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Map;

/**
 * 源命中已创建集成事件
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SourceHitCreatedEvent extends BaseIntegrationEvent {
    
    /**
     * 源命中ID
     */
    private Long sourceHitId;
    
    /**
     * 任务ID
     */
    private Long jobId;
    
    /**
     * 运行ID
     */
    private Long runId;
    
    /**
     * 批次ID
     */
    private Long batchId;
    
    /**
     * 源系统
     */
    private String sourceSystem;
    
    /**
     * 源记录ID
     */
    private String sourceRecordId;
    
    /**
     * 命中类型
     */
    private String hitType;
    
    /**
     * 数据哈希
     */
    private String dataHash;
    
    /**
     * 数据大小
     */
    private Long dataSize;
    
    /**
     * 扩展属性
     */
    private Map<String, String> properties;
    
    public SourceHitCreatedEvent() {
        super();
        this.setEventType("patra.ingest.sourcehit.created");
    }
    
    public SourceHitCreatedEvent(String aggregateId) {
        this();
        this.setAggregateId(aggregateId);
    }
}
