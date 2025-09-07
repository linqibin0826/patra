package com.patra.ingest.api.events;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 集成事件基类
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
public abstract class BaseIntegrationEvent {
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * 事件类型
     */
    private String eventType;
    
    /**
     * 聚合根ID
     */
    private String aggregateId;
    
    /**
     * 事件发生时间
     */
    private LocalDateTime occurredAt;
    
    /**
     * 事件版本
     */
    private Integer version;
    
    /**
     * 事件来源
     */
    private String source;
    
    protected BaseIntegrationEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
        this.version = 1;
        this.source = "patra-ingest";
    }
}
