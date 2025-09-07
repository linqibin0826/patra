package com.patra.ingest.api.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 运行已创建集成事件
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RunCreatedEvent extends BaseIntegrationEvent {
    
    /**
     * 运行ID
     */
    private Long runId;
    
    /**
     * 任务ID
     */
    private Long jobId;
    
    /**
     * 游标键
     */
    private String cursorKey;
    
    /**
     * 调度运行ID
     */
    private String schedulerRunId;
    
    /**
     * 关联ID
     */
    private String correlationId;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 时间窗口开始
     */
    private LocalDateTime windowStart;
    
    /**
     * 时间窗口结束
     */
    private LocalDateTime windowEnd;
    
    /**
     * 扩展属性
     */
    private Map<String, String> metadata;
    
    public RunCreatedEvent() {
        super();
        this.setEventType("patra.ingest.run.created");
    }
    
    public RunCreatedEvent(String aggregateId) {
        this();
        this.setAggregateId(aggregateId);
    }
}
