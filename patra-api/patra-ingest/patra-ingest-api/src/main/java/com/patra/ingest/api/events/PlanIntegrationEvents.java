package com.patra.ingest.api.events;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集计划集成事件
 *
 * @author linqibin
 * @since 0.1.0
 */
public class PlanIntegrationEvents {
    
    /**
     * 计划已创建事件
     */
    @Data
    public static class PlanCreated {
        private Long id;
        private String planKey;
        private String name;
        private String exprHash;
        private LocalDateTime dateFrom;
        private LocalDateTime dateTo;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 计划已激活事件
     */
    @Data
    public static class PlanActivated {
        private Long id;
        private String planKey;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 计划已暂停事件
     */
    @Data
    public static class PlanPaused {
        private Long id;
        private String planKey;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 计划已完成事件
     */
    @Data
    public static class PlanFinished {
        private Long id;
        private String planKey;
        private LocalDateTime occurredAt;
    }
}
