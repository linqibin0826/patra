package com.patra.ingest.api.events;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 采集任务集成事件
 *
 * @author linqibin
 * @since 0.1.0
 */
public class JobIntegrationEvents {
    
    /**
     * 任务已调度事件
     */
    @Data
    public static class JobScheduled {
        private Long id;
        private String jobKey;
        private String idempotentKey;
        private Long planId;
        private Long sliceId;
        private LocalDateTime scheduledAt;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 任务已开始事件
     */
    @Data
    public static class JobStarted {
        private Long id;
        private String jobKey;
        private LocalDateTime startedAt;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 任务已成功事件
     */
    @Data
    public static class JobSucceeded {
        private Long id;
        private String jobKey;
        private LocalDateTime finishedAt;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 任务已失败事件
     */
    @Data
    public static class JobFailed {
        private Long id;
        private String jobKey;
        private String error;
        private LocalDateTime finishedAt;
        private LocalDateTime occurredAt;
    }
    
    /**
     * 任务已取消事件
     */
    @Data
    public static class JobCancelled {
        private Long id;
        private String jobKey;
        private LocalDateTime occurredAt;
    }
}
