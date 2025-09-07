package com.patra.ingest.domain.model.event;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集计划领域事件
 *
 * @author linqibin
 * @since 0.1.0
 */
public class PlanEvents {
    
    /**
     * 计划已激活事件
     */
    @Data
    public static class PlanActivated {
        private final Long planId;
        private final String planKey;
        private final LocalDateTime occurredAt;
        
        public PlanActivated(Long planId, String planKey) {
            this.planId = planId;
            this.planKey = planKey;
            this.occurredAt = LocalDateTime.now();
        }
    }
    
    /**
     * 计划已暂停事件
     */
    @Data
    public static class PlanPaused {
        private final Long planId;
        private final String planKey;
        private final LocalDateTime occurredAt;
        
        public PlanPaused(Long planId, String planKey) {
            this.planId = planId;
            this.planKey = planKey;
            this.occurredAt = LocalDateTime.now();
        }
    }
    
    /**
     * 计划已完成事件
     */
    @Data
    public static class PlanFinished {
        private final Long planId;
        private final String planKey;
        private final LocalDateTime occurredAt;
        
        public PlanFinished(Long planId, String planKey) {
            this.planId = planId;
            this.planKey = planKey;
            this.occurredAt = LocalDateTime.now();
        }
    }
}
