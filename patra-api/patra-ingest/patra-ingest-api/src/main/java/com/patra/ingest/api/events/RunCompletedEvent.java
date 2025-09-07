package com.patra.ingest.api.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Map;

/**
 * 运行已完成集成事件
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RunCompletedEvent extends BaseIntegrationEvent {
    
    /**
     * 运行ID
     */
    private Long runId;
    
    /**
     * 任务ID
     */
    private Long jobId;
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 统计信息
     */
    private Map<String, Long> stats;
    
    /**
     * 错误信息
     */
    private String error;
    
    public RunCompletedEvent() {
        super();
        this.setEventType("patra.ingest.run.completed");
    }
    
    public RunCompletedEvent(String aggregateId) {
        this();
        this.setAggregateId(aggregateId);
    }
}
